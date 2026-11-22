package org.dromara.magicalathlete.handler;

import org.dromara.game.common.room.GameRoomManager;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WS 链路回归：① GAME_START 按收件人下发各自 mySeatNo；② 非当前序位选人收 ACTION_REJECT；
 * ③ 当前序位选人广播 DRAFT_PICK 且快照手牌 +1。
 *
 * <p>所有上行消息一律使用前端真实格式 {@code {type, payload:{...}}}（useGameWebSocket.send 的口径）。
 * 曾因 handler 直接读根节点字段导致轮抽/暗选全部被 ACTION_REJECT，而当时测试用平铺格式构造消息、
 * 全绿却测不到真实协议；另有 rootLevelFieldsStillAccepted 单独覆盖兼容分支。
 */
class MagicalAthleteWebSocketHandlerTest {

    private final ObjectMapper json = new ObjectMapper();

    private GameRoomManager<MagicalAthleteRoom> roomManager;
    private MagicalAthleteWebSocketHandler handler;
    private MagicalAthleteRoom room;
    private WebSocketSession alice;
    private WebSocketSession bob;

    @BeforeEach
    void setUp() throws Exception {
        roomManager = new GameRoomManager<>(id -> new MagicalAthleteRoom(id, "alice", 2));
        handler = new MagicalAthleteWebSocketHandler(roomManager);
        room = roomManager.createRoom("alice");
        alice = session("alice");
        bob = session("bob");
        handler.afterConnectionEstablished(alice);
        handler.afterConnectionEstablished(bob);
        room.takeSeat("alice", 1);
        room.takeSeat("bob", 2);
    }

    private WebSocketSession session(String name) {
        return session(room, name);
    }

