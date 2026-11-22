package org.dromara.magicalathlete.engine.ability;

import org.dromara.magicalathlete.engine.RaceSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 效果管线：优先级、赛道格最高、连锁、循环截断（D8）、硬上限、可重放日志 */
class AbilityPipelineTest {

    private static RaceSession.RacerState st(int seat, String key, int pos) {
        return new RaceSession.RacerState(seat, "p" + seat, key, pos, false, 0, 0);
    }

    private static AbilityContext ctx(List<RaceSession.RacerState> states, int dice) {
        AbilityContext c = new AbilityContext(1, states, List.of(1, 2, 3), dice);
        c.setCurrentActor(1);
        return c;
    }

    @Test
    void registryCoversTwelveRacers() {
        AbilityRegistry reg = StandardAbilities.registry();
        assertEquals(12, reg.coveredRacerCount());
        assertEquals(12, StandardAbilities.ENABLED_KEYS.size());
        for (String key : StandardAbilities.ENABLED_KEYS) {
            assertTrue(reg.supports(key), "牌池开放选手必须有已实现 handler: " + key);
        }
        assertFalse(reg.supports("unknown"));
    }

    @Test
    void priorityTrackBeforeActorBeforeClockwise() {
        // 注册三个同事件 handler：赛道格 / 当前选手 / 对手，验证执行顺序
        AbilityRegistry reg = new AbilityRegistry();
        reg.register("racer1", AbilityEvent.DICE_RESULT,
            c -> List.of(new Effect(EffectType.SCORE, 1, 1, 1, "actor")));
        reg.register("racer2", AbilityEvent.DICE_RESULT,
            c -> List.of(new Effect(EffectType.SCORE, 2, 2, 1, "clockwise")));
        reg.register("racer3", AbilityEvent.DICE_RESULT,
            c -> List.of(new Effect(EffectType.SCORE, Effect.TRACK_SOURCE, 3, 1, "track")));

        AbilityContext c = ctx(List.of(st(1, "racer1", 0), st(2, "racer2", 0), st(3, "racer3", 0)), 3);
        EffectPipeline.ChainResult out = new EffectPipeline(reg).fire(c, AbilityEvent.DICE_RESULT, 1);
        assertEquals(List.of("track", "actor", "clockwise"),
            out.applied().stream().map(Effect::reason).toList());
    }

    @Test
    void followUpChainFromAbilityFired() {
        // 炼金术士骰点替换为 4 属于实际发动 → 补发 ON_ABILITY_FIRED → 蹭蹭怪 +1
        AbilityRegistry reg = StandardAbilities.registry();
        AbilityContext c = ctx(List.of(
            st(1, "alchemist", 0), st(2, "scoocher", 5)), 2);
        EffectPipeline.ChainResult out = new EffectPipeline(reg).fire(c, AbilityEvent.ROLL_REPLACE, 1);
        assertEquals(4, c.base());
        assertEquals(1, c.seat(2).score, "蹭蹭怪因他人能力实际发动 +1");
        assertTrue(out.applied().stream().anyMatch(e -> e.reason().startsWith("scoocher@")));
        assertFalse(out.truncated());
    }

    @Test
    void cycleIsTruncatedOnce() {
        // 真实振荡：MOVE +1 → MOVE −1 → MOVE +1 … 位置与待结算队列全重复 → 第 3 轮截断
        AbilityRegistry reg = new AbilityRegistry();
        reg.register("racer1", AbilityEvent.DICE_RESULT, c ->
            List.of(new Effect(EffectType.MOVE, 1, 1, 1, "oscillate+")));
        reg.register("racer1", AbilityEvent.ON_ABILITY_FIRED, c -> {
            int delta = c.seat(1).position == 0 ? 1 : -1;
            return List.of(new Effect(EffectType.MOVE, 1, 1, delta, "oscillate" + delta));
        });
        AbilityContext c = ctx(List.of(st(1, "racer1", 0), st(2, "racer2", 0)), 3);
        EffectPipeline.ChainResult out = new EffectPipeline(reg).fire(c, AbilityEvent.DICE_RESULT, 1);
        assertTrue(out.truncated());
        assertEquals("CYCLE", out.reason());
        assertTrue(out.applied().size() < EffectPipeline.CHAIN_LIMIT);
    }

    @Test
    void hardLimitCapsRunawayChain() {
        // 状态持续变化（score 每条 +1）→ 指纹永不重复 → 由 200 条硬上限兜底
        AbilityRegistry reg = new AbilityRegistry();
        reg.register("racer1", AbilityEvent.DICE_RESULT, c ->
            List.of(new Effect(EffectType.SCORE, 1, 1, 1, "growing")));
        reg.register("racer1", AbilityEvent.ON_ABILITY_FIRED, c ->
            List.of(new Effect(EffectType.SCORE, 1, 1, 1, "self-follow")));
        AbilityContext c = ctx(List.of(st(1, "racer1", 0), st(2, "racer2", 0)), 3);
        EffectPipeline.ChainResult out = new EffectPipeline(reg).fire(c, AbilityEvent.DICE_RESULT, 1);
        assertTrue(out.truncated());
        assertEquals(EffectPipeline.CHAIN_LIMIT, out.applied().size());
        assertEquals("CHAIN_LIMIT", out.reason());
    }

    @Test
    void appliedEffectsAreReplayableLog() {
        AbilityRegistry reg = StandardAbilities.registry();
        AbilityContext c = ctx(List.of(st(1, "blimp", 10), st(2, "gunk", 3)), 4);
        EffectPipeline.ChainResult out = new EffectPipeline(reg).fire(c, AbilityEvent.BEFORE_MAIN_MOVE, 1);
        List<String> reasons = new ArrayList<>(out.applied().stream().map(Effect::reason).toList());
        assertEquals(2, reasons.size());
        assertTrue(reasons.stream().anyMatch(s -> s.startsWith("blimp@seat1:3")));
        assertTrue(reasons.stream().anyMatch(s -> s.startsWith("gunk@seat2:-1")));
        assertEquals(6, c.mainMoveDistance(), "4 + 3(飞艇 pos10<15) - 1(黏液怪)");
    }

    @Test
    void eliminateAndStealTurnAreApplied() {
        AbilityRegistry reg = new AbilityRegistry();
        reg.register("racer1", AbilityEvent.ON_STOP, c -> {
            List<Effect> e = new ArrayList<>();
            e.add(new Effect(EffectType.ELIMINATE, 1, 2, 0, "elim"));
            e.add(new Effect(EffectType.STEAL_TURN, 1, 2, 0, "steal"));
            return e;
        });
        AbilityContext c = ctx(List.of(st(1, "racer1", 0), st(2, "racer2", 0)), 3);
        new EffectPipeline(reg).fire(c, AbilityEvent.ON_STOP, 1);
        assertTrue(c.seat(2).eliminated);
        assertEquals(1, c.stolenNextActor());
    }
}
