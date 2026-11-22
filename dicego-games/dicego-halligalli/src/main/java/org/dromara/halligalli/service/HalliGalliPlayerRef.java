package org.dromara.halligalli.service;

/**
 * 开局时的座位绑定快照（M5 落库传递用）。
 * userId：登录玩家为真实 sys_user.user_id，未绑定为负座位哨兵（如 -1）。
 */
public record HalliGalliPlayerRef(
    int seatNo,
    String name,
    long userId
) {
}
