package org.dromara.game.common.enums;

import lombok.Getter;

@Getter
public enum PlayerColor {

    RED("red", "红方"),
    BLACK("black", "黑方");

    private final String code;
    private final String label;

    PlayerColor(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public PlayerColor opponent() {
        return this == RED ? BLACK : RED;
    }
}
