package org.dromara.dicego.service;

import lombok.Getter;
import org.dromara.dicego.domain.SplendorCardDef;
import org.dromara.dicego.domain.SplendorNobleDef;

import java.util.List;
import java.util.Map;

/**
 * 卡牌/贵族只读缓存（D4：DB 种子 → 启动加载 → 每局从缓存发牌）。
 * 不可变；由 SplendorDataConfig 启动时构建并校验，单测可直接 new。
 */
@Getter
public class SplendorCardCache {

    /** key = 卡牌等级 1/2/3 */
    private final Map<Integer, List<SplendorCardDef>> cardsByLevel;
    private final List<SplendorNobleDef> nobles;

    public SplendorCardCache(Map<Integer, List<SplendorCardDef>> cardsByLevel, List<SplendorNobleDef> nobles) {
        this.cardsByLevel = cardsByLevel;
        this.nobles = List.copyOf(nobles);
    }

    public List<SplendorCardDef> cardsOfLevel(int level) {
        return cardsByLevel.getOrDefault(level, List.of());
    }

    public int totalCards() {
        return cardsByLevel.values().stream().mapToInt(List::size).sum();
    }
}
