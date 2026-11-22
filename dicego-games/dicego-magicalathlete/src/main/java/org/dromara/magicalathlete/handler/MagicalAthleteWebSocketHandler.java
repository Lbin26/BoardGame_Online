package org.dromara.magicalathlete.handler;

import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.domain.vo.GameMessage;
import org.dromara.game.common.enums.GameMessageType;
import org.dromara.game.common.room.GameRoomManager;
import org.dromara.game.common.util.MessageTypeParser;
import org.dromara.game.common.ws.AbstractGameWebSocketHandler;
import org.dromara.magicalathlete.domain.vo.MagicalAthleteSnapshotVO;
import org.dromara.magicalathlete.enums.MagicalAthleteMessageType;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom.PickResult;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom.ReadyResult;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom.OptionalResult;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom.RollOutcome;
import org.dromara.magicalathlete.engine.ability.TurnIntent;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom.Settlement;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 胡闹运动会 WS 处理器。协议：docs/superpowers/specs/2026-11-22-magical-athlete-protocol.md。
 *
 * <p>处理 START_GAME / PICK_RACER / READY_RACER / ROLL；USE_OPTIONAL 为 M3 能力系统预留。
 * MUSTER 阶段快照按收件人个性化（D5）：他人暗选的选手 key 不下发。
 */
@Slf4j
@Component
public class MagicalAthleteWebSocketHandler extends AbstractGameWebSocketHandler<MagicalAthleteRoom> {

    public MagicalAthleteWebSocketHandler(GameRoomManager<MagicalAthleteRoom> roomManager) {
        super(roomManager);
    }

    @Override
    protected Map<String, Object> selfSyncPayload(MagicalAthleteRoom room, String playerName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("room", room.toVO(playerName));
        payload.put("snapshot", room.snapshot(playerName));
        return payload;
    }

    @Override
    protected Map<String, Object> joinBroadcastPayload(MagicalAthleteRoom room, String playerName) {
        return Map.of("room", room.toVO(null));
    }

    @Override
    protected String describeSeat(MagicalAthleteRoom room, String playerName) {
        Integer seat = room.seatOf(playerName);
        return seat == null ? "旁观" : "座位 " + seat;
    }

    @Override
    protected void handleGameMessage(WebSocketSession session, MagicalAthleteRoom room, String roomId,
                                     String playerName, String type, JsonNode node) {
        MagicalAthleteMessageType msgType = MessageTypeParser.parse(MagicalAthleteMessageType.class, type)
            .orElse(null);
        if (msgType == null) return;
        // 基类传入的是整条消息的根节点；前端 useGameWebSocket.send 统一发 {type, payload:{...}}，
        // 动作字段在 payload 里。这里先取出参数节点，与另外三款游戏的 node.path("payload") 口径一致。
        // 没有 payload 对象时退回根节点，兼容把字段平铺在根节点的旧客户端/测试。
        JsonNode args = actionArgs(node);
        switch (msgType) {
            case START_GAME -> handleStartGame(session, room, roomId, playerName, args);
            case PICK_RACER -> handlePickRacer(session, room, roomId, playerName, args);
            case READY_RACER -> handleReadyRacer(session, room, roomId, playerName, args);
            case ROLL -> handleRoll(session, room, roomId, playerName, false);
            case USE_OPTIONAL -> handleUseOptional(session, room, roomId, playerName, args);
            default -> { /* 下行类型 */ }
        }
    }

    /** 优先取 payload 对象；无 payload 时退回消息根节点（兼容平铺字段） */
    private JsonNode actionArgs(JsonNode node) {
        if (node == null) return null;
        JsonNode payload = node.path("payload");
        return payload.isObject() ? payload : node;
    }

