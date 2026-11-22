package org.dromara.halligalli.manager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.enums.RoomStatus;
import org.dromara.game.common.room.GameRoom;
import org.dromara.halligalli.domain.vo.HalliGalliCardVO;
import org.dromara.halligalli.domain.vo.HalliGalliPlayerVO;
import org.dromara.halligalli.domain.vo.HalliGalliRingVO;
import org.dromara.halligalli.domain.vo.HalliGalliRoomVO;
import org.dromara.halligalli.domain.vo.HalliGalliSnapshotVO;
import org.dromara.halligalli.engine.HalliGalliBoard;
import org.dromara.halligalli.engine.HalliGalliPlayer;
import org.dromara.halligalli.enums.Fruit;
import org.dromara.halligalli.service.HalliGalliActionLogBO;
import org.dromara.halligalli.service.HalliGalliMatchRecorder;
import org.dromara.halligalli.service.HalliGalliPlayerRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 德国心脏病房间：座位（2-6，D11）、房主开局、翻牌/抢铃的串行结算（D2 房间级锁）与 10s 自动翻牌兜底（D5）。
 *
 * <p>并发要点：{@link #flip(int)} 与 {@link #ring(int)} 都是 synchronized，
 * 抢铃胜负只由服务端进入锁的顺序决定，锁外不得读取/修改对局状态。
 */
@Slf4j
public class HalliGalliRoom extends GameRoom {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 6;

    /** 翻牌超时（D5）：超时由服务端代翻，避免翻牌方挂机卡死全场 */
    public static final long FLIP_TIMEOUT_MS = 10_000;

    public enum SeatResult { SUCCESS, NOT_WAITING, SEAT_TAKEN, ROOM_FULL, INVALID_SEAT }

    public enum StartResult { SUCCESS, NOT_HOST, NOT_WAITING, NOT_ENOUGH_PLAYERS }

    @Getter
    private final int maxPlayers;

    /** D4 开关：牌堆耗尽者是否仍可抢铃（默认 false） */
    private final boolean ringAfterExhausted;

    /** 对局落库记录器（M5）：失败由本类兜底捕获，不影响对局 */
    private final HalliGalliMatchRecorder recorder;

    private final TreeMap<Integer, String> seats = new TreeMap<>();

    private final Map<Integer, Boolean> seatOnline = new HashMap<>();

    /** 玩家名 → sys_user.user_id（REST 建/坐时按 token 绑定，未绑定视为游客） */
    private final Map<String, Long> userIdByName = new HashMap<>();

    /** 本房间当前对局的 DB 主键（M5，开局落库后回填） */
    @Getter
    private volatile Long matchId;

    /** 状态版本号（action_log.state_version，每次成功动作 +1） */
    private final AtomicInteger stateVersion = new AtomicInteger();

    /** 本局翻牌次数（action_log.round_no 与 game_match.total_rounds 的口径） */
    @Getter
    private volatile int flipCount;

    @Getter
    private volatile HalliGalliBoard board;

    /** 当前翻牌截止时间（epoch ms），0 表示无倒计时 */
    @Getter
    private volatile long flipDeadlineAt;

    public HalliGalliRoom(String roomId, String hostName, int maxPlayers) {
        this(roomId, hostName, maxPlayers, false, HalliGalliMatchRecorder.NOOP);
    }

    public HalliGalliRoom(String roomId, String hostName, int maxPlayers, boolean ringAfterExhausted) {
        this(roomId, hostName, maxPlayers, ringAfterExhausted, HalliGalliMatchRecorder.NOOP);
    }

    public HalliGalliRoom(String roomId, String hostName, int maxPlayers, boolean ringAfterExhausted,
                          HalliGalliMatchRecorder recorder) {
        super(roomId, hostName);
        if (maxPlayers < MIN_PLAYERS || maxPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("德国心脏病房间容量必须 2-6: " + maxPlayers);
        }
        this.maxPlayers = maxPlayers;
        this.ringAfterExhausted = ringAfterExhausted;
        this.recorder = recorder == null ? HalliGalliMatchRecorder.NOOP : recorder;
    }

    // ---- 座位与开局 ----

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
        log.info("房间 {} 入座: {} -> 座位 {}（{}/{})", getRoomId(), playerName, target, seats.size(), maxPlayers);
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

    public synchronized StartResult startGame(String playerName) {
        if (getHostName() == null || !getHostName().equals(playerName)) return StartResult.NOT_HOST;
        if (getStatus() != RoomStatus.WAITING) return StartResult.NOT_WAITING;
        if (seats.size() < MIN_PLAYERS) return StartResult.NOT_ENOUGH_PLAYERS;

        long seed = new Random().nextLong();
        this.board = HalliGalliBoard.deal(new TreeMap<>(seats), seed, ringAfterExhausted);
        this.status = RoomStatus.PLAYING;
        this.flipCount = 0;
        resetFlipDeadline();
        try {
            recorder.onStart(this, seed, seats.size(), playerRefs());
        } catch (Exception e) {
            log.error("房间 {} 开局落库失败（对局不受影响）: {}", getRoomId(), e.getMessage(), e);
        }
        touch();
        log.info("房间 {} 开局: {} 人, seed={}", getRoomId(), seats.size(), seed);
        return StartResult.SUCCESS;
    }

    // ---- 对局动作（串行） ----

    /** 翻牌：座位校验在 board 内完成，房间只负责超时、落库与终局收尾 */
    public synchronized HalliGalliBoard.FlipOutcome flip(int seatNo) {
        return flip(seatNo, false);
    }

    /** auto=true 表示 10s 超时代翻（D5），日志按真实来源标记 */
    private synchronized HalliGalliBoard.FlipOutcome flip(int seatNo, boolean auto) {
        if (board == null || getStatus() != RoomStatus.PLAYING) {
            return HalliGalliBoard.FlipOutcome.rejected(seatNo, "NOT_PLAYING");
        }
        HalliGalliBoard.FlipOutcome outcome = board.flip(seatNo);
        if (outcome.ok()) {
            resetFlipDeadline();
            flipCount++;
            logAction("FLIP", seatNo, flipParams(outcome, auto));
            finishIfNeeded();
            touch();
        }
        return outcome;
    }

    /** 抢铃：以进入本方法的顺序为唯一裁决依据（D2）；防抖/迟到不是有效动作，不落库 */
    public synchronized HalliGalliBoard.RingOutcome ring(int seatNo) {
        if (board == null || getStatus() != RoomStatus.PLAYING) {
            return HalliGalliBoard.RingOutcome.rejected(seatNo, "NOT_PLAYING");
        }
        HalliGalliBoard.RingOutcome outcome = board.ring(seatNo, System.currentTimeMillis());
        if (outcome.settled()) {
            resetFlipDeadline();
            logAction("RING", seatNo, ringParams(outcome));
            finishIfNeeded();
            touch();
        }
        return outcome;
    }

    /**
     * 超时检查（由定时任务每 500ms 调用）：当前翻牌方超时则代翻。
     * 返回非空表示刚发生了一次自动翻牌，调用方需广播 FLIP_DONE{auto:true}。
     */
    public synchronized Optional<HalliGalliBoard.FlipOutcome> autoFlip(long now) {
        if (board == null || getStatus() != RoomStatus.PLAYING) return Optional.empty();
        if (now < flipDeadlineAt) return Optional.empty();
        Integer seat = board.flipSeatNo();
        if (seat == null) {
            resetFlipDeadline();
            return Optional.empty();
        }
        HalliGalliBoard.FlipOutcome outcome = flip(seat, true);
        if (!outcome.ok()) {
            // 极小概率：该座位刚被判定出局，推迟一轮再试，避免每个 tick 重试
            flipDeadlineAt = now + 1_000;
            return Optional.empty();
        }
        return Optional.of(outcome);
    }

    /** 终局：状态置 FINISHED、清倒计时，并只落一次名次（board 内部可能先置 finished） */
    private void finishIfNeeded() {
        if (board == null || !board.isFinished()) return;
        if (getStatus() == RoomStatus.FINISHED) return;
        this.status = RoomStatus.FINISHED;
        this.flipDeadlineAt = 0;
        try {
            recorder.onFinish(this, board.rankings());
        } catch (Exception e) {
            log.error("房间 {} 终局落库失败（对局不受影响）: {}", getRoomId(), e.getMessage(), e);
        }
        log.info("房间 {} 终局", getRoomId());
    }

    private void resetFlipDeadline() {
        this.flipDeadlineAt = System.currentTimeMillis() + FLIP_TIMEOUT_MS;
    }

    // ---- 对局落库（M5） ----

    /** 结算成功才调用：stateVersion 单调递增，action_id 由版本号补，保证唯一键可用 */
    private void logAction(String actionType, int seatNo, String paramsJson) {
        int version = stateVersion.incrementAndGet();
        Long currentMatch = matchId;
        if (currentMatch == null) return;
        try {
            recorder.onAction(this, new HalliGalliActionLogBO(currentMatch, flipCount, seatNo,
                userIdOfSeat(seatNo), actionType, "srv-" + version, paramsJson, version));
        } catch (Exception e) {
            log.error("房间 {} 行动日志落库失败（对局不受影响）: {}", getRoomId(), e.getMessage(), e);
        }
    }

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
    public synchronized List<HalliGalliPlayerRef> playerRefs() {
        List<HalliGalliPlayerRef> refs = new ArrayList<>(seats.size());
        for (Map.Entry<Integer, String> e : seats.entrySet()) {
            refs.add(new HalliGalliPlayerRef(e.getKey(), e.getValue(), boundUserIdOf(e.getValue())));
        }
        return refs;
    }

    private long userIdOfSeat(int seatNo) {
        String name = seats.get(seatNo);
        return name == null ? -(long) seatNo : boundUserIdOf(name);
    }

    /** 翻牌参数（复盘只用，值域都是内部编码，无需转义） */
    private String flipParams(HalliGalliBoard.FlipOutcome o, boolean auto) {
        String card = o.card() == null ? "null"
            : "{\"fruit\":\"" + o.card().fruitCode() + "\",\"count\":" + o.card().count() + "}";
        return "{\"auto\":" + auto + ",\"card\":" + card + ",\"playerOut\":" + o.playerOut()
            + ",\"nextSeatNo\":" + (o.nextSeatNo() == null ? "null" : o.nextSeatNo()) + "}";
    }

    /** 抢铃参数（只落已结算的 CORRECT / WRONG） */
    private String ringParams(HalliGalliBoard.RingOutcome o) {
        StringBuilder penalties = new StringBuilder("[");
        for (int i = 0; i < o.penalties().size(); i++) {
            HalliGalliBoard.Penalty p = o.penalties().get(i);
            if (i > 0) penalties.append(',');
            penalties.append("{\"seatNo\":").append(p.seatNo()).append(",\"cards\":").append(p.cards()).append('}');
        }
        penalties.append(']');
        return "{\"status\":\"" + o.status().name() + "\",\"cardsTaken\":" + o.cardsTaken()
            + ",\"beneficiarySeat\":" + (o.beneficiarySeat() == null ? "null" : o.beneficiarySeat())
            + ",\"penalties\":" + penalties
            + ",\"reason\":" + (o.reason() == null ? "null" : "\"" + o.reason() + "\"") + "}";
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

    public int seatedCount() {
        return seats.size();
    }

    public List<String> seatOrder() {
        return new ArrayList<>(seats.values());
    }

    public HalliGalliRoomVO toVO(String viewerName) {
        List<HalliGalliRoomVO.SeatVO> seatVOs = new ArrayList<>(maxPlayers);
        for (int no = 1; no <= maxPlayers; no++) {
            String name = seats.get(no);
            seatVOs.add(new HalliGalliRoomVO.SeatVO(no, name,
                name != null && isConnected(name),
                name != null && isAfk(name)));
        }
        return new HalliGalliRoomVO(getRoomId(), getHostName(), maxPlayers, getStatus(), seatVOs,
            seatOf(viewerName), afkPlayers());
    }

    /** 全量快照（D6）：等待中输出座位，对局中输出每人顶牌/张数与桌面计数 */
    public synchronized HalliGalliSnapshotVO snapshot() {
        if (board == null) {
            List<HalliGalliPlayerVO> players = new ArrayList<>();
            for (Map.Entry<Integer, String> e : seats.entrySet()) {
                players.add(new HalliGalliPlayerVO(e.getKey(), e.getValue(),
                    isConnected(e.getValue()), isAfk(e.getValue()), false, 0, 0, 0, null));
            }
            return new HalliGalliSnapshotVO(getStatus().name(), null, 0, emptyTotals(), players, null);
        }

        List<HalliGalliPlayerVO> players = new ArrayList<>(board.players().size());
        for (HalliGalliPlayer p : board.players()) {
            players.add(new HalliGalliPlayerVO(p.getSeatNo(), p.getName(),
                isConnected(p.getName()), isAfk(p.getName()), p.isOut(),
                p.deckSize(), p.faceUpSize(), p.totalCards(), cardVO(p.topCard())));
        }
        return new HalliGalliSnapshotVO(getStatus().name(), board.flipSeatNo(), flipDeadlineAt,
            totalsVO(), players, ringVO(board.lastRing()));
    }

    /** 最近一次抢铃结果（下行 RING_RESULT 用），无对局返回 null */
    public HalliGalliRingVO lastRingVO() {
        return board == null ? null : ringVO(board.lastRing());
    }

    private Map<String, Integer> totalsVO() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        Map<Fruit, Integer> counts = board.visibleCounts();
        for (Fruit fruit : Fruit.values()) {
            totals.put(fruit.getCode(), counts.getOrDefault(fruit, 0));
        }
        return totals;
    }

    private Map<String, Integer> emptyTotals() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Fruit fruit : Fruit.values()) totals.put(fruit.getCode(), 0);
        return totals;
    }

    private HalliGalliCardVO cardVO(org.dromara.halligalli.domain.HalliGalliCard card) {
        return card == null ? null : new HalliGalliCardVO(card.fruitCode(), card.count());
    }

    private HalliGalliRingVO ringVO(HalliGalliBoard.RingOutcome outcome) {
        if (outcome == null) return null;
        List<HalliGalliRingVO.PenaltyVO> penalties = new ArrayList<>();
        for (HalliGalliBoard.Penalty p : outcome.penalties()) {
            penalties.add(new HalliGalliRingVO.PenaltyVO(p.seatNo(), p.cards()));
        }
        return new HalliGalliRingVO(outcome.status().name(), outcome.seatNo(), outcome.cardsTaken(),
            outcome.beneficiarySeat(), penalties, outcome.reason());
    }

    private Integer firstFreeSeat() {
        for (int no = 1; no <= maxPlayers; no++) {
            if (!seats.containsKey(no)) return no;
        }
        return null;
    }
}
