package org.dromara.halligalli.domain;

import org.dromara.halligalli.enums.Fruit;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 牌堆常量校验（对应 design/games/halli-galli 数据集）：56 张、每果 14 张、5/3/3/2/1 分布、132 水果面。
 */
class HalliGalliDeckTest {

    @Test
    void 共五十六张且每种水果十四张() {
        List<HalliGalliCard> cards = HalliGalliDeck.build();
        assertEquals(HalliGalliDeck.TOTAL_CARDS, cards.size());

        Map<Fruit, Integer> perFruit = new EnumMap<>(Fruit.class);
        for (HalliGalliCard card : cards) {
            perFruit.merge(card.fruit(), 1, Integer::sum);
        }
        assertEquals(4, perFruit.size());
        perFruit.forEach((fruit, count) -> assertEquals(HalliGalliDeck.CARDS_PER_FRUIT, count, fruit.getCode()));
    }

    @Test
    void 每种水果的牌面数量分布为五三三二一() {
        Map<Integer, Integer> perCount = new java.util.HashMap<>();
        HalliGalliDeck.build().stream()
            .filter(card -> card.fruit() == Fruit.APPLE)
            .forEach(card -> perCount.merge(card.count(), 1, Integer::sum));
        assertEquals(5, perCount.get(1));
        assertEquals(3, perCount.get(2));
        assertEquals(3, perCount.get(3));
        assertEquals(2, perCount.get(4));
        assertEquals(1, perCount.get(5));
    }

    @Test
    void 水果面合计一百三十二() {
        int faces = HalliGalliDeck.build().stream().mapToInt(HalliGalliCard::count).sum();
        assertEquals(HalliGalliDeck.TOTAL_FRUIT_FACES, faces);
    }

    @Test
    void 洗牌不增不减且种子可复现() {
        List<HalliGalliCard> a = HalliGalliDeck.shuffled(7L);
        List<HalliGalliCard> b = HalliGalliDeck.shuffled(7L);
        assertEquals(a, b);
        assertEquals(HalliGalliDeck.TOTAL_CARDS, a.size());
        assertEquals(HalliGalliDeck.TOTAL_CARDS, a.stream().map(HalliGalliCard::id).distinct().count());
        assertTrue(a.containsAll(HalliGalliDeck.build()));
    }
}
