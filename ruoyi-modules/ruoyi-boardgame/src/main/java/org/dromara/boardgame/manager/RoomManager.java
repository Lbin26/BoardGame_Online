package org.dromara.boardgame.manager;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.dromara.boardgame.enums.RoomStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RoomManager {

    private static final long WAITING_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long FINISHED_TTL_MS = TimeUnit.SECONDS.toMillis(60);
    private static final long CLEANUP_INTERVAL_SEC = 15;

    private final ConcurrentHashMap<String, ChessRoom> rooms = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public ChessRoom createRoom(String playerName) {
        String roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        ChessRoom room = new ChessRoom(roomId, playerName);
        rooms.put(roomId, room);
        log.info("创建房间: {} by {}", roomId, playerName);
        return room;
    }

    public ChessRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public ChessRoom removeRoom(String roomId) {
        ChessRoom removed = rooms.remove(roomId);
        if (removed != null) log.info("移除房间: {}", roomId);
        return removed;
    }

    public List<String> listRoomIds() {
        return rooms.keySet().stream().toList();
    }

    public long countWaiting() {
        return rooms.values().stream().filter(r -> r.getStatus() == RoomStatus.WAITING).count();
    }

    public long countPlaying() {
        return rooms.values().stream().filter(r -> r.getStatus() == RoomStatus.PLAYING).count();
    }

    @PostConstruct
    public void startCleanup() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "room-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::cleanup, CLEANUP_INTERVAL_SEC, CLEANUP_INTERVAL_SEC, TimeUnit.SECONDS);
        log.info("房间自动清理定时任务启动");
    }

    @PreDestroy
    public void stopCleanup() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (ChessRoom room : rooms.values()) {
            boolean shouldRemove = false;
            String reason = null;

            if (room.getStatus() == RoomStatus.WAITING) {
                boolean creatorGone = !room.isRedConnected();
                boolean timeout = now - room.getLastActivityAt() > WAITING_TIMEOUT_MS;
                if (creatorGone || timeout) {
                    shouldRemove = true;
                    reason = creatorGone ? "创建者已离线" : "等待超时";
                }
            } else if (room.getStatus() == RoomStatus.FINISHED) {
                boolean ttl = now - room.getLastActivityAt() > FINISHED_TTL_MS;
                if (ttl) {
                    shouldRemove = true;
                    reason = "对局结束已清理";
                }
            }

            if (shouldRemove) {
                rooms.remove(room.getRoomId());
                log.info("房间清理: {} ({})", room.getRoomId(), reason);
                removed++;
            }
        }
        if (removed > 0) log.info("本轮清理 {} 个房间, 当前剩余 {}", removed, rooms.size());
    }
}
