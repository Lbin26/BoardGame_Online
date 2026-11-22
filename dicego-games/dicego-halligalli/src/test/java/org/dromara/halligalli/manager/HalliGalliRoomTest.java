package org.dromara.halligalli.manager;

import org.dromara.game.common.enums.RoomStatus;
import org.dromara.halligalli.domain.vo.HalliGalliSnapshotVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 房间层测试：容量 2-6、房主开局、对局中锁座、10s 超时代翻、快照形状。
 */
class HalliGalliRoomTest {

    private static HalliGalliRoom room(int maxPlayers) {
        HalliGalliRoom room = new HalliGalliRoom("R1", "alice", maxPlayers);
        room.takeSeat("alice", 1);
        room.takeSeat("bob", 2);
        return room;
    }

    @Test
    void 容量必须是二到六() {
        assertThrows(IllegalArgumentException.class, () -> new HalliGalliRoom("R1", "a", 1));
        assertThrows(IllegalArgumentException.class, () -> new HalliGalliRoom("R1", "a", 7));
        assertEquals(6, new HalliGalliRoom("R1", "a", 6).getMaxPlayers());
    }

    @Test
    void 非房主不能开局_不足两人不能开局() {
        HalliGalliRoom room = new HalliGalliRoom("R1", "alice", 4);
        room.takeSeat("alice", 1);

        assertEquals(HalliGalliRoom.StartResult.NOT_ENOUGH_PLAYERS, room.startGame("alice"));
        room.takeSeat("bob", 2);
        assertEquals(HalliGalliRoom.StartResult.NOT_HOST, room.startGame("bob"));
        assertEquals(RoomStatus.WAITING, room.getStatus());
    }

    @Test
    void 开局后进入对局并发满五十六张() {
        HalliGalliRoom room = room(4);
        assertEquals(HalliGalliRoom.StartResult.SUCCESS, room.startGame("alice"));
        assertEquals(RoomStatus.PLAYING, room.getStatus());
        assertNotNull(room.getBoard());

        int total = room.getBoard().players().stream().mapToInt(p -> p.deckSize()).sum();
        assertEquals(56, total);
        assertTrue(room.getFlipDeadlineAt() > System.currentTimeMillis());
        assertEquals(HalliGalliRoom.SeatResult.NOT_WAITING, room.takeSeat("carol", 3));
    }

    @Test
    void 超时未翻牌则由服务端代翻并推进翻牌权() {
        HalliGalliRoom room = room(4);
        room.startGame("alice");
        Integer current = room.getBoard().flipSeatNo();

        assertTrue(room.autoFlip(System.currentTimeMillis()).isEmpty()); // 未到 10s
        var outcome = room.autoFlip(System.currentTimeMillis() + HalliGalliRoom.FLIP_TIMEOUT_MS + 1);
        assertTrue(outcome.isPresent());
        assertTrue(outcome.get().ok());
        assertEquals(current, outcome.get().seatNo());
        assertFalse(current.equals(room.getBoard().flipSeatNo()));
    }

    @Test
    void 快照包含四种水果计数与全部入座玩家() {
        HalliGalliRoom room = room(4);
        room.startGame("alice");

        HalliGalliSnapshotVO snapshot = room.snapshot();
        assertEquals("PLAYING", snapshot.phase());
        assertEquals(4, snapshot.totals().size());
        assertEquals(2, snapshot.players().size());
        assertEquals(1, snapshot.players().get(0).seatNo());
        assertNotNull(snapshot.flipSeatNo());
        assertTrue(snapshot.flipDeadlineAt() > 0);
    }

    @Test
    void 等待中的快照只含已入座玩家且无倒计时() {
        HalliGalliRoom room = room(6);
        HalliGalliSnapshotVO snapshot = room.snapshot();

        assertEquals("WAITING", snapshot.phase());
        assertEquals(2, snapshot.players().size());
        assertEquals(0, snapshot.flipDeadlineAt());
        assertEquals(4, snapshot.totals().size());
    }
}
