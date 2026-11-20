package org.dromara.game.common.ws;

import org.dromara.game.common.enums.RoomStatus;
import org.dromara.game.common.room.GameRoom;
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

/** 通用 WS 语义：缺参拒绝、房间校验、重复连接替换、聊天广播、心跳回应、断开广播 */
class AbstractGameWebSocketHandlerTest {

    static class TestRoom extends GameRoom {
        TestRoom(String roomId) { super(roomId); }
    }

    /** 最小子类：加入载荷仅含 roomId，游戏消息不做任何事 */
    static class TestHandler extends AbstractGameWebSocketHandler<TestRoom> {
        TestHandler(GameRoomManager<TestRoom> manager) { super(manager); }
        @Override
        protected Map<String, Object> selfSyncPayload(TestRoom room, String playerName) {
            return Map.of("roomId", room.getRoomId());
        }
        @Override
        protected Map<String, Object> joinBroadcastPayload(TestRoom room, String playerName) {
            return Map.of("roomId", room.getRoomId());
        }
        @Override
        protected void handleGameMessage(WebSocketSession session, TestRoom room, String roomId,
                                         String playerName, org.dromara.game.common.enums.GameMessageType type,
                                         tools.jackson.databind.JsonNode node) {
        }
    }

    private GameRoomManager<TestRoom> roomManager;
    private TestHandler handler;
    private TestRoom room;

    @BeforeEach
    void setUp() {
        roomManager = new GameRoomManager<>(TestRoom::new);
        handler = new TestHandler(roomManager);
        room = roomManager.createRoom("creator");
    }

    private WebSocketSession session(String playerName) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(
            URI.create("ws://x/ws/game?roomId=" + room.getRoomId() + "&playerName=" + playerName));
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
    void missingParamsRejectedAndClosed() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("ws://x/ws/game"));
        when(session.getAttributes()).thenReturn(new HashMap<>());
        handler.afterConnectionEstablished(session);
        verify(session).close(CloseStatus.BAD_DATA);
    }

    @Test
    void unknownRoomRejectedAndClosed() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("ws://x/ws/game?roomId=NOPE00&playerName=a"));
        when(session.getAttributes()).thenReturn(new HashMap<>());
        handler.afterConnectionEstablished(session);
        verify(session).close(CloseStatus.NOT_ACCEPTABLE.withReason("room not found"));
    }

    @Test
    void connectionMarksPresenceAndSendsSelfSync() throws Exception {
        WebSocketSession session = session("alice");
        handler.afterConnectionEstablished(session);
        assertTrue(room.hasConnectedPlayers());
        assertTrue(sentTexts(session).get(0).contains("PLAYER_JOINED"));
    }

    @Test
    void joinBroadcastExcludesTheJoiningSessionItself() throws Exception {
        WebSocketSession alice = session("alice");
        handler.afterConnectionEstablished(alice);
        WebSocketSession bob = session("bob");
        handler.afterConnectionEstablished(bob);
        long aliceJoined = sentTexts(alice).stream().filter(m -> m.contains("PLAYER_JOINED")).count();
        long bobJoined = sentTexts(bob).stream().filter(m -> m.contains("PLAYER_JOINED")).count();
        assertEquals(2, aliceJoined);
        assertEquals(1, bobJoined);
    }

    @Test
    void duplicateConnectionReplacesOldSession() throws Exception {
        WebSocketSession old = session("alice");
        handler.afterConnectionEstablished(old);
        WebSocketSession fresh = session("alice");
        handler.afterConnectionEstablished(fresh);

        assertEquals(true, old.getAttributes().get("replaced"));
        verify(old).close(any(CloseStatus.class));

        handler.afterConnectionClosed(old, CloseStatus.NORMAL);
        // 被替换的旧连接断开不清在线状态
        assertTrue(room.hasConnectedPlayers());
        assertNull(fresh.getAttributes().get("replaced"));
    }

    @Test
    void chatBroadcastToRoomAndBlankIgnored() throws Exception {
        WebSocketSession alice = session("alice");
        handler.afterConnectionEstablished(alice);
        WebSocketSession bob = session("bob");
        handler.afterConnectionEstablished(bob);

        handler.handleTextMessage(alice, new TextMessage("{\"type\":\"CHAT\",\"payload\":{\"text\":\"你好\"}}"));
        assertTrue(sentTexts(bob).stream().anyMatch(m -> m.contains("CHAT") && m.contains("你好")));

        long before = sentTexts(alice).size();
        handler.handleTextMessage(alice, new TextMessage("{\"type\":\"CHAT\",\"payload\":{\"text\":\"  \"}}"));
        assertEquals(before, sentTexts(alice).size());
    }

    @Test
    void heartbeatReplied() throws Exception {
        WebSocketSession alice = session("alice");
        handler.afterConnectionEstablished(alice);
        handler.handleTextMessage(alice, new TextMessage("{\"type\":\"HEARTBEAT\",\"payload\":{}}"));
        assertTrue(sentTexts(alice).stream().anyMatch(m -> m.contains("HEARTBEAT")));
    }

    @Test
    void normalDisconnectBroadcastsLeaveAndClearsPresence() throws Exception {
        WebSocketSession alice = session("alice");
        handler.afterConnectionEstablished(alice);
        WebSocketSession bob = session("bob");
        handler.afterConnectionEstablished(bob);

        handler.afterConnectionClosed(alice, CloseStatus.NORMAL);
        // alice 断开广播给 bob，但 bob 仍在线
        assertTrue(sentTexts(bob).stream().anyMatch(m -> m.contains("PLAYER_LEAVE")));
        assertTrue(room.hasConnectedPlayers());

        handler.afterConnectionClosed(bob, CloseStatus.NORMAL);
        assertFalse(room.hasConnectedPlayers());
    }
}