    private void handleStartGame(WebSocketSession session, MagicalAthleteRoom room, String roomId,
                                 String playerName, JsonNode node) {
        // M5：可选变体参数；仅 3 人局的 "THREE_DOUBLE" 生效，其余按默认变体（零回归）
        String variant = node != null && node.has("variant") && !node.get("variant").isNull()
            ? node.get("variant").asText() : null;
        MagicalAthleteRoom.StartResult result = room.startGame(playerName, variant);
        if (result != MagicalAthleteRoom.StartResult.SUCCESS) {
            String msg = switch (result) {
                case NOT_HOST -> "只有房主可以开局";
                case NOT_WAITING -> "对局已在进行中";
                case NOT_ENOUGH_PLAYERS -> "至少 2 名玩家入座才能开局";
                default -> "无法开局";
            };
            sendReject(session, roomId, "START_GAME", result.name(), msg);
            return;
        }
        // GAME_START 必须按收件人定制 mySeatNo（否则前端丢失座位，轮抽序位校验全废）
        broadcastPersonalized(roomId, name -> GameMessage.of(GameMessageType.GAME_START, roomId, playerName,
            Map.of("room", room.toVO(name), "seatOrder", room.seatOrder())));
        broadcastState(roomId, room);
    }

    private void handlePickRacer(WebSocketSession session, MagicalAthleteRoom room, String roomId,
                                 String playerName, JsonNode node) {
        String racerKey = node != null && node.has("racerKey") && !node.get("racerKey").isNull()
            ? node.get("racerKey").asText() : null;
        if (racerKey == null || racerKey.isBlank()) {
            sendReject(session, roomId, "PICK_RACER", "RACER_NOT_IN_POOL", "请选择一名选手");
            return;
        }
        PickResult result = room.pickRacer(playerName, racerKey);
        if (result != PickResult.SUCCESS) {
            sendReject(session, roomId, "PICK_RACER", result.name(), pickRejectMessage(result));
            return;
        }
        Integer seat = room.seatOf(playerName);
        broadcastPick(roomId, room, seat, playerName, racerKey, false);
    }

    /** 选牌广播（超时任务复用）：DRAFT_PICK + 全量状态 */
    public void broadcastPick(String roomId, MagicalAthleteRoom room, Integer seatNo, String playerName,
                              String racerKey, boolean auto) {
        Map<String, Object> pick = new LinkedHashMap<>();
        pick.put("seatNo", seatNo);
        pick.put("playerName", playerName);
        pick.put("racerKey", racerKey);
        pick.put("racerName", org.dromara.magicalathlete.engine.RacerCatalog.nameOf(racerKey));
        pick.put("auto", auto);
        broadcast(roomId, GameMessage.of(MagicalAthleteMessageType.DRAFT_PICK, roomId,
            auto ? "system" : playerName, pick));
        broadcastState(roomId, room);
        // 两批选完自动进入 MUSTER：补一条 RACE_START 之外的阶段提示（实际阶段以 STATE_SYNC 为准）
        if (room.getPhase() == MagicalAthleteRoom.Phase.MUSTER) {
            broadcast(roomId, GameMessage.of(MagicalAthleteMessageType.MUSTER_READY, roomId, "system",
                Map.of("raceNo", room.getRaceNo() + 1, "message", "请暗选出场选手")));
        }
    }

    private void handleReadyRacer(WebSocketSession session, MagicalAthleteRoom room, String roomId,
                                  String playerName, JsonNode node) {
        // M5 双角色局可一次提交 racerKeys 数组（顺序 = 赛前固定的出场顺序）；
        // 普通局仍用单个 racerKey。
        List<String> keys = new ArrayList<>();
        if (node != null && node.has("racerKeys") && node.get("racerKeys").isArray()) {
            for (JsonNode k : node.get("racerKeys")) {
                if (k.isTextual() && !k.asText().isBlank()) keys.add(k.asText());
            }
        }
        if (keys.isEmpty()) {
            String single = node != null && node.has("racerKey") && !node.get("racerKey").isNull()
                ? node.get("racerKey").asText() : null;
            if (single != null && !single.isBlank()) keys.add(single);
        }
        if (keys.isEmpty()) {
            sendReject(session, roomId, "READY_RACER", "RACER_NOT_IN_HAND", "请选择手牌选手");
            return;
        }
        for (String key : keys) {
            ReadyResult result = room.readyRacer(playerName, key);
            if (result != ReadyResult.SUCCESS) {
                sendReject(session, roomId, "READY_RACER", result.name(), readyRejectMessage(result));
                return;
            }
        }
        broadcastMusterReady(roomId, room, room.seatOf(playerName), playerName, false);
    }

