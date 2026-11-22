package org.dromara.game.common.match.vo;

import java.time.LocalDateTime;

/**
 * 个人最近对局（战绩）视图：一行对应 game_match_player + game_match 的合并结果。
 *
 * @param matchNo     对局编号（对外展示）
 * @param gameCode    游戏编码（splendor / xiangqi / halli_galli）
 * @param playerCount 本局人数
 * @param seatNo      我的座位号
 * @param rankNo      我的名次（1=冠军）
 * @param score       我的最终得分（各游戏口径不同）
 * @param win         是否获胜
 * @param status      对局状态（1进行中 2已结束 3异常中止）
 * @param startTime   开局时间
 * @param endTime     结束时间
 */
public record GameMatchRecentVO(
    String matchNo,
    String gameCode,
    int playerCount,
    int seatNo,
    int rankNo,
    int score,
    boolean win,
    String status,
    LocalDateTime startTime,
    LocalDateTime endTime
) {
}
