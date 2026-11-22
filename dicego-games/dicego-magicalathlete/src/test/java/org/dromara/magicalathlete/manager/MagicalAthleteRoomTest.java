package org.dromara.magicalathlete.manager;

import org.dromara.game.common.enums.RoomStatus;
import org.dromara.magicalathlete.domain.vo.MagicalAthleteSnapshotVO;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** 房间：座位、房主开局、轮抽序位校验、超时代选、快照 */
class MagicalAthleteRoomTest {

    private static MagicalAthleteRoom room3() {
        MagicalAthleteRoom room = new MagicalAthleteRoom("R1", "alice", 3);
        assertEquals(MagicalAthleteRoom.SeatResult.SUCCESS, room.takeSeat("alice", 1));
        assertEquals(MagicalAthleteRoom.SeatResult.SUCCESS, room.takeSeat("bob", 2));
        assertEquals(MagicalAthleteRoom.SeatResult.SUCCESS, room.takeSeat("carol", 3));
        return room;
    }

    @Test
    void seatManagement() {
        MagicalAthleteRoom room = room3();
        assertEquals(3, room.seatedCount());
        assertEquals(Integer.valueOf(2), room.seatOf("bob"));
        // 房间已满（3/3），优先返回 ROOM_FULL；先起身一个再验证 SEAT_TAKEN
        assertEquals(MagicalAthleteRoom.SeatResult.ROOM_FULL, room.takeSeat("dave", 1));
        assertTrue(room.standUp("bob"));
        assertEquals(MagicalAthleteRoom.SeatResult.SEAT_TAKEN, room.takeSeat("dave", 1));
        assertEquals(MagicalAthleteRoom.SeatResult.SUCCESS, room.takeSeat("bob", null));
        assertTrue(room.standUp("carol"));
        assertNull(room.seatOf("carol"));
        assertEquals(MagicalAthleteRoom.SeatResult.SUCCESS, room.takeSeat("dave", null));
        assertEquals(Integer.valueOf(3), room.seatOf("dave"));
    }

    @Test
    void onlyHostStartsAndNeedsPlayers() {
        MagicalAthleteRoom room = new MagicalAthleteRoom("R1", "alice", 4);
        assertEquals(MagicalAthleteRoom.StartResult.NOT_ENOUGH_PLAYERS, room.startGame("alice"));
        room.takeSeat("alice", 1);
        room.takeSeat("bob", 2);
        assertEquals(MagicalAthleteRoom.StartResult.NOT_HOST, room.startGame("bob"));
        assertEquals(MagicalAthleteRoom.StartResult.SUCCESS, room.startGame("alice"));
        assertEquals(MagicalAthleteRoom.Phase.DRAFT, room.getPhase());
        assertEquals(RoomStatus.PLAYING, room.getStatus());
    }

    @Test
    void pickMustFollowSnakeOrder() {
        MagicalAthleteRoom room = room3();
        room.startGameWithSeed("alice", 42L);
        MagicalAthleteSnapshotVO snap = room.snapshot();
        assertEquals("DRAFT", snap.phase());
        assertEquals(1, snap.currentSeatNo());
        assertEquals("NOT_YOUR_TURN", room.pickRacer("bob", snap.pool().get(0)).name());
        assertEquals("RACER_NOT_IN_POOL", room.pickRacer("alice", "no-such").name());
        assertEquals(MagicalAthleteRoom.PickResult.SUCCESS, room.pickRacer("alice", snap.pool().get(0)));
        assertEquals(2, room.snapshot().currentSeatNo());
    }

    @Test
    void autoPickOnlyAfterDeadlineAndAdvancesOrder() {
        MagicalAthleteRoom room = room3();
        room.startGameWithSeed("alice", 7L);
        long now = System.currentTimeMillis();
        assertTrue(room.autoPick(now).isEmpty(), "30s 截止时间之前不应代选");
        long expired = now + 31_000;
        Optional<MagicalAthleteRoom.AutoPick> auto = room.autoPick(expired);
        assertTrue(auto.isPresent());
        assertEquals(1, auto.get().seatNo());
        assertEquals("alice", auto.get().playerName());
        assertNotNull(auto.get().racer());
        assertEquals(2, room.snapshot().currentSeatNo());
    }

    @Test
    void draftCompletesThenPhaseMovesToMuster() {
        MagicalAthleteRoom room = room3();
        room.startGameWithSeed("alice", 11L);
        for (int i = 0; i < 12; i++) {
            MagicalAthleteSnapshotVO snap = room.snapshot();
            Integer seat = snap.currentSeatNo();
            assertNotNull(seat, "第 " + i + " 手应有序位");
            String name = seat == 1 ? "alice" : seat == 2 ? "bob" : "carol";
            assertEquals(MagicalAthleteRoom.PickResult.SUCCESS, room.pickRacer(name, snap.pool().get(0)));
        }
        assertEquals(MagicalAthleteRoom.Phase.MUSTER, room.getPhase());
        assertTrue(room.getDraftDeadlineAt() > 0, "M2 起 MUSTER 有 20s 暗选倒计时");
        MagicalAthleteSnapshotVO snap = room.snapshot();
        assertTrue(snap.finished());
        assertTrue(snap.hands().isEmpty(), "MUSTER 中立快照不下发任何手牌");
        // 暗选阶段按收件人只暴露自己的手牌
        assertEquals(4, room.snapshot("alice").hands().get(1).size());
        assertFalse(room.snapshot("alice").hands().containsKey(2));
    }

