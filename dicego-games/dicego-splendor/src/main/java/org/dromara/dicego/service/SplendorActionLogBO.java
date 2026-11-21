package org.dromara.dicego.service;

/**
 * 行动日志落库载荷（M5）：由房间在行动结算成功后构造，与 game_action_log 一一对应。
 */
public record SplendorActionLogBO(
    Long matchId,
    int roundNo,
    int seatNo,
    long userId,
    String actionType,
    String actionId,
    String paramsJson,
    int stateVersion
) {
}
