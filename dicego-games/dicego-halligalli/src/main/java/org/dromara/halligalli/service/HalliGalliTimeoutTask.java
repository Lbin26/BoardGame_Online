package org.dromara.halligalli.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.enums.RoomStatus;
import org.dromara.game.common.room.GameRoomManager;
import org.dromara.halligalli.handler.HalliGalliWebSocketHandler;
import org.dromara.halligalli.manager.HalliGalliRoom;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 翻牌超时任务（D5）：每 500ms 扫一遍对局中的房间，当前翻牌方超过 10s 未翻则服务端代翻。
 * 与 GameRoomManager 的清理线程同款自建调度器，不依赖全局 @EnableScheduling。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HalliGalliTimeoutTask {

    private static final long TICK_MS = 500;

    private final GameRoomManager<HalliGalliRoom> roomManager;
    private final HalliGalliWebSocketHandler handler;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "halli-galli-timeout");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        log.info("德国心脏病翻牌超时任务启动（{}ms）", TICK_MS);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    /** 包级可见便于单测直接驱动 */
    void tick() {
        long now = System.currentTimeMillis();
        for (String roomId : roomManager.listRoomIds()) {
            HalliGalliRoom room = roomManager.getRoom(roomId);
            if (room == null || room.getStatus() != RoomStatus.PLAYING) continue;
            try {
                room.autoFlip(now).ifPresent(outcome -> handler.broadcastFlip(roomId, room, outcome, true));
            } catch (Exception e) {
                log.warn("房间 {} 自动翻牌失败: {}", roomId, e.getMessage());
            }
        }
    }
}
