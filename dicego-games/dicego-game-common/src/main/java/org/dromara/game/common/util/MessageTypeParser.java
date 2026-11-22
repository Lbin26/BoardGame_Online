package org.dromara.game.common.util;

import java.util.Locale;
import java.util.Optional;

/**
 * 协议 wire 字符串 → 枚举的安全解析。
 *
 * <p>各游戏消息枚举以 {@code name()} 作为 wire 值，前端可能传小写形式（如 {@code take_gems}）。
 * 统一用 {@link Locale#ROOT} 做大小写归一：默认 Locale 下 {@code toUpperCase()} 在土耳其语环境
 * 会把小写 {@code i} 变成带点大写 {@code İ}，导致含 i 的消息名（DISCARD_GEMS、RESIGN）
 * 解析失败。用 ROOT 可保证在任何系统语言下行为一致。
 */
public final class MessageTypeParser {

    private MessageTypeParser() {
    }

    /**
     * 将 wire 字符串解析为枚举常量；无法识别（含 null/空白）时返回 empty。
     *
     * @param enumType 目标枚举类型
     * @param wire     协议中的类型字符串，大小写不敏感
     * @param <E>      枚举类型
     * @return 解析结果，未知类型为空
     */
    public static <E extends Enum<E>> Optional<E> parse(Class<E> enumType, String wire) {
        if (wire == null || wire.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(enumType, wire.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
