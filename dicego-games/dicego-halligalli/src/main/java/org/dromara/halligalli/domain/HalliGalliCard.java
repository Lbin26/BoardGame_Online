package org.dromara.halligalli.domain;

import org.dromara.halligalli.enums.Fruit;

/**
 * 一张牌：牌型 = 水果 + 该牌面上的水果个数（1-5）。
 * id 仅用于日志与测试定位，不对外暴露。
 */
public record HalliGalliCard(String id, Fruit fruit, int count) {

    public HalliGalliCard {
        if (fruit == null) throw new IllegalArgumentException("水果不能为空");
        if (count < 1 || count > 5) throw new IllegalArgumentException("牌面水果数必须 1-5: " + count);
    }

    public String fruitCode() {
        return fruit.getCode();
    }
}
