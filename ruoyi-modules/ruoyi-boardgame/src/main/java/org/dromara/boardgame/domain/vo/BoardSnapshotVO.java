package org.dromara.boardgame.domain.vo;

import org.dromara.boardgame.enums.PlayerColor;

import java.util.List;

public record BoardSnapshotVO(
    List<ChessPieceVO> pieces,
    PlayerColor currentTurn,
    boolean gameOver,
    PlayerColor winner
) {}
