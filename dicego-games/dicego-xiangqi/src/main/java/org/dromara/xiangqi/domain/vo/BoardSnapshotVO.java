package org.dromara.xiangqi.domain.vo;

import org.dromara.xiangqi.enums.PlayerColor;

import java.util.List;

public record BoardSnapshotVO(
    List<XiangqiPieceVO> pieces,
    PlayerColor currentTurn,
    boolean gameOver,
    PlayerColor winner
) {}
