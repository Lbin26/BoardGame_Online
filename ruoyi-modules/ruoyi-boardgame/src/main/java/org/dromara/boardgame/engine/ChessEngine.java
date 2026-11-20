package org.dromara.boardgame.engine;

import lombok.Getter;
import org.dromara.boardgame.enums.PieceType;
import org.dromara.game.common.enums.PlayerColor;

import java.util.ArrayList;
import java.util.List;

public class ChessEngine {

    private final ChessBoard board;
    @Getter
    private PlayerColor currentTurn;

    private final List<MoveRecord> history = new ArrayList<>();
    private PlayerColor resignedBy = null;

    public ChessEngine() {
        this.board = new ChessBoard();
        this.currentTurn = PlayerColor.RED;
    }

    public ChessBoard getBoard() {
        return board;
    }

    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        ChessPiece piece = board.getPiece(fromRow, fromCol);
        if (piece == null) return false;
        if (piece.getColor() != currentTurn) return false;
        if (fromRow == toRow && fromCol == toCol) return false;

        ChessPiece target = board.getPiece(toRow, toCol);
        if (target != null && target.getColor() == piece.getColor()) return false;

        if (!checkPieceMove(piece, toRow, toCol)) return false;

        ChessBoard tempBoard = board.copy();
        tempBoard.removePiece(fromRow, fromCol);
        tempBoard.setPiece(toRow, toCol, piece.copy());

        if (isInCheckFor(tempBoard, currentTurn)) return false;

