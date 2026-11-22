package org.dromara.halligalli.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.match.entity.GameActionLogEntity;
import org.dromara.game.common.match.entity.GameMatchEntity;
import org.dromara.game.common.match.entity.GameMatchPlayerEntity;
import org.dromara.game.common.match.mapper.GameActionLogMapper;
import org.dromara.game.common.match.mapper.GameMatchMapper;
import org.dromara.game.common.match.mapper.GameMatchPlayerMapper;
import org.dromara.halligalli.engine.HalliGalliBoard;
import org.dromara.halligalli.manager.HalliGalliRoom;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 德国心脏病对局落库（M5）：复用全游戏共用的 game_match / game_match_player / game_action_log。
 *
 * <p>设计要点：
 * <ul>
 *   <li>onStart / onFinish 多行写，@Transactional 保证原子；onAction 单行追加。</li>
 *   <li>房间侧 try/catch 兜底：DB 异常只记日志，绝不中断对局（可玩性 > 记账完整性）。</li>
 *   <li>字段口径：final_score 与 card_count 都存终局持牌数，noble_count 恒 0；
 *       total_rounds 存本局翻牌次数。</li>
 *   <li>动作类型只有 FLIP / RING；防抖与迟到的抢铃不落库（服务端不认这两次动作）。</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DbHalliGalliMatchRecorder implements HalliGalliMatchRecorder {

    private static final String GAME_CODE = "halli_galli";

    /** 对局状态（0初始化 1进行中 2已结束） */
    private static final String STATUS_PLAYING = "1";
    private static final String STATUS_FINISHED = "2";

    private final GameMatchMapper matchMapper;
    private final GameMatchPlayerMapper matchPlayerMapper;
    private final GameActionLogMapper actionLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onStart(HalliGalliRoom room, long seed, int playerCount, List<HalliGalliPlayerRef> players) {
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

        for (HalliGalliPlayerRef p : players) {
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
    public void onAction(HalliGalliRoom room, HalliGalliActionLogBO bo) {
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
    public void onFinish(HalliGalliRoom room, List<HalliGalliBoard.Rank> rankings) {
        Long matchId = room.getMatchId();
        if (matchId == null || rankings.isEmpty()) {
            log.warn("房间 {} 终局落库跳过: matchId={}", room.getRoomId(), matchId);
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        HalliGalliBoard.Rank champion = rankings.get(0);
        GameMatchEntity match = new GameMatchEntity();
        match.setId(matchId);
        match.setMatchStatus(STATUS_FINISHED);
        match.setWinnerUserId(room.boundUserIdOf(champion.name()));
        match.setTotalRounds(room.getFlipCount());
        match.setEndTime(now);
        matchMapper.updateById(match);

        for (int i = 0; i < rankings.size(); i++) {
            HalliGalliBoard.Rank r = rankings.get(i);
            matchPlayerMapper.update(null, new LambdaUpdateWrapper<GameMatchPlayerEntity>()
                .eq(GameMatchPlayerEntity::getMatchId, matchId)
                .eq(GameMatchPlayerEntity::getSeatNo, r.seatNo())
                .set(GameMatchPlayerEntity::getFinalScore, r.cardCount())
                .set(GameMatchPlayerEntity::getCardCount, r.cardCount())
                .set(GameMatchPlayerEntity::getNobleCount, 0)
                .set(GameMatchPlayerEntity::getRankNo, i + 1)
                .set(GameMatchPlayerEntity::getWinFlag, i == 0 ? "1" : "0"));
        }
        log.info("房间 {} 终局落库: matchId={}, 胜者={}({} 张牌), 翻牌 {} 次",
            room.getRoomId(), matchId, champion.name(), champion.cardCount(), room.getFlipCount());
    }
}
