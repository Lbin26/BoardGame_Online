package org.dromara.magicalathlete.engine.ability;

import org.dromara.magicalathlete.engine.RaceSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M5 copy 委托：模仿猫动态复制领先者、蛋蛋/双胞胎赛前固定委托、
 * 管线按委托后的 effectiveKey 取 handler（copy 架构命门）、委托防递归。
 */
class CopyDelegationTest {

    private static final AbilityRegistry REG = FullAbilities.registry();

    private static RaceSession.RacerState st(int seat, String key, int pos) {
        return new RaceSession.RacerState(seat, "p" + seat, key, pos, false, 0, 0);
    }

    private static AbilityContext ctx(List<RaceSession.RacerState> states, int actor) {
        AbilityContext c = new AbilityContext(1, states, List.of(1, 2, 3), 3);
        c.setCurrentActor(actor);
        return c;
    }

    @Test
    void pipelineUsesDelegatedKeyNotOwnKey() {
        // 座位 1 是模仿猫（自身无 handler），委托给领先者的大长腿 → 应触发 REPLACE_BASE=5
        AbilityContext c = ctx(List.of(st(1, "copycat", 0), st(2, "legs", 20)), 1);
        c.delegate(1, "legs", 2);
        EffectPipeline.ChainResult r = new EffectPipeline(REG).fire(c, AbilityEvent.ROLL_REPLACE, 1);
        assertTrue(r.applied().stream().anyMatch(e -> e.type() == EffectType.REPLACE_BASE && e.amount() == 5),
            "管线按委托 key=legs 取到 handler，而非 copycat 自身");
        assertEquals(1, r.applied().stream().filter(e -> e.type() == EffectType.REPLACE_BASE).findFirst()
            .orElseThrow().sourceSeat(), "能力归属座位仍是复制者本人");
    }

    @Test
    void mimicCopiesCurrentLeader() {
        // 座位 2 位置更靠前且是普通能力 → 模仿猫委托给它
        AbilityContext c = ctx(List.of(st(1, "copycat", 5), st(2, "blimp", 20), st(3, "dummy", 10)), 1);
        Map<Integer, String> applied = CopyDelegation.refreshMimic(c);
        assertEquals("blimp", applied.get(1));
        assertEquals("blimp", c.seat(1).effectiveKey);
        assertEquals(2, c.seat(1).copyFromSeat);
    }

    @Test
    void mimicSkipsCopyKeysToAvoidRecursion() {
        // 领先者是双胞胎（copy 类）→ 模仿猫跳过，取下一个普通能力者
        AbilityContext c = ctx(List.of(st(1, "copycat", 0), st(2, "twin", 30), st(3, "hare", 12)), 1);
        CopyDelegation.refreshMimic(c);
        assertEquals("hare", c.seat(1).effectiveKey, "不复制另一个 copy 类，避免键循环");
    }

    @Test
    void mimicDoesNotDelegateWhenSelfLeads() {
        AbilityContext c = ctx(List.of(st(1, "copycat", 30), st(2, "hare", 10)), 1);
        CopyDelegation.refreshMimic(c);
        assertEquals("copycat", c.seat(1).effectiveKey, "自己是领先者则无复制目标，冲突原能力优先");
    }

    @Test
    void fixedDelegationForEggAndTwin() {
        AbilityContext c = ctx(List.of(st(1, "egg", 2), st(2, "sisyphus", 3)), 1);
        CopyDelegation.bindFixed(c, 1, "sisyphus", 2);
        // 蛋蛋复制西西弗斯：赛前 +4 应通过委托生效
        EffectPipeline.ChainResult r = new EffectPipeline(REG).fire(c, AbilityEvent.BEFORE_RACE, 1);
        assertTrue(r.applied().stream().anyMatch(e -> e.type() == EffectType.SCORE && e.amount() == 4),
            "复制来的赛前能力（西西弗斯 +4）生效");
        assertEquals(4, c.seat(1).score);
    }

    @Test
    void fixedDelegationRejectsCopySource() {
        AbilityContext c = ctx(List.of(st(1, "twin", 2), st(2, "egg", 3)), 1);
        CopyDelegation.bindFixed(c, 1, "egg", 2); // 源是 copy 类 → 拒绝
        assertEquals("twin", c.seat(1).effectiveKey, "固定委托不接受 copy 类作为来源，防递归");
    }

    @Test
    void copyIsKeyClassifiedCorrectly() {
        assertTrue(CopyDelegation.isCopyKey("copycat"));
        assertTrue(CopyDelegation.isCopyKey("egg"));
        assertTrue(CopyDelegation.isCopyKey("twin"));
        assertFalse(CopyDelegation.isCopyKey("hare"));
    }
}
