package org.dromara.magicalathlete.engine;

import org.dromara.magicalathlete.engine.ability.FullAbilities;
import org.dromara.magicalathlete.engine.ability.TurnIntent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M5 幕后军师：首回合预测冠军、猜中获第二并结束、自猜金银 */
class MastermindTest {

    private static RaceSession.Entrant e(int seat, String key) {
        return new RaceSession.Entrant(seat, "p" + seat, key);
    }

    private static RaceSession session() {
        return new RaceSession(RaceTrack.Side.MILD,
            List.of(e(1, "mastermind"), e(2, "dummy")),
            List.of(1, 2), FullAbilities.registry(), List.of());
    }

    /** 让座位2（dummy）冲线：mastermind 用 1 点小步陪跑，dummy 连掷 6 */
    private static RaceSession.RollOutcome runUntilChampion(RaceSession race, TurnIntent mmFirstIntent) {
        RaceSession.RollOutcome last = null;
        for (int i = 0; i < 12; i++) {
            Integer cur = race.currentSeatNo();
            if (cur == null) break;
            if (cur == 1) {
                last = race.roll(1, 1, i == 0 ? mmFirstIntent : TurnIntent.NONE);
            } else {
                last = race.roll(2, 6);
            }
        }
        return last;
    }

    @Test
    void predictionHitGrantsSilverAndEnds() {
        RaceSession race = session();
        runUntilChampion(race, TurnIntent.targetSeat(2)); // 军师预测座位2夺冠
        // 座位2 连掷 6 冲线第 1；军师命中获第 2；finishCount=2 → 本场结束
        assertTrue(race.finished());
        List<RaceSession.Standing> st = race.standings();
        int mmRank = st.stream().filter(s -> s.seatNo() == 1).findFirst().orElseThrow().finishRank();
        int dummyRank = st.stream().filter(s -> s.seatNo() == 2).findFirst().orElseThrow().finishRank();
        assertEquals(1, dummyRank, "座位2 是冠军");
        assertEquals(2, mmRank, "军师预测命中获第二");
    }

    @Test
    void wrongPredictionGetsNothing() {
        RaceSession race = session();
        // 军师自猜（预测座位1自己）但实际是座位2夺冠 → 未命中
        runUntilChampion(race, TurnIntent.targetSeat(1));
        List<RaceSession.Standing> st = race.standings();
        int mmRank = st.stream().filter(s -> s.seatNo() == 1).findFirst().orElseThrow().finishRank();
        assertEquals(0, mmRank, "预测错误不得第二");
        assertFalse(race.isMastermindSelfWin());
    }

    @Test
    void selfGuessWinMarksSelfWin() {
        // 军师自己夺冠且自猜 → 标记自猜金银（结算时金银同人）
        RaceSession race = new RaceSession(RaceTrack.Side.MILD,
            List.of(e(1, "mastermind"), e(2, "dummy")),
            List.of(1, 2), FullAbilities.registry(), List.of());
        race.roll(1, 6, TurnIntent.targetSeat(1)); // 预测自己
        for (int i = 0; i < 16 && !race.finished(); i++) {
            Integer cur = race.currentSeatNo();
            if (cur == null) break;
            race.roll(cur, cur == 1 ? 6 : 1);
        }
        int mmRank = race.standings().stream().filter(s -> s.seatNo() == 1)
            .findFirst().orElseThrow().finishRank();
        assertEquals(1, mmRank, "军师自己是冠军");
        assertTrue(race.isMastermindSelfWin(), "自猜命中标记金银同人");
    }
}
