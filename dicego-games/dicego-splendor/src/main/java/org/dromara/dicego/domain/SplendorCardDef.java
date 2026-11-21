package org.dromara.dicego.domain;

import org.dromara.dicego.enums.GemColor;

import java.util.Map;

/**
 * 发展卡运行时定义（不可变）。由 SplendorCardCache 从 DB 种子加载，
 * 对局只读共享；数值单一事实来源为 design/games/splendor/game.yaml。
 */
public record SplendorCardDef(
    String code,
    int level,
    GemColor bonus,
    int vp,
    Map<GemColor, Integer> cost
) {
    public int costOf(GemColor color) {
        return cost.getOrDefault(color, 0);
    }
}
