package org.dromara.game.common.ws;

import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.domain.vo.GameMessage;
import org.dromara.game.common.enums.GameMessageType;
import org.dromara.game.common.room.GameRoom;
import org.dromara.game.common.room.GameRoomManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用游戏 WS 基类：连接校验与替换、自身同步/中立广播、聊天、心跳、断开广播。
 * 游戏专属消息与加入载荷由子类实现。
 */
@Slf4j
public abstract class AbstractGameWebSocketHandler<T extends GameRoom> extends TextWebSocketHandler {

    private final GameRoomManager<T> roomManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** key = roomId + ":" + playerName，同名玩家跨房间不互踢 */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    protected AbstractGameWebSocketHandler(GameRoomManager<T> roomManager) {
        this.roomManager = roomManager;
        // 房管/清理线程关房时统一走这里：广播 ROOM_CLOSED + 断连，前端回大厅
        roomManager.setRoomCloseListener(this::closeRoomAndNotify);
    }

    /**
     * 关房收尾：先广播 ROOM_CLOSED，再逐个关闭该房间连接（打标记避免 onclose 又广播离开/重连）。
     * 由 GameRoomManager.closeRoom 回调触发，room 对象此时已从管理器摘除。
     */
    private void closeRoomAndNotify(String roomId, String reason) {
        broadcast(roomId, GameMessage.of(GameMessageType.ROOM_CLOSED, roomId, "system",
            Map.of("reason", reason)));
        List<WebSocketSession> toClose = new ArrayList<>();
        for (WebSocketSession s : sessions.values()) {
            if (roomId.equals(s.getAttributes().get("roomId"))) {
                s.getAttributes().put("roomClosed", true);
                toClose.add(s);
            }
        }
        for (WebSocketSession s : toClose) {
            try {
                s.close(CloseStatus.NORMAL.withReason("room closed: " + reason));
            } catch (IOException e) {
                log.warn("关房时关闭连接失败: {}", e.getMessage());
            }
        }
        for (WebSocketSession s : toClose) {
            sessions.remove(roomId + ":" + s.getAttributes().get("playerName"), s);
        }
    }

    protected T getRoom(String roomId) {
        return roomManager.getRoom(roomId);
    }

    /** 子类判断 playerName 是否为房主（用于解散权限校验） */
    protected abstract boolean isHost(T room, String playerName);

    /** 子类连接自身同步载荷（含本人视角，如 viewerColor + 棋盘快照） */
    protected abstract Map<String, Object> selfSyncPayload(T room, String playerName);

    /** 他人加入的广播载荷（必须中立，不泄漏加入者视角） */
    protected abstract Map<String, Object> joinBroadcastPayload(T room, String playerName);

    /** 游戏专属消息（CHAT/HEARTBEAT 之外的分发）：type 为原始字符串，由子类解析自己的消息枚举 */
    protected abstract void handleGameMessage(WebSocketSession session, T room, String roomId,
                                              String playerName, String type, JsonNode node);

    /** 日志里的座位描述；默认旁观 */
    protected String describeSeat(T room, String playerName) {
        return "旁观";
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null) {
            session.close(CloseStatus.BAD_DATA.withReason("missing uri"));
            return;
        }
        String query = uri.getRawQuery();
        String roomId = extractParam(query, "roomId");
        String playerName = extractParam(query, "playerName");

        if (roomId == null || playerName == null) {
            session.sendMessage(new TextMessage(toJson(
                GameMessage.of(GameMessageType.ERROR, null, "system", Map.of("error", "roomId and playerName required"))
            )));
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        T room = roomManager.getRoom(roomId);
        if (room == null) {
            session.sendMessage(new TextMessage(toJson(
                GameMessage.of(GameMessageType.ERROR, roomId, "system", Map.of("error", "room not found"))
            )));
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("room not found"));
            return;
        }

        // 不自动入座：未选座的连接一律视为旁观者，满房也允许观战
        session.getAttributes().put("roomId", roomId);
        session.getAttributes().put("playerName", playerName);
        String key = roomId + ":" + playerName;
        WebSocketSession old = sessions.put(key, session);
        if (old != null && old != session) {
            // 同房间同名的旧连接（如刷新残留）：标记 replaced，其 close 回调不清在线状态
            old.getAttributes().put("replaced", true);
            try {
                old.close(CloseStatus.NORMAL.withReason("replaced by new connection"));
            } catch (IOException e) {
                log.warn("关闭被替换连接失败: {}", e.getMessage());
            }
        }
        room.markConnected(playerName, true);

        // 自身同步：带本人视角（刷新后恢复座位视角）
        send(session, GameMessage.of(GameMessageType.PLAYER_JOINED, roomId, playerName,
            selfSyncPayload(room, playerName)));

        // 广播：中立视角，不发给自己（自身同步已带本人视角，
        // 中立广播晚到会覆盖慢握手场景下刚选好的座位颜色）
        broadcastExcept(roomId, GameMessage.of(GameMessageType.PLAYER_JOINED, roomId, playerName,
            joinBroadcastPayload(room, playerName)), session);

