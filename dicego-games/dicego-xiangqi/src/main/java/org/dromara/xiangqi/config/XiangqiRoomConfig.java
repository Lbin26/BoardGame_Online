package org.dromara.xiangqi.config;

import org.dromara.xiangqi.manager.XiangqiRoom;
import org.dromara.game.common.room.GameRoomManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XiangqiRoomConfig {

    @Bean
    public GameRoomManager<XiangqiRoom> xiangqiRoomManager() {
        return new GameRoomManager<>(XiangqiRoom::new);
    }
}
