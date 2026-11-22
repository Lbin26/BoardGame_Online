package org.dromara.halligalli.domain;

import org.dromara.halligalli.enums.Fruit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 56 张牌的构造器（D3：牌型是规则常量，不建表、不做种子）。
 *
 * <p>每种水果 14 张，牌面数量固定分布：1 个×5、2 个×3、3 个×3、4 个×2、5 个×1。
 * 4 种水果 → 56 张；水果面合计 4×(5+6+9+8+5)=132。
 */
public final class HalliGalliDeck {

    /** 下标为牌面水果数，值为该牌型张数（0 位占位） */
    private static final int[] COPIES_BY_COUNT = {0, 5, 3, 3, 2, 1};

    public static final int CARDS_PER_FRUIT = 14;
    public static final int TOTAL_CARDS = 56;
    public static final int TOTAL_FRUIT_FACES = 132;

    private HalliGalliDeck() {
    }

    /** 未洗的完整牌堆：按水果、牌面数量升序生成，便于测试断言 */
    public static List<HalliGalliCard> build() {
        List<HalliGalliCard> cards = new ArrayList<>(TOTAL_CARDS);
        for (Fruit fruit : Fruit.values()) {
            for (int count = 1; count <= 5; count++) {
                for (int seq = 1; seq <= COPIES_BY_COUNT[count]; seq++) {
                    cards.add(new HalliGalliCard(fruit.getCode() + "-" + count + "-" + seq, fruit, count));
                }
            }
        }
        return cards;
    }

    /** 洗牌：seed 驱动，便于复盘与测试确定性 */
    public static List<HalliGalliCard> shuffled(long seed) {
        List<HalliGalliCard> cards = build();
        Collections.shuffle(cards, new Random(seed));
        return cards;
    }
}
