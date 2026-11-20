package org.dromara.boardgame.manager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.dromara.boardgame.domain.vo.BoardSnapshotVO;
import org.dromara.boardgame.domain.vo.ChessPieceVO;
import org.dromara.game.common.domain.vo.RoomVO;
import org.dromara.boardgame.engine.ChessBoard;
import org.dromara.boardgame.engine.ChessEngine;
import org.dromara.boardgame.engine.ChessPiece;
import org.dromara.game.common.enums.PlayerColor;
import org.dromara.game.common.enums.RoomStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ChessRoom {

    /** 选座结果 */
    public enum SeatResult { SUCCESS, NOT_WAITING, SEAT_TAKEN }

    @Getter
    private final String roomId;

    @Getter
    private final ChessEngine engine;

    @Getter
    private volatile RoomStatus status;

    @Getter
    private String redPlayerName;

    @Getter
    private String blackPlayerName;

    @Getter
    private volatile long lastActivityAt;

    @Getter
    private volatile boolean redConnected;

    @Getter
    private volatile boolean blackConnected;

    /** 当前在线玩家名（含旁观者），用于清理宽限判定 */
    private final Set<String> connectedPlayers = ConcurrentHashMap.newKeySet();

    public ChessRoom(String roomId) {
        this.roomId = roomId;
        this.engine = new ChessEngine();
        this.status = RoomStatus.WAITING;
        this.redPlayerName = null;
        this.blackPlayerName = null;
        this.lastActivityAt = System.currentTimeMillis();
    }

    private void touch() {
        this.lastActivityAt = System.currentTimeMillis();
    }

    public synchronized void markConnected(String playerName, boolean connected) {
        if (connected) connectedPlayers.add(playerName);
        else connectedPlayers.remove(playerName);
        PlayerColor color = getPlayerColor(playerName);
        if (color == PlayerColor.RED) this.redConnected = connected;
        else if (color == PlayerColor.BLACK) this.blackConnected = connected;
        touch();
    }

    public boolean hasConnectedPlayers() {
        return !connectedPlayers.isEmpty();
    }

    /**
     * 手动选座：仅等待中可选；目标座空或已是自己则成功（同座幂等、占另一座则换座）；
     * 双座满自动进入对局；被他人占用则失败且不变异。
     */
    public synchronized SeatResult takeSeat(String playerName, PlayerColor color) {
        if (status != RoomStatus.WAITING) return SeatResult.NOT_WAITING;
        String target = color == PlayerColor.RED ? redPlayerName : blackPlayerName;
        if (target != null && !target.equals(playerName)) return SeatResult.SEAT_TAKEN;
        // 换座：先腾出原座位
        if (playerName.equals(redPlayerName)) redPlayerName = null;
        if (playerName.equals(blackPlayerName)) blackPlayerName = null;
        if (color == PlayerColor.RED) redPlayerName = playerName;
        else blackPlayerName = playerName;
        if (redPlayerName != null && blackPlayerName != null) {
            this.status = RoomStatus.PLAYING;
        }
        touch();
        return SeatResult.SUCCESS;
    }

    /** 起身离座：仅等待中且已入座可行；对局中离开视为认输而非起身 */
    public synchronized boolean standUp(String playerName) {
        if (status != RoomStatus.WAITING) return false;
        if (playerName != null && playerName.equals(redPlayerName)) {
            redPlayerName = null;
        } else if (playerName != null && playerName.equals(blackPlayerName)) {
            blackPlayerName = null;
        } else {
            return false;
        }
        touch();
        return true;
    }

    public synchronized PlayerColor getPlayerColor(String playerName) {
        if (playerName != null && playerName.equals(redPlayerName)) return PlayerColor.RED;
        if (playerName != null && playerName.equals(blackPlayerName)) return PlayerColor.BLACK;
        return null;
    }

    public synchronized String getPlayerName(PlayerColor color) {
        if (color == PlayerColor.RED) return redPlayerName;
        return blackPlayerName;
    }

    public synchronized boolean makeMove(String playerName, int fromRow, int fromCol, int toRow, int toCol) {
        if (status != RoomStatus.PLAYING) return false;
        PlayerColor color = getPlayerColor(playerName);
        if (color == null) return false;
        if (color != engine.getCurrentTurn()) return false;
        // engine.move 返回被吃子（未吃子为 null），不能用返回值判成败
        if (!engine.isValidMove(fromRow, fromCol, toRow, toCol)) return false;
        engine.move(fromRow, fromCol, toRow, toCol);
        touch();
        if (engine.isGameOver()) {
            this.status = RoomStatus.FINISHED;
        }
        return true;
    }

    public synchronized boolean undoMove(String playerName) {
        if (status != RoomStatus.PLAYING && status != RoomStatus.FINISHED) return false;
        PlayerColor color = getPlayerColor(playerName);
        if (color == null) return false;
        if (!engine.undoMove()) return false;
        if (status == RoomStatus.FINISHED) {
            this.status = RoomStatus.PLAYING;
        }
        touch();
        return true;
    }

    public synchronized boolean resign(String playerName) {
        if (status != RoomStatus.PLAYING) return false;
        PlayerColor color = getPlayerColor(playerName);
        if (color == null) return false;
        if (!engine.resign(color)) return false;
        this.status = RoomStatus.FINISHED;
        touch();
        return true;
    }

    public synchronized List<int[]> getValidMoves(int row, int col) {
        ChessPiece piece = engine.getBoard().getPiece(row, col);
        if (piece == null || piece.getColor() != engine.getCurrentTurn()) return List.of();
        return engine.getValidMoves(piece);
    }

    /** 再来一局：保留双方座位，重置棋盘；双座满直接回到对局中 */
    public synchronized void resetForRematch() {
        engine.reset();
        this.status = (redPlayerName != null && blackPlayerName != null)
            ? RoomStatus.PLAYING : RoomStatus.WAITING;
        touch();
    }

    /** 管理端完全重置：清空双方座位回到等待中 */
    public synchronized void reset() {
        engine.reset();
        this.status = RoomStatus.WAITING;
        this.redPlayerName = null;
        this.blackPlayerName = null;
        touch();
    }

    public BoardSnapshotVO getSnapshot() {
        ChessBoard board = engine.getBoard();
        List<ChessPieceVO> pieces = new ArrayList<>();
        for (int r = 0; r < ChessBoard.ROWS; r++) {
            for (int c = 0; c < ChessBoard.COLS; c++) {
                ChessPiece p = board.getPiece(r, c);
                if (p != null) {
                    pieces.add(new ChessPieceVO(p.getType(), p.getColor(), r, c, p.getName()));
                }
            }
        }
        return new BoardSnapshotVO(pieces, engine.getCurrentTurn(), engine.isGameOver(), engine.findWinner());
    }

    public RoomVO toVO(String viewerName) {
        PlayerColor viewerColor = getPlayerColor(viewerName);
        return new RoomVO(roomId, redPlayerName, blackPlayerName, status, viewerColor);
    }

    public boolean isInCheck() {
        return engine.isInCheck();
    }

    public boolean isCheckmate() {
        return engine.isCheckmate();
    }

    public boolean isStalemate() {
        return engine.isStalemate();
    }
}
