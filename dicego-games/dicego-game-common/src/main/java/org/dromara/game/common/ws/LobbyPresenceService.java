package org.dromara.game.common.ws;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大厅在线心跳表（进程内）。
 *
 * <p>游戏 WS 只能统计"进了房间"的玩家，逛大厅的玩家没有 WS 连接。这里让大厅页面
 * 每 {@code ONLINE_REFRESH_MS} 上报一次心跳，服务端按玩家名记最后活跃时间；
 * 超过 {@link #TTL_MS} 未续约视为离开。查在线数时与 WS 连接玩家取并集，因此
 * 同一玩家既在大厅又在房间、或多标签页重复上报都只算一人。
 *
 * <p>单机内存实现足够课程项目使用；若将来部署多实例，需要换成 Redis 带 TTL 的 SETEX。
 */
@Service
public class LobbyPresenceService {

    /** 心跳有效期：前端 10 秒续约一次，留 3 倍余量容忍切后台/网络抖动 */
    public static final long TTL_MS = 30_000L;

    private final ConcurrentHashMap<String, Long> lastSeen = new ConcurrentHashMap<>();
    private final Clock clock;

    public LobbyPresenceService() {
        this(Clock.systemDefaultZone());
    }

    /** 测试用：注入可控时钟，避免依赖真实时间 */
    LobbyPresenceService(Clock clock) {
        this.clock = clock;
    }

    /** 收到大厅心跳，刷新该玩家的最后活跃时间 */
    public void touch(String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        lastSeen.put(playerName.trim(), clock.millis());
    }

    /**
     * 主动下线：退出登录 / 关页 / 进入待机时立即清除心跳，不必等 TTL 自然过期。
     * 玩家名口径与 {@link #touch(String)} 一致（去空格）。
     */
    public void leave(String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        lastSeen.remove(playerName.trim());
    }

    /** 当前仍在有效期内的玩家名（顺手清理过期项） */
    public Set<String> activeNames() {
        long now = clock.millis();
        Set<String> names = new HashSet<>();
        lastSeen.forEach((name, ts) -> {
            if (now - ts <= TTL_MS) {
                names.add(name);
            } else {
                lastSeen.remove(name, ts);
            }
        });
        return names;
    }
}
