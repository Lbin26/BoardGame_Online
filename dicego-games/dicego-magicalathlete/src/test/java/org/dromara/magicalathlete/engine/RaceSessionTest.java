package org.dromara.magicalathlete.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RaceSessionTest {

    private static RaceSession.Entrant e(int seat) {
        return new RaceSession.Entrant(seat, "p" + seat, "racer" + seat);
    }

    /** 反复让当前座位掷满骰，直到比赛结束 */
    private static void runToEnd(RaceSession race) {
        for (int i = 0; i < 40 && !race.finished(); i++) {
            Integer cur = race.currentSeatNo();
            if (cur == null) break;
            race.roll(cur, 6);
        }
    }

    @Test
    void mainMoveUsesInjectedDiceAndAdvancesTurn() {
        RaceSession race = new RaceSession(1, List.of(e(1), e(2), e(3)));
        assertEquals(1, race.currentSeatNo());
        RaceSession.RollOutcome out = race.roll(1, 4);
        assertTrue(out.ok());
        assertEquals(4, out.to());
        assertEquals(2, race.currentSeatNo(), "冲线者之外按座位顺序轮转");
        assertEquals(RaceSession.RollReason.NOT_YOUR_TURN, race.roll(1, 3).reason());
    }

    @Test
    void serverRejectsClientDiceOutOfRange() {
        RaceSession race = new RaceSession(1, List.of(e(1), e(2)));
        assertEquals(RaceSession.RollReason.INVALID_DICE, race.roll(1, 0).reason());
        assertEquals(RaceSession.RollReason.INVALID_DICE, race.roll(1, 7).reason());
        assertEquals(1, race.currentSeatNo(), "非法骰点不应推进回合");
    }

    @Test
    void overshootBeyondThirtyCountsAsFinish() {
        RaceSession race = new RaceSession(1, List.of(e(1), e(2)));
        // 座位1: 0→6→12→18→24→30（第5次恰好 30）
        for (int i = 0; i < 4; i++) {
            race.roll(1, 6);
            Integer cur = race.currentSeatNo();
            if (cur != null && cur != 1) race.roll(cur, 1);
        }
        RaceSession.RollOutcome finish = race.roll(1, 6);
        assertTrue(finish.effects().stream().anyMatch(s -> s.startsWith("FINISH:")));
        assertEquals(RaceTrack.FINISH, race.racers().stream()
            .filter(r -> r.seatNo() == 1).findFirst().orElseThrow().position());
    }

    @Test
    void secondFinisherEndsRace() {
        RaceSession race = new RaceSession(1, List.of(e(1), e(2)));
        runToEnd(race);
        assertTrue(race.finished());
        assertEquals(2, race.finishCount());
        assertEquals(2, race.racers().stream().filter(r -> r.finishRank() > 0).count());
        assertTrue(race.racers().stream().allMatch(r -> r.position() <= RaceTrack.FINISH));
    }

    @Test
    void wildTripSkipsOnlyNextMainMove() {
        RaceSession race = new RaceSession(RaceTrack.Side.WILD, List.of(e(1), e(2)), List.of(1, 2));
        race.roll(1, 5); // 0→5 停格绊倒
        assertTrue(race.racers().stream().filter(r -> r.seatNo() == 1).findFirst().orElseThrow().tripped());
        race.roll(2, 2);
        RaceSession.RollOutcome skip = race.roll(1, 6);
        assertTrue(skip.ok());
        assertEquals(RaceSession.RollReason.TRIPPED_SKIP, skip.reason());
        assertFalse(race.racers().stream().filter(r -> r.seatNo() == 1).findFirst().orElseThrow().tripped(),
            "跳过一次后绊倒状态清除");
    }

    @Test
    void wildArrowAndBronzeResolveOnStop() {
        RaceSession race = new RaceSession(RaceTrack.Side.WILD, List.of(e(1), e(2)), List.of(1, 2));
        RaceSession.RollOutcome bronze = race.roll(1, 1); // 0→1 星星格
        assertTrue(bronze.effects().stream().anyMatch(s -> s.startsWith("BRONZE:")));
        race.roll(2, 1);
        RaceSession.RollOutcome arrow = race.roll(1, 6); // 1→7 箭头+3 →10
        assertTrue(arrow.effects().stream().anyMatch(s -> s.startsWith("ARROW:")));
    }

    @Test
    void standingsOrderByPositionWhenNoFinisher() {
        RaceSession race = new RaceSession(1, List.of(e(1), e(2), e(3)));
        race.roll(1, 3);
        race.roll(2, 5);
        race.roll(3, 1);
        List<RaceSession.Standing> standings = race.standings();
        assertEquals(2, standings.get(0).seatNo(), "位置 5 领先");
        assertEquals(1, standings.get(1).seatNo());
        assertEquals(3, standings.get(2).seatNo());
    }
}
