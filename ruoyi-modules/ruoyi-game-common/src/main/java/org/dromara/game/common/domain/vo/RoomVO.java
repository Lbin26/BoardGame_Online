package org.dromara.game.common.domain.vo;

import org.dromara.game.common.enums.PlayerColor;
import org.dromara.game.common.enums.RoomStatus;

public record RoomVO(
    String roomId,
    String redPlayerName,
    String blackPlayerName,
    RoomStatus status,
    PlayerColor viewerColor
) {}
