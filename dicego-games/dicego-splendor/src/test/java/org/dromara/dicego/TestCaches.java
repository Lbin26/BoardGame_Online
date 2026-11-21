package org.dromara.dicego;

import org.dromara.dicego.domain.SplendorCardDef;
import org.dromara.dicego.domain.SplendorNobleDef;
import org.dromara.dicego.enums.GemColor;
import org.dromara.dicego.service.SplendorCardCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用合成卡池（不依赖 DB）：每级 perLevel 张卡 + count 个贵族。
 * 卡面数值无规则含义，仅保证发牌/快照逻辑可断言。
 */
public final class TestCaches {

    private TestCaches() {
    }

    public static SplendorCardCache cache(int perLevel, int nobleCount) {
        GemColor[] colors = {GemColor.WHITE, GemColor.BLUE, GemColor.GREEN, GemColor.RED, GemColor.BLACK};
        Map<Integer, List<SplendorCardDef>> byLevel = new HashMap<>();
        for (int level = 1; level <= 3; level++) {
            List<SplendorCardDef> cards = new ArrayList<>();
            for (int i = 1; i <= perLevel; i++) {
                cards.add(new SplendorCardDef(
                    "l" + level + "-" + String.format("%03d", i), level, colors[i % colors.length],
                    i % 5, Map.of(GemColor.WHITE, 1, GemColor.BLUE, i % 3)));
            }
            byLevel.put(level, List.copyOf(cards));
        }
        List<SplendorNobleDef> nobles = new ArrayList<>();
        for (int i = 1; i <= nobleCount; i++) {
            nobles.add(new SplendorNobleDef("noble-" + String.format("%02d", i), null, 3,
                Map.of(GemColor.WHITE, 4, GemColor.BLUE, 4)));
        }
        return new SplendorCardCache(byLevel, nobles);
    }
}
