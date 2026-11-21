package org.dromara.xiangqi.enums;

import lombok.Getter;

/** 象棋对局消息类型（原公共 GameMessageType 的象棋专属值迁出；wire 值 = 枚举名，保持兼容） */
@Getter
public enum XiangqiMessageType {

    MOVE("move", "落子"),
    MOVE_VALID("move_valid", "落子有效"),
    MOVE_INVALID("move_invalid", "落子无效"),
    RESIGN("resign", "认输"),
    UNDO("undo", "悔棋"),
    UNDO_REQUEST("undo_request", "悔棋请求"),
    REMATCH("rematch", "重新开始");

    private final String code;
    private final String desc;

    XiangqiMessageType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
