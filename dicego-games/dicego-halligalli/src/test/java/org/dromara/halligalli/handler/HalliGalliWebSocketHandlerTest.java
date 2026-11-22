package org.dromara.halligalli.handler;

import org.dromara.game.common.room.GameRoomManager;
import org.dromara.halligalli.manager.HalliGalliRoom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WS 链路回归：开局广播必须带收件人自己的座位，否则前端无法判断"我是不是当前翻牌方"，
 * 表现为翻牌按钮永久不可用（线上试玩复现过）。
 */
class HalliGalliWebSocketHandlerTest {

    private final ObjectMapper json = new ObjectMapper();

    private GameRoomManager<HalliGalliRoom> roomManager;
    private HalliGalliWebSocketHandler handler;
    private HalliGalliRoom room;
    private WebSocketSession alice;
    private WebSocketSession bob;

    @BeforeEach
    void setUp() throws Exception {
        roomManager = new GameRoomManager<>(id -> new HalliGalliRoom(id, "alice", 2));
        handler = new HalliGalliWebSocketHandler(roomManager);
        room = roomManager.createRoom("alice");
        alice = session("alice");
        bob = session("bob");
        handler.afterConnectionEstablished(alice);
        handler.afterConnectionEstablished(bob);
        room.takeSeat("alice", 1);
        room.takeSeat("bob", 2);
    }

    private WebSocketSession session(String name) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getUri()).thenReturn(URI.create(
            "ws://x/ws/halli-galli?roomId=" + room.getRoomId() + "&playerName=" + name));
        when(s.getAttributes()).thenReturn(new HashMap<>());
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    private List<JsonNode> received(WebSocketSession s) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(s, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(t -> json.readTree(t.getPayload())).toList();
    }

    private JsonNode find(List<JsonNode> messages, String type) {
        return messages.stream()
            .filter(m -> type.equals(m.path("type").asText()))
            .reduce((first, second) -> second)
            .orElse(null);
    }

    private void start() throws Exception {
        handler.handleTextMessage(alice, new TextMessage("{\"type\":\"START_GAME\"}"));
    }

    @Test
    void gameStartCarriesEachViewersOwnSeat() throws Exception {
        start();

        JsonNode mine = find(received(alice), "GAME_START");
        assertNotNull(mine, "房主应收到 GAME_START");
        assertEquals(1, mine.path("payload").path("room").path("mySeatNo").asInt());

        JsonNode others = find(received(bob), "GAME_START");
        assertNotNull(others, "其他玩家也应收到 GAME_START");
        assertEquals(2, others.path("payload").path("room").path("mySeatNo").asInt());
    }

    @Test
    void currentFlipSeatCanFlipAndFlipAdvancesTurn() throws Exception {
        start();
        Integer first = room.getBoard().flipSeatNo();
        assertNotNull(first);

        WebSocketSession firstSession = first == 1 ? alice : bob;
        WebSocketSession otherSession = first == 1 ? bob : alice;

        // 非当前方翻牌必须被拒绝，而不是静默无响应（两人局翻完会换手，所以先测拒绝）
        handler.handleTextMessage(otherSession, new TextMessage("{\"type\":\"FLIP\"}"));
        JsonNode reject = find(received(otherSession), "ACTION_REJECT");
        assertNotNull(reject, "非当前翻牌方应收到 ACTION_REJECT");
        assertEquals("NOT_YOUR_TURN", reject.path("payload").path("reason").asText());

        handler.handleTextMessage(firstSession, new TextMessage("{\"type\":\"FLIP\"}"));

        JsonNode done = find(received(alice), "FLIP_DONE");
        assertNotNull(done, "翻牌成功应广播 FLIP_DONE");
        assertEquals(first.intValue(), done.path("payload").path("seatNo").asInt());
        assertEquals(1, room.getBoard().playerOf(first).faceUpSize(), "明牌堆应新增一张");
    }
}
