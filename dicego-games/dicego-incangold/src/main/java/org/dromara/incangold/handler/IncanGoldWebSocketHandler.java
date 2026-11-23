package org.dromara.incangold.handler;

import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.domain.vo.GameMessage;
import org.dromara.game.common.enums.GameMessageType;
import org.dromara.game.common.enums.RoomStatus;
import org.dromara.game.common.room.GameRoomManager;
import org.dromara.game.common.util.MessageTypeParser;
import org.dromara.game.common.ws.AbstractGameWebSocketHandler;
import org.dromara.incangold.engine.IncanGoldEngine;
import org.dromara.incangold.enums.IncanGoldMessageType;
import org.dromara.incangold.manager.IncanGoldRoom;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 印加宝藏 WS 处理器：连接/聊天/心跳/AFK/解散/关房全走公用基类，
 * 本类只处理 START_GAME / CHOICE / NEXT_ROUND 三条游戏消息与按人定制快照广播。
 */
@Slf4j
@Component
public class IncanGoldWebSocketHandler extends AbstractGameWebSocketHandler<IncanGoldRoom> {

    /** roomId → 上次已广播终局的 eventSeq，避免每 500ms 重复播 GAME_OVER */
    private final ConcurrentHashMap<String, Long> announcedGameOver = new ConcurrentHashMap<>();

    public IncanGoldWebSocketHandler(GameRoomManager<IncanGoldRoom> roomManager) {
        super(roomManager);
    }

    @Override
    protected Map<String, Object> selfSyncPayload(IncanGoldRoom room, String playerName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("room", room.toVO(playerName));
        payload.put("snapshot", room.snapshot(playerName));
        return payload;
    }

    @Override
    protected Map<String, Object> joinBroadcastPayload(IncanGoldRoom room, String playerName) {
        // 只发中立房间信息，绝不带快照：他人视角的 STATE_SYNC 由广播推送，
        // 否则会覆盖已入座玩家自己的宝石视角（与 halliGalli 同一约定）
        return Map.of("room", room.toVO(null));
    }

    @Override
    protected String describeSeat(IncanGoldRoom room, String playerName) {
        Integer seat = room.seatOf(playerName);
        return seat == null ? "旁观" : "座位 " + seat;
    }

    @Override
    protected void handleGameMessage(WebSocketSession session, IncanGoldRoom room, String roomId,
                                     String playerName, String type, JsonNode node) {
        IncanGoldMessageType messageType = MessageTypeParser.parse(IncanGoldMessageType.class, type).orElse(null);
        if (messageType == null) return;
        switch (messageType) {
            case START_GAME -> handleStartGame(session, room, roomId, playerName);
            case CHOICE -> handleChoice(session, room, roomId, playerName, node);
            case NEXT_ROUND -> handleNextRound(session, room, roomId, playerName);
            default -> { /* 下行类型 */ }
        }
    }

    private void handleStartGame(WebSocketSession session, IncanGoldRoom room, String roomId, String playerName) {
        IncanGoldRoom.StartResult result = room.startGame(playerName);
        if (result != IncanGoldRoom.StartResult.SUCCESS) {
            String message = switch (result) {
                case NOT_HOST -> "只有房主可以开始探险";
                case NOT_WAITING -> "对局已在进行中";
                case NOT_ENOUGH_PLAYERS -> "至少 3 名玩家入座才能开始";
                default -> "无法开始";
            };
            sendReject(session, roomId, "START_GAME", result.name(), message);
            return;
        }
        // GAME_START 必须按收件人定制，否则各玩家丢失"我是几号位"与自己的宝石视角
        broadcastPersonalized(roomId, name -> GameMessage.of(GameMessageType.GAME_START, roomId, playerName,
            Map.of("room", room.toVO(name), "snapshot", room.snapshot(name))));
        broadcastState(roomId, room);
    }

