package org.dromara.game.common.match.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.match.entity.GamePlayerStatEntity;
import org.dromara.game.common.match.mapper.GamePlayerStatMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 玩家战绩汇总写入（全游戏共用）。
 *
 * <p>各游戏终局落库只需调用 {@link #record(long, int, boolean, LocalDateTime)}，
 * 由本服务统一维护 game_player_stat 的累加口径，避免每个游戏各写一遍 upsert。
 *
 * <p>字段口径：totalMatches 累加 1；win 决定 winMatches / loseMatches 之一加 1；
 * highestScore 取历史最大；totalScore 累加；lastPlayTime / updateTime 置为本次终局时间。
 * 游客（userId &lt;= 0，房间侧用负数哨兵）不写本表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GamePlayerStatService {

    private final GamePlayerStatMapper playerStatMapper;

    /**
     * 对局完成后累加一名玩家的战绩。
     *
     * @param userId 真实 sys_user.user_id；&lt;= 0 的游客直接跳过
     * @param score  本局得分（用于最高分与累计分）
     * @param win    是否获胜（第一名）
     * @param now    本局结束时间，由调用方统一传入，保证一局内多行时间一致
     */
    @Transactional(rollbackFor = Exception.class)
    public void record(long userId, int score, boolean win, LocalDateTime now) {
        if (userId <= 0) return;
        GamePlayerStatEntity stat = playerStatMapper.selectOne(new LambdaQueryWrapper<GamePlayerStatEntity>()
            .eq(GamePlayerStatEntity::getUserId, userId)
            .eq(GamePlayerStatEntity::getDelFlag, "0"));
        if (stat == null) {
            GamePlayerStatEntity row = new GamePlayerStatEntity();
            row.setUserId(userId);
            row.setTotalMatches(1);
            row.setWinMatches(win ? 1 : 0);
            row.setLoseMatches(win ? 0 : 1);
            row.setHighestScore(score);
            row.setTotalScore((long) score);
            row.setLastPlayTime(now);
            row.setDelFlag("0");
            row.setCreateTime(now);
            row.setUpdateTime(now);
            playerStatMapper.insert(row);
            return;
        }
        playerStatMapper.update(null, new LambdaUpdateWrapper<GamePlayerStatEntity>()
            .eq(GamePlayerStatEntity::getId, stat.getId())
            .setSql("total_matches = total_matches + 1")
            .setSql(win ? "win_matches = win_matches + 1" : "lose_matches = lose_matches + 1")
            .setSql("highest_score = GREATEST(highest_score, " + score + ")")
            .setSql("total_score = total_score + " + score)
            .set(GamePlayerStatEntity::getLastPlayTime, now)
            .set(GamePlayerStatEntity::getUpdateTime, now));
    }
}
