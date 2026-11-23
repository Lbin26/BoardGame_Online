package org.dromara.game.common.room;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.enums.RoomStatus;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 通用房间管理器：建房/查房/自动清理。每个游戏注册自己的实例并注入房间工厂。
 */
@Slf4j
public class GameRoomManager<T extends GameRoom> {

    /** 无人在线时的清理宽限：等待中 / 对局中（双方掉线） / 已结束 */
    private static final long WAITING_GRACE_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long PLAYING_ABANDON_MS = TimeUnit.SECONDS.toMillis(120);
    private static final long FINISHED_TTL_MS = TimeUnit.SECONDS.toMillis(60);
    /** 房间绝对寿命：1 小时。到点无论是否有人在线、是否在活动都关闭，避免僵尸对局常驻 */
    private static final long MAX_ROOM_AGE_MS = TimeUnit.HOURS.toMillis(1);
    private static final long CLEANUP_INTERVAL_SEC = 15;

    private final ConcurrentHashMap<String, T> rooms = new ConcurrentHashMap<>();
    private final Function<String, T> roomFactory;
    private ScheduledExecutorService scheduler;

    /** 关房回调：由 WS handler 注册，用于广播 ROOM_CLOSED 并主动断开该房间全部连接 */
    private BiConsumer<String, String> roomCloseListener = (roomId, reason) -> {};

    public GameRoomManager(Function<String, T> roomFactory) {
        this.roomFactory = roomFactory;
    }

    /** 注册关房回调（roomId, reason） */
    public void setRoomCloseListener(BiConsumer<String, String> listener) {
        this.roomCloseListener = listener;
    }

    /**
     * 关闭房间：先摘除房间对象（阻止后续消息），再回调 handler 通知并断连。
     * 返回是否确有房间被关闭。
     */
    public boolean closeRoom(String roomId, String reason) {
        T removed = rooms.remove(roomId);
        if (removed == null) return false;
        roomCloseListener.accept(roomId, reason);
        log.info("房间关闭: {} ({})", roomId, reason);
        return true;
    }

    public T createRoom(String playerName) {
        return createRoom(playerName, roomFactory);
    }

    /**
     * 按次工厂重载（D3）：建房时携带每房参数（如 Splendor 的 maxPlayers 2-4）。
     * 象棋等固定容量游戏继续用上面的无参版本。
     */
    public T createRoom(String playerName, Function<String, T> factory) {
        String roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        T room = factory.apply(roomId);
        rooms.put(roomId, room);
        log.info("创建房间: {} by {}（座位待选）", roomId, playerName);
        return room;
    }

    public T getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public T removeRoom(String roomId) {
        T removed = rooms.remove(roomId);
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
        for (T room : rooms.values()) {
            // 绝对寿命优先：超过 1 小时的房间一律关闭（含仍有人在线的情况），并广播 ROOM_CLOSED
            if (now - room.getCreatedAt() > MAX_ROOM_AGE_MS) {
                closeRoom(room.getRoomId(), "超过1小时");
                removed++;
                continue;
            }
            // 全员待机优先：即使都还连着也关房（AFK 判定的兜底，主路径在 handler 收到 AFK 时立即触发）
            if (room.allParticipantsAfk()) {
                closeRoom(room.getRoomId(), "全员待机");
                removed++;
                continue;
            }
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
