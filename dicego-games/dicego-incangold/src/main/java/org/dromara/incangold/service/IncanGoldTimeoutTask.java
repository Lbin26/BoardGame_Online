package org.dromara.incangold.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.enums.RoomStatus;
import org.dromara.game.common.room.GameRoomManager;
import org.dromara.incangold.handler.IncanGoldWebSocketHandler;
import org.dromara.incangold.manager.IncanGoldRoom;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 推进任务：每 500ms 扫对局中的房间，决策超时按「继续」处理，结算展台超时进入下一轮。
 * 与公用房管同款自建守护调度器，不依赖全局 @EnableScheduling。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncanGoldTimeoutTask {

    private static final long TICK_MS = 500;

    private final GameRoomManager<IncanGoldRoom> roomManager;
    private final IncanGoldWebSocketHandler handler;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "incan-gold-timeout");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        log.info("印加宝藏推进任务启动（{}ms）", TICK_MS);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    /** 包级可见，便于单测直接驱动 */
    void tick() {
        long now = System.currentTimeMillis();
        for (String roomId : roomManager.listRoomIds()) {
            IncanGoldRoom room = roomManager.getRoom(roomId);
            if (room == null || room.getStatus() != RoomStatus.PLAYING) continue;
            try {
                long before = room.eventSeq();
                if (room.autoAdvance(now) == org.dromara.incangold.engine.IncanGoldEngine.AutoResult.NONE) {
                    continue;
                }
                handler.broadcastStateIfChanged(roomId, room, before);
                handler.broadcastGameOverIfFinished(roomId, room);
            } catch (Exception e) {
                log.warn("房间 {} 自动推进失败: {}", roomId, e.getMessage());
            }
        }
    }
}
