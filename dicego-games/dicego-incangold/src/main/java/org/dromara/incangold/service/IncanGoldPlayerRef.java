package org.dromara.incangold.service;

/**
 * 开局座位绑定快照（落库传递用）。
 * userId：登录玩家为真实 sys_user.user_id，未绑定为负座位哨兵。
 */
public record IncanGoldPlayerRef(
    int seatNo,
    String name,
    long userId
) {
}
