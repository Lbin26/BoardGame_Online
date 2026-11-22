package org.dromara.magicalathlete.engine.ability;

import org.dromara.magicalathlete.engine.RaceSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M4 warp 类 4 名（读意图产效果） */
class ChoiceAbilitiesTest {

    private static final AbilityRegistry REG = FullAbilities.registry();

    private static RaceSession.RacerState st(int seat, String key, int pos) {
        return new RaceSession.RacerState(seat, "p" + seat, key, pos, false, 0, 0);
    }

    private static AbilityContext ctx(List<RaceSession.RacerState> states, int actor) {
        AbilityContext c = new AbilityContext(1, states, List.of(1, 2, 3), 3);
        c.setCurrentActor(actor);
        return c;
    }

    private static EffectPipeline.ChainResult fire(AbilityContext c, AbilityEvent event, int actor) {
        return new EffectPipeline(REG).fire(c, event, actor);
    }

    @Test
    void flipFlopSwapsAndCancels() {
        AbilityContext c = ctx(List.of(st(1, "flip_flop", 5), st(2, "dummy", 20)), 1);
        c.setIntent(TurnIntent.targetSeat(2));
        fire(c, AbilityEvent.BEFORE_MAIN_MOVE, 1);
        assertEquals(20, c.seat(1).position, "人字拖交换到对手位置");
        assertEquals(5, c.seat(2).position, "对手交换到人字拖位置");
        assertTrue(c.mainMoveCancelled(), "交换替代主移动");
    }

    @Test
    void flipFlopNeedsIntent() {
        AbilityContext c = ctx(List.of(st(1, "flip_flop", 5), st(2, "dummy", 20)), 1);
        fire(c, AbilityEvent.BEFORE_MAIN_MOVE, 1);
        assertEquals(5, c.seat(1).position, "无意图不交换");
        assertFalse(c.mainMoveCancelled());
    }

    @Test
    void hypnotistWarpsTargetToSelf() {
        AbilityContext c = ctx(List.of(st(1, "hypnotist", 12), st(2, "dummy", 3)), 1);
        c.setIntent(TurnIntent.targetSeat(2));
        fire(c, AbilityEvent.DICE_RESULT, 1);
        assertEquals(12, c.seat(2).position, "被催眠者传送到催眠师所在格");
    }

    @Test
    void thirdWheelWarpsToSpaceWithExactlyTwoOthers() {
        AbilityContext c = ctx(List.of(
            st(1, "third_wheel", 2), st(2, "dummy", 10), st(3, "dummy", 10)), 1);
        c.setIntent(new TurnIntent(true, 0, 10, 0));
        fire(c, AbilityEvent.DICE_RESULT, 1);
        assertEquals(10, c.seat(1).position, "传送到恰有两人的格");
    }

    @Test
    void thirdWheelRejectsSpaceWithoutTwoOthers() {
        AbilityContext c = ctx(List.of(
            st(1, "third_wheel", 2), st(2, "dummy", 10), st(3, "dummy", 11)), 1);
        c.setIntent(new TurnIntent(true, 0, 10, 0));
        fire(c, AbilityEvent.DICE_RESULT, 1);
        assertEquals(2, c.seat(1).position, "格内不足两人不传送");
    }

    @Test
    void suckerfishFollowsAndCancels() {
        AbilityContext c = ctx(List.of(st(1, "suckerfish", 2), st(2, "dummy", 17)), 1);
        c.setIntent(TurnIntent.targetSeat(2));
        fire(c, AbilityEvent.DICE_RESULT, 1);
        assertEquals(17, c.seat(1).position, "吸盘鱼传送到被跟随者位置");
        assertTrue(c.mainMoveCancelled());
    }
}
