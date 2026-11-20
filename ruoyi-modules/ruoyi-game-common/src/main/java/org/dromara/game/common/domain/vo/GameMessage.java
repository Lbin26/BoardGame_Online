package org.dromara.game.common.domain.vo;

import org.dromara.game.common.enums.GameMessageType;

public record GameMessage<T>(
    GameMessageType type,
    String roomId,
    String playerName,
    T payload,
    long timestamp
) {
    public static <T> GameMessage<T> of(GameMessageType type, String roomId, String playerName, T payload) {
        return new GameMessage<>(type, roomId, playerName, payload, System.currentTimeMillis());
    }
}
