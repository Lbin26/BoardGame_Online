package org.dromara.halligalli.service;

/**
 * 行动日志落库载荷（M5）：由房间在翻牌/抢铃结算成功后构造，与 game_action_log 一一对应。
 *
 * <p>roundNo 取"第几次翻牌"；stateVersion 与 actionId 保证 (match_id, action_id) 唯一。
 */
public record HalliGalliActionLogBO(
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
