package org.dromara.halligalli.handler;

import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.domain.vo.GameMessage;
import org.dromara.game.common.enums.GameMessageType;
import org.dromara.game.common.room.GameRoomManager;
import org.dromara.game.common.util.MessageTypeParser;
import org.dromara.game.common.ws.AbstractGameWebSocketHandler;
import org.dromara.halligalli.engine.HalliGalliBoard;
import org.dromara.halligalli.enums.HalliGalliMessageType;
import org.dromara.halligalli.manager.HalliGalliRoom;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 德国心脏病 WS 处理器。协议：docs/superpowers/specs/2026-11-21-halli-galli-protocol.md。
 *
 * <p>与 Splendor 的区别：快照全量广播（D6），无个性化视角；
 * 抢铃是"无轮次"动作，只做串行仲裁，迟到/防抖请求静默丢弃（D2）。
 */
@Slf4j
@Component
public class HalliGalliWebSocketHandler extends AbstractGameWebSocketHandler<HalliGalliRoom> {

    public HalliGalliWebSocketHandler(GameRoomManager<HalliGalliRoom> roomManager) {
        super(roomManager);
    }

    @Override
    protected Map<String, Object> selfSyncPayload(HalliGalliRoom room, String playerName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("room", room.toVO(playerName));
        payload.put("snapshot", room.snapshot());
        return payload;
    }

    @Override
    protected Map<String, Object> joinBroadcastPayload(HalliGalliRoom room, String playerName) {
        return Map.of("room", room.toVO(null));
    }

    @Override
    protected String describeSeat(HalliGalliRoom room, String playerName) {
        Integer seat = room.seatOf(playerName);
        return seat == null ? "旁观" : "座位 " + seat;
    }

    @Override
    protected void handleGameMessage(WebSocketSession session, HalliGalliRoom room, String roomId,
                                     String playerName, String type, JsonNode node) {
        HalliGalliMessageType msgType = MessageTypeParser.parse(HalliGalliMessageType.class, type).orElse(null);
        if (msgType == null) return;
        switch (msgType) {
            case START_GAME -> handleStartGame(session, room, roomId, playerName);
            case FLIP -> handleFlip(session, room, roomId, playerName);
            case RING -> handleRing(session, room, roomId, playerName);
            default -> { /* 下行类型 */ }
        }
    }

    private void handleStartGame(WebSocketSession session, HalliGalliRoom room, String roomId, String playerName) {
        HalliGalliRoom.StartResult result = room.startGame(playerName);
        if (result != HalliGalliRoom.StartResult.SUCCESS) {
            String message = switch (result) {
                case NOT_HOST -> "只有房主可以开局";
                case NOT_WAITING -> "对局已在进行中";
                case NOT_ENOUGH_PLAYERS -> "至少 2 名玩家入座才能开局";
                default -> "无法开局";
            };
            sendReject(session, roomId, "START_GAME", result.name(), message);
            return;
        }
        // 必须按收件人定制：room.toVO(null) 的 mySeatNo 为空，前端拿到后
        // 会失去"我是几号位"，翻牌按钮永远不可用（翻牌点了没反应）
        broadcastPersonalized(roomId, name -> GameMessage.of(GameMessageType.GAME_START, roomId, playerName,
            Map.of("room", room.toVO(name), "seatOrder", room.seatOrder())));
        broadcastState(roomId, room);
    }

    private void handleFlip(WebSocketSession session, HalliGalliRoom room, String roomId, String playerName) {
        Integer seat = room.seatOf(playerName);
        if (seat == null) {
            sendReject(session, roomId, "FLIP", "NOT_IN_GAME", "你还没有入座");
            return;
        }
        HalliGalliBoard.FlipOutcome outcome = room.flip(seat);
        if (!outcome.ok()) {
            sendReject(session, roomId, "FLIP", outcome.reason(), flipRejectMessage(outcome.reason()));
            return;
        }
        broadcastFlip(roomId, room, outcome, false);
    }

