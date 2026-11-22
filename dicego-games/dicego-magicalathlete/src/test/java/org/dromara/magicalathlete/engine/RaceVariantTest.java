package org.dromara.magicalathlete.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M5 3 人双角色变体：选序、批次、手牌数、lane↔seat 映射 */
class RaceVariantTest {

    @Test
    void threeDoublePickOrderMatchesRulebook() {
        assertEquals(List.of(1, 2, 3, 3, 2, 1), DraftOrder.threeDoubleSnake(0), "ABCCBA");
        assertEquals(List.of(2, 3, 1, 1, 3, 2), DraftOrder.threeDoubleSnake(1), "BCAACB");
        assertEquals(List.of(3, 1, 2, 2, 1, 3), DraftOrder.threeDoubleSnake(2), "CABBAC");
        assertEquals(List.of(1, 2, 3, 3, 2, 1), DraftOrder.threeDoubleSnake(3), "ABCCBA");
        assertEquals(4, DraftOrder.threeDoubleBatchCount());
        assertThrows(IllegalArgumentException.class, () -> DraftOrder.threeDoubleSnake(4));
    }

    @Test
    void threeDoubleBatchGivesEachSeatTwoPicks() {
        for (int batch = 0; batch < 4; batch++) {
            List<Integer> order = DraftOrder.threeDoubleSnake(batch);
            assertEquals(6, order.size());
            for (int seat = 1; seat <= 3; seat++) {
                final int s = seat;
                assertEquals(2, order.stream().filter(x -> x == s).count(),
                    "第 " + batch + " 批座位 " + seat + " 应恰好选 2 张");
            }
        }
    }

    @Test
    void variantParameters() {
        assertEquals(1, RaceVariant.NORMAL.racersPerSeat());
        assertEquals(2, RaceVariant.THREE_DOUBLE.racersPerSeat());
        assertEquals(2, RaceVariant.NORMAL.batchCount());
        assertEquals(4, RaceVariant.THREE_DOUBLE.batchCount());
        assertEquals(6, RaceVariant.THREE_DOUBLE.poolSize(3));
        assertEquals(8, RaceVariant.THREE_DOUBLE.racersPerPlayer(3));
        assertEquals(6, RaceVariant.THREE_DOUBLE.laneCount(3));
    }

    @Test
    void laneMappingIsBijective() {
        RaceVariant v = RaceVariant.THREE_DOUBLE;
        assertEquals(1, v.laneOf(1, 0));
        assertEquals(2, v.laneOf(1, 1));
        assertEquals(3, v.laneOf(2, 0));
        assertEquals(4, v.laneOf(2, 1));
        assertEquals(5, v.laneOf(3, 0));
        assertEquals(6, v.laneOf(3, 1));
        for (int lane = 1; lane <= 6; lane++) {
            int seat = v.seatOfLane(lane);
            int slot = v.slotOfLane(lane);
            assertEquals(lane, v.laneOf(seat, slot), "lane " + lane + " 往返一致");
        }
    }

    @Test
    void normalVariantLaneEqualsSeat() {
        assertEquals(2, RaceVariant.NORMAL.laneOf(2, 0));
        assertEquals(2, RaceVariant.NORMAL.seatOfLane(2));
        assertEquals(0, RaceVariant.NORMAL.slotOfLane(2));
        assertEquals(3, RaceVariant.NORMAL.laneCount(3));
    }

    @Test
    void variantIsOptInAndOnlyForThreePlayers() {
        assertEquals(RaceVariant.NORMAL, RaceVariant.of(3, null));
        assertEquals(RaceVariant.NORMAL, RaceVariant.of(3, "unknown"));
        assertEquals(RaceVariant.THREE_DOUBLE, RaceVariant.of(3, "THREE_DOUBLE"));
        assertEquals(RaceVariant.NORMAL, RaceVariant.of(4, "THREE_DOUBLE"), "4 人局不允许双角色");
        assertEquals(RaceVariant.TWO_PLAYER, RaceVariant.of(2, null));
        assertEquals(RaceVariant.TWO_PLAYER, RaceVariant.defaultOf(2));
        assertEquals(RaceVariant.NORMAL, RaceVariant.defaultOf(3));
    }

    @Test
    void draftSessionFourBatchesEightCardsEach() {
        DraftSession s = new DraftSession(2026L, 3, RaceVariant.THREE_DOUBLE);
        int picks = 0;
        while (!s.finished()) {
            Integer seat = s.currentSeatNo();
            assertNotNull(seat);
            String key = s.pool().isEmpty() ? null : s.pool().get(0).key();
            assertTrue(s.pick(seat, key).ok());
            picks++;
            assertTrue(picks <= 24, "四批共 24 手，实际 " + picks);
        }
        assertEquals(24, picks, "4 批 × 6 手 = 24 手");
        for (int seat = 1; seat <= 3; seat++) {
            assertEquals(8, s.hands().get(seat).size(), "每人 8 张");
        }
    }

    @Test
    void defaultDraftSessionKeepsTwoBatches() {
        DraftSession s = new DraftSession(2026L, 3);
        int picks = 0;
        while (!s.finished()) {
            String key = s.pool().isEmpty() ? null : s.pool().get(0).key();
            assertTrue(s.pick(s.currentSeatNo(), key).ok());
            picks++;
            assertTrue(picks <= 12, "普通 3 人局两批共 12 手");
        }
        assertEquals(12, picks);
        for (int seat = 1; seat <= 3; seat++) assertEquals(4, s.hands().get(seat).size());
    }
}
