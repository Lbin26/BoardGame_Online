package org.dromara.halligalli.config;

import org.dromara.game.common.room.GameRoomManager;
import org.dromara.halligalli.manager.HalliGalliRoom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 德国心脏病房间管理器。默认工厂仅占位：建房必须走
 * {@code createRoom(playerName, factory)} 携带容量（2-6）与 D4 开关，
 * 缺省工厂被调用说明调用方漏传参数，直接失败暴露问题。
 */
@Configuration
public class HalliGalliRoomConfig {

    @Bean
    public GameRoomManager<HalliGalliRoom> halliGalliRoomManager() {
        return new GameRoomManager<>(id -> {
            throw new IllegalStateException("德国心脏病建房必须携带容量参数（见 HalliGalliController#createRoom）");
        });
    }
}
