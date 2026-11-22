package org.dromara.magicalathlete.engine;

import org.dromara.magicalathlete.engine.DraftSession.PickOutcome;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 轮抽会话：顺序校验、两批装载、每人手牌数、代选、完成态 */
class DraftSessionTest {

    /** 按蛇形序把整局选完，返回每人手牌 */
    private static void pickAll(DraftSession s, boolean useAuto) {
        while (!s.finished()) {
            Integer seat = s.currentSeatNo();
            assertNotNull(seat);
            String key = s.pool().get(0).key();
            PickOutcome out = useAuto ? s.autoPick() : s.pick(seat, key);
            assertTrue(out.ok(), out.reason());
        }
    }

    @Test
    void rejectsOutOfTurnPick() {
        DraftSession s = new DraftSession(1L, 3);
        assertEquals(1, s.currentSeatNo());
        assertEquals("NOT_YOUR_TURN", s.pick(2, s.pool().get(0).key()).reason());
    }

    @Test
    void rejectsKeyNotInPool() {
        DraftSession s = new DraftSession(1L, 3);
        assertEquals("RACER_NOT_IN_POOL", s.pick(1, "definitely_not_a_racer").reason());
    }

    @Test
    void threePlayersEachGetFourAndNoDuplicate() {
        DraftSession s = new DraftSession(2026L, 3);
        pickAll(s, false);
        assertTrue(s.finished());
        assertEquals(1, s.batch(), "两批（0/1）走完，末批索引为 1");
        Set<String> allPicked = new HashSet<>();
        for (int seat = 1; seat <= 3; seat++) {
            List<org.dromara.magicalathlete.domain.Racer> hand = s.hands().get(seat);
            assertEquals(4, hand.size(), "座位 " + seat + " 应 4 张");
            for (var r : hand) assertTrue(allPicked.add(r.key()), "不能重复选到同一名选手");
        }
        assertEquals(12, allPicked.size());
        assertNull(s.currentSeatNo());
    }

    @Test
    void twoPlayersEachGetEight() {
        DraftSession s = new DraftSession(99L, 2);
        pickAll(s, true); // 全程代选
        assertTrue(s.finished());
        assertEquals(8, s.hands().get(1).size());
        assertEquals(8, s.hands().get(2).size());
    }

    @Test
    void batchAdvancesAfterFirstBatch() {
        DraftSession s = new DraftSession(7L, 4);
        assertEquals(0, s.batch());
        for (int i = 0; i < 8; i++) {
            PickOutcome out = s.pick(s.currentSeatNo(), s.pool().get(0).key());
            assertTrue(out.ok());
        }
        assertEquals(1, s.batch(), "第一批 8 手走完应装载第二批");
        assertFalse(s.finished());
    }

    @Test
    void pickAfterFinishedIsRejected() {
        DraftSession s = new DraftSession(3L, 3);
        pickAll(s, false);
        assertEquals("DRAFT_FINISHED", s.autoPick().reason());
    }
}
