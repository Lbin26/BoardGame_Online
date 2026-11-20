package org.dromara.boardgame.handler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.boardgame.domain.vo.BoardSnapshotVO;
import org.dromara.boardgame.domain.vo.GameMessage;
import org.dromara.boardgame.domain.vo.RoomVO;
import org.dromara.boardgame.enums.GameMessageType;
import org.dromara.boardgame.enums.PlayerColor;
import org.dromara.boardgame.enums.RoomStatus;
import org.dromara.boardgame.manager.ChessRoom;
import org.dromara.boardgame.manager.RoomManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChessWebSocketHandler extends TextWebSocketHandler {

    private final RoomManager roomManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** key = roomId + ":" + playerName，同名玩家跨房间不互踢 */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null) {
            session.close(CloseStatus.BAD_DATA.withReason("missing uri"));
            return;
        }
        String query = uri.getRawQuery();
        String roomId = extractParam(query, "roomId");
        String playerName = extractParam(query, "playerName");

        if (roomId == null || playerName == null) {
            session.sendMessage(new TextMessage(toJson(
                GameMessage.of(GameMessageType.MOVE_INVALID, null, "system", Map.of("error", "roomId and playerName required"))
            )));
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        ChessRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            session.sendMessage(new TextMessage(toJson(
                GameMessage.of(GameMessageType.MOVE_INVALID, roomId, "system", Map.of("error", "room not found"))
            )));
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("room not found"));
            return;
        }

        // 不自动入座：未选座的连接一律视为旁观者，满房也允许观战
        session.getAttributes().put("roomId", roomId);
        session.getAttributes().put("playerName", playerName);
        String key = roomId + ":" + playerName;
        WebSocketSession old = sessions.put(key, session);
        if (old != null && old != session) {
            // 同房间同名的旧连接（如刷新残留）：标记 replaced，其 close 回调不清在线状态
            old.getAttributes().put("replaced", true);
            try {
                old.close(CloseStatus.NORMAL.withReason("replaced by new connection"));
            } catch (IOException e) {
                log.warn("关闭被替换连接失败: {}", e.getMessage());
            }
        }
        room.markConnected(playerName, true);

        // 自身同步：带自己的 viewerColor + 棋盘（刷新后恢复座位视角）
        send(session, GameMessage.of(GameMessageType.PLAYER_JOINED, roomId, playerName,
            Map.of("room", room.toVO(playerName), "board", room.getSnapshot())));

        // 广播：中立视角，不泄漏加入者的 viewerColor；不发给自己（自身同步已带本人视角，
        // 中立广播晚到会覆盖慢握手场景下刚选好的座位颜色）
        broadcastExcept(roomId, GameMessage.of(GameMessageType.PLAYER_JOINED, roomId, playerName,
            Map.of("room", room.toVO(null))), session);

        log.info("玩家连接: {} -> 房间 {}（{}）", playerName, roomId,
            room.getPlayerColor(playerName) == null ? "旁观" : room.getPlayerColor(playerName));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomId = (String) session.getAttributes().get("roomId");
        String playerName = (String) session.getAttributes().get("playerName");
        if (roomId == null || playerName == null) return;

        ChessRoom room = roomManager.getRoom(roomId);
        if (room == null) return;

        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText();
            GameMessageType msgType = GameMessageType.valueOf(type.toUpperCase());

            switch (msgType) {
                case MOVE -> handleMove(session, room, roomId, playerName, node);
                case CHAT -> handleChat(room, roomId, playerName, node);
                case HEARTBEAT -> send(session, GameMessage.of(GameMessageType.HEARTBEAT, roomId, "system", null));
                case RESIGN -> handleResign(room, roomId, playerName);
                case UNDO -> handleUndo(room, roomId, playerName);
                case REMATCH -> handleRematch(room, roomId, playerName);
                default -> {}
            }
        } catch (Exception e) {
            log.warn("WebSocket消息解析失败: {}", e.getMessage());
        }
    }

    private void handleMove(WebSocketSession session, ChessRoom room, String roomId, String playerName, JsonNode node) {
        JsonNode payload = node.path("payload");
        int fromRow = payload.path("fromRow").asInt(-1);
        int fromCol = payload.path("fromCol").asInt(-1);
        int toRow = payload.path("toRow").asInt(-1);
        int toCol = payload.path("toCol").asInt(-1);

        boolean ok = room.makeMove(playerName, fromRow, fromCol, toRow, toCol);
        if (!ok) {
            send(session, GameMessage.of(GameMessageType.MOVE_INVALID, roomId, playerName,
                Map.of("fromRow", fromRow, "fromCol", fromCol, "toRow", toRow, "toCol", toCol)));
            return;
        }

        BoardSnapshotVO snapshot = room.getSnapshot();
        boolean inCheck = room.isInCheck();
        broadcast(roomId, GameMessage.of(GameMessageType.MOVE, roomId, playerName,
            Map.of("fromRow", fromRow, "fromCol", fromCol, "toRow", toRow, "toCol", toCol, "board", snapshot, "inCheck", inCheck)));

        if (room.getStatus() == RoomStatus.FINISHED) {
            PlayerColor winner = room.getEngine().findWinner();
            boolean isCheckmate = room.isCheckmate();
            boolean isStalemate = room.isStalemate();
            String reason = winner == null && isStalemate ? "STALEMATE" : (isCheckmate ? "CHECKMATE" : "NORMAL");
            broadcastGameOver(roomId, winner, snapshot, reason);
        } else if (inCheck) {
            broadcast(roomId, GameMessage.of(GameMessageType.CHAT, roomId, "system",
                Map.of("text", "将军！")));
        }
    }

    private void handleChat(ChessRoom room, String roomId, String playerName, JsonNode node) {
        String text = node.path("payload").path("text").asText();
        if (text.isBlank()) return;
        broadcast(roomId, GameMessage.of(GameMessageType.CHAT, roomId, playerName,
            Map.of("text", text)));
    }

    private void handleResign(ChessRoom room, String roomId, String playerName) {
        if (!room.resign(playerName)) {
            return;
        }
        BoardSnapshotVO snapshot = room.getSnapshot();
        PlayerColor winner = room.getEngine().findWinner();
        broadcast(roomId, GameMessage.of(GameMessageType.RESIGN, roomId, playerName,
            Map.of("board", snapshot, "winner", winner)));
        broadcastGameOver(roomId, winner, snapshot, "RESIGN");
    }

    private void handleUndo(ChessRoom room, String roomId, String playerName) {
        if (!room.undoMove(playerName)) {
            return;
        }
        BoardSnapshotVO snapshot = room.getSnapshot();
        broadcast(roomId, GameMessage.of(GameMessageType.UNDO, roomId, playerName,
            Map.of("board", snapshot)));
    }

    /** 再来一局：仅已结束且请求者已入座；保留双方座位 */
    private void handleRematch(ChessRoom room, String roomId, String playerName) {
        if (room.getStatus() != RoomStatus.FINISHED) return;
        if (room.getPlayerColor(playerName) == null) return;
        room.resetForRematch();
        BoardSnapshotVO snapshot = room.getSnapshot();
        broadcast(roomId, GameMessage.of(GameMessageType.REMATCH, roomId, playerName,
            Map.of("board", snapshot, "room", room.toVO(null))));
        if (room.getStatus() == RoomStatus.PLAYING) {
            broadcast(roomId, GameMessage.of(GameMessageType.GAME_START, roomId, "system",
                Map.of("board", snapshot, "red", room.getRedPlayerName(), "black", room.getBlackPlayerName())));
        }
    }

    /** 座位变更广播（REST 选座/起身成功后由 controller 调用）；color 为 null 表示起身 */
    public void broadcastSeatChange(String roomId, String playerName, PlayerColor color) {
        ChessRoom room = roomManager.getRoom(roomId);
        if (room == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("room", room.toVO(null));
        payload.put("color", color);
        broadcast(roomId, GameMessage.of(GameMessageType.SEAT_CHANGED, roomId, playerName, payload));
        if (room.getStatus() == RoomStatus.PLAYING) {
            broadcast(roomId, GameMessage.of(GameMessageType.GAME_START, roomId, "system",
                Map.of("board", room.getSnapshot(), "red", room.getRedPlayerName(), "black", room.getBlackPlayerName())));
        }
    }

    private void broadcastGameOver(String roomId, PlayerColor winner, BoardSnapshotVO snapshot, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("winner", winner);
        payload.put("board", snapshot);
        payload.put("reason", reason);
        broadcast(roomId, GameMessage.of(GameMessageType.GAME_OVER, roomId, "system", payload));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 被新连接替换的旧 session：不做任何离开处理，避免误清新连接的在线状态
        if (Boolean.TRUE.equals(session.getAttributes().get("replaced"))) return;

        String roomId = (String) session.getAttributes().get("roomId");
        String playerName = (String) session.getAttributes().get("playerName");
        if (roomId == null || playerName == null) return;

        sessions.remove(roomId + ":" + playerName, session);
        ChessRoom room = roomManager.getRoom(roomId);
        // 断线不清座位：刷新/短暂掉线后重连可原位恢复
        if (room != null) room.markConnected(playerName, false);
        log.info("玩家断开: {} <- 房间 {}", playerName, roomId);
        broadcast(roomId, GameMessage.of(GameMessageType.PLAYER_LEAVE, roomId, playerName, null));
    }

    private void send(WebSocketSession session, GameMessage<?> msg) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(toJson(msg)));
        } catch (IOException e) {
            log.warn("发送消息失败: {}", e.getMessage());
        }
    }

    private void broadcast(String roomId, GameMessage<?> msg) {
        broadcastExcept(roomId, msg, null);
    }

    private void broadcastExcept(String roomId, GameMessage<?> msg, WebSocketSession exclude) {
        for (WebSocketSession s : sessions.values()) {
            if (s == exclude) continue;
            String rid = (String) s.getAttributes().get("roomId");
            if (roomId.equals(rid) && s.isOpen()) {
                send(s, msg);
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String extractParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                // 前端 encodeURIComponent 编码（中文名→%XX），必须按 UTF-8 解码，
                // 否则 WS 身份与 REST 选座名字不一致：显示乱码且已选座玩家被当旁观者
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
