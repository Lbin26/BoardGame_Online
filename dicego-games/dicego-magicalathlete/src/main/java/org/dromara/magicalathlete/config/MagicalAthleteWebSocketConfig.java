package org.dromara.magicalathlete.config;

import lombok.RequiredArgsConstructor;
import org.dromara.magicalathlete.handler.MagicalAthleteWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 胡闹运动会 WS 端点：/ws/magical-athlete?roomId=..&playerName=..
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class MagicalAthleteWebSocketConfig implements WebSocketConfigurer {

    private final MagicalAthleteWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/magical-athlete").setAllowedOrigins("*");
    }
}
