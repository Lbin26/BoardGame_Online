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

    /** 无人在线时的清理宽限：等待中 / 对局中（双方掉线） / 已结束 */
    private static final long WAITING_GRACE_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long PLAYING_ABANDON_MS = TimeUnit.SECONDS.toMillis(120);
    private static final long FINISHED_TTL_MS = TimeUnit.SECONDS.toMillis(60);
    private static final long CLEANUP_INTERVAL_SEC = 15;

    private final ConcurrentHashMap<String, ChessRoom> rooms = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public ChessRoom createRoom(String playerName) {
        String roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        ChessRoom room = new ChessRoom(roomId);
        rooms.put(roomId, room);
        log.info("创建房间: {} by {}（座位待选）", roomId, playerName);
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
        scheduler.scheduleAtFixedRate(() -> cleanup(System.currentTimeMillis()),
            CLEANUP_INTERVAL_SEC, CLEANUP_INTERVAL_SEC, TimeUnit.SECONDS);
        log.info("房间自动清理定时任务启动");
    }

    @PreDestroy
    public void stopCleanup() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    /**
     * 统一清理规则：仅当无人在线（含旁观者）且空闲超过对应状态宽限时移除。
     * 有人在线的房间一律保留；刷新页面造成的短暂断线不会误杀房间。
     * 包级可见 + now 参数便于单测注入时间。
     */
    void cleanup(long now) {
        int removed = 0;
        for (ChessRoom room : rooms.values()) {
            if (room.hasConnectedPlayers()) continue;
            long idle = now - room.getLastActivityAt();
            long grace = switch (room.getStatus()) {
                case WAITING -> WAITING_GRACE_MS;
                case PLAYING -> PLAYING_ABANDON_MS;
                case FINISHED -> FINISHED_TTL_MS;
            };
            if (idle > grace) {
                rooms.remove(room.getRoomId());
                log.info("房间清理: {} ({} 无人在线超宽限)", room.getRoomId(), room.getStatus());
                removed++;
            }
        }
        if (removed > 0) log.info("本轮清理 {} 个房间, 当前剩余 {}", removed, rooms.size());
    }
}
