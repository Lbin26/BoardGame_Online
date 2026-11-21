package org.dromara.xiangqi.enums;

import lombok.Getter;

/** 象棋阵营：红/黑（象棋专属语义，不入公共层） */
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
