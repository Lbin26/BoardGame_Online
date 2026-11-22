package org.dromara.dicego.manager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.dromara.dicego.domain.vo.SplendorRoomVO;
import org.dromara.dicego.engine.SplendorActionResult;
import org.dromara.dicego.engine.SplendorBoard;
import org.dromara.dicego.engine.SplendorEngine;
import org.dromara.dicego.enums.SplendorFailReason;
import org.dromara.dicego.service.SplendorActionLogBO;
import org.dromara.dicego.service.SplendorMatchRecorder;
import org.dromara.dicego.service.SplendorCardCache;
import org.dromara.dicego.service.SplendorPlayerRef;
import org.dromara.game.common.enums.RoomStatus;
import org.dromara.game.common.room.GameRoom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Splendor 房间：座位（seatNo 1..maxPlayers，D2）、房主开局（D2）、开局发牌、
 * 行动串行结算与落库回调（M5，D6）。座位/开局/行动均为 synchronized，
 * 与基类 WS 在线钩子配合；对局状态全部委托 SplendorBoard（纯逻辑，随机仅由 seed 驱动）。
 */
@Slf4j
public class SplendorRoom extends GameRoom {

    public enum SeatResult { SUCCESS, NOT_WAITING, SEAT_TAKEN, ROOM_FULL, INVALID_SEAT }

    public enum StartResult { SUCCESS, NOT_HOST, NOT_WAITING, NOT_ENOUGH_PLAYERS }

    @Getter
    private final int maxPlayers;

    private final SplendorCardCache cache;

    /** 对局落库记录器（M5）：失败由本类兜底捕获，不影响对局 */
    private final SplendorMatchRecorder recorder;

    /** seatNo → 玩家名（TreeMap 保证座位号升序，开局顺序即座位顺序） */
    private final TreeMap<Integer, String> seats = new TreeMap<>();

    /** seatNo → 在线标记（断线不清座位，仅清在线） */
    private final Map<Integer, Boolean> seatOnline = new HashMap<>();

    /** 玩家名 → sys_user.user_id（REST 建/坐时按 token 绑定，未绑定视为游客） */
    private final Map<String, Long> userIdByName = new HashMap<>();

    /** 本房间当前对局的 DB 主键（M5，开局落库后回填） */
    @Getter
    private volatile Long matchId;

    /** 状态版本号（action_log.state_version，每成功行动 +1） */
    private final AtomicInteger stateVersion = new AtomicInteger();

    @Getter
    private volatile SplendorBoard board;

    public SplendorRoom(String roomId, String hostName, int maxPlayers, SplendorCardCache cache,
                        SplendorMatchRecorder recorder) {
        super(roomId, hostName);
        if (maxPlayers < 2 || maxPlayers > 4) {
            throw new IllegalArgumentException("Splendor 房间容量必须 2-4: " + maxPlayers);
        }
        this.maxPlayers = maxPlayers;
        this.cache = cache;
        this.recorder = recorder;
    }

    /**
     * 入座（自动补最小空位或指定座位）：仅等待中；同座幂等、换座自由，被占座拒绝，满员拒绝新人。
     */
    public synchronized SeatResult takeSeat(String playerName, Integer seatNo) {
        if (getStatus() != RoomStatus.WAITING) return SeatResult.NOT_WAITING;

        boolean wasSeated = seats.containsValue(playerName);
        if (!wasSeated && seats.size() >= maxPlayers) return SeatResult.ROOM_FULL;

        Integer target = seatNo;
        if (target != null && (target < 1 || target > maxPlayers)) return SeatResult.INVALID_SEAT;
        if (target == null) target = firstFreeSeat();
        String occupied = seats.get(target);
        if (occupied != null && !occupied.equals(playerName)) return SeatResult.SEAT_TAKEN;

        // 换座：先腾出原座位（在线状态随之迁移）
        Integer oldSeat = seatOf(playerName);
        if (oldSeat != null) {
            seats.remove(oldSeat);
            seatOnline.remove(oldSeat);
        }
        seats.put(target, playerName);
        seatOnline.put(target, isConnected(playerName));
        touch();
        log.info("房间 {} 入座: {} -> 座位 {}（{}/{})", getRoomId(), playerName, target, seats.size(), maxPlayers);
        return SeatResult.SUCCESS;
    }

    /** 起身离座：仅等待中且已入座 */
    public synchronized boolean standUp(String playerName) {
        if (getStatus() != RoomStatus.WAITING) return false;
        Integer seat = seatOf(playerName);
        if (seat == null) return false;
        seats.remove(seat);
        seatOnline.remove(seat);
        touch();
        return true;
    }

    /** 房主开局：≥2 人入座才可发牌（D2：座位号 + 房主开局）；成功后回调落库（M5） */
    public synchronized StartResult startGame(String playerName) {
        if (!getHostName().equals(playerName)) return StartResult.NOT_HOST;
        if (getStatus() != RoomStatus.WAITING) return StartResult.NOT_WAITING;
        if (seats.size() < 2) return StartResult.NOT_ENOUGH_PLAYERS;

        long seed = new Random().nextLong();
        this.board = SplendorBoard.deal(seats.size(), new TreeMap<>(seats), cache, seed);
        this.status = RoomStatus.PLAYING;
        try {
            recorder.onStart(this, seed, seats.size(), playerRefs());
        } catch (Exception e) {
            log.error("房间 {} 开局落库失败（对局不受影响）: {}", getRoomId(), e.getMessage(), e);
        }
        touch();
        log.info("房间 {} 开局: {} 人, seed={}", getRoomId(), seats.size(), seed);
        return StartResult.SUCCESS;
    }

