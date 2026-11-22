package org.dromara.magicalathlete.engine;

import org.dromara.magicalathlete.engine.ability.FullAbilities;
import org.dromara.magicalathlete.engine.ability.TurnIntent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M4 引擎接线：抢行动、额外回合、回合末事件、意图带入 */
class RaceSessionM4Test {

    private static RaceSession.Entrant e(int seat, String key) {
        return new RaceSession.Entrant(seat, "p" + seat, key);
    }

    private static RaceSession race(RaceSession.Entrant... entrants) {
        return new RaceSession(1, List.of(entrants), FullAbilities.registry());
    }

    @Test
    void skipperStealsNextTurnInEngine() {
        RaceSession r = race(e(1, "hare"), e(2, "skipper"));
        // seat1 掷 1 → 船长(seat2)抢下一行动
        RaceSession.RollOutcome out = r.roll(1, 1);
        assertTrue(out.abilityChain().stream().anyMatch(s -> s.startsWith("skipper@")));
        assertEquals(2, r.currentSeatNo(), "船长抢到下一行动");
    }

    @Test
    void geniusExtraTurnKeepsSameActor() {
        RaceSession r = race(e(1, "genius"), e(2, "hare"));
        RaceSession.RollOutcome out = r.roll(1, 4, TurnIntent.guess(4));
        assertTrue(out.abilityChain().stream().anyMatch(s -> s.startsWith("genius@")));
        assertEquals(1, r.currentSeatNo(), "猜中保留当前行动者（额外回合）");
    }

    @Test
    void wrongGuessAdvancesTurn() {
        RaceSession r = race(e(1, "genius"), e(2, "hare"));
        r.roll(1, 4, TurnIntent.guess(2));
        assertEquals(2, r.currentSeatNo(), "猜错正常换手");
    }

    @Test
    void afterTurnFiresAndScoresHeckler() {
        RaceSession r = race(e(1, "dummy"), e(2, "heckler"));
        // seat1 掷 1 走 1 格，位移恰为 1 → 起哄者 +2
        r.roll(1, 1);
        assertEquals(2, r.extraScoreBySeat().get(2));
    }

    @Test
    void intentReachesAbilityChain() {
        // 火箭科学家声明翻倍：骰 3 → 主移动 6
        RaceSession r = race(e(1, "rocket_scientist"), e(2, "hare"));
        RaceSession.RollOutcome out = r.roll(1, 3, TurnIntent.primary());
        assertTrue(out.effects().stream().anyMatch(s -> s.equals("MAIN_MOVE:0->6")));
        assertTrue(out.abilityChain().stream().anyMatch(s -> s.startsWith("rocket_scientist@")));
    }

    @Test
    void noIntentMeansNoOptionalAbility() {
        RaceSession r = race(e(1, "rocket_scientist"), e(2, "hare"));
        RaceSession.RollOutcome out = r.roll(1, 3);
        assertTrue(out.effects().stream().anyMatch(s -> s.equals("MAIN_MOVE:0->3")));
        assertFalse(out.abilityChain().stream().anyMatch(s -> s.startsWith("rocket_scientist@")));
    }
}
