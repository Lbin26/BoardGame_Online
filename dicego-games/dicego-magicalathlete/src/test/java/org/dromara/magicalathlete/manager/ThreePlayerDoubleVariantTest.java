package org.dromara.magicalathlete.manager;

import org.dromara.magicalathlete.domain.vo.MagicalAthleteSnapshotVO;
import org.dromara.magicalathlete.engine.RaceVariant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M5 3 人双角色变体（可选开关）：四批轮抽 8 张/人、暗选累计 2 名、6 个参赛号、
 * 回合内依次行动两名选手、奖牌/铜币按 lane→真实座位归并。
 */
class ThreePlayerDoubleVariantTest {

    private static final String[] NAMES = {"alice", "bob", "carol"};

    private static MagicalAthleteRoom doubleRoom() {
        MagicalAthleteRoom room = new MagicalAthleteRoom("RD", "alice", 3);
        for (int seat = 1; seat <= 3; seat++) {
            assertEquals(MagicalAthleteRoom.SeatResult.SUCCESS, room.takeSeat(NAMES[seat - 1], seat));
        }
        room.startGameWithSeed("alice", 2026L, "THREE_DOUBLE");
        return room;
    }

    /** 走完四批轮抽（每手取牌池第一张） */
    private static void finishDraft(MagicalAthleteRoom room) {
        int guard = 0;
        while (room.getPhase() == MagicalAthleteRoom.Phase.DRAFT && guard++ < 40) {
            MagicalAthleteSnapshotVO snap = room.snapshot();
            Integer seat = snap.currentSeatNo();
            assertNotNull(seat, "轮抽每一手都应有序位");
            assertFalse(snap.pool().isEmpty(), "牌池不应为空");
            assertEquals(MagicalAthleteRoom.PickResult.SUCCESS,
                room.pickRacer(NAMES[seat - 1], snap.pool().get(0)));
        }
        assertEquals(MagicalAthleteRoom.Phase.MUSTER, room.getPhase(), "四批选完进入 MUSTER");
    }

    /** 为指定玩家提交本场 2 名未用过的选手（顺序即固定出场顺序） */
    private static List<String> readyTwo(MagicalAthleteRoom room, String player, int seat) {
        List<String> picked = new ArrayList<>();
        for (String key : room.snapshot(player).hands().get(seat)) {
            if (picked.size() >= 2) break;
            if (room.readyRacer(player, key) == MagicalAthleteRoom.ReadyResult.SUCCESS) {
                picked.add(key);
            }
        }
        return picked;
    }

    @Test
    void optInActivatesVariantAndKeepsDefaultNormal() {
        MagicalAthleteRoom normal = new MagicalAthleteRoom("RN", "alice", 3);
        for (int seat = 1; seat <= 3; seat++) normal.takeSeat(NAMES[seat - 1], seat);
        assertEquals(MagicalAthleteRoom.StartResult.SUCCESS, normal.startGame("alice"));
        assertEquals(RaceVariant.NORMAL, normal.getVariant(), "默认仍为普通局，零回归");
        assertEquals("NORMAL", normal.snapshot().variant());
        assertEquals(1, normal.snapshot().racersPerSeat(), "普通局每座位 1 名");

        MagicalAthleteRoom dbl = doubleRoom();
        assertEquals(RaceVariant.THREE_DOUBLE, dbl.getVariant());
        assertEquals("THREE_DOUBLE", dbl.snapshot().variant());
        assertEquals(2, dbl.snapshot().racersPerSeat());
    }

    @Test
    void fourBatchesGiveEightCardsPerSeat() {
        MagicalAthleteRoom room = doubleRoom();
        int picks = 0;
        while (room.getPhase() == MagicalAthleteRoom.Phase.DRAFT) {
            MagicalAthleteSnapshotVO snap = room.snapshot();
            assertNotNull(snap.currentSeatNo());
            assertEquals(6, snap.pickOrder().size(), "每批 6 手");
            assertTrue(snap.batch() >= 0 && snap.batch() <= 3);
            room.pickRacer(NAMES[snap.currentSeatNo() - 1], snap.pool().get(0));
            picks++;
            assertTrue(picks <= 24);
        }
        assertEquals(24, picks, "四批 × 6 手 = 24 手");
        for (int seat = 1; seat <= 3; seat++) {
            assertEquals(8, room.snapshot(NAMES[seat - 1]).hands().get(seat).size(),
                "每人 8 张手牌");
        }
    }

    @Test
    void musterRequiresTwoPicksAndHidesKeys() {
        MagicalAthleteRoom room = doubleRoom();
        finishDraft(room);

        String first = room.snapshot("alice").hands().get(1).get(0);
        assertEquals(MagicalAthleteRoom.ReadyResult.SUCCESS, room.readyRacer("alice", first));
        // 只提交 1 名时仍未准备，进度对外可见但不含 key
        MagicalAthleteSnapshotVO mid = room.snapshot("alice");
        assertEquals(List.of(first), mid.myActiveRacerKeys());
        assertEquals(1, mid.musterProgress().get(1));
        assertFalse(mid.readySeats().contains(1), "选满 2 名才算准备");
        assertTrue(mid.activeRacerKeys().isEmpty(), "MUSTER 不得泄露具体 key");
        // bob 看得见进度数字（1），但自己的 myActiveRacerKeys 不含 alice 的 key
        assertEquals(Integer.valueOf(1), room.snapshot("bob").musterProgress().get(1));
        assertFalse(room.snapshot("bob").myActiveRacerKeys().contains(first));

        String second = room.snapshot("alice").hands().get(1).stream()
            .filter(k -> !k.equals(first)).findFirst().orElseThrow();
        assertEquals(MagicalAthleteRoom.ReadyResult.SUCCESS, room.readyRacer("alice", second));
        assertTrue(room.snapshot("alice").readySeats().contains(1));
        assertEquals(MagicalAthleteRoom.ReadyResult.ALREADY_READY,
            room.readyRacer("alice", "some-other"), "选满后拒绝第 3 名");
    }

