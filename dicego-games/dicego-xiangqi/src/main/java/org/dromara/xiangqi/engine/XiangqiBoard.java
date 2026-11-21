package org.dromara.xiangqi.engine;

import lombok.Getter;
import org.dromara.xiangqi.enums.PieceType;
import org.dromara.xiangqi.enums.PlayerColor;

import java.util.ArrayList;
import java.util.List;

@Getter
public class XiangqiBoard {

    public static final int ROWS = 10;
    public static final int COLS = 9;

    private final XiangqiPiece[][] board;

    public XiangqiBoard() {
        this.board = new XiangqiPiece[ROWS][COLS];
        initDefaultPosition();
    }

    public XiangqiPiece getPiece(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return null;
        }
        return board[row][col];
    }

    public void setPiece(int row, int col, XiangqiPiece piece) {
        if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
            board[row][col] = piece;
            if (piece != null) {
                piece.setRow(row);
                piece.setCol(col);
            }
        }
    }

    public XiangqiPiece removePiece(int row, int col) {
        XiangqiPiece piece = getPiece(row, col);
        if (piece != null) {
            board[row][col] = null;
        }
        return piece;
    }

    public void initDefaultPosition() {
        clear();

        PieceType[] backRowPieces = {
            null, PieceType.ROOK, PieceType.HORSE, PieceType.ELEPHANT,
            PieceType.ADVISOR, PieceType.KING, PieceType.ADVISOR,
            PieceType.ELEPHANT, PieceType.HORSE, PieceType.ROOK
        };

        for (int col = 0; col < COLS; col++) {
            PieceType type = backRowPieces[col + 1];
            setPiece(0, col, new XiangqiPiece(type, PlayerColor.BLACK, 0, col));
            setPiece(9, col, new XiangqiPiece(type, PlayerColor.RED, 9, col));
        }

        setPiece(2, 1, new XiangqiPiece(PieceType.CANNON, PlayerColor.BLACK, 2, 1));
        setPiece(2, 7, new XiangqiPiece(PieceType.CANNON, PlayerColor.BLACK, 2, 7));
        setPiece(7, 1, new XiangqiPiece(PieceType.CANNON, PlayerColor.RED, 7, 1));
        setPiece(7, 7, new XiangqiPiece(PieceType.CANNON, PlayerColor.RED, 7, 7));

        for (int col = 0; col < COLS; col += 2) {
            setPiece(3, col, new XiangqiPiece(PieceType.PAWN, PlayerColor.BLACK, 3, col));
            setPiece(6, col, new XiangqiPiece(PieceType.PAWN, PlayerColor.RED, 6, col));
        }
    }

    private void clear() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                board[r][c] = null;
            }
        }
    }

    public XiangqiBoard copy() {
        XiangqiBoard newBoard = new XiangqiBoard();
        newBoard.clear();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                XiangqiPiece piece = board[r][c];
                if (piece != null) {
                    newBoard.board[r][c] = piece.copy();
                }
            }
        }
        return newBoard;
    }

    public XiangqiPiece findKing(PlayerColor color) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                XiangqiPiece piece = board[r][c];
                if (piece != null && piece.getType() == PieceType.KING && piece.getColor() == color) {
                    return piece;
                }
            }
        }
        return null;
    }

    public List<XiangqiPiece> getPiecesByColor(PlayerColor color) {
        List<XiangqiPiece> pieces = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                XiangqiPiece piece = board[r][c];
                if (piece != null && piece.getColor() == color) {
                    pieces.add(piece);
                }
            }
        }
        return pieces;
    }
}
