package org.dromara.boardgame.handler;

import lombok.extern.slf4j.Slf4j;
import org.dromara.boardgame.domain.vo.BoardSnapshotVO;
import org.dromara.game.common.domain.vo.GameMessage;
import org.dromara.game.common.enums.GameMessageType;
import org.dromara.game.common.enums.PlayerColor;
import org.dromara.game.common.enums.RoomStatus;
import org.dromara.boardgame.manager.ChessRoom;
import org.dromara.game.common.room.GameRoomManager;
import org.dromara.game.common.ws.AbstractGameWebSocketHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ChessWebSocketHandler extends AbstractGameWebSocketHandler<ChessRoom> {

    public ChessWebSocketHandler(GameRoomManager<ChessRoom> roomManager) {
        super(roomManager);
    }

    @Override
    protected Map<String, Object> selfSyncPayload(ChessRoom room, String playerName) {
        return Map.of("room", room.toVO(playerName), "board", room.getSnapshot());
    }

    @Override
    protected Map<String, Object> joinBroadcastPayload(ChessRoom room, String playerName) {
        return Map.of("room", room.toVO(null));
    }

    @Override
    protected String describeSeat(ChessRoom room, String playerName) {
        PlayerColor color = room.getPlayerColor(playerName);
        return color == null ? "旁观" : color.toString();
    }

    @Override
    protected void handleGameMessage(WebSocketSession session, ChessRoom room, String roomId,
                                     String playerName, GameMessageType type, JsonNode node) {
        switch (type) {
            case MOVE -> handleMove(session, room, roomId, playerName, node);
            case RESIGN -> handleResign(room, roomId, playerName);
            case UNDO -> handleUndo(room, roomId, playerName);
            case REMATCH -> handleRematch(room, roomId, playerName);
            default -> {}
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
        ChessRoom room = getRoom(roomId);
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
}
