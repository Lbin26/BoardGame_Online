package org.dromara.boardgame.config;

import lombok.RequiredArgsConstructor;
import org.dromara.boardgame.handler.ChessWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ChessWebSocketConfig implements WebSocketConfigurer {

    private final ChessWebSocketHandler chessWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chessWebSocketHandler, "/ws/chess")
            .setAllowedOrigins("*");
    }
}
