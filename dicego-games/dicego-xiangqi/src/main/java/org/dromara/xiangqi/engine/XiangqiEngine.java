package org.dromara.xiangqi.engine;

import lombok.Getter;
import org.dromara.xiangqi.enums.PieceType;
import org.dromara.xiangqi.enums.PlayerColor;

import java.util.ArrayList;
import java.util.List;

public class XiangqiEngine {

    private final XiangqiBoard board;
    @Getter
    private PlayerColor currentTurn;

    private final List<MoveRecord> history = new ArrayList<>();
    private PlayerColor resignedBy = null;

    public XiangqiEngine() {
        this.board = new XiangqiBoard();
        this.currentTurn = PlayerColor.RED;
    }

    public XiangqiBoard getBoard() {
        return board;
    }

    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        XiangqiPiece piece = board.getPiece(fromRow, fromCol);
        if (piece == null) return false;
        if (piece.getColor() != currentTurn) return false;
        if (fromRow == toRow && fromCol == toCol) return false;

        XiangqiPiece target = board.getPiece(toRow, toCol);
        if (target != null && target.getColor() == piece.getColor()) return false;

        if (!checkPieceMove(piece, toRow, toCol)) return false;

        XiangqiBoard tempBoard = board.copy();
        tempBoard.removePiece(fromRow, fromCol);
        tempBoard.setPiece(toRow, toCol, piece.copy());

        if (isInCheckFor(tempBoard, currentTurn)) return false;

