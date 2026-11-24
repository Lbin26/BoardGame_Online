package org.dromara.game.common.match.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.match.entity.GameActionLogEntity;
import org.dromara.game.common.match.entity.GameMatchEntity;
import org.dromara.game.common.match.entity.GameMatchPlayerEntity;
import org.dromara.game.common.match.mapper.GameActionLogMapper;
import org.dromara.game.common.match.mapper.GameMatchMapper;
import org.dromara.game.common.match.mapper.GameMatchPlayerMapper;
import org.dromara.game.common.room.GameRoomManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 对局记录定时清理（全游戏共用）。
 *
 * <p>两类记录会被清掉：
 * <ol>
 *   <li>创建时间超过 1 小时的对局（无论状态，含已结束的历史）；</li>
 *   <li>解散/ abandoned 的对局：状态为初始化/进行中，但其内存房间已不存在。</li>
 * </ol>
 * 清理采用逻辑删除（del_flag=1）：三张表都只标记，不物理删除，
 * 便于追溯历史对局；查询侧（GameMatchHistoryService）只查 del_flag=0。
 * 累计战绩 game_player_stat 是聚合数据，不随单局删除。
 *
 * <p>与项目其它定时任务一致，自建 daemon 调度器，不依赖全局 @EnableScheduling。
 */
@Slf4j
@Component
public class GameMatchCleanupTask {

    /** 默认对局最长保留时长（小时），可由 dicego.match.cleanup.max-age-hours 覆盖 */
    public static final int DEFAULT_MAX_AGE_HOURS = 1;

    private static final long INITIAL_DELAY_MIN = 1;
    private static final long INTERVAL_MIN = 5;

    /** 未开局 / 进行中：房间没了就算解散或异常中止 */
    private static final List<String> UNFINISHED_STATUS = List.of("0", "1");

    private final GameMatchMapper matchMapper;
    private final GameMatchPlayerMapper matchPlayerMapper;
    private final GameActionLogMapper actionLogMapper;
    /** 各游戏注册的房间管理器，用于判定「进行中但房间已解散」 */
    private final List<GameRoomManager<?>> roomManagers;

    @Value("${dicego.match.cleanup.max-age-hours:1}")
    private int maxAgeHours = DEFAULT_MAX_AGE_HOURS;

    private ScheduledExecutorService scheduler;

    public GameMatchCleanupTask(GameMatchMapper matchMapper,
                                GameMatchPlayerMapper matchPlayerMapper,
                                GameActionLogMapper actionLogMapper,
                                List<GameRoomManager<?>> roomManagers) {
        this.matchMapper = matchMapper;
        this.matchPlayerMapper = matchPlayerMapper;
        this.actionLogMapper = actionLogMapper;
        this.roomManagers = roomManagers;
    }

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "match-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
            () -> cleanup(LocalDateTime.now()),
            INITIAL_DELAY_MIN, INTERVAL_MIN, TimeUnit.MINUTES);
        log.info("对局记录清理任务启动（超过 {} 小时 / 房间已解散，每 {} 分钟一次）",
            maxAgeHours, INTERVAL_MIN);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    /** 定时入口：保留配置的最大时长；单元测试走下面的显式参数重载 */
    public int cleanup(LocalDateTime now) {
        return cleanup(now, maxAgeHours);
    }

    /**
     * 执行一次清理，返回被删除的对局数。包级可见便于单测注入时间与阈值。
     */
    int cleanup(LocalDateTime now, int ageHours) {
        LocalDateTime cutoff = now.minusHours(Math.max(1, ageHours));

        // 1) 超过保留时长的对局（只查未删除的，避免重复清理）
        List<Long> staleIds = matchMapper.selectList(new LambdaQueryWrapper<GameMatchEntity>()
                .select(GameMatchEntity::getId)
                .eq(GameMatchEntity::getDelFlag, "0")
                .lt(GameMatchEntity::getCreateTime, cutoff))
            .stream().map(GameMatchEntity::getId).toList();

        // 2) 解散/中止：状态未收尾但房间对象已被摘除
        Set<String> activeRooms = roomManagers.stream()
            .flatMap(manager -> manager.listRoomIds().stream())
            .collect(Collectors.toSet());
        List<Long> dissolvedIds = matchMapper.selectList(new LambdaQueryWrapper<GameMatchEntity>()
                .select(GameMatchEntity::getId, GameMatchEntity::getRoomNo)
                .eq(GameMatchEntity::getDelFlag, "0")
                .in(GameMatchEntity::getMatchStatus, UNFINISHED_STATUS))
            .stream()
            .filter(match -> match.getRoomNo() == null || !activeRooms.contains(match.getRoomNo()))
            .map(GameMatchEntity::getId)
            .toList();

        LinkedHashSet<Long> allIds = new LinkedHashSet<>(new ArrayList<>(staleIds));
        allIds.addAll(dissolvedIds);
        if (allIds.isEmpty()) return 0;

        List<Long> ids = List.copyOf(allIds);
        // 逻辑删除：三张表统一标记 del_flag=1，主表最后标记
        matchPlayerMapper.update(null, new LambdaUpdateWrapper<GameMatchPlayerEntity>()
            .set(GameMatchPlayerEntity::getDelFlag, "1")
            .eq(GameMatchPlayerEntity::getDelFlag, "0")
            .in(GameMatchPlayerEntity::getMatchId, ids));
        actionLogMapper.update(null, new LambdaUpdateWrapper<GameActionLogEntity>()
            .set(GameActionLogEntity::getDelFlag, "1")
            .eq(GameActionLogEntity::getDelFlag, "0")
            .in(GameActionLogEntity::getMatchId, ids));
        matchMapper.update(null, new LambdaUpdateWrapper<GameMatchEntity>()
            .set(GameMatchEntity::getDelFlag, "1")
            .eq(GameMatchEntity::getDelFlag, "0")
            .in(GameMatchEntity::getId, ids));
        log.info("对局记录逻辑删除: 标记 {} 局（超时 {} / 解散 {}）",
            ids.size(), staleIds.size(), dissolvedIds.size());
        return ids.size();
    }
}
