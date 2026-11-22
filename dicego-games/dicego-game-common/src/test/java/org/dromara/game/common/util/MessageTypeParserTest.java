package org.dromara.game.common.util;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageTypeParserTest {

    private enum WireType {
        START_GAME,
        DISCARD_GEMS,
        RESIGN,
        NOBLE_SELECT
    }

    @Test
    void parsesCaseInsensitively() {
        assertEquals(Optional.of(WireType.START_GAME), MessageTypeParser.parse(WireType.class, "START_GAME"));
        assertEquals(Optional.of(WireType.START_GAME), MessageTypeParser.parse(WireType.class, "start_game"));
        assertEquals(Optional.of(WireType.NOBLE_SELECT), MessageTypeParser.parse(WireType.class, "nObLe_SeLeCt"));
    }

    @Test
    void returnsEmptyForUnknownNullOrBlank() {
        assertEquals(Optional.empty(), MessageTypeParser.parse(WireType.class, "UNKNOWN"));
        assertEquals(Optional.empty(), MessageTypeParser.parse(WireType.class, null));
        assertEquals(Optional.empty(), MessageTypeParser.parse(WireType.class, ""));
        assertEquals(Optional.empty(), MessageTypeParser.parse(WireType.class, "   "));
    }

    /**
     * 回归：土耳其语默认 Locale 下 "i".toUpperCase() 变成 "İ"，
     * 若 parser 不指定 Locale.ROOT，DISCARD_GEMS / RESIGN 会解析失败。
     */
    @Test
    void parsesNamesWithIUnderTurkishLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(
                Optional.of(WireType.DISCARD_GEMS),
                MessageTypeParser.parse(WireType.class, "discard_gems")
            );
            assertEquals(
                Optional.of(WireType.RESIGN),
                MessageTypeParser.parse(WireType.class, "resign")
            );
        } finally {
            Locale.setDefault(original);
        }
    }
}
