package org.dromara.game.common.ws;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 大厅心跳：按玩家名去重、续约后保留、过期自动剔除 */
class LobbyPresenceServiceTest {

    private static Clock clockAt(long epochMs) {
        return Clock.fixed(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC);
    }

    @Test
    void touchesAreDeduplicatedByPlayerName() {
        LobbyPresenceService service = new LobbyPresenceService(clockAt(0));
        service.touch("alice");
        service.touch("alice");
        service.touch(" bob ");
        service.touch(null);
        service.touch("  ");

        assertEquals(2, service.activeNames().size());
        assertTrue(service.activeNames().contains("alice"));
        assertTrue(service.activeNames().contains("bob"));
    }

    @Test
    void heartbeatRefreshesTtl() {
        MutableClock clock = new MutableClock(0);
        LobbyPresenceService service = new LobbyPresenceService(clock);
        service.touch("alice");

        clock.ms = LobbyPresenceService.TTL_MS - 1;
        assertEquals(1, service.activeNames().size(), "未过期应保留");

        service.touch("alice");
        clock.ms = LobbyPresenceService.TTL_MS + 1;
        assertEquals(1, service.activeNames().size(), "刚续约过，旧 TTL 之后仍在线");

        clock.ms = 2 * LobbyPresenceService.TTL_MS + 2;
        assertEquals(0, service.activeNames().size(), "再次超过 TTL 视为离开");
    }

    @Test
    void explicitLeaveRemovesPlayerImmediately() {
        MutableClock clock = new MutableClock(0);
        LobbyPresenceService service = new LobbyPresenceService(clock);
        service.touch("alice");
        service.touch("bob");

        service.leave(" alice ");

        assertEquals(1, service.activeNames().size());
        assertTrue(service.activeNames().contains("bob"));
    }

    /** 可控时钟：直接改字段即可推进时间 */
    private static final class MutableClock extends Clock {
        private long ms;

        private MutableClock(long ms) {
            this.ms = ms;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(ms);
        }

        @Override
        public long millis() {
            return ms;
        }
    }
}
