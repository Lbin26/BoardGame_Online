package org.dromara.dicego.config;

import lombok.RequiredArgsConstructor;
import org.dromara.dicego.handler.SplendorWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Splendor WS 端点注册（协议 §0：/ws/splendor?roomId=..&playerName=..）。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class SplendorWebSocketConfig implements WebSocketConfigurer {

    private final SplendorWebSocketHandler splendorWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(splendorWebSocketHandler, "/ws/splendor")
            .setAllowedOrigins("*");
    }
}
