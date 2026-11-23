package org.dromara.incangold.config;

import org.dromara.game.common.room.GameRoomManager;
import org.dromara.incangold.manager.IncanGoldRoom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 印加宝藏房间管理器。默认工厂仅占位：建房必须走
 * {@code createRoom(playerName, factory)} 携带容量（3-8），
 * 缺省工厂被调用说明调用方漏传参数，直接失败暴露问题。
 */
@Configuration
public class IncanGoldRoomConfig {

    @Bean
    public GameRoomManager<IncanGoldRoom> incanGoldRoomManager() {
        return new GameRoomManager<>(id -> {
            throw new IllegalStateException("印加宝藏建房必须携带容量参数（见 IncanGoldController#createRoom）");
        });
    }
}
