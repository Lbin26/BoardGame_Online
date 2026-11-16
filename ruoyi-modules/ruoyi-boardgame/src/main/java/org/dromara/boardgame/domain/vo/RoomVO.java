package org.dromara.boardgame.domain.vo;

import org.dromara.boardgame.enums.PlayerColor;
import org.dromara.boardgame.enums.RoomStatus;

public record RoomVO(
    String roomId,
    String redPlayerName,
    String blackPlayerName,
    RoomStatus status,
    PlayerColor viewerColor
) {}
