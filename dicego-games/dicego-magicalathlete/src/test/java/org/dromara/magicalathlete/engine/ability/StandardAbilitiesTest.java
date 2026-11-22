package org.dromara.magicalathlete.engine.ability;

import org.dromara.magicalathlete.engine.RaceSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 12 名代表选手能力逐项验证（只读 handler → EffectPipeline 执行） */
class StandardAbilitiesTest {

    private static RaceSession.RacerState st(int seat, String key, int pos) {
        return new RaceSession.RacerState(seat, "p" + seat, key, pos, false, 0, 0);
    }

    private static AbilityContext ctx(List<RaceSession.RacerState> states, int dice, int actor) {
        AbilityContext c = new AbilityContext(1, states, List.of(1, 2, 3), dice);
        c.setCurrentActor(actor);
        return c;
    }

    private static EffectPipeline.ChainResult fire(AbilityContext c, AbilityEvent event, int actor) {
        return new EffectPipeline(StandardAbilities.registry()).fire(c, event, actor);
    }

    @Test
    void alchemistReplacesBaseOnOneOrTwo() {
        AbilityContext c = ctx(List.of(st(1, "alchemist", 0), st(2, "hare", 0)), 2, 1);
        fire(c, AbilityEvent.ROLL_REPLACE, 1);
        assertEquals(4, c.base());
        AbilityContext other = ctx(List.of(st(1, "alchemist", 0), st(2, "hare", 0)), 3, 1);
        fire(other, AbilityEvent.ROLL_REPLACE, 1);
        assertEquals(3, other.base(), "骰点 3 不触发炼金术士");
    }

    @Test
    void legsReplacesBaseWithFive() {
        AbilityContext c = ctx(List.of(st(1, "legs", 0), st(2, "hare", 0)), 2, 1);
        fire(c, AbilityEvent.ROLL_REPLACE, 1);
        assertEquals(5, c.base());
    }

    @Test
    void blimpModifiesBySecondCorner() {
        AbilityContext before = ctx(List.of(st(1, "blimp", 14), st(2, "hare", 0)), 4, 1);
        fire(before, AbilityEvent.BEFORE_MAIN_MOVE, 1);
        assertEquals(7, before.mainMoveDistance(), "14 < 15 → +3");
        AbilityContext after = ctx(List.of(st(1, "blimp", 15), st(2, "hare", 0)), 4, 1);
        fire(after, AbilityEvent.BEFORE_MAIN_MOVE, 1);
        assertEquals(3, after.mainMoveDistance(), "≥15 → −1");
    }

    @Test
    void gunkStacksAndHareSkipsWhenSoleLeader() {
        AbilityContext c = ctx(List.of(st(1, "hare", 20), st(2, "gunk", 3), st(3, "gunk", 3)), 4, 1);
        fire(c, AbilityEvent.BEFORE_MAIN_MOVE, 1);
        assertTrue(c.mainMoveCancelled(), "野兔独自领先跳过主移动");

        AbilityContext target = ctx(List.of(st(1, "hare", 0), st(2, "gunk", 3), st(3, "gunk", 3)), 4, 1);
        fire(target, AbilityEvent.BEFORE_MAIN_MOVE, 1);
        assertEquals(4, target.mainMoveDistance(), "4 +2(野兔) -1-1(两只黏液怪各 −1)");
    }

    @Test
    void inchwormCancelsOnOneAndScores() {
        AbilityContext c = ctx(List.of(st(1, "hare", 0), st(2, "inchworm", 5)), 1, 1);
        fire(c, AbilityEvent.DICE_RESULT, 1);
        assertTrue(c.mainMoveCancelled());
        assertEquals(1, c.seat(2).score);
        AbilityContext six = ctx(List.of(st(1, "hare", 0), st(2, "inchworm", 5)), 6, 1);
        fire(six, AbilityEvent.DICE_RESULT, 1);
        assertFalse(six.mainMoveCancelled(), "骰点 6 不触发尺蠖");
    }

    @Test
    void lackeyScoresOnSix() {
        AbilityContext c = ctx(List.of(st(1, "hare", 0), st(2, "lackey", 5)), 6, 1);
        fire(c, AbilityEvent.DICE_RESULT, 1);
        assertEquals(2, c.seat(2).score);
    }

    @Test
    void bananaTripsMoverWhenPassed() {
        AbilityContext c = ctx(List.of(st(1, "hare", 0), st(2, "banana", 4)), 6, 1);
        c.markPassed(2);
        fire(c, AbilityEvent.ON_PASS, 1);
        assertTrue(c.seat(1).tripped, "越过香蕉者被绊倒");
    }

    @Test
    void centaurPushesPassedRacerBack() {
        AbilityContext c = ctx(List.of(st(1, "hare", 0), st(2, "centaur", 8)), 6, 1);
        c.markPassed(2);
        fire(c, AbilityEvent.ON_PASS, 1);
        assertEquals(6, c.seat(2).position, "半人马被越过后后退 2 格");
    }

    @Test
    void sisyphusPreScoreAndWarpOnSix() {
        AbilityContext c = ctx(List.of(st(1, "sisyphus", 0), st(2, "hare", 0)), 6, 1);
        fire(c, AbilityEvent.BEFORE_RACE, 1);
        assertEquals(4, c.seat(1).score);
        c.setCurrentActor(1);
        fire(c, AbilityEvent.DICE_RESULT, 1);
        assertEquals(3, c.seat(1).score, "赛前4 − 骰6惩罚1");
        assertEquals(0, c.seat(1).position);
    }

    @Test
    void romanticScoresWhenExactlyOneOccupant() {
        AbilityContext c = ctx(List.of(st(1, "hare", 7), st(2, "romantic", 3)), 6, 1);
        c.setStopSpace(7);
        fire(c, AbilityEvent.ON_STOP, 1);
        assertEquals(2, c.seat(2).score, "浪漫家在有人单独停格时 +2");
    }

    @Test
    void scoocherChainsFromAnyOtherAbility() {
        AbilityContext c = ctx(List.of(st(1, "blimp", 0), st(2, "scoocher", 5)), 3, 1);
        // 先跑一次飞艇真实发动，再验证蹭蹭怪累计（不限每回合一次）
        fire(c, AbilityEvent.BEFORE_MAIN_MOVE, 1);
        fire(c, AbilityEvent.BEFORE_MAIN_MOVE, 1);
        assertEquals(2, c.seat(2).score);
    }
}