    private WebSocketSession session(MagicalAthleteRoom target, String name) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getUri()).thenReturn(URI.create(
            "ws://x/ws/magical-athlete?roomId=" + target.getRoomId() + "&playerName=" + name));
        when(s.getAttributes()).thenReturn(new HashMap<>());
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    private List<JsonNode> received(WebSocketSession s) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(s, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(t -> {
            try {
                return json.readTree(t.getPayload());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();
    }

    private JsonNode find(List<JsonNode> messages, String type) {
        return messages.stream()
            .filter(m -> type.equals(m.path("type").asText()))
            .reduce((first, second) -> second)
            .orElse(null);
    }

    @Test
    void gameStartIsPersonalizedPerRecipient() throws Exception {
        handler.handleTextMessage(alice, new TextMessage("{\"type\":\"START_GAME\",\"payload\":{}}"));

        JsonNode startAlice = find(received(alice), "GAME_START");
        JsonNode startBob = find(received(bob), "GAME_START");
        assertNotNull(startAlice);
        assertNotNull(startBob);
        assertEquals(1, startAlice.path("payload").path("room").path("mySeatNo").asInt());
        assertEquals(2, startBob.path("payload").path("room").path("mySeatNo").asInt());
    }

    @Test
    void pickRacerRespectsTurnAndBroadcasts() throws Exception {
        handler.handleTextMessage(alice, new TextMessage("{\"type\":\"START_GAME\",\"payload\":{}}"));

        // 2 人局首序位为 A（1 号 alice）；bob 抢选应被拒
        JsonNode snap = find(received(bob), "STATE_SYNC");
        assertNotNull(snap);
        String firstKey = snap.path("payload").path("pool").get(0).asText();

        handler.handleTextMessage(bob, new TextMessage("{\"type\":\"PICK_RACER\",\"payload\":{\"racerKey\":\"" + firstKey + "\"}}"));
        JsonNode reject = find(received(bob), "ACTION_REJECT");
        assertNotNull(reject, "非当前序位应收到拒绝");
        assertEquals("NOT_YOUR_TURN", reject.path("payload").path("reason").asText());

        handler.handleTextMessage(alice, new TextMessage("{\"type\":\"PICK_RACER\",\"payload\":{\"racerKey\":\"" + firstKey + "\"}}"));
        JsonNode pick = find(received(alice), "DRAFT_PICK");
        assertNotNull(pick, "有效选人应广播 DRAFT_PICK");
        assertEquals(firstKey, pick.path("payload").path("racerKey").asText());
        assertEquals(1, pick.path("payload").path("seatNo").asInt());
        assertEquals(1, room.snapshot().hands().get(1).size());
    }

    /** 通过 WS 走完 2 人局两批轮抽（每批 8 手），进入 MUSTER */
    private void finishDraftViaWs() throws Exception {
        handler.handleTextMessage(alice, new TextMessage("{\"type\":\"START_GAME\",\"payload\":{}}"));
        for (int i = 0; i < 16; i++) {
            JsonNode snap = find(received(alice), "STATE_SYNC");
            int seat = snap.path("payload").path("currentSeatNo").asInt();
            String key = snap.path("payload").path("pool").get(0).asText();
            WebSocketSession session = seat == 1 ? alice : bob;
            handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"PICK_RACER\",\"payload\":{\"racerKey\":\"" + key + "\"}}"));
        }
    }

    @Test
    void musterHidesChoiceInBroadcastAndRaceRevealsWithDice() throws Exception {
        finishDraftViaWs();
        assertEquals(MagicalAthleteRoom.Phase.MUSTER, room.getPhase());

        JsonNode aliceSnap = find(received(alice), "STATE_SYNC");
        JsonNode bobSnap = find(received(bob), "STATE_SYNC");
        String aliceKey = aliceSnap.path("payload").path("hands").path("1").get(0).asText();
        assertTrue(aliceSnap.path("payload").path("myActiveRacerKey").isNull(), "未暗选前为空");

        handler.handleTextMessage(alice,
            new TextMessage("{\"type\":\"READY_RACER\",\"payload\":{\"racerKey\":\"" + aliceKey + "\"}}"));
        aliceSnap = find(received(alice), "STATE_SYNC");
        bobSnap = find(received(bob), "STATE_SYNC");
        assertEquals(aliceKey, aliceSnap.path("payload").path("myActiveRacerKey").asText());
        assertTrue(bobSnap.path("payload").path("myActiveRacerKey").isNull(), "对手看不到 alice 的暗选");
        assertTrue(bobSnap.path("payload").path("readySeats").toString().contains("1"),
            "只广播已准备座位");
        assertNotNull(find(received(bob), "MUSTER_READY"));

        String bobKey = bobSnap.path("payload").path("hands").path("2").get(0).asText();
        handler.handleTextMessage(bob,
            new TextMessage("{\"type\":\"READY_RACER\",\"payload\":{\"racerKey\":\"" + bobKey + "\"}}"));
        assertEquals(MagicalAthleteRoom.Phase.RACE, room.getPhase());
        JsonNode start = find(received(alice), "RACE_START");
        assertNotNull(start, "开赛才广播 RACE_START");
        assertEquals(2, start.path("payload").path("activeRacerKeys").size());

        handler.handleTextMessage(alice, new TextMessage("{\"type\":\"ROLL\",\"payload\":{}}"));
        JsonNode dice = find(received(alice), "DICE_RESULT");
        assertNotNull(dice, "有效 ROLL 应广播 DICE_RESULT");
        int value = dice.path("payload").path("value").asInt();
        assertTrue(value >= 1 && value <= 6, "骰点由服务端生成且在 1-6");
        assertEquals(value, room.snapshot().lastDice());
    }

    /** 兼容分支：旧客户端/内部调用把字段平铺在根节点时仍能正常选人 */
    @Test
    void rootLevelFieldsStillAccepted() throws Exception {
        handler.handleTextMessage(alice, new TextMessage("{\"type\":\"START_GAME\",\"payload\":{}}"));
        JsonNode snap = find(received(bob), "STATE_SYNC");
        String firstKey = snap.path("payload").path("pool").get(0).asText();

        handler.handleTextMessage(alice,
            new TextMessage("{\"type\":\"PICK_RACER\",\"racerKey\":\"" + firstKey + "\"}"));
        assertNotNull(find(received(alice), "DRAFT_PICK"), "根节点平铺格式仍应兼容");
        assertEquals(1, room.snapshot().hands().get(1).size());
    }

    /** M5 变体开关也在 payload 里：之前被静默忽略，导致 3 人双角色开关形同虚设 */
    @Test
    void startGameVariantIsReadFromPayload() throws Exception {
        GameRoomManager<MagicalAthleteRoom> mgr =
            new GameRoomManager<>(id -> new MagicalAthleteRoom(id, "alice", 3));
        MagicalAthleteWebSocketHandler h = new MagicalAthleteWebSocketHandler(mgr);
        MagicalAthleteRoom r = mgr.createRoom("alice");
        WebSocketSession a = session(r, "alice");
        WebSocketSession b = session(r, "bob");
        WebSocketSession c = session(r, "carol");
        h.afterConnectionEstablished(a);
        h.afterConnectionEstablished(b);
        h.afterConnectionEstablished(c);
        r.takeSeat("alice", 1);
        r.takeSeat("bob", 2);
        r.takeSeat("carol", 3);

        h.handleTextMessage(a, new TextMessage(
            "{\"type\":\"START_GAME\",\"payload\":{\"variant\":\"THREE_DOUBLE\"}}"));
        assertEquals("THREE_DOUBLE", r.snapshot().variant(), "payload 里的变体参数必须生效");
    }
}
