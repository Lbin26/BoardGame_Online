package org.dromara.dicego.handler;

import lombok.extern.slf4j.Slf4j;
import org.dromara.dicego.domain.vo.SplendorSnapshotVO;
import org.dromara.dicego.engine.SplendorActionResult;
import org.dromara.dicego.engine.SplendorEngine;
import org.dromara.dicego.enums.GemColor;
import org.dromara.dicego.enums.SplendorFailReason;
import org.dromara.dicego.enums.SplendorMessageType;
import org.dromara.dicego.manager.SplendorRoom;
import org.dromara.game.common.domain.vo.GameMessage;
import org.dromara.game.common.enums.GameMessageType;
import org.dromara.game.common.room.GameRoomManager;
import org.dromara.game.common.ws.AbstractGameWebSocketHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Splendor WS 处理器。协议见 docs/superpowers/specs/2026-11-20-splendor-protocol.md：
 * 群发消息一律中立（不含任何 reservedCards），个性化状态仅经 STATE_SYNC 走
 * broadcastPersonalized（D5）。
 */
@Slf4j
@Component
public class SplendorWebSocketHandler extends AbstractGameWebSocketHandler<SplendorRoom> {

    public SplendorWebSocketHandler(GameRoomManager<SplendorRoom> roomManager) {
        super(roomManager);
    }

    @Override
    protected boolean isHost(SplendorRoom room, String playerName) {
        return playerName != null && playerName.equals(room.getHostName());
    }

    @Override
    protected Map<String, Object> selfSyncPayload(SplendorRoom room, String playerName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("room", room.toVO(playerName));
        if (room.getBoard() != null) {
            payload.put("snapshot", room.getBoard().snapshotFor(playerName));
        }
        return payload;
    }

    @Override
    protected Map<String, Object> joinBroadcastPayload(SplendorRoom room, String playerName) {
        return Map.of("room", room.toVO(null));
    }

    @Override
    protected String describeSeat(SplendorRoom room, String playerName) {
        Integer seat = room.seatOf(playerName);
        return seat == null ? "旁观" : "座位 " + seat;
    }

    @Override
    protected void handleGameMessage(WebSocketSession session, SplendorRoom room, String roomId,
                                     String playerName, String type, JsonNode node) {
        SplendorMessageType msgType;
        try {
            msgType = SplendorMessageType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }
        switch (msgType) {
            case START_GAME -> handleStartGame(session, room, roomId, playerName);
            case TAKE_GEMS -> handleTakeGems(session, room, roomId, playerName, type, node);
            case BUY_CARD -> handleBuyCard(session, room, roomId, playerName, type, node);
            case RESERVE_CARD -> handleReserveCard(session, room, roomId, playerName, type, node);
            case DISCARD_GEMS -> handleDiscardGems(session, room, roomId, playerName, type, node);
            case NOBLE_SELECT -> handleNobleSelect(session, room, roomId, playerName, type, node);
            default -> { /* STATE_SYNC/ACTION_DONE/ACTION_REJECT 为服务端专用 */ }
        }
    }

    // ---- 行动分发（M2/M3）：解析 → 引擎 → ACTION_DONE(中立) + STATE_SYNC(个性化) ----

    /** 日志参数序列化（M5：action_params 列，复盘重放的原始请求） */
    private static final ObjectMapper JSON = new ObjectMapper();