        log.info("玩家连接: {} -> 房间 {}（{}）", playerName, roomId, describeSeat(room, playerName));
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomId = (String) session.getAttributes().get("roomId");
        String playerName = (String) session.getAttributes().get("playerName");
        if (roomId == null || playerName == null) return;

        T room = roomManager.getRoom(roomId);
        if (room == null) return;

        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText();
            // 公共类型按 name() 分发；其余原样交给子类，由子类解析各自的消息枚举
            switch (type) {
                case "CHAT" -> handleChat(room, roomId, playerName, node);
                case "HEARTBEAT" -> send(session, GameMessage.of(GameMessageType.HEARTBEAT, roomId, "system", null));
                case "AFK" -> handleAfk(room, roomId, playerName, true);
                case "ACTIVE" -> handleAfk(room, roomId, playerName, false);
                case "DISSOLVE_ROOM" -> handleDissolve(session, room, roomId, playerName);
                default -> {
                    // 任意真实游戏消息都视作"有操作"，自动解除待机
                    if (room.isAfk(playerName)) handleAfk(room, roomId, playerName, false);
                    handleGameMessage(session, room, roomId, playerName, type, node);
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket消息解析失败: {}", e.getMessage());
        }
    }

    private void handleChat(T room, String roomId, String playerName, JsonNode node) {
        String text = node.path("payload").path("text").asText();
        if (text.isBlank()) return;
        if (room.isAfk(playerName)) handleAfk(room, roomId, playerName, false);
        broadcast(roomId, GameMessage.of(GameMessageType.CHAT, roomId, playerName,
            Map.of("text", text)));
    }

    /** 待机状态变更：更新房间状态→广播→若全员待机则关房 */
    private void handleAfk(T room, String roomId, String playerName, boolean afk) {
        if (!room.setAfk(playerName, afk)) return; // 状态未变，不重复广播
        broadcast(roomId, GameMessage.of(GameMessageType.PLAYER_AFK, roomId, playerName,
            Map.of("afk", afk)));
        // 只有入座玩家全员待机才关房；旁观者待机不触发（participantNames 由子类给出）
        if (afk && room.allParticipantsAfk()) {
            roomManager.closeRoom(roomId, "全员待机");
        }
    }

    /** 房主解散：校验权限后关房；非房主回错误 */
    private void handleDissolve(WebSocketSession session, T room, String roomId, String playerName) {
        if (!isHost(room, playerName)) {
            send(session, GameMessage.of(GameMessageType.ERROR, roomId, "system",
                Map.of("error", "只有房主可以解散房间")));
            return;
        }
        roomManager.closeRoom(roomId, "房主解散");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 被新连接替换的旧 session：不做任何离开处理，避免误清新连接的在线状态
        if (Boolean.TRUE.equals(session.getAttributes().get("replaced"))) return;
        // 房主解散/全员待机主动关闭的连接：房间已不存在，不再广播离开
        if (Boolean.TRUE.equals(session.getAttributes().get("roomClosed"))) return;

        String roomId = (String) session.getAttributes().get("roomId");
        String playerName = (String) session.getAttributes().get("playerName");
        if (roomId == null || playerName == null) return;

        sessions.remove(roomId + ":" + playerName, session);
        T room = roomManager.getRoom(roomId);
        // 断线不清座位：刷新/短暂掉线后重连可原位恢复
        if (room != null) room.markConnected(playerName, false);
        log.info("玩家断开: {} <- 房间 {}", playerName, roomId);
        broadcast(roomId, GameMessage.of(GameMessageType.PLAYER_LEAVE, roomId, playerName, null));
    }

    protected void send(WebSocketSession session, GameMessage<?> msg) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(toJson(msg)));
        } catch (IOException e) {
            log.warn("发送消息失败: {}", e.getMessage());
        }
    }

    protected void broadcast(String roomId, GameMessage<?> msg) {
        broadcastExcept(roomId, msg, null);
    }

    protected void broadcastExcept(String roomId, GameMessage<?> msg, WebSocketSession exclude) {
        for (WebSocketSession s : sessions.values()) {
            if (s == exclude) continue;
            String rid = (String) s.getAttributes().get("roomId");
            if (roomId.equals(rid) && s.isOpen()) {
                send(s, msg);
            }
        }
    }

    /**
     * 按收件人差异化广播（D5）：对房间内每个连接按玩家名定制消息。
     * 用于含隐藏信息的游戏（如 Splendor 预留卡仅本人可见），
     * 取代"同一快照群发"。旁观者收到的是其名字对应的视角。
     */
    protected void broadcastPersonalized(String roomId, java.util.function.Function<String, GameMessage<?>> msgByPlayerName) {
        for (WebSocketSession s : sessions.values()) {
            String rid = (String) s.getAttributes().get("roomId");
            if (roomId.equals(rid) && s.isOpen()) {
                String playerName = (String) s.getAttributes().get("playerName");
                send(s, msgByPlayerName.apply(playerName));
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String extractParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                // 前端 encodeURIComponent 编码（中文名→%XX），必须按 UTF-8 解码，
                // 否则 WS 身份与 REST 选座名字不一致：显示乱码且已选座玩家被当旁观者
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
