package org.dromara.dicego.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.dicego.domain.vo.SplendorRankVO;
import org.dromara.dicego.manager.SplendorRoom;
import org.dromara.game.common.match.entity.GameActionLogEntity;
import org.dromara.game.common.match.entity.GameMatchEntity;
import org.dromara.game.common.match.entity.GameMatchPlayerEntity;
import org.dromara.game.common.match.mapper.GameActionLogMapper;
import org.dromara.game.common.match.mapper.GameMatchMapper;
import org.dromara.game.common.match.mapper.GameMatchPlayerMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对局落库（M5，D6）：game_match / game_match_player / game_action_log 三表。
 *
 * <p>设计要点：
 * <ul>
 *   <li>onStart / onFinish 为多行写，@Transactional 保证原子；onAction 单行追加。</li>
 *   <li>所有方法被房间侧 try/catch 兜底：DB 异常只记日志，绝不中断对局（可玩性 > 记账完整性）。</li>
 *   <li>match_no = "M" + 雪花串；room_no 存内存房间号快照（房间会被 TTL 清理）。</li>
 *   <li>room/seat 属内存态生命周期（GameRoomManager 管理），不做房间持久化——
 *       表结构保留给未来的跨进程恢复需求。</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DbSplendorMatchRecorder implements SplendorMatchRecorder {

    private static final String GAME_CODE = "splendor";

    /** 对局状态（0初始化 1进行中 2已结束） */
    private static final String STATUS_PLAYING = "1";
    private static final String STATUS_FINISHED = "2";

    private final GameMatchMapper matchMapper;
    private final GameMatchPlayerMapper matchPlayerMapper;
    private final GameActionLogMapper actionLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onStart(SplendorRoom room, long seed, int playerCount, List<SplendorPlayerRef> players) {
        LocalDateTime now = LocalDateTime.now();
        GameMatchEntity match = new GameMatchEntity();
        match.setMatchNo("M" + IdWorker.getIdStr());
        match.setRoomNo(room.getRoomId());
        match.setGameCode(GAME_CODE);
        match.setPlayerCount(playerCount);
        match.setRandomSeed(seed);
        match.setSeatOrder(players.stream().map(p -> String.valueOf(p.userId())).collect(Collectors.joining(",")));
        match.setMatchStatus(STATUS_PLAYING);
        match.setStartTime(now);
        match.setCreateTime(now);
        matchMapper.insert(match);

        for (SplendorPlayerRef p : players) {
            GameMatchPlayerEntity row = new GameMatchPlayerEntity();
            row.setMatchId(match.getId());
            row.setUserId(p.userId());
            row.setSeatNo(p.seatNo());
            row.setFinalScore(0);
            row.setCardCount(0);
            row.setNobleCount(0);
            row.setRankNo(0);
            row.setWinFlag("0");
            row.setQuitFlag("0");
            row.setCreateTime(now);
            matchPlayerMapper.insert(row);
        }
        room.adoptMatchId(match.getId());
        log.info("房间 {} 开局落库: matchNo={}, seed={}, players={}",
            room.getRoomId(), match.getMatchNo(), seed, playerCount);
    }

    @Override
    public void onAction(SplendorRoom room, SplendorActionLogBO bo) {
        GameActionLogEntity row = new GameActionLogEntity();
        row.setMatchId(bo.matchId());
        row.setRoundNo(bo.roundNo());
        row.setSeatNo(bo.seatNo());
        row.setUserId(bo.userId());
        row.setActionId(bo.actionId());
        row.setActionType(bo.actionType());
        row.setActionParams(bo.paramsJson());
        row.setStateVersion(bo.stateVersion());
        row.setExecuteTime(LocalDateTime.now());
        actionLogMapper.insert(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onFinish(SplendorRoom room, List<SplendorRankVO> rankings) {
        Long matchId = room.getMatchId();
        if (matchId == null || rankings.isEmpty()) {
            log.warn("房间 {} 终局落库跳过: matchId={}", room.getRoomId(), matchId);
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        SplendorRankVO champion = rankings.get(0);
        GameMatchEntity match = new GameMatchEntity();
        match.setId(matchId);
        match.setMatchStatus(STATUS_FINISHED);
        match.setWinnerUserId(room.boundUserIdOf(champion.name()));
        match.setTotalRounds(room.getBoard().getRoundNo());
        match.setEndTime(now);
        matchMapper.updateById(match);

        for (int i = 0; i < rankings.size(); i++) {
            SplendorRankVO r = rankings.get(i);
            matchPlayerMapper.update(null, new LambdaUpdateWrapper<GameMatchPlayerEntity>()
                .eq(GameMatchPlayerEntity::getMatchId, matchId)
                .eq(GameMatchPlayerEntity::getSeatNo, r.seatNo())
                .set(GameMatchPlayerEntity::getFinalScore, r.vp())
                .set(GameMatchPlayerEntity::getCardCount, r.cards())
                .set(GameMatchPlayerEntity::getNobleCount, r.nobleCount())
                .set(GameMatchPlayerEntity::getRankNo, i + 1)
                .set(GameMatchPlayerEntity::getWinFlag, i == 0 ? "1" : "0"));
        }
        log.info("房间 {} 终局落库: matchId={}, 胜者={}({}VP)", room.getRoomId(), matchId,
            champion.name(), champion.vp());
    }
}
