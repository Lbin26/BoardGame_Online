package org.dromara.incangold.manager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.enums.RoomStatus;
import org.dromara.game.common.room.GameRoom;
import org.dromara.incangold.domain.vo.IncanGoldRoomVO;
import org.dromara.incangold.domain.vo.IncanGoldSnapshotVO;
import org.dromara.incangold.engine.IncanGoldCards.HazardType;
import org.dromara.incangold.engine.IncanGoldEngine;
import org.dromara.incangold.service.IncanGoldActionLogBO;
import org.dromara.incangold.service.IncanGoldMatchRecorder;
import org.dromara.incangold.service.IncanGoldPlayerRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 印加宝藏房间：座位（3-8，复用公共 GameRoom 的房主/在线/待机）、房主开局、
 * 决策提交的串行结算，以及决策/结算展台的超时推进。
 *
 * <p>对局状态全部委托给纯逻辑 {@link IncanGoldEngine}；本类只负责并发边界、
 * 房间状态与按收件人定制的快照。
 */
@Slf4j
public class IncanGoldRoom extends GameRoom {

    public static final int MIN_PLAYERS = 3;
    public static final int MAX_PLAYERS = 8;

    public enum SeatResult { SUCCESS, NOT_WAITING, SEAT_TAKEN, ROOM_FULL, INVALID_SEAT }

    public enum StartResult { SUCCESS, NOT_HOST, NOT_WAITING, NOT_ENOUGH_PLAYERS }

    @Getter
    private final int maxPlayers;

    private final TreeMap<Integer, String> seats = new TreeMap<>();

    private final Map<Integer, Boolean> seatOnline = new HashMap<>();

    @Getter
    private volatile IncanGoldEngine engine;

    private final IncanGoldMatchRecorder recorder;
    private final Map<String, Long> userIdByName = new HashMap<>();
    private final AtomicInteger stateVersion = new AtomicInteger();
    private volatile Long matchId;

    public IncanGoldRoom(String roomId, String hostName, int maxPlayers) {
        this(roomId, hostName, maxPlayers, IncanGoldMatchRecorder.NOOP);
    }

    public IncanGoldRoom(String roomId, String hostName, int maxPlayers, IncanGoldMatchRecorder recorder) {
        super(roomId, hostName);
        if (maxPlayers < MIN_PLAYERS || maxPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("印加宝藏房间容量必须 3-8: " + maxPlayers);
        }
        this.maxPlayers = maxPlayers;
        this.recorder = recorder == null ? IncanGoldMatchRecorder.NOOP : recorder;
    }

    // ---- 座位（复用公共 GameRoom 的生命周期） ----

    public synchronized SeatResult takeSeat(String playerName, Integer seatNo) {
        if (getStatus() != RoomStatus.WAITING) return SeatResult.NOT_WAITING;

        boolean wasSeated = seats.containsValue(playerName);
        if (!wasSeated && seats.size() >= maxPlayers) return SeatResult.ROOM_FULL;

        Integer target = seatNo;
        if (target != null && (target < 1 || target > maxPlayers)) return SeatResult.INVALID_SEAT;
        if (target == null) target = firstFreeSeat();
        String occupied = seats.get(target);
        if (occupied != null && !occupied.equals(playerName)) return SeatResult.SEAT_TAKEN;

        Integer oldSeat = seatOf(playerName);
        if (oldSeat != null) {
            seats.remove(oldSeat);
            seatOnline.remove(oldSeat);
        }
        seats.put(target, playerName);
        seatOnline.put(target, isConnected(playerName));
        touch();
        log.info("房间 {} 入座: {} -> 座位 {}（{}/{}）", getRoomId(), playerName, target, seats.size(), maxPlayers);
        return SeatResult.SUCCESS;
    }

    public synchronized boolean standUp(String playerName) {
        if (getStatus() != RoomStatus.WAITING) return false;
        Integer seat = seatOf(playerName);
        if (seat == null) return false;
        seats.remove(seat);
        seatOnline.remove(seat);
        touch();
        return true;
    }

    // ---- 对局 ----

