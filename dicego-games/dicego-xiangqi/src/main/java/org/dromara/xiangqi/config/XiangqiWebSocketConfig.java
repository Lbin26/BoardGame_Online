package org.dromara.xiangqi.config;

import lombok.RequiredArgsConstructor;
import org.dromara.xiangqi.handler.XiangqiWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class XiangqiWebSocketConfig implements WebSocketConfigurer {

    private final XiangqiWebSocketHandler chessWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chessWebSocketHandler, "/ws/chess")
            .setAllowedOrigins("*");
    }
}
