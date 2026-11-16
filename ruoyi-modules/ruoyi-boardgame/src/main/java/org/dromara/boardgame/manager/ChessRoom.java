package org.dromara.boardgame.manager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.dromara.boardgame.domain.vo.BoardSnapshotVO;
import org.dromara.boardgame.domain.vo.ChessPieceVO;
import org.dromara.boardgame.domain.vo.RoomVO;
import org.dromara.boardgame.engine.ChessBoard;
import org.dromara.boardgame.engine.ChessEngine;
import org.dromara.boardgame.engine.ChessPiece;
import org.dromara.boardgame.enums.PlayerColor;
import org.dromara.boardgame.enums.RoomStatus;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ChessRoom {

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

    private PlayerColor lastMoveCaptured = null;

    public ChessRoom(String roomId, String creatorName) {
        this.roomId = roomId;
        this.engine = new ChessEngine();
        this.status = RoomStatus.WAITING;
        this.redPlayerName = creatorName;
        this.blackPlayerName = null;
        this.lastActivityAt = System.currentTimeMillis();
    }

    private void touch() {
        this.lastActivityAt = System.currentTimeMillis();
    }

    public synchronized void markConnected(String playerName, boolean connected) {
        PlayerColor color = getPlayerColor(playerName);
        if (color == PlayerColor.RED) this.redConnected = connected;
        else if (color == PlayerColor.BLACK) this.blackConnected = connected;
        touch();
    }

    public synchronized boolean joinAsBlack(String playerName) {
        if (status != RoomStatus.WAITING) return false;
        if (blackPlayerName != null) return false;
        this.blackPlayerName = playerName;
        this.status = RoomStatus.PLAYING;
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
        ChessPiece captured = engine.move(fromRow, fromCol, toRow, toCol);
        if (captured == null) return false;
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

    public synchronized void reset() {
        engine.reset();
        this.status = RoomStatus.WAITING;
        this.blackPlayerName = null;
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
