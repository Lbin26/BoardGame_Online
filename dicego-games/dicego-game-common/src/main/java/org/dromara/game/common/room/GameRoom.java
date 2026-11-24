package org.dromara.game.common.room;

import lombok.Getter;
import org.dromara.game.common.enums.RoomStatus;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏房间公共基类：房间生命周期、在线状态、清理时间戳。
 * 座位与玩法由具体游戏子类实现。
 */
public abstract class GameRoom {

    @Getter
    private final String roomId;

    /** 房主（建房者）：解散房间等房间级协作的权限依据，与具体游戏无关 */
    @Getter
    private final String hostName;

    @Getter
    protected volatile RoomStatus status;

    @Getter
    private volatile long lastActivityAt;

    /** 建房时刻：超过 1 小时的房间无论是否在线都强制关闭，避免僵尸对局永久占用大厅 */
    @Getter
    private final long createdAt;

    /** 当前在线玩家名（含旁观者），用于清理宽限判定 */
    private final Set<String> connectedPlayers = ConcurrentHashMap.newKeySet();

    /** 待机玩家名（AFK）：由前端"一段时间无操作"上报，断线也视为待机 */
    private final Set<String> afkPlayers = ConcurrentHashMap.newKeySet();

    protected GameRoom(String roomId) {
        this(roomId, null);
    }

    protected GameRoom(String roomId, String hostName) {
        this.roomId = roomId;
        this.hostName = hostName;
        this.status = RoomStatus.WAITING;
        this.createdAt = System.currentTimeMillis();
        this.lastActivityAt = this.createdAt;
    }

    protected void touch() {
        this.lastActivityAt = System.currentTimeMillis();
    }

    /**
     * 标记为已关闭：关房回调与广播发出前先落状态，
     * 让「全员待机」等主动关房与正常打完的 FINISHED 明确区分。
     */
    public synchronized void close() {
        this.status = RoomStatus.CLOSED;
        touch();
    }

    public synchronized void markConnected(String playerName, boolean connected) {
        if (connected) {
            connectedPlayers.add(playerName);
            afkPlayers.remove(playerName); // 重连即视作恢复操作
        } else {
            connectedPlayers.remove(playerName);
        }
        onConnectedChanged(playerName, connected);
        touch();
    }

    /**
     * 设置玩家待机状态：activity=false 表示恢复操作（解除待机）。
     * 返回是否发生状态变化，供 handler 决定是否广播 PLAYER_AFK。
     */
    public synchronized boolean setAfk(String playerName, boolean afk) {
        boolean changed = afk ? afkPlayers.add(playerName) : afkPlayers.remove(playerName);
        touch();
        return changed;
    }

    public boolean isAfk(String playerName) {
        return afkPlayers.contains(playerName);
    }

    public Set<String> afkPlayers() {
        return Set.copyOf(afkPlayers);
    }

    /**
     * 参与"全员待机"判定的玩家集合，由子类返回实际入座玩家（不含旁观者）。
     * 空集合（无人入座）永不判定为全员待机，避免空房被误关。
     */
    protected Collection<String> participantNames() {
        return List.of();
    }

    /** 全员入座玩家均待机（空房不算），用于自动关房 */
    public boolean allParticipantsAfk() {
        Collection<String> names = participantNames();
        return !names.isEmpty() && names.stream().allMatch(this::isAfk);
    }

    /** 子类钩子：连接状态变化时维护各游戏自己的在线标记（如某方掉线） */
    protected void onConnectedChanged(String playerName, boolean connected) {
    }

    public boolean hasConnectedPlayers() {
        return !connectedPlayers.isEmpty();
    }

    /** 指定玩家当前是否在线（子类座位视图用） */
    public boolean isConnected(String playerName) {
        return connectedPlayers.contains(playerName);
    }
}
