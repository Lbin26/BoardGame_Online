package org.dromara.game.common.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.game.common.ws.AbstractGameWebSocketHandler;
import org.dromara.game.common.ws.LobbyPresenceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 平台级在线人数（只读）。
 *
 * <p>每款游戏各有一个 {@link AbstractGameWebSocketHandler} 子类 bean，各自维护自己的 WS 连接表；
 * 此外大厅玩家通过 {@code POST /api/online/ping} 上报心跳（{@link LobbyPresenceService}）。
 * 这里汇总"房间 WS 连接 + 大厅心跳"并按玩家名取并集：
 * 同一玩家多标签页、多房间、跨游戏多开、既在大厅又在房间都只算一人；心跳过期视为离开。
 *
 * <p>响应沿用游戏 REST 的裸 Map 口径（前端 rawRequest 透传），不包 R 信封。
 */
@Slf4j
@RestController
@RequestMapping("/api/online")
@RequiredArgsConstructor
public class OnlineController {

    /**
     * 用 raw 类型的 ObjectProvider 获取全部 WS 处理器。
     *
     * <p>注意：不能用 {@code List<AbstractGameWebSocketHandler<?>>} 注入。带通配符的泛型集合
     * 在 Spring 严格泛型匹配下可能匹配不到具体泛型（如 {@code <SplendorRoom>}）的子类 bean，
     * 导致列表为空、在线人数恒为 0。ObjectProvider 按原始 Class 取 bean，确定性包含全部处理器。
     */
    private final ObjectProvider<AbstractGameWebSocketHandler> handlerProvider;
    private final LobbyPresenceService lobbyPresence;

    /** 大厅心跳请求体；playerName 缺省时从登录态取 */
    public record PingBody(String playerName) {
    }

    /**
     * 大厅玩家续约。该路径不在 Sa-Token 排除名单内，正常浏览器请求带登录令牌；
     * playerName 仅作为显式兜底，未带时回落到登录用户名。
     */
    @PostMapping("/ping")
    public Map<String, Object> ping(@RequestBody(required = false) PingBody body) {
        String name = body == null ? null : body.playerName();
        if (name == null || name.isBlank()) {
            try {
                name = LoginHelper.getUsername();
            } catch (Exception e) {
                log.debug("大厅心跳未取到登录用户名", e);
            }
        }
        lobbyPresence.touch(name);
        return Map.of("success", true);
    }

    /**
     * 主动下线：退出登录 / 关闭页面 / 进入待机时调用，立即把玩家从在线表移除，
     * 不再等待心跳 TTL 自然过期。房间 WS 连接靠服务端 close 事件自行清理。
     */
    @PostMapping("/leave")
    public Map<String, Object> leave(@RequestBody(required = false) PingBody body) {
        String name = body == null ? null : body.playerName();
        if (name == null || name.isBlank()) {
            try {
                name = LoginHelper.getUsername();
            } catch (Exception e) {
                log.debug("下线请求未取到登录用户名", e);
            }
        }
        lobbyPresence.leave(name);
        return Map.of("success", true);
    }

    @GetMapping
    public Map<String, Object> count() {
        Set<String> names = new HashSet<>(lobbyPresence.activeNames());
        int handlerCount = 0;
        for (AbstractGameWebSocketHandler handler : handlerProvider.orderedStream().toList()) {
            handlerCount++;
            names.addAll(handler.connectedPlayerNames());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("onlineCount", names.size());
        log.debug("平台在线人数统计: handler 数={}, 在线玩家数={}（含大厅心跳）", handlerCount, names.size());
        return result;
    }
}