    private void handleChoice(WebSocketSession session, IncanGoldRoom room, String roomId,
                              String playerName, JsonNode node) {
        Integer seat = room.seatOf(playerName);
        if (seat == null) {
            sendReject(session, roomId, "CHOICE", "NOT_IN_GAME", "你还没有入座");
            return;
        }
        String raw = node.path("payload").path("choice").asText();
        IncanGoldEngine.Choice choice = parseChoice(raw);
        if (choice == null) {
            sendReject(session, roomId, "CHOICE", "INVALID_CHOICE", "只能提交 CONTINUE 或 RETREAT");
            return;
        }
        long before = room.eventSeq();
        IncanGoldEngine.SubmitResult result = room.submitChoice(playerName, choice);
        if (result != IncanGoldEngine.SubmitResult.SUCCESS) {
            sendReject(session, roomId, "CHOICE", result.name(), choiceRejectMessage(result));
            return;
        }
        broadcastStateIfChanged(roomId, room, before);
        broadcastGameOverIfFinished(roomId, room);
    }

    private void handleNextRound(WebSocketSession session, IncanGoldRoom room, String roomId, String playerName) {
        long before = room.eventSeq();
        if (!room.advanceRound(playerName)) {
            sendReject(session, roomId, "NEXT_ROUND", "NOT_ALLOWED", "只有房主能在结算展示时推进下一轮");
            return;
        }
        broadcastStateIfChanged(roomId, room, before);
        broadcastGameOverIfFinished(roomId, room);
    }

    private static IncanGoldEngine.Choice parseChoice(String raw) {
        if ("CONTINUE".equalsIgnoreCase(raw)) return IncanGoldEngine.Choice.CONTINUE;
        if ("RETREAT".equalsIgnoreCase(raw)) return IncanGoldEngine.Choice.RETREAT;
        return null;
    }

    private static String choiceRejectMessage(IncanGoldEngine.SubmitResult result) {
        return switch (result) {
            case NOT_IN_DECIDE -> "当前不在决策阶段";
            case NOT_IN_EXPEDITION -> "你已撤回营地，本轮不能再决策";
            case ALREADY_SUBMITTED -> "你已经提交过决策";
            case NO_SUCH_PLAYER -> "你不在本局中";
            default -> "无法提交决策";
        };
    }

    // ---- 下行广播（WS 消息处理与定时任务共用） ----

    /** 按收件人定制：每人只看到自己的宝石，他人帐篷遮挡 */
    public void broadcastState(String roomId, IncanGoldRoom room) {
        broadcastPersonalized(roomId, name -> GameMessage.of(IncanGoldMessageType.STATE_SYNC, roomId, "system",
            room.snapshot(name)));
    }

    public void broadcastStateIfChanged(String roomId, IncanGoldRoom room, long beforeSeq) {
        if (room.eventSeq() > beforeSeq) {
            broadcastState(roomId, room);
        }
    }

    public void broadcastGameOverIfFinished(String roomId, IncanGoldRoom room) {
        if (room.getStatus() != RoomStatus.FINISHED) return;
        long seq = room.eventSeq();
        Long previous = announcedGameOver.get(roomId);
        if (previous != null && previous >= seq) return;
        announcedGameOver.put(roomId, seq);
        IncanGoldEngine engine = room.getEngine();
        broadcast(roomId, GameMessage.of(GameMessageType.GAME_OVER, roomId, "system",
            Map.of("rankings", engine == null ? List.of() : engine.rankings())));
    }

    /** REST 入座/起身触发的中立广播 */
    public void broadcastSeatChange(String roomId, String playerName, Integer seatNo) {
        IncanGoldRoom room = getRoom(roomId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("room", room == null ? null : room.toVO(null));
        // 不带快照：SEAT_CHANGED 是中立广播，下发会冲掉玩家自己的宝石视角
        payload.put("seatNo", seatNo);
        payload.put("playerName", playerName);
        broadcast(roomId, GameMessage.of(GameMessageType.SEAT_CHANGED, roomId, playerName, payload));
    }

    private void sendReject(WebSocketSession session, String roomId, String action, String reason, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("reason", reason);
        payload.put("message", message);
        send(session, GameMessage.of(IncanGoldMessageType.ACTION_REJECT, roomId, "system", payload));
    }
}
