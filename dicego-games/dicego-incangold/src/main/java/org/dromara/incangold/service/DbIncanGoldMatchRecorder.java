package org.dromara.incangold.service;

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
import org.dromara.game.common.match.service.GamePlayerStatService;
import org.dromara.incangold.engine.IncanGoldEngine;
import org.dromara.incangold.manager.IncanGoldRoom;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 印加宝藏对局落库：复用全游戏共用的 game_match / game_match_player / game_action_log，
 * 终局再按真实 user_id 汇总到 game_player_stat，让大厅与主页的战绩保持新鲜。
 *
 * <p>字段口径：finalScore = 总分（宝石 + 神器加分），cardCount = 宝石数，
 * nobleCount = 神器数；totalRounds = 固定 5 轮。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DbIncanGoldMatchRecorder implements IncanGoldMatchRecorder {

    private static final String GAME_CODE = "incan_gold";
    private static final String STATUS_PLAYING = "1";
    private static final String STATUS_FINISHED = "2";

    private final GameMatchMapper matchMapper;
    private final GameMatchPlayerMapper matchPlayerMapper;
    private final GameActionLogMapper actionLogMapper;
    private final GamePlayerStatService playerStatService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onStart(IncanGoldRoom room, long seed, int playerCount, List<IncanGoldPlayerRef> players) {
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

        for (IncanGoldPlayerRef p : players) {
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
    public void onAction(IncanGoldRoom room, IncanGoldActionLogBO bo) {
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
    public void onFinish(IncanGoldRoom room, List<IncanGoldEngine.Ranking> rankings) {
        Long matchId = room.getMatchId();
        if (matchId == null || rankings.isEmpty()) {
            log.warn("房间 {} 终局落库跳过: matchId={}", room.getRoomId(), matchId);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        IncanGoldEngine.Ranking champion = rankings.get(0);

        GameMatchEntity match = new GameMatchEntity();
        match.setId(matchId);
        match.setMatchStatus(STATUS_FINISHED);
        match.setWinnerUserId(room.boundUserIdOf(champion.name()));
        match.setTotalRounds(IncanGoldEngine.TOTAL_ROUNDS);
        match.setEndTime(now);
        matchMapper.updateById(match);

        for (int i = 0; i < rankings.size(); i++) {
            IncanGoldEngine.Ranking r = rankings.get(i);
            boolean win = i == 0;
            matchPlayerMapper.update(null, new LambdaUpdateWrapper<GameMatchPlayerEntity>()
                .eq(GameMatchPlayerEntity::getMatchId, matchId)
                .eq(GameMatchPlayerEntity::getSeatNo, r.seatNo())
                .set(GameMatchPlayerEntity::getFinalScore, r.score())
                .set(GameMatchPlayerEntity::getCardCount, r.gems())
                .set(GameMatchPlayerEntity::getNobleCount, r.artifacts())
                .set(GameMatchPlayerEntity::getRankNo, i + 1)
                .set(GameMatchPlayerEntity::getWinFlag, win ? "1" : "0"));
            // 战绩汇总写入已提炼到公共层，全游戏统一口径
            playerStatService.record(room.boundUserIdOf(r.name()), r.score(), win, now);
        }
        log.info("房间 {} 终局落库: matchId={}, 胜者={}({} 分)",
            room.getRoomId(), matchId, champion.name(), champion.score());
    }
}
