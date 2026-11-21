package org.dromara.dicego.controller;

import org.dromara.dicego.TestCaches;
import org.dromara.dicego.TestRecorder;
import org.dromara.dicego.domain.bo.SplendorSeatBO;
import org.dromara.dicego.domain.vo.SplendorRoomVO;
import org.dromara.dicego.handler.SplendorWebSocketHandler;
import org.dromara.dicego.manager.SplendorRoom;
import org.dromara.game.common.domain.bo.CreateRoomBO;
import org.dromara.game.common.room.GameRoomManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REST 建房/入座冒烟（M1）：容量参数（D3）与错误口径。
 * M5 起 Controller 依赖 recorder 并从 request 解析用户（测试传 null request，按游客处理）。
 */
class SplendorControllerSeatTest {

    private GameRoomManager<SplendorRoom> manager;
    private SplendorController controller;

    private void newController() {
        manager = new GameRoomManager<>(id -> {
            throw new IllegalStateException("测试不应触达缺省工厂");
        });
        controller = new SplendorController(manager, new SplendorWebSocketHandler(manager),
            TestCaches.cache(8, 6), new TestRecorder());
    }

    @Test
    void 建房_容量缺省4_非法容量拒绝() {
        newController();
        CreateRoomBO bo = new CreateRoomBO();
        bo.setPlayerName("alice");
        Map<String, Object> ok = controller.createRoom(bo, null);
        assertTrue((Boolean) ok.get("success"));
        SplendorRoomVO vo = (SplendorRoomVO) ok.get("room");
        assertEquals(4, vo.maxPlayers());
        assertEquals("alice", vo.hostName());

        CreateRoomBO bad = new CreateRoomBO();
        bad.setPlayerName("alice");
        bad.setMaxPlayers(5);
        assertFalse((Boolean) controller.createRoom(bad, null).get("success"));
        bad.setMaxPlayers(1);
        assertFalse((Boolean) controller.createRoom(bad, null).get("success"));
    }

    @Test
    void 入座_自动补位_满员错误() {
        newController();
        CreateRoomBO bo = new CreateRoomBO();
        bo.setPlayerName("alice");
        bo.setMaxPlayers(2);
        SplendorRoomVO room = (SplendorRoomVO) controller.createRoom(bo, null).get("room");

        SplendorSeatBO seat = new SplendorSeatBO();
        seat.setPlayerName("bob");
        Map<String, Object> r1 = controller.takeSeat(room.roomId(), seat, null);
        assertTrue((Boolean) r1.get("success"));

        seat.setPlayerName("carol");
        assertTrue((Boolean) controller.takeSeat(room.roomId(), seat, null).get("success"), "2/2 满座");

        seat.setPlayerName("dave");
        Map<String, Object> r2 = controller.takeSeat(room.roomId(), seat, null);
        assertFalse((Boolean) r2.get("success"));
        assertEquals("房间已满", r2.get("error"));
        assertEquals(2, ((SplendorRoomVO) r2.get("room")).seats().stream()
            .filter(s -> s.playerName() != null).count());
    }

    @Test
    void 房间不存在_统一错误() {
        newController();
        SplendorSeatBO seat = new SplendorSeatBO();
        seat.setPlayerName("alice");
        Map<String, Object> result = controller.takeSeat("NOPE", seat, null);
        assertFalse((Boolean) result.get("success"));
        assertEquals("房间不存在", result.get("error"));
    }
}
