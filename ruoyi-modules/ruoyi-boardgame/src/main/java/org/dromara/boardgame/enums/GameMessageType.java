package org.dromara.boardgame.enums;

import lombok.Getter;

@Getter
public enum GameMessageType {

    ROOM_CREATED("room_created", "房间创建"),
    PLAYER_JOINED("player_joined", "玩家加入"),
    SEAT_CHANGED("seat_changed", "座位变更"),
    GAME_START("game_start", "游戏开始"),
    MOVE("move", "落子"),
    MOVE_VALID("move_valid", "落子有效"),
    MOVE_INVALID("move_invalid", "落子无效"),
    GAME_OVER("game_over", "游戏结束"),
    PLAYER_LEAVE("player_leave", "玩家离开"),
    CHAT("chat", "聊天消息"),
    HEARTBEAT("heartbeat", "心跳"),
    RESIGN("resign", "认输"),
    UNDO("undo", "悔棋"),
    UNDO_REQUEST("undo_request", "悔棋请求"),
    REMATCH("rematch", "重新开始");

    private final String code;
    private final String desc;

    GameMessageType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
