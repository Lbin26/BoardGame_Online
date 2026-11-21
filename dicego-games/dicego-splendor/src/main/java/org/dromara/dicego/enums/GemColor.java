package org.dromara.dicego.enums;

import lombok.Getter;

/**
 * Splendor 宝石色。wire 值 = 小写色名（快照/行动协议均用此值）。
 * GOLD 为黄金（万能宝石），仅出现在宝石持有与银行，不作为卡牌加成/费用色。
 */
@Getter
public enum GemColor {
    WHITE("white"),
    BLUE("blue"),
    GREEN("green"),
    RED("red"),
    BLACK("black"),
    GOLD("gold");

    private final String code;

    GemColor(String code) {
        this.code = code;
    }

    public static GemColor fromCode(String code) {
        for (GemColor c : values()) {
            if (c.code.equals(code)) return c;
        }
        throw new IllegalArgumentException("未知宝石色: " + code);
    }
}
