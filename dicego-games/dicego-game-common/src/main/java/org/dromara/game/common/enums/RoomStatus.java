package org.dromara.game.common.enums;

import lombok.Getter;

@Getter
public enum RoomStatus {

    WAITING("waiting", "等待中"),
    PLAYING("playing", "对局中"),
    FINISHED("finished", "已结束"),
    /** 全员待机/房主解散等主动关房：与正常打完的「已结束」区分开 */
    CLOSED("closed", "已关闭");

    private final String code;
    private final String label;

    RoomStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
