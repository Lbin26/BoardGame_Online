package org.dromara.boardgame.manager;

import org.dromara.boardgame.enums.PlayerColor;
import org.dromara.boardgame.enums.RoomStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 清理规则测试：仅无人在线且超宽限才移除；刷新页面（断线重连）不误杀 */
class RoomManagerCleanupTest {

    private static final long WAITING_GRACE = 30_000;
    private static final long PLAYING_GRACE = 120_000;
    private static final long FINISHED_GRACE = 60_000;

    private final RoomManager manager = new RoomManager();

    private ChessRoom create() {
        return manager.createRoom("creator");
    }

    @Test
    void createRoomNoLongerSeatsCreator() {
        ChessRoom room = create();
        assertNull(room.getRedPlayerName());
        assertNull(room.getBlackPlayerName());
        assertEquals(RoomStatus.WAITING, room.getStatus());
    }

    @Test
    void waitingRoomRemovedOnlyAfterGraceWithNobodyConnected() {
        ChessRoom room = create();
        long base = room.getLastActivityAt();
        manager.cleanup(base + WAITING_GRACE - 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
        manager.cleanup(base + WAITING_GRACE + 1);
        assertNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    void refreshReconnectKeepsWaitingRoomAlive() {
        ChessRoom room = create();
        room.markConnected("creator", true);
        room.markConnected("creator", false); // 刷新页面断开
        room.markConnected("creator", true); // 数秒后重连
        manager.cleanup(room.getLastActivityAt() + WAITING_GRACE + 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    void spectatorOnlyPresenceKeepsWaitingRoom() {
        ChessRoom room = create();
        room.markConnected("watcher", true);
        manager.cleanup(room.getLastActivityAt() + WAITING_GRACE + 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    void abandonedPlayingRoomRemovedAfterLongerGrace() {
        ChessRoom room = create();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        room.markConnected("alice", true);
        room.markConnected("bob", true);
        room.markConnected("alice", false);
        room.markConnected("bob", false);
        long base = room.getLastActivityAt();
        manager.cleanup(base + PLAYING_GRACE - 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
        manager.cleanup(base + PLAYING_GRACE + 1);
        assertNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    void playingRoomWithOneConnectedSurvives() {
        ChessRoom room = create();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        room.markConnected("alice", true);
        manager.cleanup(room.getLastActivityAt() + PLAYING_GRACE + 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    void finishedRoomRemovedAfterTtlWhenNobodyConnected() {
        ChessRoom room = create();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        room.resign("alice");
        assertEquals(RoomStatus.FINISHED, room.getStatus());
        long base = room.getLastActivityAt();
        manager.cleanup(base + FINISHED_GRACE - 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
        manager.cleanup(base + FINISHED_GRACE + 1);
        assertNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    void finishedRoomWithConnectedPlayerSurvivesTtl() {
        ChessRoom room = create();
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        room.resign("alice");
        room.markConnected("alice", true);
        manager.cleanup(room.getLastActivityAt() + FINISHED_GRACE + 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
    }
}
