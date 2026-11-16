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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChessWebSocketHandler extends TextWebSocketHandler {

    private final RoomManager roomManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

        if (room.getStatus() == RoomStatus.WAITING && room.getPlayerColor(playerName) == null) {
            boolean joined = room.joinAsBlack(playerName);
            if (!joined) {
                session.sendMessage(new TextMessage(toJson(
                    GameMessage.of(GameMessageType.MOVE_INVALID, roomId, "system", Map.of("error", "room full"))
                )));
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("room full"));
                return;
            }
        }

        session.getAttributes().put("roomId", roomId);
        session.getAttributes().put("playerName", playerName);
        sessions.put(playerName, session);
        room.markConnected(playerName, true);

        RoomVO roomVO = room.toVO(playerName);
        BoardSnapshotVO snapshot = room.getSnapshot();

        send(session, GameMessage.of(GameMessageType.PLAYER_JOINED, roomId, playerName,
            Map.of("room", roomVO, "board", snapshot)));

        broadcast(roomId, GameMessage.of(GameMessageType.PLAYER_JOINED, roomId, playerName,
            Map.of("room", roomVO)));

        log.info("玩家连接: {} -> 房间 {}", playerName, roomId);

        if (room.getStatus() == RoomStatus.PLAYING && room.getPlayerColor(playerName) != null) {
            broadcast(roomId, GameMessage.of(GameMessageType.GAME_START, roomId, "system",
                Map.of("board", snapshot, "red", room.getRedPlayerName(), "black", room.getBlackPlayerName())));
        }
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
            broadcast(roomId, GameMessage.of(GameMessageType.GAME_OVER, roomId, "system",
                Map.of("winner", winner, "board", snapshot, "reason", reason)));
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
        broadcast(roomId, GameMessage.of(GameMessageType.GAME_OVER, roomId, "system",
            Map.of("winner", winner, "board", snapshot, "reason", "RESIGN")));
    }

    private void handleUndo(ChessRoom room, String roomId, String playerName) {
        if (!room.undoMove(playerName)) {
            return;
        }
        BoardSnapshotVO snapshot = room.getSnapshot();
        broadcast(roomId, GameMessage.of(GameMessageType.UNDO, roomId, playerName,
            Map.of("board", snapshot)));
    }

    private void handleRematch(ChessRoom room, String roomId, String playerName) {
        room.reset();
        String blackName = room.getBlackPlayerName();
        boolean rejoined = false;
        if (blackName != null) {
            rejoined = room.joinAsBlack(blackName);
        }
        BoardSnapshotVO snapshot = room.getSnapshot();
        RoomVO roomVO = room.toVO(null);
        broadcast(roomId, GameMessage.of(GameMessageType.REMATCH, roomId, playerName,
            Map.of("board", snapshot, "room", roomVO, "rejoined", rejoined)));
        broadcast(roomId, GameMessage.of(GameMessageType.GAME_START, roomId, "system",
            Map.of("board", snapshot, "red", room.getRedPlayerName(), "black", room.getBlackPlayerName())));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = (String) session.getAttributes().get("roomId");
        String playerName = (String) session.getAttributes().get("playerName");
        if (playerName != null) sessions.remove(playerName);
        if (roomId != null && playerName != null) {
            ChessRoom room = roomManager.getRoom(roomId);
            if (room != null) room.markConnected(playerName, false);
            log.info("玩家断开: {} <- 房间 {}", playerName, roomId);
            broadcast(roomId, GameMessage.of(GameMessageType.PLAYER_LEAVE, roomId, playerName, null));
        }
    }

    private void send(WebSocketSession session, GameMessage<?> msg) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(toJson(msg)));
        } catch (IOException e) {
            log.warn("发送消息失败: {}", e.getMessage());
        }
    }

    private void broadcast(String roomId, GameMessage<?> msg) {
        for (WebSocketSession s : sessions.values()) {
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
                return kv[1];
            }
        }
        return null;
    }
}