        return true;
    }

    public boolean checkPieceMove(XiangqiPiece piece, int toRow, int toCol) {
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

    public XiangqiPiece move(int fromRow, int fromCol, int toRow, int toCol) {
        if (!isValidMove(fromRow, fromCol, toRow, toCol)) {
            return null;
        }
        XiangqiPiece movingPiece = board.removePiece(fromRow, fromCol);
        XiangqiPiece captured = board.removePiece(toRow, toCol);
        board.setPiece(toRow, toCol, movingPiece);
        history.add(new MoveRecord(fromRow, fromCol, toRow, toCol, movingPiece.copy(), captured == null ? null : captured.copy()));
        switchTurn();
        return captured;
    }

    public boolean undoMove() {
        if (history.isEmpty()) return false;
        MoveRecord last = history.remove(history.size() - 1);
        XiangqiPiece movedBack = board.removePiece(last.toRow, last.toCol);
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
        XiangqiPiece redKing = board.findKing(PlayerColor.RED);
        XiangqiPiece blackKing = board.findKing(PlayerColor.BLACK);
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

    private boolean isInCheckFor(XiangqiBoard checkBoard, PlayerColor color) {
        XiangqiPiece king = checkBoard.findKing(color);
        if (king == null) return false;

        List<XiangqiPiece> opponentPieces = checkBoard.getPiecesByColor(color.opponent());
        for (XiangqiPiece piece : opponentPieces) {
            if (checkPieceMoveOnBoard(checkBoard, piece, king.getRow(), king.getCol())) {
                return true;
            }
        }

        XiangqiPiece otherKing = checkBoard.findKing(color.opponent());
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
        List<XiangqiPiece> pieces = board.getPiecesByColor(color);
        for (XiangqiPiece piece : pieces) {
            List<int[]> moves = getValidMoves(piece);
            if (!moves.isEmpty()) return true;
        }
        return false;
    }

    public List<int[]> getValidMoves(XiangqiPiece piece) {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                if (isValidMove(piece.getRow(), piece.getCol(), r, c)) {
                    moves.add(new int[]{r, c});
                }
            }
        }
        return moves;
    }

    public boolean checkKingMove(XiangqiPiece piece, int toRow, int toCol) {
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

    public boolean checkAdvisorMove(XiangqiPiece piece, int toRow, int toCol) {
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

    public boolean checkElephantMove(XiangqiPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        PlayerColor color = piece.getColor();

        boolean crossRiver = color == PlayerColor.RED ? toRow < 5 : toRow > 4;
        if (crossRiver) return false;

        int dRow = toRow - row, dCol = toCol - col;
        if (Math.abs(dRow) != 2 || Math.abs(dCol) != 2) return false;

        int eyeRow = row + dRow / 2, eyeCol = col + dCol / 2;
        return board.getPiece(eyeRow, eyeCol) == null;
    }

    public boolean checkHorseMove(XiangqiPiece piece, int toRow, int toCol) {
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

    public boolean checkRookMove(XiangqiPiece piece, int toRow, int toCol) {
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

    public boolean checkCannonMove(XiangqiPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        XiangqiPiece target = board.getPiece(toRow, toCol);

        if (row != toRow && col != toCol) return false;

        List<XiangqiPiece> path = new ArrayList<>();
        if (row == toRow) {
            int minC = Math.min(col, toCol) + 1;
            int maxC = Math.max(col, toCol);
            for (int c = minC; c < maxC; c++) {
                XiangqiPiece p = board.getPiece(row, c);
                if (p != null) path.add(p);
            }
        } else {
            int minR = Math.min(row, toRow) + 1;
            int maxR = Math.max(row, toRow);
            for (int r = minR; r < maxR; r++) {
                XiangqiPiece p = board.getPiece(r, col);
                if (p != null) path.add(p);
            }
        }

        if (target == null) {
            return path.isEmpty();
        } else {
            return path.size() == 1;
        }
    }

    public boolean checkPawnMove(XiangqiPiece piece, int toRow, int toCol) {
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

    private boolean checkPieceMoveOnBoard(XiangqiBoard checkBoard, XiangqiPiece piece, int toRow, int toCol) {
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

    private boolean checkKingMoveOnBoard(XiangqiBoard checkBoard, XiangqiPiece piece, int toRow, int toCol) {
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

    private boolean checkAdvisorMoveOnBoard(XiangqiBoard checkBoard, XiangqiPiece piece, int toRow, int toCol) {
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

    private boolean checkElephantMoveOnBoard(XiangqiBoard checkBoard, XiangqiPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        PlayerColor color = piece.getColor();
        boolean crossRiver = color == PlayerColor.RED ? toRow < 5 : toRow > 4;
        if (crossRiver) return false;
        int dRow = toRow - row, dCol = toCol - col;
        if (Math.abs(dRow) != 2 || Math.abs(dCol) != 2) return false;
        int eyeRow = row + dRow / 2, eyeCol = col + dCol / 2;
        return checkBoard.getPiece(eyeRow, eyeCol) == null;
    }

    private boolean checkHorseMoveOnBoard(XiangqiBoard checkBoard, XiangqiPiece piece, int toRow, int toCol) {
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

    private boolean checkRookMoveOnBoard(XiangqiBoard checkBoard, XiangqiPiece piece, int toRow, int toCol) {
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

    private boolean checkCannonMoveOnBoard(XiangqiBoard checkBoard, XiangqiPiece piece, int toRow, int toCol) {
        int row = piece.getRow(), col = piece.getCol();
        XiangqiPiece target = checkBoard.getPiece(toRow, toCol);
        if (row != toRow && col != toCol) return false;
        List<XiangqiPiece> path = new ArrayList<>();
        if (row == toRow) {
            int minC = Math.min(col, toCol) + 1, maxC = Math.max(col, toCol);
            for (int c = minC; c < maxC; c++) {
                XiangqiPiece p = checkBoard.getPiece(row, c);
                if (p != null) path.add(p);
            }
        } else {
            int minR = Math.min(row, toRow) + 1, maxR = Math.max(row, toRow);
            for (int r = minR; r < maxR; r++) {
                XiangqiPiece p = checkBoard.getPiece(r, col);
                if (p != null) path.add(p);
            }
        }
        if (target == null) return path.isEmpty();
        else return path.size() == 1;
    }

    private boolean checkPawnMoveOnBoard(XiangqiBoard checkBoard, XiangqiPiece piece, int toRow, int toCol) {
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
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                XiangqiPiece p = board.getPiece(r, c);
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
        final XiangqiPiece moved;
        final XiangqiPiece captured;

        MoveRecord(int fromRow, int fromCol, int toRow, int toCol, XiangqiPiece moved, XiangqiPiece captured) {
            this.fromRow = fromRow; this.fromCol = fromCol;
            this.toRow = toRow; this.toCol = toCol;
            this.moved = moved; this.captured = captured;
        }
    }
}