    private void handleRing(WebSocketSession session, HalliGalliRoom room, String roomId, String playerName) {
        Integer seat = room.seatOf(playerName);
        if (seat == null) {
            sendReject(session, roomId, "RING", "NOT_IN_GAME", "你还没有入座");
            return;
        }
        HalliGalliBoard.RingOutcome outcome = room.ring(seat);
        switch (outcome.status()) {
            case REJECTED -> sendReject(session, roomId, "RING", outcome.reason(), ringRejectMessage(outcome.reason()));
            // 防抖与迟到一律静默丢弃：不刷屏、不改状态，客户端按 300ms 自动恢复按钮
            case DEBOUNCED, LATE -> { }
            default -> broadcastRing(roomId, room, outcome);
        }
    }

    // ---- 下行广播（超时任务也复用） ----

    public void broadcastState(String roomId, HalliGalliRoom room) {
        broadcast(roomId, GameMessage.of(HalliGalliMessageType.STATE_SYNC, roomId, "system", room.snapshot()));
    }

    /** 翻牌结果 + 出局事件 + 全量状态；auto=true 表示 10s 超时代翻（D5） */
    public void broadcastFlip(String roomId, HalliGalliRoom room, HalliGalliBoard.FlipOutcome outcome, boolean auto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seatNo", outcome.seatNo());
        payload.put("auto", auto);
        payload.put("card", outcome.card() == null ? null : Map.of(
            "fruit", outcome.card().fruitCode(), "count", outcome.card().count()));
        payload.put("playerOut", outcome.playerOut());
        payload.put("nextSeatNo", outcome.nextSeatNo());
        broadcast(roomId, GameMessage.of(HalliGalliMessageType.FLIP_DONE, roomId, "system", payload));

        if (outcome.playerOut()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("seatNo", outcome.seatNo());
            out.put("name", nameOf(room, outcome.seatNo()));
            broadcast(roomId, GameMessage.of(HalliGalliMessageType.PLAYER_OUT, roomId, "system", out));
        }
        broadcastState(roomId, room);
        broadcastGameOverIfFinished(roomId, room);
    }

    /** 座位变更中立广播（seatNo 为入座结果，起身时为 null），由 REST 入座/起身触发 */
    public void broadcastSeatChange(String roomId, String playerName, Integer seatNo) {
        HalliGalliRoom room = getRoom(roomId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("room", room == null ? null : room.toVO(null));
        payload.put("seatNo", seatNo);
        payload.put("playerName", playerName);
        broadcast(roomId, GameMessage.of(GameMessageType.SEAT_CHANGED, roomId, playerName, payload));
    }

    /** 抢铃结果：先播结果（音效/浮层），再同步全量状态 */
    public void broadcastRing(String roomId, HalliGalliRoom room, HalliGalliBoard.RingOutcome outcome) {
        broadcast(roomId, GameMessage.of(HalliGalliMessageType.RING_RESULT, roomId, "system",
            room.lastRingVO()));
        broadcastState(roomId, room);
        broadcastGameOverIfFinished(roomId, room);
    }

    private void broadcastGameOverIfFinished(String roomId, HalliGalliRoom room) {
        if (room.getStatus() != org.dromara.game.common.enums.RoomStatus.FINISHED) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rankings", room.getBoard() == null ? java.util.List.of() : room.getBoard().rankings());
        broadcast(roomId, GameMessage.of(GameMessageType.GAME_OVER, roomId, "system", payload));
    }

    private String nameOf(HalliGalliRoom room, int seatNo) {
        if (room.getBoard() == null) return null;
        var player = room.getBoard().playerOf(seatNo);
        return player == null ? null : player.getName();
    }

    private void sendReject(WebSocketSession session, String roomId, String action, String reason, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("reason", reason);
        payload.put("message", message);
        send(session, GameMessage.of(HalliGalliMessageType.ACTION_REJECT, roomId, "system", payload));
    }

    private String flipRejectMessage(String reason) {
        return switch (reason == null ? "" : reason) {
            case "NOT_YOUR_TURN" -> "还没轮到你翻牌";
            case "NOT_PLAYING" -> "对局未开始或已结束";
            case "NOT_IN_GAME" -> "你不在本局中";
            case "PLAYER_OUT" -> "你已出局";
            default -> "无法翻牌";
        };
    }

    private String ringRejectMessage(String reason) {
        return switch (reason == null ? "" : reason) {
            case "PLAYER_OUT" -> "你已出局，不能再抢铃";
            case "NOT_PLAYING" -> "对局未开始或已结束";
            case "NOT_IN_GAME" -> "你不在本局中";
            default -> "无法抢铃";
        };
    }
}
