package org.dromara.incangold.service;

/**
 * 行动日志落库载荷：暗选提交时写一行，与 game_action_log 一一对应。
 */
public record IncanGoldActionLogBO(
    long matchId,
    int roundNo,
    int seatNo,
    long userId,
    String actionId,
    String actionType,
    String paramsJson,
    int stateVersion
) {
}
