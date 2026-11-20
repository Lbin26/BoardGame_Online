package org.dromara.xiangqi.engine;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dromara.xiangqi.enums.PieceType;
import org.dromara.game.common.enums.PlayerColor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class XiangqiPiece {

    private PieceType type;
    private PlayerColor color;
    private int row;
    private int col;

    public XiangqiPiece(PieceType type, PlayerColor color) {
        this.type = type;
        this.color = color;
    }

    public String getName() {
        return type.getName(color == PlayerColor.RED);
    }

    public XiangqiPiece copy() {
        return new XiangqiPiece(type, color, row, col);
    }
}
