package org.dromara.xiangqi.manager;

import org.dromara.game.common.enums.PlayerColor;
import org.dromara.game.common.enums.RoomStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 手动选座模型测试：座位竞争、换座、起身、重开、在线追踪 */
class XiangqiRoomSeatTest {

    private XiangqiRoom newRoom() {
        return new XiangqiRoom("R1");
    }

    @Test
    void newRoomHasBothSeatsEmptyAndWaiting() {
        XiangqiRoom room = newRoom();
        assertNull(room.getRedPlayerName());
        assertNull(room.getBlackPlayerName());
        assertEquals(RoomStatus.WAITING, room.getStatus());
        assertNull(room.toVO("anyone").viewerColor());
    }

    @Test
    void takeRedSeatKeepsWaitingAndSetsViewerColor() {
        XiangqiRoom room = newRoom();
        assertEquals(XiangqiRoom.SeatResult.SUCCESS, room.takeSeat("alice", PlayerColor.RED));
        assertEquals("alice", room.getRedPlayerName());
        assertEquals(RoomStatus.WAITING, room.getStatus());
        assertEquals(PlayerColor.RED, room.toVO("alice").viewerColor());
    }

    @Test
    void bothSeatsFilledStartsGame() {
        XiangqiRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        assertEquals(XiangqiRoom.SeatResult.SUCCESS, room.takeSeat("bob", PlayerColor.BLACK));
        assertEquals(RoomStatus.PLAYING, room.getStatus());
    }

    @Test
    void thirdPlayerCannotTakeOccupiedSeat() {
        // WAITING 场景：红座被占、黑座空，第三人抢红座失败且不变异
        XiangqiRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        assertEquals(XiangqiRoom.SeatResult.SEAT_TAKEN, room.takeSeat("bob", PlayerColor.RED));
        assertEquals(XiangqiRoom.SeatResult.SEAT_TAKEN, room.takeSeat("carol", PlayerColor.RED));
        assertEquals("alice", room.getRedPlayerName());
        assertNull(room.getBlackPlayerName());
        assertEquals(RoomStatus.WAITING, room.getStatus());
    }

    @Test
    void cannotTakeSeatWhilePlaying() {
        XiangqiRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        assertEquals(XiangqiRoom.SeatResult.NOT_WAITING, room.takeSeat("carol", PlayerColor.RED));
    }

    @Test
    void sameSeatTwiceIsIdempotent() {
        XiangqiRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        assertEquals(XiangqiRoom.SeatResult.SUCCESS, room.takeSeat("alice", PlayerColor.RED));
        assertEquals("alice", room.getRedPlayerName());
    }

    @Test
    void switchToEmptySeatVacatesOldSeat() {
        XiangqiRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        assertEquals(XiangqiRoom.SeatResult.SUCCESS, room.takeSeat("alice", PlayerColor.BLACK));
        assertNull(room.getRedPlayerName());
        assertEquals("alice", room.getBlackPlayerName());
        assertEquals(RoomStatus.WAITING, room.getStatus());
    }

    @Test
    void switchSeatWhilePlayingRejectedAndKeepsSeats() {
        // 双座满即 PLAYING，对局中换座一律拒绝且座位不变
        XiangqiRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        assertEquals(XiangqiRoom.SeatResult.NOT_WAITING, room.takeSeat("alice", PlayerColor.BLACK));
        assertEquals("alice", room.getRedPlayerName());
        assertEquals("bob", room.getBlackPlayerName());
    }

    @Test
    void cannotTakeSeatAfterFinished() {
        XiangqiRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        assertTrue(room.resign("alice"));
        assertEquals(RoomStatus.FINISHED, room.getStatus());
        assertEquals(XiangqiRoom.SeatResult.NOT_WAITING, room.takeSeat("carol", PlayerColor.RED));
        assertEquals(XiangqiRoom.SeatResult.NOT_WAITING, room.takeSeat("alice", PlayerColor.BLACK));
        assertEquals("alice", room.getRedPlayerName());
        assertEquals("bob", room.getBlackPlayerName());
    }

    @Test
    void standUpOnlyWhileWaitingAndSeated() {
        XiangqiRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        assertTrue(room.standUp("alice"));
        assertNull(room.getRedPlayerName());
        assertFalse(room.standUp("alice")); // 已起身
        assertFalse(room.standUp("spectator")); // 旁观者
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        assertFalse(room.standUp("alice")); // 对局中
        assertEquals("alice", room.getRedPlayerName());
    }

    @Test
    void spectatorAndWrongTurnCannotMove() {
        XiangqiRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        assertFalse(room.makeMove("spectator", 7, 1, 7, 4));
        assertFalse(room.makeMove("bob", 7, 1, 7, 4)); // 黑方不能走红子/错轮
        assertTrue(room.makeMove("alice", 7, 1, 7, 4)); // 红炮平中
    }

    @Test
    void rematchKeepsBothPlayersAndResetsBoard() {
        XiangqiRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        assertTrue(room.resign("alice"));
        assertEquals(RoomStatus.FINISHED, room.getStatus());
        long before = room.getLastActivityAt();
        room.resetForRematch();
        assertEquals("alice", room.getRedPlayerName());
        assertEquals("bob", room.getBlackPlayerName());
        assertEquals(RoomStatus.PLAYING, room.getStatus());
        assertEquals(PlayerColor.RED, room.getEngine().getCurrentTurn());
        assertTrue(room.getLastActivityAt() >= before);
    }

    @Test
    void presenceTracksSpectatorsAndSurvivesDisconnect() {
        XiangqiRoom room = newRoom();
        assertFalse(room.hasConnectedPlayers());
        room.markConnected("watcher", true);
        assertTrue(room.hasConnectedPlayers());
        room.takeSeat("alice", PlayerColor.RED);
        room.markConnected("alice", true);
        room.markConnected("alice", false);
        assertFalse(room.isRedConnected());
        assertEquals("alice", room.getRedPlayerName()); // 断线不清座位
        assertTrue(room.hasConnectedPlayers()); // 旁观者仍在
        room.markConnected("watcher", false);
        assertFalse(room.hasConnectedPlayers());
    }

    @Test
    void concurrentSeatRaceHasExactlyOneWinner() throws Exception {
        XiangqiRoom room = newRoom();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<XiangqiRoom.SeatResult> results = new ArrayList<>();
        for (String name : List.of("t1", "t2")) {
            pool.submit(() -> {
                try {
                    start.await();
                    synchronized (results) {
                        results.add(room.takeSeat(name, PlayerColor.BLACK));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(2, results.size());
        assertTrue(results.contains(XiangqiRoom.SeatResult.SUCCESS));
        assertTrue(results.contains(XiangqiRoom.SeatResult.SEAT_TAKEN));
        assertEquals(1, results.stream().filter(r -> r == XiangqiRoom.SeatResult.SUCCESS).count());
    }
}
