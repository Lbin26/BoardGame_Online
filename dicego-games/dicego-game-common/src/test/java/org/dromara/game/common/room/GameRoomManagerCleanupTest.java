package org.dromara.game.common.room;

import org.dromara.game.common.enums.RoomStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 清理规则测试：仅无人在线且超宽限才移除；刷新页面（断线重连）不误杀 */
class GameRoomManagerCleanupTest {

    private static final long WAITING_GRACE = 30_000;
    private static final long PLAYING_GRACE = 120_000;
    private static final long FINISHED_GRACE = 60_000;

    /** 最小 GameRoom 子类：takeSeat 进 PLAYING，resign 进 FINISHED，驱动各状态宽限分支 */
    static class TestRoom extends GameRoom {
        TestRoom(String roomId) { super(roomId); }
        void takeSeat(String playerName) {
            this.status = RoomStatus.PLAYING;
            touch();
        }
        void resign() {
            this.status = RoomStatus.FINISHED;
            touch();
        }
    }

    private final GameRoomManager<TestRoom> manager = new GameRoomManager<>(TestRoom::new);

    private TestRoom create() {
        return manager.createRoom("creator");
    }

    @Test
    void createdRoomIsWaiting() {
        TestRoom room = create();
        assertEquals(RoomStatus.WAITING, room.getStatus());
    }

    @Test
    void waitingRoomRemovedOnlyAfterGraceWithNobodyConnected() {
        TestRoom room = create();
        long base = room.getLastActivityAt();
        manager.cleanup(base + WAITING_GRACE - 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
        manager.cleanup(base + WAITING_GRACE + 1);
        assertNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    void refreshReconnectKeepsWaitingRoomAlive() {
        TestRoom room = create();
        room.markConnected("creator", true);
        room.markConnected("creator", false); // 刷新页面断开
        room.markConnected("creator", true); // 数秒后重连
        manager.cleanup(room.getLastActivityAt() + WAITING_GRACE + 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    void spectatorOnlyPresenceKeepsWaitingRoom() {
        TestRoom room = create();
        room.markConnected("watcher", true);
        manager.cleanup(room.getLastActivityAt() + WAITING_GRACE + 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    void abandonedPlayingRoomRemovedAfterLongerGrace() {
        TestRoom room = create();
        room.takeSeat("alice");
        room.takeSeat("bob");
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
        TestRoom room = create();
        room.takeSeat("alice");
        room.takeSeat("bob");
        room.markConnected("alice", true);
        manager.cleanup(room.getLastActivityAt() + PLAYING_GRACE + 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    void finishedRoomRemovedAfterTtlWhenNobodyConnected() {
        TestRoom room = create();
        room.takeSeat("alice");
        room.takeSeat("bob");
        room.resign();
        assertEquals(RoomStatus.FINISHED, room.getStatus());
        long base = room.getLastActivityAt();
        manager.cleanup(base + FINISHED_GRACE - 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
        manager.cleanup(base + FINISHED_GRACE + 1);
        assertNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    void finishedRoomWithConnectedPlayerSurvivesTtl() {
        TestRoom room = create();
        room.takeSeat("alice");
        room.takeSeat("bob");
        room.resign();
        room.markConnected("alice", true);
        manager.cleanup(room.getLastActivityAt() + FINISHED_GRACE + 1);
        assertNotNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    void roomOlderThanOneHourIsClosedEvenWithConnectedPlayers() {
        TestRoom room = create();
        room.markConnected("alice", true);
        manager.cleanup(room.getCreatedAt() + 3_600_000 + 1);
        assertNull(manager.getRoom(room.getRoomId()));
    }
}
