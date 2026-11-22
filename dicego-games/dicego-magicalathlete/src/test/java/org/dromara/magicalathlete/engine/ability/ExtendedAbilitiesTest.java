package org.dromara.magicalathlete.engine.ability;

import org.dromara.magicalathlete.engine.RaceSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M4 第二批 12 名能力逐项验证（纯 handler + 意图） */
class ExtendedAbilitiesTest {

    private static final AbilityRegistry REG = FullAbilities.registry();

    private static RaceSession.RacerState st(int seat, String key, int pos) {
        return new RaceSession.RacerState(seat, "p" + seat, key, pos, false, 0, 0);
    }

    private static AbilityContext ctx(List<RaceSession.RacerState> states, int dice, int actor) {
        AbilityContext c = new AbilityContext(1, states, List.of(1, 2, 3, 4), dice);
        c.setCurrentActor(actor);
        return c;
    }

    private static EffectPipeline.ChainResult fire(AbilityContext c, AbilityEvent event, int actor) {
        return new EffectPipeline(REG).fire(c, event, actor);
    }

    @Test
    void fullRegistryCovers28() {
        assertEquals(28, REG.coveredRacerCount());
        for (String key : FullAbilities.ENABLED_KEYS) {
            assertTrue(REG.supports(key), "开放选手必须有 handler: " + key);
        }
    }

    // ---- 停格干扰 ----

    @Test
    void babaYagaTripsVictimOnSharedSpace() {
        // 座位1 鸡脚屋停在座位2 所在格 5 → 座位2 被绊倒
        AbilityContext c = ctx(List.of(st(1, "baba_yaga", 5), st(2, "hare", 5)), 3, 1);
        c.setStopSpace(5);
        fire(c, AbilityEvent.ON_STOP, 1);
        assertTrue(c.seat(2).tripped, "鸡脚屋令同格对手绊倒");
        assertFalse(c.seat(1).tripped, "自己不被绊倒");
    }

    @Test
    void babaYagaTripsWhenEntered() {
        // 座位1 鸡脚屋在 8，座位2 走进来 → 座位2 被绊倒
        AbilityContext c = ctx(List.of(st(1, "baba_yaga", 8), st(2, "hare", 8)), 3, 2);
        c.setStopSpace(8);
        fire(c, AbilityEvent.ON_STOP, 2);
        assertTrue(c.seat(2).tripped, "后来者被鸡脚屋绊倒");
    }

    @Test
    void mouthEliminatesVictim() {
        AbilityContext c = ctx(List.of(st(1, "mouth", 7), st(2, "hare", 7)), 3, 1);
        c.setStopSpace(7);
        fire(c, AbilityEvent.ON_STOP, 1);
        assertTrue(c.seat(2).eliminated, "大嘴怪淘汰同格对手");
    }

    @Test
    void hugeBabyPushesNewcomer() {
        // 巨婴(1)在 6 不动，后来者(2)停入 → 被挪到 7
        AbilityContext c = ctx(List.of(st(1, "huge_baby", 6), st(2, "hare", 6)), 3, 2);
        c.setStopSpace(6);
        fire(c, AbilityEvent.ON_STOP, 2);
        assertEquals(7, c.seat(2).position, "后来者被挤到后一格");
    }

    // ---- 距离修正 ----

    @Test
    void coachAddsOneToMoverOnSameSpace() {
        AbilityContext c = ctx(List.of(st(1, "coach", 4), st(2, "dummy", 4)), 3, 2);
        fire(c, AbilityEvent.BEFORE_MAIN_MOVE, 2);
        assertEquals(4, c.mainMoveDistance(), "教练同格 → 3+1");
    }

    @Test
    void sticklerCancelsOvershoot() {
        // 行动者(2)在 28，骰 4 会到 32（过量）→ 较真怪(1)取消整次
        AbilityContext c = ctx(List.of(st(1, "stickler", 0), st(2, "dummy", 28)), 4, 2);
        fire(c, AbilityEvent.BEFORE_MAIN_MOVE, 2);
        assertTrue(c.mainMoveCancelled(), "过量冲线被较真怪取消");
        AbilityContext ok = ctx(List.of(st(1, "stickler", 0), st(2, "dummy", 26)), 4, 2);
        fire(ok, AbilityEvent.BEFORE_MAIN_MOVE, 2);
        assertFalse(ok.mainMoveCancelled(), "恰好冲线不取消");
    }