    private void handleTakeGems(WebSocketSession session, SplendorRoom room, String roomId,
                                String playerName, String type, JsonNode node) {
        Map<GemColor, Integer> gems;
        try {
            gems = parseGems(node.path("payload").path("gems"));
        } catch (IllegalArgumentException e) {
            sendReject(session, roomId, type, actionIdOf(node), SplendorFailReason.INVALID_GEMS, "宝石格式非法: " + e.getMessage());
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("gems", wireGems(gems));
        finishAction(session, room, roomId, playerName, type, actionIdOf(node), room.applyAction(
            playerName, type, actionIdOf(node), toJson(params),
            b -> SplendorEngine.takeGems(b, playerName, gems)));
    }

    private void handleBuyCard(WebSocketSession session, SplendorRoom room, String roomId,
                               String playerName, String type, JsonNode node) {
        JsonNode payload = node.path("payload");
        String cardId = textOrNull(payload.path("cardId"));
        String source = textOrNull(payload.path("source"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("cardId", cardId);
        params.put("source", source);
        finishAction(session, room, roomId, playerName, type, actionIdOf(node), room.applyAction(
            playerName, type, actionIdOf(node), toJson(params),
            b -> SplendorEngine.buyCard(b, playerName, cardId, source)));
    }

    private void handleReserveCard(WebSocketSession session, SplendorRoom room, String roomId,
                                   String playerName, String type, JsonNode node) {
        JsonNode payload = node.path("payload");
        String cardId = textOrNull(payload.path("cardId"));
        JsonNode levelNode = payload.path("level");
        Integer level = levelNode.isNumber() ? levelNode.asInt() : null;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("cardId", cardId);
        params.put("level", level);
        finishAction(session, room, roomId, playerName, type, actionIdOf(node), room.applyAction(
            playerName, type, actionIdOf(node), toJson(params),
            b -> SplendorEngine.reserveCard(b, playerName, cardId, level)));
    }

    private void handleDiscardGems(WebSocketSession session, SplendorRoom room, String roomId,
                                   String playerName, String type, JsonNode node) {
        Map<GemColor, Integer> gems;
        try {
            gems = parseGems(node.path("payload").path("gems"));
        } catch (IllegalArgumentException e) {
            sendReject(session, roomId, type, actionIdOf(node), SplendorFailReason.INVALID_PARAM, "宝石格式非法: " + e.getMessage());
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("gems", wireGems(gems));
        finishAction(session, room, roomId, playerName, type, actionIdOf(node), room.applyAction(
            playerName, type, actionIdOf(node), toJson(params),
            b -> SplendorEngine.discardGems(b, playerName, gems)));
    }

    private void handleNobleSelect(WebSocketSession session, SplendorRoom room, String roomId,
                                   String playerName, String type, JsonNode node) {
        String nobleId = textOrNull(node.path("payload").path("nobleId"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nobleId", nobleId);
        finishAction(session, room, roomId, playerName, type, actionIdOf(node), room.applyAction(
            playerName, type, actionIdOf(node), toJson(params),
            b -> SplendorEngine.selectNoble(b, playerName, nobleId)));
    }

    /** 成功：中立 ACTION_DONE（供动画）→ 逐人个性化 STATE_SYNC → 终局则 GAME_OVER；失败仅回发送者 */
    private void finishAction(WebSocketSession session, SplendorRoom room, String roomId, String playerName,
                              String action, String actionId, SplendorActionResult result) {
        if (!result.isOk()) {
            sendReject(session, roomId, action, actionId, result.reason(), result.message());
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("seatNo", room.seatOf(playerName));
        payload.put("action", action);
        payload.put("actionId", actionId);
        payload.put("detail", result.detail());
        broadcast(roomId, GameMessage.of(SplendorMessageType.ACTION_DONE, roomId, playerName, payload));
        broadcastStateSync(roomId, room);
        if (room.getBoard() != null && room.getBoard().isFinished()) {
            Map<String, Object> over = new HashMap<>();
            over.put("rankings", SplendorEngine.rankingsOf(room.getBoard()));
            broadcast(roomId, GameMessage.of(GameMessageType.GAME_OVER, roomId, "system", over));
        }
    }

    private void handleStartGame(WebSocketSession session, SplendorRoom room, String roomId, String playerName) {
        SplendorRoom.StartResult result = room.startGame(playerName);
        if (result != SplendorRoom.StartResult.SUCCESS) {
            sendReject(session, roomId, "START_GAME", null, result.name(), switch (result) {
                case NOT_HOST -> "只有房主可以开局";
                case NOT_WAITING -> "对局已在进行中";
                case NOT_ENOUGH_PLAYERS -> "至少 2 名玩家入座才能开局";
                default -> "无法开局";
            });
            return;
        }
        // 中立开局广播（座位顺序等公开信息），随后逐人个性化快照
        broadcast(roomId, GameMessage.of(GameMessageType.GAME_START, roomId, playerName,
            Map.of("room", room.toVO(null), "seatOrder", room.seatOrder())));
        broadcastStateSync(roomId, room);
    }

    /** 全房间逐人 STATE_SYNC：每人拿到自己的视角（D5 预留卡仅本人可见） */
    public void broadcastStateSync(String roomId, SplendorRoom room) {
        if (room.getBoard() == null) return;
        broadcastPersonalized(roomId, playerName -> {
            SplendorSnapshotVO snapshot = room.getBoard().snapshotFor(playerName);
            return GameMessage.of(SplendorMessageType.STATE_SYNC, roomId, "system", snapshot);
        });
    }

    /** 座位变更中立广播（seatNo 为入座结果，起身时为 null） */
    public void broadcastSeatChange(String roomId, String playerName, Integer seatNo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("room", getRoom(roomId) == null ? null : getRoom(roomId).toVO(null));
        payload.put("seatNo", seatNo);
        payload.put("playerName", playerName);
        broadcast(roomId, GameMessage.of(GameMessageType.SEAT_CHANGED, roomId, playerName, payload));
    }

    private void sendReject(WebSocketSession session, String roomId, String action, String actionId,
                            String reason, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("actionId", actionId);
        payload.put("reason", reason);
        payload.put("message", message);
        send(session, GameMessage.of(SplendorMessageType.ACTION_REJECT, roomId, "system", payload));
    }

    private void sendReject(WebSocketSession session, String roomId, String action, String actionId,
                            SplendorFailReason reason, String message) {
        sendReject(session, roomId, action, actionId, reason.name(), message);
    }

    // ---- payload 解析小工具 ----

    /** gems 映射：键为色名 wire 值（white/blue/green/red/black/gold），未知色名抛异常 */
    private Map<GemColor, Integer> parseGems(JsonNode gemsNode) {
        if (gemsNode == null || !gemsNode.isObject()) {
            throw new IllegalArgumentException("gems 须为对象");
        }
        Map<GemColor, Integer> gems = new EnumMap<>(GemColor.class);
        for (Map.Entry<String, JsonNode> e : gemsNode.properties()) {
            GemColor color = GemColor.fromCode(e.getKey());
            gems.put(color, e.getValue().asInt());
        }
        return gems;
    }

    /** 色名 → 数量（只输出实际拿/弃的色，日志参数用） */
    private Map<String, Integer> wireGems(Map<GemColor, Integer> gems) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<GemColor, Integer> e : gems.entrySet()) {
            if (e.getValue() != null && e.getValue() != 0) {
                out.put(e.getKey().getCode(), e.getValue());
            }
        }
        return out;
    }

    private String toJson(Map<String, Object> params) {
        try {
            return JSON.writeValueAsString(params);
        } catch (Exception e) {
            return null;
        }
    }

    private String textOrNull(JsonNode node) {
        return (node == null || node.isNull() || node.isMissingNode()) ? null : node.asText();
    }

    /** 客户端幂等去重用的 actionId，原样透传（日志与回执） */
    private String actionIdOf(JsonNode node) {
        return textOrNull(node.path("payload").path("actionId"));
    }
}