    /** 房主开局：座位号升序即探险顺序 */
    public synchronized StartResult startGame(String playerName) {
        if (getHostName() == null || !getHostName().equals(playerName)) return StartResult.NOT_HOST;
        if (getStatus() != RoomStatus.WAITING) return StartResult.NOT_WAITING;
        if (seats.size() < MIN_PLAYERS) return StartResult.NOT_ENOUGH_PLAYERS;

        long seed = new Random().nextLong();
        IncanGoldEngine fresh = new IncanGoldEngine(seed);
        fresh.startGame(seatOrder());
        this.engine = fresh;
        this.status = RoomStatus.PLAYING;
        this.stateVersion.set(0);
        try {
            recorder.onStart(this, seed, seats.size(), playerRefs());
        } catch (Exception e) {
            log.error("房间 {} 开局落库失败（对局不受影响）: {}", getRoomId(), e.getMessage(), e);
        }
        touch();
        log.info("房间 {} 开局: {} 人, seed={}", getRoomId(), seats.size(), seed);
        return StartResult.SUCCESS;
    }

    /** 秘密决策提交；只有本房间内已入座且仍在探险的玩家可提交 */
    public synchronized IncanGoldEngine.SubmitResult submitChoice(String playerName, IncanGoldEngine.Choice choice) {
        IncanGoldEngine current = engine;
        if (current == null || getStatus() != RoomStatus.PLAYING) {
            return IncanGoldEngine.SubmitResult.NOT_IN_DECIDE;
        }
        Integer seat = seatOf(playerName);
        if (seat == null) return IncanGoldEngine.SubmitResult.NO_SUCH_PLAYER;
        IncanGoldEngine.SubmitResult result = current.submitChoice(seat, choice);
        if (result == IncanGoldEngine.SubmitResult.SUCCESS) {
            logAction("CHOICE", seat, "{\"choice\":\"" + choice.name() + "\"}");
            finishIfNeeded();
            touch();
        }
        return result;
    }

    /** 结算展台手动推进（房主可跳过等待） */
    public synchronized boolean advanceRound(String playerName) {
        if (!playerName.equals(getHostName())) return false;
        IncanGoldEngine current = engine;
        if (current == null || current.phase() != IncanGoldEngine.Phase.ROUND_END) return false;
        current.advanceRound();
        finishIfNeeded();
        touch();
        return true;
    }

    /** 由 500ms 定时任务驱动：决策超时视为继续，展台超时进入下一轮 */
    public synchronized IncanGoldEngine.AutoResult autoAdvance(long now) {
        IncanGoldEngine current = engine;
        if (current == null || getStatus() != RoomStatus.PLAYING) {
            return IncanGoldEngine.AutoResult.NONE;
        }
        IncanGoldEngine.AutoResult result = current.autoAdvance(now);
        if (result != IncanGoldEngine.AutoResult.NONE) {
            finishIfNeeded();
            touch();
        }
        return result;
    }

    private void finishIfNeeded() {
        IncanGoldEngine current = engine;
        if (current != null && current.phase() == IncanGoldEngine.Phase.FINISHED
            && getStatus() == RoomStatus.PLAYING) {
            this.status = RoomStatus.FINISHED;
            try {
                recorder.onFinish(this, current.rankings());
            } catch (Exception e) {
                log.error("房间 {} 终局落库失败（对局不受影响）: {}", getRoomId(), e.getMessage(), e);
            }
            log.info("房间 {} 终局", getRoomId());
        }
    }

    // ---- 对局落库（复用全游戏共用表） ----

    /** 玩家名 ↔ sys_user.user_id 绑定，建房/入座时由 Controller 调用 */
    public synchronized void bindUser(String playerName, Long userId) {
        if (playerName == null || userId == null || userId <= 0) return;
        // 同一真实用户一局只能占一个座位：本机多开时后续昵称会被 resolveUserId 解析成同一个
        // user_id，若照单全收，onStart 插入 game_match_player 会撞 uk_match_user(match_id,user_id)
        // 唯一键并触发事务回滚，整局战绩丢失。多出的昵称降级为游客哨兵（boundUserIdOf 负数分支）。
        if (userIdByName.containsKey(playerName)) {
            // 同一昵称重复绑定（建房 + 入座各一次）允许覆盖为相同值
            userIdByName.put(playerName, userId);
            return;
        }
        for (Map.Entry<String, Long> e : userIdByName.entrySet()) {
            if (!e.getKey().equals(playerName) && userId.equals(e.getValue())) return;
        }
        userIdByName.put(playerName, userId);
    }

