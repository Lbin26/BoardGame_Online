package org.dromara.boardgame.manager;

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
class ChessRoomSeatTest {

    private ChessRoom newRoom() {
        return new ChessRoom("R1");
    }

    @Test
    void newRoomHasBothSeatsEmptyAndWaiting() {
        ChessRoom room = newRoom();
        assertNull(room.getRedPlayerName());
        assertNull(room.getBlackPlayerName());
        assertEquals(RoomStatus.WAITING, room.getStatus());
        assertNull(room.toVO("anyone").viewerColor());
    }

    @Test
    void takeRedSeatKeepsWaitingAndSetsViewerColor() {
        ChessRoom room = newRoom();
        assertEquals(ChessRoom.SeatResult.SUCCESS, room.takeSeat("alice", PlayerColor.RED));
        assertEquals("alice", room.getRedPlayerName());
        assertEquals(RoomStatus.WAITING, room.getStatus());
        assertEquals(PlayerColor.RED, room.toVO("alice").viewerColor());
    }

    @Test
    void bothSeatsFilledStartsGame() {
        ChessRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        assertEquals(ChessRoom.SeatResult.SUCCESS, room.takeSeat("bob", PlayerColor.BLACK));
        assertEquals(RoomStatus.PLAYING, room.getStatus());
    }

    @Test
    void thirdPlayerCannotTakeOccupiedSeat() {
        // WAITING 场景：红座被占、黑座空，第三人抢红座失败且不变异
        ChessRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        assertEquals(ChessRoom.SeatResult.SEAT_TAKEN, room.takeSeat("bob", PlayerColor.RED));
        assertEquals(ChessRoom.SeatResult.SEAT_TAKEN, room.takeSeat("carol", PlayerColor.RED));
        assertEquals("alice", room.getRedPlayerName());
        assertNull(room.getBlackPlayerName());
        assertEquals(RoomStatus.WAITING, room.getStatus());
    }

    @Test
    void cannotTakeSeatWhilePlaying() {
        ChessRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        assertEquals(ChessRoom.SeatResult.NOT_WAITING, room.takeSeat("carol", PlayerColor.RED));
    }

    @Test
    void sameSeatTwiceIsIdempotent() {
        ChessRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        assertEquals(ChessRoom.SeatResult.SUCCESS, room.takeSeat("alice", PlayerColor.RED));
        assertEquals("alice", room.getRedPlayerName());
    }

    @Test
    void switchToEmptySeatVacatesOldSeat() {
        ChessRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        assertEquals(ChessRoom.SeatResult.SUCCESS, room.takeSeat("alice", PlayerColor.BLACK));
        assertNull(room.getRedPlayerName());
        assertEquals("alice", room.getBlackPlayerName());
        assertEquals(RoomStatus.WAITING, room.getStatus());
    }

    @Test
    void switchSeatWhilePlayingRejectedAndKeepsSeats() {
        // 双座满即 PLAYING，对局中换座一律拒绝且座位不变
        ChessRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        assertEquals(ChessRoom.SeatResult.NOT_WAITING, room.takeSeat("alice", PlayerColor.BLACK));
        assertEquals("alice", room.getRedPlayerName());
        assertEquals("bob", room.getBlackPlayerName());
    }

    @Test
    void cannotTakeSeatAfterFinished() {
        ChessRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        assertTrue(room.resign("alice"));
        assertEquals(RoomStatus.FINISHED, room.getStatus());
        assertEquals(ChessRoom.SeatResult.NOT_WAITING, room.takeSeat("carol", PlayerColor.RED));
        assertEquals(ChessRoom.SeatResult.NOT_WAITING, room.takeSeat("alice", PlayerColor.BLACK));
        assertEquals("alice", room.getRedPlayerName());
        assertEquals("bob", room.getBlackPlayerName());
    }

    @Test
    void standUpOnlyWhileWaitingAndSeated() {
        ChessRoom room = newRoom();
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
        ChessRoom room = newRoom();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        assertFalse(room.makeMove("spectator", 7, 1, 7, 4));
        assertFalse(room.makeMove("bob", 7, 1, 7, 4)); // 黑方不能走红子/错轮
        assertTrue(room.makeMove("alice", 7, 1, 7, 4)); // 红炮平中
    }

    @Test
    void rematchKeepsBothPlayersAndResetsBoard() {
        ChessRoom room = newRoom();
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
        ChessRoom room = newRoom();
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
        ChessRoom room = newRoom();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<ChessRoom.SeatResult> results = new ArrayList<>();
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
        assertTrue(results.contains(ChessRoom.SeatResult.SUCCESS));
        assertTrue(results.contains(ChessRoom.SeatResult.SEAT_TAKEN));
        assertEquals(1, results.stream().filter(r -> r == ChessRoom.SeatResult.SUCCESS).count());
    }
}
