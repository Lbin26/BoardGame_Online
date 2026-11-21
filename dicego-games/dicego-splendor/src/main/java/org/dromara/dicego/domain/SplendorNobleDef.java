package org.dromara.dicego.domain;

import org.dromara.dicego.enums.GemColor;

import java.util.Map;

/**
 * 贵族运行时定义（不可变）。需求为五色加成卡数量（不含黄金）。
 */
public record SplendorNobleDef(
    String code,
    String name,
    int vp,
    Map<GemColor, Integer> need
) {
    public int needOf(GemColor color) {
        return need.getOrDefault(color, 0);
    }
}
