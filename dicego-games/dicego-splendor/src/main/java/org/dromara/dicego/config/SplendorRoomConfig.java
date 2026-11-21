package org.dromara.dicego.config;

import org.dromara.dicego.manager.SplendorRoom;
import org.dromara.dicego.service.SplendorCardCache;
import org.dromara.game.common.room.GameRoomManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Splendor 房间管理器。默认工厂仅占位：建房必须走 createRoom(playerName, factory)
 * 重载携带每房容量（D3），缺省工厂被调用即说明调用方漏传参数，直接失败暴露问题。
 */
@Configuration
public class SplendorRoomConfig {

    @Bean
    public GameRoomManager<SplendorRoom> splendorRoomManager(SplendorCardCache cardCache) {
        return new GameRoomManager<>(id -> {
            throw new IllegalStateException("Splendor 建房必须携带容量与房主参数（见 SplendorController#createRoom）");
        });
    }
}
