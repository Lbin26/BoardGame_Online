package org.dromara.game.common.enums;

import lombok.Getter;

@Getter
public enum RoomStatus {

    WAITING("waiting", "等待中"),
    PLAYING("playing", "对局中"),
    FINISHED("finished", "已结束");

    private final String code;
    private final String label;

    RoomStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
