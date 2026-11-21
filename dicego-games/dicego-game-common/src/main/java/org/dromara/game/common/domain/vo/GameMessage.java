package org.dromara.game.common.domain.vo;

import org.dromara.game.common.enums.GameMessageType;

/**
 * 通用消息信封。type 为字符串以兼容各游戏自建消息枚举；
 * 序列化值为枚举 name()，与历史 wire 格式完全一致。
 */
public record GameMessage<T>(
    String type,
    String roomId,
    String playerName,
    T payload,
    long timestamp
) {
    public static <T> GameMessage<T> of(GameMessageType type, String roomId, String playerName, T payload) {
        return of(type.name(), roomId, playerName, payload);
    }

    /** 各游戏传自己的消息枚举（如 XiangqiMessageType），取其 name() 作为 wire 值 */
    public static <T> GameMessage<T> of(Enum<?> type, String roomId, String playerName, T payload) {
        return of(type.name(), roomId, playerName, payload);
    }

    public static <T> GameMessage<T> of(String type, String roomId, String playerName, T payload) {
        return new GameMessage<>(type, roomId, playerName, payload, System.currentTimeMillis());
    }
}
