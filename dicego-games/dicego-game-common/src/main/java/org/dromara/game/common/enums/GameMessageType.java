package org.dromara.game.common.enums;

import lombok.Getter;

/**
 * 公共消息类型：仅房间生命周期 / 聊天 / 心跳 / 胜负 / 错误。
 * 各游戏的对局消息自建枚举（如 xiangqi 的 XiangqiMessageType），经 GameMessage.type 字符串透传。
 */
@Getter
public enum GameMessageType {

    ROOM_CREATED("room_created", "房间创建"),
    PLAYER_JOINED("player_joined", "玩家加入"),
    SEAT_CHANGED("seat_changed", "座位变更"),
    GAME_START("game_start", "游戏开始"),
    GAME_OVER("game_over", "游戏结束"),
    PLAYER_LEAVE("player_leave", "玩家离开"),
    PLAYER_AFK("player_afk", "玩家待机状态变更"),
    ROOM_CLOSED("room_closed", "房间关闭"),
    CHAT("chat", "聊天消息"),
    HEARTBEAT("heartbeat", "心跳"),
    ERROR("error", "连接或请求错误");

    private final String code;
    private final String desc;

    GameMessageType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
