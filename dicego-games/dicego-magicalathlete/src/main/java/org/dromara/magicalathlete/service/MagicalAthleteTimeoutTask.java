package org.dromara.magicalathlete.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.enums.RoomStatus;
import org.dromara.game.common.room.GameRoomManager;
import org.dromara.magicalathlete.handler.MagicalAthleteWebSocketHandler;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 阶段超时任务（D6）：每 500ms 扫一遍 PLAYING 房间——
 * DRAFT 30s 代选最左、MUSTER 20s 代选手牌第一张、RACE 20s 代掷骰（能力全放弃）。
 * 与 halli 同款自建调度器，不依赖全局 @EnableScheduling。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MagicalAthleteTimeoutTask {

    private static final long TICK_MS = 500;

    private final GameRoomManager<MagicalAthleteRoom> roomManager;
    private final MagicalAthleteWebSocketHandler handler;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "magical-athlete-timeout");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        log.info("胡闹运动会阶段超时任务启动（{}ms）", TICK_MS);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    /** 包级可见便于单测直接驱动 */
    void tick() {
        long now = System.currentTimeMillis();
        for (String roomId : roomManager.listRoomIds()) {
            MagicalAthleteRoom room = roomManager.getRoom(roomId);
            if (room == null || room.getStatus() != RoomStatus.PLAYING) continue;
            try {
                switch (room.getPhase()) {
                    case DRAFT -> {
                        Optional<MagicalAthleteRoom.AutoPick> auto = room.autoPick(now);
                        auto.ifPresent(pick -> handler.broadcastPick(roomId, room, pick.seatNo(),
                            pick.playerName(), pick.racer().key(), true));
                    }
                    case MUSTER -> room.autoReady(now).forEach(ready ->
                        handler.broadcastMusterReady(roomId, room, ready.seatNo(), ready.playerName(), true));
                    case RACE -> {
                        MagicalAthleteRoom.RollOutcome roll = room.autoRoll(now);
                        if (roll.ok()) handler.broadcastRoll(roomId, room, roll, true);
                    }
                    default -> { /* WAITING / RACE_END / FINISHED 无需托管 */ }
                }
            } catch (Exception e) {
                log.warn("房间 {} 超时托管失败: {}", roomId, e.getMessage());
            }
        }
    }
}
