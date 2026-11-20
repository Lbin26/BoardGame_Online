package org.dromara.xiangqi.domain.vo;

import org.dromara.xiangqi.enums.PieceType;
import org.dromara.game.common.enums.PlayerColor;

public record XiangqiPieceVO(
    PieceType type,
    PlayerColor color,
    int row,
    int col,
    String name
) {}
