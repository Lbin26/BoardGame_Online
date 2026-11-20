package org.dromara.boardgame.config;

import org.dromara.boardgame.manager.ChessRoom;
import org.dromara.game.common.room.GameRoomManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChessRoomConfig {

    @Bean
    public GameRoomManager<ChessRoom> chessRoomManager() {
        return new GameRoomManager<>(ChessRoom::new);
    }
}
