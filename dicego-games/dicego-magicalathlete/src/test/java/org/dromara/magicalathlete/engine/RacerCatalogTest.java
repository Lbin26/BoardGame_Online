package org.dromara.magicalathlete.engine;

import org.dromara.magicalathlete.domain.Racer;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 36 名选手数据集断言（对齐 game.yaml，防止数据集与实现漂移） */
class RacerCatalogTest {

    @Test
    void catalogHas36UniqueRacers() {
        List<Racer> all = RacerCatalog.all();
        assertEquals(36, all.size());
        Set<String> keys = new HashSet<>();
        for (Racer r : all) {
            assertTrue(keys.add(r.key()), "key 必须唯一: " + r.key());
            assertFalse(r.name().isBlank());
            assertFalse(r.abilityType().isBlank());
        }
    }

    @Test
    void allAbilitiesAreKnownTypes() {
        Set<String> types = Set.of("fixed-move", "mod-move", "interrupt", "reactive", "scoring", "warp", "copy");
        for (Racer r : RacerCatalog.all()) {
            assertTrue(types.contains(r.abilityType()), r.key() + " 的能力分类非法: " + r.abilityType());
        }
    }

    @Test
    void shuffledKeepsAllAndIsSeedDeterministic() {
        List<Racer> a = RacerCatalog.shuffled(12345L);
        List<Racer> b = RacerCatalog.shuffled(12345L);
        assertEquals(36, a.size());
        assertEquals(a, b, "同 seed 洗牌结果必须一致");
        assertEquals(36, a.stream().map(Racer::key).distinct().count());
    }

    @Test
    void lookupByName() {
        assertTrue(RacerCatalog.exists("alchemist"));
        assertEquals("炼金术士", RacerCatalog.nameOf("alchemist"));
        assertEquals("unknown", RacerCatalog.nameOf("unknown"));
    }
}
