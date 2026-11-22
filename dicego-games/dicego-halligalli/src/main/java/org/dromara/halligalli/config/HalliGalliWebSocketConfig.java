package org.dromara.halligalli.config;

import lombok.RequiredArgsConstructor;
import org.dromara.halligalli.handler.HalliGalliWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 德国心脏病 WS 端点：/ws/halli-galli?roomId=..&playerName=..
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class HalliGalliWebSocketConfig implements WebSocketConfigurer {

    private final HalliGalliWebSocketHandler halliGalliWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(halliGalliWebSocketHandler, "/ws/halli-galli")
            .setAllowedOrigins("*");
    }
}
