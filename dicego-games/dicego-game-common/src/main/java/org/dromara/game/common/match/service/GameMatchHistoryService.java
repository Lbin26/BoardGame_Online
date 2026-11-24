package org.dromara.game.common.match.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.game.common.match.entity.GameMatchEntity;
import org.dromara.game.common.match.entity.GameMatchPlayerEntity;
import org.dromara.game.common.match.mapper.GameMatchMapper;
import org.dromara.game.common.match.mapper.GameMatchPlayerMapper;
import org.dromara.game.common.match.vo.GameMatchRecentVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人战绩查询（全游戏共用）：按 game_match_player.user_id 取最近对局，再补 game_match 概要。
 *
 * <p>两条 SQL 即可完成（不需要 XML）：先按主键倒序取玩家行（雪花 ID 递增 ≈ 时间倒序），
 * 再批量取对局主体；三表均为逻辑删除，查询只取 del_flag=0 的活跃记录，
 * 对局已被清理（标记删除）时该行直接跳过，不抛异常。
 */
@Service
@RequiredArgsConstructor
public class GameMatchHistoryService {

    /** 单次最多返回条数，避免前端传大数拖库 */
    public static final int MAX_LIMIT = 50;

    private final GameMatchPlayerMapper matchPlayerMapper;
    private final GameMatchMapper matchMapper;

    public List<GameMatchRecentVO> recent(Long userId, int limit) {
        if (userId == null || userId <= 0) return List.of();
        int size = clampLimit(limit);

        List<GameMatchPlayerEntity> rows = matchPlayerMapper.selectList(
            new LambdaQueryWrapper<GameMatchPlayerEntity>()
                .eq(GameMatchPlayerEntity::getUserId, userId)
                .eq(GameMatchPlayerEntity::getDelFlag, "0")
                .orderByDesc(GameMatchPlayerEntity::getId)
                .last("limit " + size));
        if (rows.isEmpty()) return List.of();

        List<Long> matchIds = rows.stream().map(GameMatchPlayerEntity::getMatchId).distinct().toList();
        Map<Long, GameMatchEntity> matches = new HashMap<>();
        for (GameMatchEntity match : matchMapper.selectList(new LambdaQueryWrapper<GameMatchEntity>()
            .in(GameMatchEntity::getId, matchIds)
            .eq(GameMatchEntity::getDelFlag, "0"))) {
            matches.put(match.getId(), match);
        }

        List<GameMatchRecentVO> result = new ArrayList<>(rows.size());
        for (GameMatchPlayerEntity row : rows) {
            GameMatchEntity match = matches.get(row.getMatchId());
            if (match == null) continue;
            result.add(new GameMatchRecentVO(
                match.getMatchNo(),
                match.getGameCode(),
                match.getPlayerCount() == null ? 0 : match.getPlayerCount(),
                row.getSeatNo() == null ? 0 : row.getSeatNo(),
                row.getRankNo() == null ? 0 : row.getRankNo(),
                row.getFinalScore() == null ? 0 : row.getFinalScore(),
                "1".equals(row.getWinFlag()),
                match.getMatchStatus(),
                match.getStartTime(),
                match.getEndTime()));
        }
        return result;
    }

    /** 条数收敛到 [1, MAX_LIMIT]，避免前端传 0/负数/大数 */
    static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }
}
