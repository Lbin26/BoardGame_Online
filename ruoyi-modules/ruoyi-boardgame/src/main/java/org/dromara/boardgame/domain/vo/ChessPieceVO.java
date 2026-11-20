package org.dromara.boardgame.domain.vo;

import org.dromara.boardgame.enums.PieceType;
import org.dromara.game.common.enums.PlayerColor;

public record ChessPieceVO(
    PieceType type,
    PlayerColor color,
    int row,
    int col,
    String name
) {}
