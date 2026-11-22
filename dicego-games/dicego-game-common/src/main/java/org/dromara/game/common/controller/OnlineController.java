package org.dromara.game.common.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.game.common.ws.AbstractGameWebSocketHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 平台级在线人数（只读）。
 *
 * <p>每款游戏各有一个 {@link AbstractGameWebSocketHandler} 子类 bean，各自维护自己的 WS 连接表。
 * 这里由 Spring 注入全部处理器，汇总当前打开的连接并按玩家名去重：
 * 同一玩家多标签页、多房间、跨游戏多开都只算一人；已入座但断线的玩家不计在线。
 *
 * <p>响应沿用游戏 REST 的裸 Map 口径（前端 rawRequest 透传），不包 R 信封。
 */
@RestController
@RequestMapping("/api/online")
@RequiredArgsConstructor
public class OnlineController {

    /** Spring 注入所有游戏的 WS 处理器 bean；无处理器时为空列表，返回 0 */
    private final List<AbstractGameWebSocketHandler<?>> handlers;

    @GetMapping
    public Map<String, Object> count() {
        Set<String> names = new HashSet<>();
        for (AbstractGameWebSocketHandler<?> handler : handlers) {
            names.addAll(handler.connectedPlayerNames());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("onlineCount", names.size());
        return result;
    }
}
