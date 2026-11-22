package org.dromara.magicalathlete.config;

import org.dromara.game.common.room.GameRoomManager;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 胡闹运动会房间管理器。默认工厂仅占位：建房必须走
 * {@code createRoom(playerName, factory)} 携带容量（2-6）。
 */
@Configuration
public class MagicalAthleteRoomConfig {

    @Bean
    public GameRoomManager<MagicalAthleteRoom> magicalAthleteRoomManager() {
        return new GameRoomManager<>(id -> {
            throw new IllegalStateException("胡闹运动会建房必须携带容量参数（见 MagicalAthleteController#createRoom）");
        });
    }
}
