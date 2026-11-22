package org.dromara.game.common.controller;

import org.dromara.game.common.ws.AbstractGameWebSocketHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 平台在线人数：跨游戏处理器汇总后按玩家名去重 */
class OnlineControllerTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AbstractGameWebSocketHandler<?> handlerOf(Set<String> names) {
        AbstractGameWebSocketHandler h = mock(AbstractGameWebSocketHandler.class);
        when(h.connectedPlayerNames()).thenReturn(names);
        return h;
    }

    @Test
    void countsDistinctPlayersAcrossAllHandlers() {
        List<AbstractGameWebSocketHandler<?>> handlers = List.of(
            handlerOf(Set.of("alice", "bob")),
            handlerOf(Set.of("bob", "carol")),
            handlerOf(Set.of())
        );
        Map<String, Object> body = new OnlineController(handlers).count();

        assertEquals(true, body.get("success"));
        assertEquals(3, body.get("onlineCount"), "alice/bob/carol，跨处理器去重后共 3 人");
    }

    @Test
    void emptyHandlersReturnsZero() {
        List<AbstractGameWebSocketHandler<?>> handlers = List.of();
        assertEquals(0, new OnlineController(handlers).count().get("onlineCount"));
    }
}