        return true;
    }

    public boolean checkPieceMove(ChessPiece piece, int toRow, int toCol) {
        return switch (piece.getType()) {
            case KING -> checkKingMove(piece, toRow, toCol);
            case ADVISOR -> checkAdvisorMove(piece, toRow, toCol);
            case ELEPHANT -> checkElephantMove(piece, toRow, toCol);
            case HORSE -> checkHorseMove(piece, toRow, toCol);
            case ROOK -> checkRookMove(piece, toRow, toCol);
            case CANNON -> checkCannonMove(piece, toRow, toCol);
            case PAWN -> checkPawnMove(piece, toRow, toCol);
        };
    }

    public ChessPiece move(int fromRow, int fromCol, int toRow, int toCol) {
        if (!isValidMove(fromRow, fromCol, toRow, toCol)) {
            return null;
        }
        ChessPiece movingPiece = board.removePiece(fromRow, fromCol);
        ChessPiece captured = board.removePiece(toRow, toCol);
        board.setPiece(toRow, toCol, movingPiece);
        history.add(new MoveRecord(fromRow, fromCol, toRow, toCol, movingPiece.copy(), captured == null ? null : captured.copy()));
        switchTurn();
        return captured;
    }

    public boolean undoMove() {
        if (history.isEmpty()) return false;
        MoveRecord last = history.remove(history.size() - 1);
        ChessPiece movedBack = board.removePiece(last.toRow, last.toCol);
        board.setPiece(last.fromRow, last.fromCol, movedBack);
        if (last.captured != null) {
            board.setPiece(last.toRow, last.toCol, last.captured);
        }
        switchTurn();
        return true;
    }

    public int getHistorySize() {
        return history.size();
    }

    public void switchTurn() {
        currentTurn = currentTurn.opponent();
    }

    public boolean isGameOver() {
        if (resignedBy != null) return true;
        return findWinner() != null || isStalemate();
    }

    public PlayerColor findWinner() {
        if (resignedBy != null) return resignedBy.opponent();
        ChessPiece redKing = board.findKing(PlayerColor.RED);
        ChessPiece blackKing = board.findKing(PlayerColor.BLACK);
        if (redKing == null) return PlayerColor.BLACK;
        if (blackKing == null) return PlayerColor.RED;
        if (isCheckmate()) return currentTurn.opponent();
        return null;
    }

    public boolean resign(PlayerColor by) {
        if (isGameOver()) return false;
        this.resignedBy = by;
        return true;
    }

    public boolean isCheckmate() {
        return isInCheck() && !hasAnyValidMove(currentTurn);
    }

    public boolean isStalemate() {
        return !isInCheck() && !hasAnyValidMove(currentTurn);
    }

    public boolean isInCheck() {
        return isInCheckFor(board, currentTurn);
    }

    public boolean isInCheckFor(PlayerColor color) {
        return isInCheckFor(board, color);
    }

    private boolean isInCheckFor(ChessBoard checkBoard, PlayerColor color) {
        ChessPiece king = checkBoard.findKing(color);
        if (king == null) return false;

        List<ChessPiece> opponentPieces = checkBoard.getPiecesByColor(color.opponent());
        for (ChessPiece piece : opponentPieces) {
            if (checkPieceMoveOnBoard(checkBoard, piece, king.getRow(), king.getCol())) {
                return true;
            }
        }

        ChessPiece otherKing = checkBoard.findKing(color.opponent());
        if (otherKing != null && king.getCol() == otherKing.getCol()) {
            int minRow = Math.min(king.getRow(), otherKing.getRow()) + 1;
            int maxRow = Math.max(king.getRow(), otherKing.getRow());
            boolean blocked = false;
            for (int r = minRow; r < maxRow; r++) {
                if (checkBoard.getPiece(r, king.getCol()) != null) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) return true;
        }

        return false;
    }

    private boolean hasAnyValidMove(PlayerColor color) {
        List<ChessPiece> pieces = board.getPiecesByColor(color);
        for (ChessPiece piece : pieces) {
            List<int[]> moves = getValidMoves(piece);
            if (!moves.isEmpty()) return true;
        }
        return false;
    }

    public List<int[]> getValidMoves(ChessPiece piece) {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < ChessBoard.ROWS; r++) {
            for (int c = 0; c < ChessBoard.COLS; c++) {
                if (isValidMove(piece.getRow(), piece.getCol(), r, c)) {
                    moves.add(new int[]{r, c});
                }
            }
        }
        return moves;
    }

    public boolean checkKingMove(ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        PlayerColor color = piece.getColor();

        int redMin = 7, redMax = 9, blackMin = 0, blackMax = 2;
        boolean inPalace = color == PlayerColor.RED
            ? toRow >= redMin && toRow <= redMax && toCol >= 3 && toCol <= 5
            : toRow >= blackMin && toRow <= blackMax && toCol >= 3 && toCol <= 5;
        if (!inPalace) return false;

        int dRow = Math.abs(toRow - row), dCol = Math.abs(toCol - col);
        return (dRow + dCol == 1);
    }

    public boolean checkAdvisorMove(ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        PlayerColor color = piece.getColor();

        int redMin = 7, redMax = 9, blackMin = 0, blackMax = 2;
        boolean inPalace = color == PlayerColor.RED
            ? toRow >= redMin && toRow <= redMax && toCol >= 3 && toCol <= 5
            : toRow >= blackMin && toRow <= blackMax && toCol >= 3 && toCol <= 5;
        if (!inPalace) return false;

        int dRow = Math.abs(toRow - row), dCol = Math.abs(toCol - col);
        return dRow == 1 && dCol == 1;
    }

    public boolean checkElephantMove(ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        PlayerColor color = piece.getColor();

        boolean crossRiver = color == PlayerColor.RED ? toRow < 5 : toRow > 4;
        if (crossRiver) return false;

        int dRow = toRow - row, dCol = toCol - col;
        if (Math.abs(dRow) != 2 || Math.abs(dCol) != 2) return false;

        int eyeRow = row + dRow / 2, eyeCol = col + dCol / 2;
        return board.getPiece(eyeRow, eyeCol) == null;
    }

    public boolean checkHorseMove(ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        int dRow = toRow - row, dCol = toCol - col;

        if (!((Math.abs(dRow) == 2 && Math.abs(dCol) == 1) || (Math.abs(dRow) == 1 && Math.abs(dCol) == 2))) {
            return false;
        }

        if (Math.abs(dRow) == 2) {
            int legRow = row + dRow / 2;
            if (board.getPiece(legRow, col) != null) return false;
        } else {
            int legCol = col + dCol / 2;
            if (board.getPiece(row, legCol) != null) return false;
        }

        return true;
    }

    public boolean checkRookMove(ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();

        if (row != toRow && col != toCol) return false;

        if (row == toRow) {
            int minC = Math.min(col, toCol) + 1;
            int maxC = Math.max(col, toCol);
            for (int c = minC; c < maxC; c++) {
                if (board.getPiece(row, c) != null) return false;
            }
        } else {
            int minR = Math.min(row, toRow) + 1;
            int maxR = Math.max(row, toRow);
            for (int r = minR; r < maxR; r++) {
                if (board.getPiece(r, col) != null) return false;
            }
        }
        return true;
    }

    public boolean checkCannonMove(ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        ChessPiece target = board.getPiece(toRow, toCol);

        if (row != toRow && col != toCol) return false;

        List<ChessPiece> path = new ArrayList<>();
        if (row == toRow) {
            int minC = Math.min(col, toCol) + 1;
            int maxC = Math.max(col, toCol);
            for (int c = minC; c < maxC; c++) {
                ChessPiece p = board.getPiece(row, c);
                if (p != null) path.add(p);
            }
        } else {
            int minR = Math.min(row, toRow) + 1;
            int maxR = Math.max(row, toRow);
            for (int r = minR; r < maxR; r++) {
                ChessPiece p = board.getPiece(r, col);
                if (p != null) path.add(p);
            }
        }

        if (target == null) {
            return path.isEmpty();
        } else {
            return path.size() == 1;
        }
    }

    public boolean checkPawnMove(ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        PlayerColor color = piece.getColor();
        int dRow = toRow - row;
        int dCol = toCol - col;

        if (dRow == 0 && dCol == 0) return false;

        if (color == PlayerColor.RED) {
            if (dRow > 0) return false;
            boolean crossed = row <= 4;
            if (!crossed) {
                return dRow == -1 && dCol == 0;
            } else {
                return (dRow == -1 && dCol == 0) || (dRow == 0 && Math.abs(dCol) == 1);
            }
        } else {
            if (dRow < 0) return false;
            boolean crossed = row >= 5;
            if (!crossed) {
                return dRow == 1 && dCol == 0;
            } else {
                return (dRow == 1 && dCol == 0) || (dRow == 0 && Math.abs(dCol) == 1);
            }
        }
    }

    private boolean checkPieceMoveOnBoard(ChessBoard checkBoard, ChessPiece piece, int toRow, int toCol) {
        return switch (piece.getType()) {
            case KING -> checkKingMoveOnBoard(checkBoard, piece, toRow, toCol);
            case ADVISOR -> checkAdvisorMoveOnBoard(checkBoard, piece, toRow, toCol);
            case ELEPHANT -> checkElephantMoveOnBoard(checkBoard, piece, toRow, toCol);
            case HORSE -> checkHorseMoveOnBoard(checkBoard, piece, toRow, toCol);
            case ROOK -> checkRookMoveOnBoard(checkBoard, piece, toRow, toCol);
            case CANNON -> checkCannonMoveOnBoard(checkBoard, piece, toRow, toCol);
            case PAWN -> checkPawnMoveOnBoard(checkBoard, piece, toRow, toCol);
        };
    }

    private boolean checkKingMoveOnBoard(ChessBoard checkBoard, ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        PlayerColor color = piece.getColor();
        int redMin = 7, redMax = 9, blackMin = 0, blackMax = 2;
        boolean inPalace = color == PlayerColor.RED
            ? toRow >= redMin && toRow <= redMax && toCol >= 3 && toCol <= 5
            : toRow >= blackMin && toRow <= blackMax && toCol >= 3 && toCol <= 5;
        if (!inPalace) return false;
        int dRow = Math.abs(toRow - row), dCol = Math.abs(toCol - col);
        return (dRow + dCol == 1);
    }

    private boolean checkAdvisorMoveOnBoard(ChessBoard checkBoard, ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        PlayerColor color = piece.getColor();
        int redMin = 7, redMax = 9, blackMin = 0, blackMax = 2;
        boolean inPalace = color == PlayerColor.RED
            ? toRow >= redMin && toRow <= redMax && toCol >= 3 && toCol <= 5
            : toRow >= blackMin && toRow <= blackMax && toCol >= 3 && toCol <= 5;
        if (!inPalace) return false;
        int dRow = Math.abs(toRow - row), dCol = Math.abs(toCol - col);
        return dRow == 1 && dCol == 1;
    }

    private boolean checkElephantMoveOnBoard(ChessBoard checkBoard, ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        PlayerColor color = piece.getColor();
        boolean crossRiver = color == PlayerColor.RED ? toRow < 5 : toRow > 4;
        if (crossRiver) return false;
        int dRow = toRow - row, dCol = toCol - col;
        if (Math.abs(dRow) != 2 || Math.abs(dCol) != 2) return false;
        int eyeRow = row + dRow / 2, eyeCol = col + dCol / 2;
        return checkBoard.getPiece(eyeRow, eyeCol) == null;
    }

    private boolean checkHorseMoveOnBoard(ChessBoard checkBoard, ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        int dRow = toRow - row, dCol = toCol - col;
        if (!((Math.abs(dRow) == 2 && Math.abs(dCol) == 1) || (Math.abs(dRow) == 1 && Math.abs(dCol) == 2))) {
            return false;
        }
        if (Math.abs(dRow) == 2) {
            int legRow = row + dRow / 2;
            if (checkBoard.getPiece(legRow, col) != null) return false;
        } else {
            int legCol = col + dCol / 2;
            if (checkBoard.getPiece(row, legCol) != null) return false;
        }
        return true;
    }

    private boolean checkRookMoveOnBoard(ChessBoard checkBoard, ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        if (row != toRow && col != toCol) return false;
        if (row == toRow) {
            int minC = Math.min(col, toCol) + 1, maxC = Math.max(col, toCol);
            for (int c = minC; c < maxC; c++) {
                if (checkBoard.getPiece(row, c) != null) return false;
            }
        } else {
            int minR = Math.min(row, toRow) + 1, maxR = Math.max(row, toRow);
            for (int r = minR; r < maxR; r++) {
                if (checkBoard.getPiece(r, col) != null) return false;
            }
        }
        return true;
    }

    private boolean checkCannonMoveOnBoard(ChessBoard checkBoard, ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        ChessPiece target = checkBoard.getPiece(toRow, toCol);
        if (row != toRow && col != toCol) return false;
        List<ChessPiece> path = new ArrayList<>();
        if (row == toRow) {
            int minC = Math.min(col, toCol) + 1, maxC = Math.max(col, toCol);
            for (int c = minC; c < maxC; c++) {
                ChessPiece p = checkBoard.getPiece(row, c);
                if (p != null) path.add(p);
            }
        } else {
            int minR = Math.min(row, toRow) + 1, maxR = Math.max(row, toRow);
            for (int r = minR; r < maxR; r++) {
                ChessPiece p = checkBoard.getPiece(r, col);
                if (p != null) path.add(p);
            }
        }
        if (target == null) return path.isEmpty();
        else return path.size() == 1;
    }

    private boolean checkPawnMoveOnBoard(ChessBoard checkBoard, ChessPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        PlayerColor color = piece.getColor();
        int dRow = toRow - row;
        int dCol = toCol - col;

        if (dRow == 0 && dCol == 0) return false;

        if (color == PlayerColor.RED) {
            if (dRow > 0) return false;
            boolean crossed = row <= 4;
            if (!crossed) return dRow == -1 && dCol == 0;
            else return (dRow == -1 && dCol == 0) || (dRow == 0 && Math.abs(dCol) == 1);
        } else {
            if (dRow < 0) return false;
            boolean crossed = row >= 5;
            if (!crossed) return dRow == 1 && dCol == 0;
            else return (dRow == 1 && dCol == 0) || (dRow == 0 && Math.abs(dCol) == 1);
        }
    }

    public void reset() {
        board.initDefaultPosition();
        currentTurn = PlayerColor.RED;
        history.clear();
        resignedBy = null;
    }

    public String getBoardString() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < ChessBoard.ROWS; r++) {
            for (int c = 0; c < ChessBoard.COLS; c++) {
                ChessPiece p = board.getPiece(r, c);
                if (p != null) {
                    sb.append(p.getName()).append(" ");
                } else {
                    sb.append(".. ");
                }
            }
            sb.append("\n");
        }
        sb.append("当前轮: ").append(currentTurn == PlayerColor.RED ? "红方" : "黑方");
        return sb.toString();
    }

    private static class MoveRecord {
        final int fromRow, fromCol, toRow, toCol;
        final ChessPiece moved;
        final ChessPiece captured;

        MoveRecord(int fromRow, int fromCol, int toRow, int toCol, ChessPiece moved, ChessPiece captured) {
            this.fromRow = fromRow; this.fromCol = fromCol;
            this.toRow = toRow; this.toCol = toCol;
            this.moved = moved; this.captured = captured;
        }
    }
}
