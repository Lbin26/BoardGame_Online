package org.dromara.boardgame.engine;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dromara.boardgame.enums.PieceType;
import org.dromara.game.common.enums.PlayerColor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChessPiece {

    private PieceType type;
    private PlayerColor color;
    private int row;
    private int col;

    public ChessPiece(PieceType type, PlayerColor color) {
        this.type = type;
        this.color = color;
    }

    public String getName() {
        return type.getName(color == PlayerColor.RED);
    }

    public ChessPiece copy() {
        return new ChessPiece(type, color, row, col);
    }
}
