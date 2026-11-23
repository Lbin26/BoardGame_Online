package org.dromara.incangold.config;

import lombok.RequiredArgsConstructor;
import org.dromara.incangold.handler.IncanGoldWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 印加宝藏 WS 端点：/ws/incan-gold?roomId=..&playerName=.. */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class IncanGoldWebSocketConfig implements WebSocketConfigurer {

    private final IncanGoldWebSocketHandler incanGoldWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(incanGoldWebSocketHandler, "/ws/incan-gold")
            .setAllowedOrigins("*");
    }
}