    @Override
    protected void onConnectedChanged(String playerName, boolean connected) {
        Integer seat = seatOf(playerName);
        if (seat != null) seatOnline.put(seat, connected);
    }

    /** 全员待机判定只看入座玩家（旁观者不参与） */
    @Override
    protected java.util.Collection<String> participantNames() {
        return new ArrayList<>(seats.values());
    }

    // ---- 行动入口（M2/M3）：synchronized 保证引擎结算串行；成功行动写 action_log，终局写名次 ----

    /**
     * 统一行动入口：校验 → 引擎结算 → 落 action_log（M5，D6）→ 终局置 FINISHED 并写名次。
     * actionId/paramsJson 透传日志（缺 actionId 时按版本号服务端补，保证唯一键可用）。
     */
    public synchronized SplendorActionResult applyAction(String playerName, String action, String actionId,
                                                         String paramsJson,
                                                         Function<SplendorBoard, SplendorActionResult> engineCall) {
        if (board == null || getStatus() != RoomStatus.PLAYING) {
            return SplendorActionResult.fail(SplendorFailReason.NOT_PLAYING, "对局未开始或已结束");
        }
        SplendorActionResult result = engineCall.apply(board);
        if (result.isOk()) {
            int version = stateVersion.incrementAndGet();
            Integer seat = seatOf(playerName);
            try {
                recorder.onAction(this, new SplendorActionLogBO(matchId, board.getRoundNo(),
                    seat == null ? -1 : seat, boundUserIdOf(playerName), action,
                    actionId != null ? actionId : "srv-" + version, paramsJson, version));
            } catch (Exception e) {
                log.error("房间 {} 行动日志落库失败（对局不受影响）: {}", getRoomId(), e.getMessage(), e);
            }
        }
        if (board.isFinished()) {
            this.status = RoomStatus.FINISHED;
            try {
                recorder.onFinish(this, SplendorEngine.rankingsOf(board));
            } catch (Exception e) {
                log.error("房间 {} 终局落库失败（对局不受影响）: {}", getRoomId(), e.getMessage(), e);
            }
            log.info("房间 {} 终局", getRoomId());
        }
        touch();
        return result;
    }

    // ---- 用户绑定（M5）：REST 层按 Bearer token 解析后绑定 ----

    /** 玩家名 ↔ sys_user.user_id 绑定（建房/入座时由 Controller 调用；未绑定为游客） */
    public synchronized void bindUser(String playerName, Long userId) {
        if (playerName != null && userId != null && userId > 0) {
            userIdByName.put(playerName, userId);
        }
    }

    /**
     * 玩家对应的落库 user_id：已绑定返回真实 ID；未绑定返回负座位哨兵（保证
     * uk(match_id, user_id) 唯一键不冲突）；未入座返回 0。
     */
    public long boundUserIdOf(String playerName) {
        Long bound = userIdByName.get(playerName);
        if (bound != null) return bound;
        Integer seat = seatOf(playerName);
        return seat == null ? 0L : -(long) seat;
    }

    /** 开局落库回填 matchId（recorder.onStart 内部调用） */
    public void adoptMatchId(Long matchId) {
        this.matchId = matchId;
    }

    /** 开局时刻的座位绑定快照（座位号升序），M5 落库传递用 */
    public synchronized List<SplendorPlayerRef> playerRefs() {
        List<SplendorPlayerRef> refs = new ArrayList<>(seats.size());
        for (Map.Entry<Integer, String> e : seats.entrySet()) {
            refs.add(new SplendorPlayerRef(e.getKey(), e.getValue(), boundUserIdOf(e.getValue())));
        }
        return refs;
    }

    public Integer seatOf(String playerName) {
        for (Map.Entry<Integer, String> e : seats.entrySet()) {
            if (e.getValue().equals(playerName)) return e.getKey();
        }
        return null;
    }

    /** 座位快照（协议 §1 room 载荷）：空座也输出，前端按 1..maxPlayers 渲染 */
    public SplendorRoomVO toVO(String viewerName) {
        var seatVOs = new ArrayList<SplendorRoomVO.SeatVO>(maxPlayers);
        for (int no = 1; no <= maxPlayers; no++) {
            String name = seats.get(no);
            seatVOs.add(new SplendorRoomVO.SeatVO(no, name,
                name != null && seatOnline.getOrDefault(no, false),
                name != null && isAfk(name)));
        }
        return new SplendorRoomVO(getRoomId(), getHostName(), maxPlayers, getStatus(), seatVOs,
            seatOf(viewerName), afkPlayers());
    }

    public int seatedCount() {
        return seats.size();
    }

    /** 开局座位顺序（座位号升序的玩家名列表），仅开局后有效 */
    public java.util.List<String> seatOrder() {
        return new ArrayList<>(seats.values());
    }

    private Integer firstFreeSeat() {
        for (int no = 1; no <= maxPlayers; no++) {
            if (!seats.containsKey(no)) return no;
        }
        return null;
    }
}
