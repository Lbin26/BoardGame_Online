package org.dromara.xiangqi.controller;

import org.dromara.game.common.domain.bo.CreateRoomBO;
import org.dromara.xiangqi.domain.bo.SeatBO;
import org.dromara.xiangqi.domain.vo.XiangqiRoomVO;
import org.dromara.xiangqi.enums.PlayerColor;
import org.dromara.xiangqi.handler.XiangqiWebSocketHandler;
import org.dromara.xiangqi.manager.XiangqiRoom;
import org.dromara.game.common.room.GameRoomManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 选座/起身接口校验与竞争重同步契约 */
class XiangqiControllerSeatTest {

    private GameRoomManager<XiangqiRoom> roomManager;
    private XiangqiWebSocketHandler handler;
    private XiangqiController controller;

    @BeforeEach
    void setUp() {
        roomManager = new GameRoomManager<>(XiangqiRoom::new);
        handler = mock(XiangqiWebSocketHandler.class);
        controller = new XiangqiController(roomManager, handler);
    }

    private SeatBO seatBo(String name, String color) {
        SeatBO bo = new SeatBO();
        bo.setPlayerName(name);
        bo.setColor(color);
        return bo;
    }

    private String createRoom(String name) {
        CreateRoomBO bo = new CreateRoomBO();
        bo.setPlayerName(name);
        Map<String, Object> result = controller.createRoom(bo);
        assertEquals(true, result.get("success"));
        return ((XiangqiRoomVO) result.get("room")).roomId();
    }

    @Test
    void createRoomRejectsBlankName() {
        CreateRoomBO bo = new CreateRoomBO();
        bo.setPlayerName("  ");
        Map<String, Object> result = controller.createRoom(bo);
        assertEquals(false, result.get("success"));
        assertEquals("玩家名不能为空", result.get("error"));
        assertTrue(roomManager.listRoomIds().isEmpty());
    }

    @Test
    void takeSeatMissingRoom() {
        Map<String, Object> result = controller.takeSeat("NOPE", seatBo("alice", "RED"));
        assertEquals(false, result.get("success"));
        assertEquals("房间不存在", result.get("error"));
    }

    @Test
    void takeSeatBlankName() {
        String roomId = createRoom("creator");
        Map<String, Object> result = controller.takeSeat(roomId, seatBo("", "RED"));
        assertEquals(false, result.get("success"));
        assertEquals("玩家名不能为空", result.get("error"));
    }

    @Test
    void takeSeatInvalidColor() {
        String roomId = createRoom("creator");
        Map<String, Object> result = controller.takeSeat(roomId, seatBo("alice", "GREEN"));
        assertEquals(false, result.get("success"));
        assertEquals("无效的颜色", result.get("error"));
    }

    @Test
    void seatRaceLoserGetsErrorPlusFreshRoomAndBoard() {
        String roomId = createRoom("creator");
        controller.takeSeat(roomId, seatBo("alice", "RED"));
        Map<String, Object> result = controller.takeSeat(roomId, seatBo("bob", "RED"));
        assertEquals(false, result.get("success"));
        assertEquals("该座位已被占用", result.get("error"));
        XiangqiRoomVO room = (XiangqiRoomVO) result.get("room");
        assertEquals("alice", room.redPlayerName());
        assertNull(room.viewerColor()); // bob 视角无座
        assertNotNull(result.get("board"));
        // 败者 bob 不触发广播（alice 此前的成功落座已广播过一次）
        verify(handler, never()).broadcastSeatChange(anyString(), eq("bob"), any());
    }

    @Test
    void takeSeatSuccessBroadcastsSeatChange() {
        String roomId = createRoom("creator");
        Map<String, Object> result = controller.takeSeat(roomId, seatBo("alice", "RED"));
        assertEquals(true, result.get("success"));
        assertEquals(PlayerColor.RED, ((XiangqiRoomVO) result.get("room")).viewerColor());
        verify(handler).broadcastSeatChange(roomId, "alice", PlayerColor.RED);
    }

    @Test
    void secondSeatTriggersGameStartBroadcast() {
        String roomId = createRoom("creator");
        controller.takeSeat(roomId, seatBo("alice", "RED"));
        controller.takeSeat(roomId, seatBo("bob", "BLACK"));
        // 第二次落座使房间进入 PLAYING，handler 负责追加 GAME_START
        verify(handler).broadcastSeatChange(roomId, "bob", PlayerColor.BLACK);
        assertEquals(org.dromara.game.common.enums.RoomStatus.PLAYING,
            roomManager.getRoom(roomId).getStatus());
    }

    @Test
    void standUpClearsSeatAndBroadcasts() {
        String roomId = createRoom("creator");
        controller.takeSeat(roomId, seatBo("alice", "RED"));
        Map<String, Object> result = controller.standUp(roomId, seatBo("alice", null));
        assertEquals(true, result.get("success"));
        assertNull(((XiangqiRoomVO) result.get("room")).redPlayerName());
        verify(handler).broadcastSeatChange(eq(roomId), eq("alice"), isNull());
    }

    @Test
    void standUpFailsForSpectator() {
        String roomId = createRoom("creator");
        Map<String, Object> result = controller.standUp(roomId, seatBo("watcher", null));
        assertEquals(false, result.get("success"));
        assertEquals("当前无法起身", result.get("error"));
        assertNotNull(result.get("room"));
    }
}