    /** 暗选就绪广播：只说"某人已准备"，不含选手 key（D5） */
    public void broadcastMusterReady(String roomId, MagicalAthleteRoom room, Integer seatNo,
                                     String playerName, boolean auto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seatNo", seatNo);
        payload.put("playerName", playerName);
        payload.put("ready", true);
        payload.put("auto", auto);
        payload.put("raceNo", room.getRaceNo() + 1);
        broadcast(roomId, GameMessage.of(MagicalAthleteMessageType.MUSTER_READY, roomId,
            auto ? "system" : playerName, payload));
        broadcastState(roomId, room);
        if (room.getPhase() == MagicalAthleteRoom.Phase.RACE) {
            broadcastRaceStart(roomId, room);
        }
    }

    /** 比赛开始广播：此时才公开出场选手 */
    public void broadcastRaceStart(String roomId, MagicalAthleteRoom room) {
        MagicalAthleteSnapshotVO snapshot = room.snapshot(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("raceNo", room.getRaceNo());
        payload.put("track", org.dromara.magicalathlete.engine.RaceTrack.sideOfRace(room.getRaceNo()).name());
        payload.put("activeRacerKeys", snapshot.activeRacerKeys());
        // M5 双角色：参赛号精确视图 + lane→真实座位映射
        payload.put("variant", snapshot.variant());
        payload.put("racersPerSeat", snapshot.racersPerSeat());
        payload.put("activeRacerKeysByLane", snapshot.activeRacerKeysByLane());
        payload.put("laneToSeat", snapshot.laneToSeat());
        broadcast(roomId, GameMessage.of(MagicalAthleteMessageType.RACE_START, roomId, "system", payload));
        broadcastState(roomId, room);
    }

    private void handleUseOptional(WebSocketSession session, MagicalAthleteRoom room, String roomId,
                                   String playerName, JsonNode node) {
        boolean usePrimary = node != null && node.has("usePrimary") && !node.get("usePrimary").isNull()
            && node.get("usePrimary").asBoolean(false);
        int targetSeat = node != null && node.has("targetSeat") && !node.get("targetSeat").isNull()
            ? node.get("targetSeat").asInt(0) : 0;
        int targetSpace = node != null && node.has("targetSpace") && !node.get("targetSpace").isNull()
            ? node.get("targetSpace").asInt(0) : 0;
        int guessDice = node != null && node.has("guessDice") && !node.get("guessDice").isNull()
            ? node.get("guessDice").asInt(0) : 0;
        TurnIntent intent = new TurnIntent(usePrimary, targetSeat, targetSpace, guessDice);

        OptionalResult result = room.useOptional(playerName, intent);
        if (result != OptionalResult.SUCCESS) {
            sendReject(session, roomId, "USE_OPTIONAL", result.name(), optionalRejectMessage(result));
            return;
        }
        // 意图是私有信息：只给声明者回一条个性化快照（含其可用选项），不广播、不下发他人意图
        send(session, GameMessage.of(MagicalAthleteMessageType.STATE_SYNC, roomId, playerName, room.snapshot(playerName)));
    }

    private void handleRoll(WebSocketSession session, MagicalAthleteRoom room, String roomId,
                            String playerName, boolean auto) {
        RollOutcome outcome = auto ? room.autoRoll(System.currentTimeMillis()) : room.roll(playerName);
        if (!outcome.ok()) {
            if (!auto) {
                String msg = switch (outcome.reason()) {
                    case NOT_IN_GAME -> "你还没有入座";
                    case PHASE_MISMATCH -> "当前不在比赛阶段";
                    case NOT_YOUR_TURN -> "还没轮到你掷骰";
                    case RACE_FINISHED -> "本场已结束";
                    default -> "无法掷骰";
                };
                sendReject(session, roomId, "ROLL", outcome.reason().name(), msg);
            }
            return;
        }
        broadcastRoll(roomId, room, outcome, auto);
    }

    /** 掷骰广播（超时任务复用）：DICE_RESULT → 可选 RACE_RESULT → STATE_SYNC */
    public void broadcastRoll(String roomId, MagicalAthleteRoom room, RollOutcome outcome, boolean auto) {
        Map<String, Object> dice = new LinkedHashMap<>();
        dice.put("seatNo", outcome.seatNo());
        dice.put("playerName", room.nameOfSeat(outcome.seatNo()));
        dice.put("value", outcome.dice());
        dice.put("tripSkipped", outcome.tripSkipped());
        dice.put("effects", outcome.effects());
        dice.put("auto", auto);
        broadcast(roomId, GameMessage.of(MagicalAthleteMessageType.DICE_RESULT, roomId,
            auto ? "system" : room.nameOfSeat(outcome.seatNo()), dice));

        Settlement settlement = outcome.settlement();
        if (settlement != null) broadcastSettlement(roomId, room, settlement);
        broadcastState(roomId, room);
    }

    /** 单场结算广播：RACE_RESULT（含奖牌面值）→ 终局再补 GAME_OVER（含并列标记） */
    public void broadcastSettlement(String roomId, MagicalAthleteRoom room, Settlement settlement) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("raceNo", settlement.raceNo());
        result.put("goldSeat", settlement.goldSeat());
        result.put("silverSeat", settlement.silverSeat());
        result.put("goldName", room.nameOfSeat(settlement.goldSeat()));
        result.put("silverName", room.nameOfSeat(settlement.silverSeat()));
        result.put("goldValue", settlement.goldValue());
        result.put("silverValue", settlement.silverValue());
        result.put("standings", settlement.standings());
        broadcast(roomId, GameMessage.of(MagicalAthleteMessageType.RACE_RESULT, roomId, "system", result));

        if (settlement.gameOver()) {
            MagicalAthleteSnapshotVO snapshot = room.snapshot(null);
            Map<String, Object> over = new LinkedHashMap<>();
            over.put("rankings", snapshot.rankings());
            boolean tie = !snapshot.rankings().isEmpty() && snapshot.rankings().get(0).rank() == 1
                && snapshot.rankings().stream().filter(s -> s.rank() == 1).count() > 1;
            over.put("tie", tie);
            broadcast(roomId, GameMessage.of(MagicalAthleteMessageType.GAME_OVER, roomId, "system", over));
        }
    }