    /** 走完轮抽，进入 MUSTER */
    private static void finishDraft(MagicalAthleteRoom room) {
        while (room.getPhase() == MagicalAthleteRoom.Phase.DRAFT) {
            MagicalAthleteSnapshotVO snap = room.snapshot();
            Integer seat = snap.currentSeatNo();
            assertNotNull(seat);
            String name = "p" + seat;
            assertEquals(MagicalAthleteRoom.PickResult.SUCCESS, room.pickRacer(name, snap.pool().get(0)));
        }
    }

    private static MagicalAthleteRoom room2() {
        MagicalAthleteRoom room = new MagicalAthleteRoom("R2", "p1", 2);
        assertEquals(MagicalAthleteRoom.SeatResult.SUCCESS, room.takeSeat("p1", 1));
        assertEquals(MagicalAthleteRoom.SeatResult.SUCCESS, room.takeSeat("p2", 2));
        room.startGameWithSeed("p1", 99L);
        return room;
    }

    @Test
    void musterHidesOpponentChoiceAndRevealsAtRace() {
        MagicalAthleteRoom room = room2();
        finishDraft(room);
        assertEquals(MagicalAthleteRoom.Phase.MUSTER, room.getPhase());

        String key1 = room.snapshot("p1").hands().get(1).get(0);
        String key2 = room.snapshot("p2").hands().get(2).get(0);
        assertEquals(MagicalAthleteRoom.ReadyResult.SUCCESS, room.readyRacer("p1", key1));

        MagicalAthleteSnapshotVO p1 = room.snapshot("p1");
        MagicalAthleteSnapshotVO p2 = room.snapshot("p2");
        assertEquals(key1, p1.myActiveRacerKey());
        assertNull(p2.myActiveRacerKey());
        assertTrue(p1.readySeats().contains(1) && p2.readySeats().contains(1));
        assertTrue(p1.activeRacerKeys().isEmpty(), "MUSTER 全量字段不得泄露已选 key");
        assertFalse(p2.hands().containsKey(1), "p2 看不到 p1 的手牌");

        assertEquals(MagicalAthleteRoom.ReadyResult.SUCCESS, room.readyRacer("p2", key2));
        assertEquals(MagicalAthleteRoom.Phase.RACE, room.getPhase());
        assertEquals(1, room.getRaceNo());
        assertEquals(2, room.snapshot().activeRacerKeys().size(), "开赛后才公开双方出场选手");
    }

    @Test
    void readyRejectsForeignOrUsedRacer() {
        MagicalAthleteRoom room = room2();
        finishDraft(room);
        assertEquals(MagicalAthleteRoom.ReadyResult.RACER_NOT_IN_HAND,
            room.readyRacer("p1", "not-in-hand"));
        assertEquals(MagicalAthleteRoom.ReadyResult.RACER_NOT_IN_HAND,
            room.readyRacer("p1", room.snapshot("p2").hands().get(2).get(0)));
    }

    @Test
    void autoReadyThenAutoRollAdvanceWithoutClients() {
        MagicalAthleteRoom room = room2();
        finishDraft(room);
        long after = System.currentTimeMillis() + 21_000;
        assertEquals(2, room.autoReady(after).size());
        assertEquals(MagicalAthleteRoom.Phase.RACE, room.getPhase());

        // 托管掷骰：两队交替，直到本场结束
        int guard = 0;
        while (room.getPhase() == MagicalAthleteRoom.Phase.RACE && guard++ < 60) {
            assertTrue(room.autoRoll(after).ok());
        }
        assertEquals(MagicalAthleteRoom.Phase.MUSTER, room.getPhase(), "第 1 场结束进入第 2 场暗选");
        assertEquals(1, room.getRaceNo(), "第 1 场已完赛，raceNo 保持为刚结束的场次");
    }

    /** 逐张尝试手牌，找到第一张本场可用的出场选手 */
    private static void readyFirstAvailable(MagicalAthleteRoom room, String player, int seat) {
        for (String key : room.snapshot(player).hands().get(seat)) {
            if (room.readyRacer(player, key) == MagicalAthleteRoom.ReadyResult.SUCCESS) return;
        }
        fail("玩家 " + player + " 没有可用选手");
    }

    @Test
    void fourRacesEndWithRankings() {
        MagicalAthleteRoom room = room2();
        finishDraft(room);
        for (int r = 1; r <= 4; r++) {
            readyFirstAvailable(room, "p1", 1);
            readyFirstAvailable(room, "p2", 2);
            int guard = 0;
            while (room.getPhase() == MagicalAthleteRoom.Phase.RACE && guard++ < 60) {
                // 只让当前行动者掷骰，避免"非当前回合"的拒绝干扰断言
                MagicalAthleteSnapshotVO s = room.snapshot();
                Integer cur = s.currentSeatNo();
                if (cur != null) assertTrue(room.roll(cur == 1 ? "p1" : "p2").ok());
            }
        }
        assertEquals(MagicalAthleteRoom.Phase.FINISHED, room.getPhase());
        assertEquals(RoomStatus.FINISHED, room.getStatus());
        assertEquals(2, room.snapshot().rankings().size(), "2 人局只有两名参赛者");
        assertEquals(1, room.snapshot().rankings().get(0).rank());
        // 仅断言分数已累计（并列与否取决于四场骰点）
        assertTrue(room.snapshot().scores().values().stream().anyMatch(v -> v > 0));
    }

    @Test
    void toVoIsPersonalized() {
        MagicalAthleteRoom room = room3();
        assertEquals(Integer.valueOf(1), room.toVO("alice").mySeatNo());
        assertEquals(Integer.valueOf(2), room.toVO("bob").mySeatNo());
        assertNull(room.toVO("spectator").mySeatNo());
        assertEquals(3, room.toVO("alice").seats().size());
    }
}
