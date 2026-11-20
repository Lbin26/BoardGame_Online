package org.dromara.xiangqi.handler;

import org.dromara.game.common.enums.PlayerColor;
import org.dromara.game.common.enums.RoomStatus;
import org.dromara.xiangqi.manager.XiangqiRoom;
import org.dromara.game.common.room.GameRoomManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** WS 连接语义：陌生人=旁观者、自身视角同步、广播中立、重复连接替换 */
class XiangqiWebSocketHandlerTest {

    private GameRoomManager<XiangqiRoom> roomManager;
    private XiangqiWebSocketHandler handler;
    private XiangqiRoom room;

    @BeforeEach
    void setUp() {
        roomManager = new GameRoomManager<>(XiangqiRoom::new);
        handler = new XiangqiWebSocketHandler(roomManager);
        room = roomManager.createRoom("creator");
    }

    private WebSocketSession session(String playerName) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(
            URI.create("ws://x/ws/chess?roomId=" + room.getRoomId() + "&playerName=" + playerName));
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private List<String> sentTexts(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(TextMessage::getPayload).toList();
    }

    @Test
    void unknownPlayerConnectsAsSpectatorWithoutSeat() throws Exception {
        WebSocketSession session = session("stranger");
        handler.afterConnectionEstablished(session);
        assertNull(room.getPlayerColor("stranger"));
        assertEquals(RoomStatus.WAITING, room.getStatus());
        assertTrue(room.hasConnectedPlayers());
        assertTrue(sentTexts(session).get(0).contains("PLAYER_JOINED"));
    }

    @Test
    void selfSyncCarriesOwnViewerColorButBroadcastIsNeutral() throws Exception {
        room.takeSeat("alice", PlayerColor.RED);
        WebSocketSession alice = session("alice");
        handler.afterConnectionEstablished(alice);
        assertTrue(sentTexts(alice).get(0).contains("\"viewerColor\":\"RED\""));

        WebSocketSession bob = session("bob");
        handler.afterConnectionEstablished(bob);
        // bob 自身同步：无座
        assertTrue(sentTexts(bob).get(0).contains("\"viewerColor\":null"));
        // alice 收到的 bob 加入广播：viewerColor 中立（null），不泄漏 bob 视角
        List<String> aliceMsgs = sentTexts(alice);
        String last = aliceMsgs.get(aliceMsgs.size() - 1);
        assertTrue(last.contains("PLAYER_JOINED"));
        assertTrue(last.contains("\"viewerColor\":null"));
    }

    @Test
    void joinBroadcastExcludesTheJoiningSessionItself() throws Exception {
        WebSocketSession alice = session("alice");
        handler.afterConnectionEstablished(alice);
        WebSocketSession bob = session("bob");
        handler.afterConnectionEstablished(bob);
        // alice：自身同步 + bob 的加入广播 = 2；不含自己加入的中立广播
        long aliceJoined = sentTexts(alice).stream().filter(m -> m.contains("PLAYER_JOINED")).count();
        long bobJoined = sentTexts(bob).stream().filter(m -> m.contains("PLAYER_JOINED")).count();
        assertEquals(2, aliceJoined);
        assertEquals(1, bobJoined);
    }

    @Test
    void reconnectDoesNotBroadcastGameStart() throws Exception {
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        WebSocketSession alice = session("alice");
        handler.afterConnectionEstablished(alice);
        assertFalse(sentTexts(alice).stream().anyMatch(m -> m.contains("GAME_START")));
    }

    @Test
    void duplicateConnectionReplacesOldSessionWithoutLeaveSideEffects() throws Exception {
        room.takeSeat("alice", PlayerColor.RED);
        WebSocketSession old = session("alice");
        handler.afterConnectionEstablished(old);
        WebSocketSession fresh = session("alice");
        handler.afterConnectionEstablished(fresh);

        assertEquals(true, old.getAttributes().get("replaced"));
        verify(old).close(any(CloseStatus.class));

        handler.afterConnectionClosed(old, CloseStatus.NORMAL);
        // 被替换的旧连接断开不应清除在线状态，也不应影响新连接
        assertTrue(room.isRedConnected());
        assertTrue(room.hasConnectedPlayers());
        assertNull(fresh.getAttributes().get("replaced"));
    }

    @Test
    void normalDisconnectKeepsSeatButClearsPresence() throws Exception {
        room.takeSeat("alice", PlayerColor.RED);
        WebSocketSession alice = session("alice");
        handler.afterConnectionEstablished(alice);
        handler.afterConnectionClosed(alice, CloseStatus.NORMAL);
        assertEquals("alice", room.getRedPlayerName());
        assertFalse(room.isRedConnected());
        assertFalse(room.hasConnectedPlayers());
    }

    @Test
    void encodedChineseNameIsDecodedAndMatchesRestSeat() throws Exception {
        // REST 选座落库的是解码后的名字；WS URL 带 percent-encoded 名字（前端 encodeURIComponent）
        room.takeSeat("小潭葛", PlayerColor.RED);
        String encoded = java.net.URLEncoder.encode("小潭葛", java.nio.charset.StandardCharsets.UTF_8);
        WebSocketSession session = session(encoded);
        handler.afterConnectionEstablished(session);
        assertEquals("小潭葛", session.getAttributes().get("playerName"));
        assertTrue(room.isRedConnected());
        // 自身同步必须认得出已选座身份（否则中文名玩家被当旁观者、禁手）
        assertTrue(sentTexts(session).get(0).contains("\"viewerColor\":\"RED\""));
        assertTrue(sentTexts(session).get(0).contains("小潭葛"));
    }

    @Test
    void spectatorCannotRematchButSeatedPlayerCan() throws Exception {
        room.takeSeat("alice", PlayerColor.RED);
        room.takeSeat("bob", PlayerColor.BLACK);
        room.resign("alice");
        assertEquals(RoomStatus.FINISHED, room.getStatus());

        WebSocketSession watcher = session("watcher");
        handler.afterConnectionEstablished(watcher);
        watcher.getAttributes().put("roomId", room.getRoomId());
        watcher.getAttributes().put("playerName", "watcher");
        handler.handleTextMessage(watcher, new TextMessage("{\"type\":\"REMATCH\",\"payload\":{}}"));
        assertEquals(RoomStatus.FINISHED, room.getStatus());

        WebSocketSession alice = session("alice");
        handler.afterConnectionEstablished(alice);
        handler.handleTextMessage(alice, new TextMessage("{\"type\":\"REMATCH\",\"payload\":{}}"));
        assertEquals(RoomStatus.PLAYING, room.getStatus());
        assertEquals("alice", room.getRedPlayerName());
        assertEquals("bob", room.getBlackPlayerName());
    }
}
