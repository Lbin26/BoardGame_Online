package org.dromara.halligalli.enums;

import lombok.Getter;

/**
 * 德国心脏病的四种水果（规则常量，不建表，见 D3）。
 * code 为 wire 值，前端按 code 取素材。
 * 取苹果 / 柠檬 / 樱桃 / 葡萄——重制版的四果组合，与已备好的素材一致。
 */
@Getter
public enum Fruit {

    APPLE("apple", "苹果"),
    LEMON("lemon", "柠檬"),
    CHERRY("cherry", "樱桃"),
    GRAPE("grape", "葡萄");

    private final String code;
    private final String label;

    Fruit(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 未知 code 返回 null（由调用方决定是否报错），避免解析层抛异常打断整局 */
    public static Fruit fromCode(String code) {
        if (code == null) return null;
        for (Fruit fruit : values()) {
            if (fruit.code.equals(code)) return fruit;
        }
        return null;
    }
}