    @Test
    void raceHasSixLanesWithCorrectOwners() {
        MagicalAthleteRoom room = doubleRoom();
        finishDraft(room);
        for (int seat = 1; seat <= 3; seat++) {
            assertEquals(2, readyTwo(room, NAMES[seat - 1], seat).size());
        }
        assertEquals(MagicalAthleteRoom.Phase.RACE, room.getPhase());

        MagicalAthleteSnapshotVO snap = room.snapshot("alice");
        assertEquals(6, snap.racers().size(), "3 座位 × 2 名 = 6 个参赛号");
        assertEquals(6, snap.activeRacerKeysByLane().size(), "lane 精确视图含 6 名选手");
        assertEquals(6, snap.laneToSeat().size());
        // lane 1,2 属座位 1；3,4 属座位 2；5,6 属座位 3
        assertEquals(1, snap.laneToSeat().get(1));
        assertEquals(1, snap.laneToSeat().get(2));
        assertEquals(2, snap.laneToSeat().get(3));
        assertEquals(2, snap.laneToSeat().get(4));
        assertEquals(3, snap.laneToSeat().get(5));
        assertEquals(3, snap.laneToSeat().get(6));
        for (MagicalAthleteSnapshotVO.RacerStateVO r : snap.racers()) {
            assertEquals(snap.laneToSeat().get(r.seatNo()), r.ownerSeatNo(),
                "棋子 ownerSeatNo 与 lane→seat 一致");
        }
        // 对外 currentSeatNo 必须是真实座位（前端"轮到我"判断零改动）
        Integer cur = snap.currentSeatNo();
        assertNotNull(cur);
        assertTrue(cur >= 1 && cur <= 3, "对外当前座位应为真实座位 1-3，实际 " + cur);
        assertEquals(cur, snap.laneToSeat().get(snap.currentLane()),
            "currentLane 的归属应等于 currentSeatNo");
    }

    @Test
    void twoRacersPerTurnAndScoreMergedByOwnerSeat() {
        MagicalAthleteRoom room = doubleRoom();
        finishDraft(room);
        for (int seat = 1; seat <= 3; seat++) readyTwo(room, NAMES[seat - 1], seat);
        assertEquals(MagicalAthleteRoom.Phase.RACE, room.getPhase());

        // 前两次行动都应归属座位 1（回合内依次移动两名选手，赛前固定顺序）
        assertEquals(1, room.snapshot().currentSeatNo());
        assertTrue(room.roll("alice").ok(), "座位 1 的第 1 名选手行动");
        assertEquals(1, room.snapshot().currentSeatNo(), "同一座位的第 2 名选手接着行动");
        assertTrue(room.roll("alice").ok(), "座位 1 的第 2 名选手行动");
        assertEquals(2, room.snapshot().currentSeatNo(), "之后轮到座位 2");
        assertEquals(MagicalAthleteRoom.RollResult.NOT_YOUR_TURN,
            room.roll("alice").reason(), "此时不再是 alice 的回合");

        // 托管跑完第一场，检查奖牌落在真实座位
        long after = System.currentTimeMillis() + 21_000;
        int guard = 0;
        while (room.getPhase() == MagicalAthleteRoom.Phase.RACE && guard++ < 200) {
            assertTrue(room.autoRoll(after).ok());
        }
        assertTrue(guard < 200, "比赛应能结束");
        for (Integer seat : room.snapshot().scores().keySet()) {
            assertTrue(seat >= 1 && seat <= 3, "记分键必须是真实座位 1-3，实际 " + seat);
        }
    }

    @Test
    void fourRacesUseAllEightCardsAndFinish() {
        MagicalAthleteRoom room = doubleRoom();
        finishDraft(room);
        for (int r = 1; r <= 4; r++) {
            for (int seat = 1; seat <= 3; seat++) {
                assertEquals(2, readyTwo(room, NAMES[seat - 1], seat).size(),
                    "第 " + r + " 场座位 " + seat + " 应能选满 2 名（8 张手牌恰好够 4 场）");
            }
            assertEquals(MagicalAthleteRoom.Phase.RACE, room.getPhase(), "第 " + r + " 场应开赛");
            long after = System.currentTimeMillis() + 21_000;
            int guard = 0;
            while (room.getPhase() == MagicalAthleteRoom.Phase.RACE && guard++ < 200) {
                room.autoRoll(after);
            }
            assertTrue(guard < 200);
        }
        assertEquals(MagicalAthleteRoom.Phase.FINISHED, room.getPhase());
        assertEquals(3, room.snapshot().rankings().size(), "3 名真实玩家参与排名");
        assertEquals(3, room.snapshot().scores().size(), "记分按真实座位而非 6 个 lane");
    }
}
