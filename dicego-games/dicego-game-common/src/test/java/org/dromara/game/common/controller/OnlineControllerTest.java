package org.dromara.game.common.controller;

import org.dromara.game.common.ws.AbstractGameWebSocketHandler;
import org.dromara.game.common.ws.LobbyPresenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 平台在线人数：跨游戏处理器汇总后按玩家名去重 */
class OnlineControllerTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AbstractGameWebSocketHandler handlerOf(Set<String> names) {
        AbstractGameWebSocketHandler h = mock(AbstractGameWebSocketHandler.class);
        when(h.connectedPlayerNames()).thenReturn(names);
        return h;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AbstractGameWebSocketHandler> providerOf(AbstractGameWebSocketHandler... handlers) {
        ObjectProvider<AbstractGameWebSocketHandler> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(Stream.of(handlers));
        return provider;
    }

    private static LobbyPresenceService presenceOf(Set<String> names) {
        LobbyPresenceService presence = mock(LobbyPresenceService.class);
        when(presence.activeNames()).thenReturn(names);
        return presence;
    }

    @Test
    void countsDistinctPlayersAcrossAllHandlers() {
        ObjectProvider<AbstractGameWebSocketHandler> provider = providerOf(
            handlerOf(Set.of("alice", "bob")),
            handlerOf(Set.of("bob", "carol")),
            handlerOf(Set.of())
        );
        Map<String, Object> body = new OnlineController(provider, presenceOf(Set.of())).count();

        assertEquals(true, body.get("success"));
        assertEquals(3, body.get("onlineCount"), "alice/bob/carol，跨处理器去重后共 3 人");
    }

    @Test
    void emptyHandlersReturnsZero() {
        ObjectProvider<AbstractGameWebSocketHandler> provider = providerOf();
        assertEquals(0, new OnlineController(provider, presenceOf(Set.of())).count().get("onlineCount"));
    }

    @Test
    void samePlayerAcrossGamesCountsOnce() {
        ObjectProvider<AbstractGameWebSocketHandler> provider = providerOf(
            handlerOf(Set.of("alice")),
            handlerOf(Set.of("alice"))
        );
        assertEquals(1, new OnlineController(provider, presenceOf(Set.of())).count().get("onlineCount"),
            "同一玩家跨游戏多开只算一人");
    }

    @Test
    void lobbyPlayersAreIncludedAndDeduplicatedWithRoomPlayers() {
        ObjectProvider<AbstractGameWebSocketHandler> provider = providerOf(
            handlerOf(Set.of("alice", "inRoom"))
        );
        Map<String, Object> body = new OnlineController(provider,
            presenceOf(Set.of("alice", "inLobby", "inLobby2"))).count();

        assertEquals(4, body.get("onlineCount"),
            "alice（大厅+房间只算一次）+ inRoom + inLobby + inLobby2");
    }

    @Test
    void overlapsBetweenLobbyAndRoomCountOnce() {
        ObjectProvider<AbstractGameWebSocketHandler> provider = providerOf(handlerOf(Set.of("alice", "bob")));
        assertEquals(3, new OnlineController(provider, presenceOf(Set.of("alice", "carol")))
            .count().get("onlineCount"), "alice 重叠只算一次，bob/carol 各一次");
    }

    @Test
    void leaveClearsLobbyHeartbeatImmediately() {
        LobbyPresenceService presence = mock(LobbyPresenceService.class);
        OnlineController controller = new OnlineController(providerOf(), presence);

        assertEquals(true, controller.leave(new OnlineController.PingBody(" alice ")).get("success"));
        verify(presence).leave(" alice ");
    }
}
