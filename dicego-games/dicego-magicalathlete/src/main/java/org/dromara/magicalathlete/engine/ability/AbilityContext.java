package org.dromara.magicalathlete.engine.ability;

import org.dromara.magicalathlete.engine.RaceSession;
import org.dromara.magicalathlete.engine.RaceTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 能力结算上下文：一次事件触发时的可变比赛状态。
 *
 * <p>handler 只读本对象并产出 {@link Effect} 列表；状态变更只能由 {@link EffectPipeline} 执行指令完成。
 * 这样同一批指令可重放，且循环检测能拿到完整的状态指纹。
 *
 * <p>这里复用 {@link RaceSession.RacerState} 作为初始快照来源，结算后由房间把
 * {@link #positions()} / {@link #bronzeBySeat()} 等写回 RaceSession（M3 接入点）。
 */
public final class AbilityContext {

    /** 能力可见的可变座位状态 */
    public static final class Seat {
        public final int seatNo;
        public final String name;
        public final String racerKey;
        public int position;
        public boolean tripped;
        public int bronze;
        public int score;
        public int finishRank;
        public boolean eliminated;

        /** M5 copy 委托：实际取 handler 的 key（默认=自身 racerKey）；copyFromSeat 为被复制者座位 */
        public String effectiveKey;
        public Integer copyFromSeat;

        Seat(RaceSession.RacerState s) {
            this.seatNo = s.seatNo();
            this.name = s.name();
            this.racerKey = s.racerKey();
            this.position = s.position();
            this.tripped = s.tripped();
            this.bronze = s.bronze();
            // 回读已累积的能力分：赛前 +4 与跨回合计分必须延续，否则每次 fire 后被清零
            this.score = s.extraScore();
            this.finishRank = s.finishRank();
            this.eliminated = false;
            this.effectiveKey = s.racerKey();
            this.copyFromSeat = null;
        }
    }

    private final int raceNo;
    private final RaceTrack.Side side;
    private final Map<Integer, Seat> seats = new LinkedHashMap<>();
    private final List<Integer> clockwiseOrder;

    /** 服务端最终主骰点数（D4，不可篡改），供尺蠖/跟班/天才等按点数判定 */
    private final int finalDice;
    /** 本次移动越过的座位集合（ON_PASS 事件参数，由 RaceSession 写入） */
    private final Set<Integer> passedSeats = new LinkedHashSet<>();
    /** 本次停格位置（ON_STOP 事件参数，RaceSession 写入） */
    private int stopSpace = -1;
    /** 当前行动座位（由调用方在 fire 前设置，供 handler 推断"自己/别人"） */
    private Integer currentActor;
    /** ON_ABILITY_FIRED 的发动者座位（供蹭蹭怪排除自己） */
    private Integer abilityFiredBy;
    /** 当前正在执行哪个座位的 handler（区别于 currentActor 掷骰者；避免对手座位的能力被误触发） */
    private Integer handlerSeat;

    /** 主移动基数：初始等于最终骰点，可被 REPLACE_BASE / MODIFY_MOVE 改写 */
    private int base;
    private int modifier;
    private boolean mainMoveCancelled;
    private boolean extraTurnGranted;
    private Integer stolenNextActor;

    /** M4：玩家在 ROLL 前声明的回合意图（可选能力），NONE 表示放弃全部可选能力 */
    private TurnIntent intent = TurnIntent.NONE;
    /** M4：本回合起点位置，供起哄者(AFTER_TURN)判定位移≤1 */
    private int turnStartPosition;

    public AbilityContext(int raceNo, List<RaceSession.RacerState> states, List<Integer> clockwiseOrder, int dice) {
        this(raceNo, RaceTrack.sideOfRace(raceNo), states, clockwiseOrder, dice);
    }

    /** 直接指定赛道面（RaceSession 已知 side，避免反推 raceNo） */
    public AbilityContext(int raceNo, RaceTrack.Side side, List<RaceSession.RacerState> states,
                          List<Integer> clockwiseOrder, int dice) {
        this.raceNo = raceNo;
        this.side = side;
        this.finalDice = dice;
        for (RaceSession.RacerState s : states) {
            Seat seat = new Seat(s);
            if (seats.putIfAbsent(seat.seatNo, seat) != null) {
                throw new IllegalArgumentException("能力上下文中座位重复: " + seat.seatNo);
            }
        }
        this.clockwiseOrder = List.copyOf(clockwiseOrder);
        this.base = dice;
        this.modifier = 0;
        this.turnStartPosition = states.isEmpty() ? 0 : states.get(0).position();
    }

    public int raceNo() {
        return raceNo;
    }

    public RaceTrack.Side side() {
        return side;
    }

    public int finalDice() {
        return finalDice;
    }

    public Set<Integer> passedSeats() {
        return java.util.Collections.unmodifiableSet(passedSeats);
    }

    public AbilityContext markPassed(int seatNo) {
        passedSeats.add(seatNo);
        return this;
    }

    public int stopSpace() {
        return stopSpace;
    }

    public AbilityContext setStopSpace(int space) {
        this.stopSpace = space;
        return this;
    }

    public Integer currentActor() {
        return currentActor;
    }

    public AbilityContext setCurrentActor(int seatNo) {
        this.currentActor = seatNo;
        return this;
    }

    public Integer abilityFiredBy() {
        return abilityFiredBy;
    }

    public AbilityContext setAbilityFiredBy(int seatNo) {
        this.abilityFiredBy = seatNo;
        return this;
    }

    public Integer handlerSeat() {
        return handlerSeat;
    }

    public AbilityContext setHandlerSeat(int seatNo) {
        this.handlerSeat = seatNo;
        return this;
    }

    public List<Integer> clockwiseOrder() {
        return clockwiseOrder;
    }

    /** 主移动最终距离 = (基数 + 修正)，下限 0；被取消时为 0 */
    public int mainMoveDistance() {
        return mainMoveCancelled ? 0 : Math.max(0, base + modifier);
    }

    public int base() {
        return base;
    }

    public int modifier() {
        return modifier;
    }

    public boolean mainMoveCancelled() {
        return mainMoveCancelled;
    }

    public boolean extraTurnGranted() {
        return extraTurnGranted;
    }

    public Integer stolenNextActor() {
        return stolenNextActor;
    }

    public TurnIntent intent() {
        return intent;
    }

    public AbilityContext setIntent(TurnIntent intent) {
        this.intent = intent == null ? TurnIntent.NONE : intent;
        return this;
    }

    public int turnStartPosition() {
        return turnStartPosition;
    }

    public AbilityContext setTurnStartPosition(int pos) {
        this.turnStartPosition = pos;
        return this;
    }

    void replaceBase(int value) {
        this.base = value;
    }

    void addModifier(int delta) {
        this.modifier += delta;
    }

    void cancelMainMove() {
        this.mainMoveCancelled = true;
    }

    void grantExtraTurn() {
        this.extraTurnGranted = true;
    }

    void stealNextActor(int seatNo) {
        this.stolenNextActor = seatNo;
    }

    public Map<Integer, Seat> seats() {
        return seats;
    }

    public Seat seat(int seatNo) {
        return seats.get(seatNo);
    }

    // ---- M5 copy 委托 ----

    /** 注入一名座位的委托目标：effectiveKey 为被复制能力的 racerKey，sourceSeat 为被复制者座位 */
    public AbilityContext delegate(int seatNo, String effectiveKey, int sourceSeat) {
        Seat s = seats.get(seatNo);
        if (s != null) {
            s.effectiveKey = effectiveKey;
            s.copyFromSeat = sourceSeat;
        }
        return this;
    }

    /** 管线取 handler 时使用的 key：优先委托目标，否则自身 racerKey */
    public String effectiveKeyOf(Seat s) {
        return s.effectiveKey == null ? s.racerKey : s.effectiveKey;
    }

    /** 除指定座位外、按顺时针顺序排列的对手（从其后一位开始） */
    public List<Seat> othersClockwise(int actorSeatNo) {
        List<Seat> out = new ArrayList<>();
        int idx = clockwiseOrder.indexOf(actorSeatNo);
        if (idx < 0) return out;
        for (int i = 1; i < clockwiseOrder.size(); i++) {
            Seat s = seats.get(clockwiseOrder.get((idx + i) % clockwiseOrder.size()));
            if (s != null && !s.eliminated) out.add(s);
        }
        return out;
    }

    /** 当前位置在该格的未淘汰座位 */
    public List<Seat> occupants(int space) {
        List<Seat> out = new ArrayList<>();
        for (Seat s : seats.values()) {
            if (!s.eliminated && s.position == space) out.add(s);
        }
        return out;
    }

    /** 最终名次：finishRank 优先，未冲线按位置倒序；用于领先/垫底/复制目标判定 */
    public List<Seat> ranked() {
        List<Seat> out = new ArrayList<>(seats.values());
        out.sort(Comparator
            .comparingInt((Seat s) -> s.finishRank == 0 ? Integer.MAX_VALUE : s.finishRank)
            .thenComparing(Comparator.comparingInt((Seat s) -> s.position).reversed())
            .thenComparingInt((Seat s) -> s.seatNo));
        return out;
    }

    /** 独立领先者：第一名且严格领先第二名；无则返回 null */
    public Seat soleLeader() {
        List<Seat> r = ranked();
        if (r.size() < 2) return r.isEmpty() ? null : r.get(0);
        Seat first = r.get(0), second = r.get(1);
        boolean firstRank = first.finishRank > 0 && first.finishRank != second.finishRank;
        boolean firstPos = first.finishRank == 0 && first.position > second.position;
        return (firstRank || firstPos) ? first : null;
    }

    /** 独立垫底者：最后一名且严格落后倒数第二；无则返回 null */
    public Seat soleTrailer() {
        List<Seat> r = ranked();
        if (r.size() < 2) return r.isEmpty() ? null : r.get(0);
        Seat last = r.get(r.size() - 1), prev = r.get(r.size() - 2);
        boolean lastPos = last.finishRank == 0 && last.position < prev.position;
        return lastPos ? last : null;
    }

    public Map<Integer, Integer> positions() {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        seats.forEach((k, s) -> out.put(k, s.position));
        return out;
    }

    public Map<Integer, Integer> bronzeBySeat() {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        seats.forEach((k, s) -> out.put(k, s.bronze));
        return out;
    }

    public Map<Integer, Integer> scoreBySeat() {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        seats.forEach((k, s) -> out.put(k, s.score));
        return out;
    }

    /** 循环检测指纹：位置 + 状态 + 主移动参数 + 淘汰标记全量纳入（D8） */
    public String stateSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(base).append('|').append(modifier).append('|')
          .append(mainMoveCancelled ? 1 : 0).append('|')
          .append(extraTurnGranted ? 1 : 0).append('|')
          .append(Objects.toString(stolenNextActor, "-")).append(';');
        for (Seat s : seats.values()) {
            sb.append(s.seatNo).append(':').append(s.position).append(':')
              .append(s.tripped ? 1 : 0).append(':').append(s.bronze).append(':')
              .append(s.score).append(':').append(s.finishRank).append(':')
              .append(s.eliminated ? 1 : 0).append(':')
              .append(effectiveKeyOf(s)).append(':').append(Objects.toString(s.copyFromSeat, "-"))
              .append(',');
        }
        return sb.toString();
    }
}