    /** 未绑定用户用负座位哨兵，保证 (match_id, user_id) 唯一键不冲突 */
    public long boundUserIdOf(String playerName) {
        Long bound = userIdByName.get(playerName);
        if (bound != null) return bound;
        Integer seat = seatOf(playerName);
        return seat == null ? 0L : -(long) seat;
    }

    public void adoptMatchId(Long matchId) {
        this.matchId = matchId;
    }

    public Long getMatchId() {
        return matchId;
    }

    /** 开局时的座位绑定快照（座位号升序） */
    public synchronized List<IncanGoldPlayerRef> playerRefs() {
        List<IncanGoldPlayerRef> refs = new ArrayList<>(seats.size());
        for (Map.Entry<Integer, String> e : seats.entrySet()) {
            refs.add(new IncanGoldPlayerRef(e.getKey(), e.getValue(), boundUserIdOf(e.getValue())));
        }
        return refs;
    }

    /** 暗选提交落库；action_id 由版本号构造，保证唯一键可用 */
    private void logAction(String actionType, int seatNo, String paramsJson) {
        int version = stateVersion.incrementAndGet();
        Long currentMatch = matchId;
        if (currentMatch == null) return;
        IncanGoldEngine current = engine;
        try {
            recorder.onAction(this, new IncanGoldActionLogBO(currentMatch,
                current == null ? 0 : current.round(), seatNo, boundUserIdOf(seats.get(seatNo)),
                "srv-" + version, actionType, paramsJson, version));
        } catch (Exception e) {
            log.error("房间 {} 行动日志落库失败（对局不受影响）: {}", getRoomId(), e.getMessage(), e);
        }
    }

    // ---- 查询与视图 ----

    @Override
    protected void onConnectedChanged(String playerName, boolean connected) {
        Integer seat = seatOf(playerName);
        if (seat != null) seatOnline.put(seat, connected);
    }

    @Override
    protected Collection<String> participantNames() {
        return new ArrayList<>(seats.values());
    }

    public Integer seatOf(String playerName) {
        for (Map.Entry<Integer, String> e : seats.entrySet()) {
            if (e.getValue().equals(playerName)) return e.getKey();
        }
        return null;
    }

    public List<String> seatOrder() {
        return new ArrayList<>(seats.values());
    }

    public int seatedCount() {
        return seats.size();
    }

    /** 事件序号：handler/定时任务据此判断是否发生了需要广播的状态变化 */
    public long eventSeq() {
        IncanGoldEngine current = engine;
        return current == null ? 0 : current.eventSeq();
    }

    public IncanGoldRoomVO toVO(String viewerName) {
        List<IncanGoldRoomVO.SeatVO> seatVOs = new ArrayList<>(maxPlayers);
        for (int no = 1; no <= maxPlayers; no++) {
            String name = seats.get(no);
            seatVOs.add(new IncanGoldRoomVO.SeatVO(no, name,
                name != null && isConnected(name),
                name != null && isAfk(name)));
        }
        return new IncanGoldRoomVO(getRoomId(), getHostName(), maxPlayers, getStatus(), seatVOs,
            seatOf(viewerName), afkPlayers());
    }

    /** 等待中的快照：座位视图 + 满额灾难计数；对局中交给引擎按人定制 */
    public synchronized IncanGoldSnapshotVO snapshot(String viewerName) {
        IncanGoldEngine current = engine;
        if (current == null) {
            List<IncanGoldSnapshotVO.PlayerVO> players = new ArrayList<>();
            for (Map.Entry<Integer, String> e : seats.entrySet()) {
                players.add(new IncanGoldSnapshotVO.PlayerVO(e.getKey(), e.getValue(),
                    isConnected(e.getValue()), isAfk(e.getValue()), false, false, null, null, 0));
            }
            Map<String, Integer> hazards = new LinkedHashMap<>();
            for (HazardType type : HazardType.values()) {
                hazards.put(type.code(), IncanGoldEngine.HAZARD_COPIES);
            }
            return new IncanGoldSnapshotVO("WAITING", 0, null, 0, List.of(), 0,
                hazards, players, List.of(), List.of());
        }
        return current.snapshot(viewerName, this::isConnected, this::isAfk);
    }

    private Integer firstFreeSeat() {
        for (int no = 1; no <= maxPlayers; no++) {
            if (!seats.containsKey(no)) return no;
        }
        return null;
    }
}