    @Test
    void rocketScientistDoublesOnlyWithIntent() {
        AbilityContext c = ctx(List.of(st(1, "rocket_scientist", 0), st(2, "hare", 0)), 3, 1);
        c.setIntent(TurnIntent.primary());
        fire(c, AbilityEvent.ROLL_REPLACE, 1);
        assertEquals(6, c.base(), "3 翻倍为 6");
        fire(c, AbilityEvent.AFTER_TURN, 1);
        assertTrue(c.seat(1).tripped, "翻倍后自己绊倒");

        AbilityContext noIntent = ctx(List.of(st(1, "rocket_scientist", 0), st(2, "hare", 0)), 3, 1);
        fire(noIntent, AbilityEvent.ROLL_REPLACE, 1);
        assertEquals(3, noIntent.base(), "未声明不翻倍");
    }

    @Test
    void cheerleaderCheersTrailersAndSelf() {
        // 行动者(1)啦啦队长领先 20；2/3 并列垫底 5 → 各 +2，自己主移动 +1
        AbilityContext c = ctx(List.of(
            st(1, "cheerleader", 20), st(2, "dummy", 5), st(3, "dummy", 5)), 4, 1);
        c.setIntent(TurnIntent.primary());
        fire(c, AbilityEvent.BEFORE_MAIN_MOVE, 1);
        assertEquals(7, c.seat(2).position, "垫底者 +2");
        assertEquals(7, c.seat(3).position, "并列垫底者 +2");
        assertEquals(5, c.mainMoveDistance(), "自己 4+1");
    }

    // ---- 骰点反应 ----

    @Test
    void geniusGetsExtraTurnOnCorrectGuess() {
        AbilityContext c = ctx(List.of(st(1, "genius", 0), st(2, "hare", 0)), 4, 1);
        c.setIntent(TurnIntent.guess(4));
        fire(c, AbilityEvent.DICE_RESULT, 1);
        assertTrue(c.extraTurnGranted(), "猜中得额外回合");
        AbilityContext wrong = ctx(List.of(st(1, "genius", 0), st(2, "hare", 0)), 4, 1);
        wrong.setIntent(TurnIntent.guess(3));
        fire(wrong, AbilityEvent.DICE_RESULT, 1);
        assertFalse(wrong.extraTurnGranted());
    }

    @Test
    void skipperStealsTurnOnOthersDiceOne() {
        // 座位2 船长，座位1 掷出 1 → 船长抢下一行动
        AbilityContext c = ctx(List.of(st(1, "hare", 0), st(2, "skipper", 0)), 1, 1);
        fire(c, AbilityEvent.DICE_RESULT, 1);
        assertEquals(2, c.stolenNextActor(), "船长抢行动权");
    }

    // ---- 计分 ----

    @Test
    void hecklerScoresWhenMoverBarelyMoves() {
        // 行动者(1)从 10 只走到 9? 位移 0（被取消）→ 起哄者(2)+2
        AbilityContext c = ctx(List.of(st(1, "hare", 10), st(2, "heckler", 0)), 4, 1);
        c.setTurnStartPosition(10);
        c.seat(1).position = 10; // 位移 0
        fire(c, AbilityEvent.AFTER_TURN, 1);
        assertEquals(2, c.seat(2).score, "位移≤1 起哄者 +2");
    }

    @Test
    void lovableLoserScoresWhenSoleTrailer() {
        AbilityContext c = ctx(List.of(st(1, "lovable_loser", 2), st(2, "hare", 20), st(3, "gunk", 18)), 3, 1);
        fire(c, AbilityEvent.DICE_RESULT, 1);
        assertEquals(1, c.seat(1).score, "独自垫底 +1");
    }
}
