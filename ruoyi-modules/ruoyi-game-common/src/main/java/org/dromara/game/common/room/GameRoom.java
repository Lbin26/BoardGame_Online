package org.dromara.game.common.room;

import lombok.Getter;
import org.dromara.game.common.enums.RoomStatus;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏房间公共基类：房间生命周期、在线状态、清理时间戳。
 * 座位与玩法由具体游戏子类实现。
 */
public abstract class GameRoom {

    @Getter
    private final String roomId;

    @Getter
    protected volatile RoomStatus status;

    @Getter
    private volatile long lastActivityAt;

    /** 当前在线玩家名（含旁观者），用于清理宽限判定 */
    private final Set<String> connectedPlayers = ConcurrentHashMap.newKeySet();

    protected GameRoom(String roomId) {
        this.roomId = roomId;
        this.status = RoomStatus.WAITING;
        this.lastActivityAt = System.currentTimeMillis();
    }

    protected void touch() {
        this.lastActivityAt = System.currentTimeMillis();
    }

    public synchronized void markConnected(String playerName, boolean connected) {
        if (connected) connectedPlayers.add(playerName);
        else connectedPlayers.remove(playerName);
        onConnectedChanged(playerName, connected);
        touch();
    }

    /** 子类钩子：连接状态变化时维护各游戏自己的在线标记（如某方掉线） */
    protected void onConnectedChanged(String playerName, boolean connected) {
    }

    public boolean hasConnectedPlayers() {
        return !connectedPlayers.isEmpty();
    }
}