    /** 全量状态：MUSTER 必须个性化，避免泄露他人暗选 */
    public void broadcastState(String roomId, MagicalAthleteRoom room) {
        broadcastPersonalized(roomId, name -> GameMessage.of(MagicalAthleteMessageType.STATE_SYNC, roomId,
            "system", room.snapshot(name)));
    }

    /** 座位变更中立广播（REST 入座/起身触发） */
    public void broadcastSeatChange(String roomId, String playerName, Integer seatNo) {
        MagicalAthleteRoom room = getRoom(roomId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("room", room == null ? null : room.toVO(null));
        payload.put("seatNo", seatNo);
        payload.put("playerName", playerName);
        broadcast(roomId, GameMessage.of(GameMessageType.SEAT_CHANGED, roomId, playerName, payload));
    }

    private void sendReject(WebSocketSession session, String roomId, String action, String reason, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("reason", reason);
        payload.put("message", message);
        send(session, GameMessage.of(MagicalAthleteMessageType.ACTION_REJECT, roomId, "system", payload));
    }

    private String pickRejectMessage(PickResult result) {
        return switch (result) {
            case NOT_IN_GAME -> "你还没有入座";
            case PHASE_MISMATCH -> "当前不在轮抽阶段";
            case NOT_YOUR_TURN -> "还没轮到你选";
            case RACER_NOT_IN_POOL -> "该选手不在当前牌池";
            case DRAFT_FINISHED -> "轮抽已结束";
            default -> "无法选择";
        };
    }

    private String optionalRejectMessage(OptionalResult result) {
        return switch (result) {
            case NOT_IN_GAME -> "你还没有入座";
            case PHASE_MISMATCH -> "当前不在比赛阶段";
            case NOT_YOUR_TURN -> "还没轮到你行动";
            case INVALID_CHOICE -> "选择的目标无效";
            case NO_OPTIONAL -> "你的选手没有该可选能力";
            default -> "无法使用能力";
        };
    }

    private String readyRejectMessage(ReadyResult result) {
        return switch (result) {
            case NOT_IN_GAME -> "你还没有入座";
            case PHASE_MISMATCH -> "当前不在赛前准备阶段";
            case RACER_NOT_IN_HAND -> "该选手不在你的手牌";
            case ALREADY_READY -> "你已准备，等待其他玩家";
            case RACER_USED -> "该选手已在往场出场";
            default -> "无法选择";
        };
    }
}
