package org.dromara.magicalathlete.engine;

import org.dromara.magicalathlete.engine.ability.AbilityContext;
import org.dromara.magicalathlete.engine.ability.AbilityEvent;
import org.dromara.magicalathlete.engine.ability.AbilityRegistry;
import org.dromara.magicalathlete.engine.ability.CopyDelegation;
import org.dromara.magicalathlete.engine.ability.Effect;
import org.dromara.magicalathlete.engine.ability.EffectPipeline;
import org.dromara.magicalathlete.engine.ability.TurnIntent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单场比赛会话：主要移动、停格结算、冲线判定（纯逻辑，骰点由外部注入——D4 服务端生成）。
 *
 * <p>不含能力系统（M3）。绊倒只跳过下一次主要移动，不禁用其它结算；箭头格是一次独立移动，
 * 可链式触发落点效果；终点 30 不可停留，超过按 30 计；第二名冲线立即结束本场。
 */
public class RaceSession {

    public record Entrant(int seatNo, String name, String racerKey) {
    }

    /** 对外快照：finishRank=0 表示未冲线；extraScore 为能力额外分（M3） */
    public record RacerState(int seatNo, String name, String racerKey, int position,
                             boolean tripped, int finishRank, int bronze, int extraScore) {
        /** 兼容 M2 老构造（无能力分） */
        public RacerState(int seatNo, String name, String racerKey, int position,
                          boolean tripped, int finishRank, int bronze) {
            this(seatNo, name, racerKey, position, tripped, finishRank, bronze, 0);
        }
    }

    public record Standing(int seatNo, String name, String racerKey, int position, int finishRank, int bronze) {
    }

    public enum RollReason {
        OK, NOT_YOUR_TURN, RACE_FINISHED, INVALID_DICE, TRIPPED_SKIP
    }

    /** 一次掷骰的完整结果，effects 为可落日志的效果指令（停格/绊倒/铜币/冲线/能力链） */
    public record RollOutcome(boolean ok, RollReason reason, int seatNo, int dice, int from, int to,
                              List<String> effects, boolean raceFinished, int finishRank,
                              List<String> abilityChain, boolean chainTruncated) {
        static RollOutcome fail(RollReason reason, int seatNo) {
            return new RollOutcome(false, reason, seatNo, 0, 0, 0, List.of(), false, 0, List.of(), false);
        }

        /** 兼容 M2 老构造子（无能力链） */
        public RollOutcome(boolean ok, RollReason reason, int seatNo, int dice, int from, int to,
                           List<String> effects, boolean raceFinished, int finishRank) {
            this(ok, reason, seatNo, dice, from, to, effects, raceFinished, finishRank, List.of(), false);
        }
    }

    private static class Racer {
        final Entrant entrant;
        int position;
        boolean tripped;
        int finishRank;
        int bronze;
        int extraScore;
        boolean eliminated;

        Racer(Entrant entrant) {
            this.entrant = entrant;
        }
    }

    private final RaceTrack.Side side;
    private final Map<Integer, Racer> racers = new LinkedHashMap<>();
    private final List<Integer> turnOrder;
    /** M3：能力注册表与管线；null 表示不启用能力（M2 老构造子行为） */
    private final AbilityRegistry abilities;
    private final EffectPipeline pipeline;
    /** M5：赛前固定委托（蛋蛋/双胞胎）；模仿猫的动态委托每回合重算 */
    private final Map<Integer, CopyBinding> fixedDelegations;

    /** 赛前委托记录：copySeat 复制了 sourceSeat 的 sourceKey 能力 */
    public record CopyBinding(int copySeat, String sourceKey, int sourceSeat) {
    }

    /** M5 幕后军师：预测窗口（本场首回合掷骰后关闭） */
    private boolean predictionLocked;
    /** M5：预测者座位 → 被预测夺冠座位 */
    private final Map<Integer, Integer> championPredictions = new LinkedHashMap<>();
    /** M5：自猜命中（预测者=冠军）→ 结算时金银同人 */
    private boolean mastermindSelfWin;
    /** M5：本场军师判定是否已执行（避免多次冲线时重复结算） */
    private boolean mastermindResolved;

    private int turnIndex;
    private int finishCount;

    public RaceSession(int raceNo, List<Entrant> entrants) {
        this(RaceTrack.sideOfRace(raceNo), entrants, entrants.stream().map(Entrant::seatNo).sorted().toList());
    }

    /** M3：启用能力的构造子——房间实际对局使用，老构造子保持 M2 行为零回归 */
    public RaceSession(int raceNo, List<Entrant> entrants, AbilityRegistry abilities) {
        this(RaceTrack.sideOfRace(raceNo), entrants, entrants.stream().map(Entrant::seatNo).sorted().toList(), abilities);
    }

    public RaceSession(RaceTrack.Side side, List<Entrant> entrants, List<Integer> turnOrder) {
        this(side, entrants, turnOrder, null);
    }

    public RaceSession(RaceTrack.Side side, List<Entrant> entrants, List<Integer> turnOrder, AbilityRegistry abilities) {
        this(side, entrants, turnOrder, abilities, List.of());
    }

    /** M5：带赛前固定委托的构造子（蛋蛋/双胞胎在本场的能力来源）；老构造子委托为空，零回归 */
    public RaceSession(int raceNo, List<Entrant> entrants, AbilityRegistry abilities,
                       List<CopyBinding> fixedDelegations) {
        this(RaceTrack.sideOfRace(raceNo), entrants,
            entrants.stream().map(Entrant::seatNo).sorted().toList(), abilities, fixedDelegations);
    }

    public RaceSession(RaceTrack.Side side, List<Entrant> entrants, List<Integer> turnOrder,
                       AbilityRegistry abilities, List<CopyBinding> fixedDelegations) {
        this.side = side;
        this.abilities = abilities;
        this.pipeline = abilities == null ? null : new EffectPipeline(abilities);
        Map<Integer, CopyBinding> bySeat = new LinkedHashMap<>();
        if (fixedDelegations != null) {
            for (CopyBinding b : fixedDelegations) bySeat.put(b.copySeat(), b);
        }
        this.fixedDelegations = Map.copyOf(bySeat);
        for (Entrant e : entrants) {
            if (racers.putIfAbsent(e.seatNo(), new Racer(e)) != null) {
                throw new IllegalArgumentException("同一座位不能派两名选手: " + e.seatNo());
            }
        }
        if (!new HashSet<>(turnOrder).equals(racers.keySet()) || turnOrder.size() != racers.size()) {
            throw new IllegalArgumentException("行动顺序必须恰好覆盖所有参赛座位");
        }
        this.turnOrder = List.copyOf(turnOrder);
    }

    public RaceTrack.Side side() {
        return side;
    }

    /**
     * M5：赛前能力结算（BEFORE_RACE，如西西弗斯 +4、蛋蛋复制来的赛前能力）。
     * 每场开始调用一次；模仿猫不刷新委托（规则：不复制赛前能力），只注入蛋蛋/双胞胎的固定委托。
     * 产出的额外分写回各座位 extraScore，最终由 ScoreBoard.awardAbilities 结算。
     *
     * @return 可落日志的能力链 reason 列表
     */
    public synchronized List<String> applyPreRace() {
        if (abilities == null) return List.of();
        AbilityContext ctx = new AbilityContext(0, side, racers(), turnOrder, 0);
        for (CopyBinding b : fixedDelegations.values()) {
            CopyDelegation.bindFixed(ctx, b.copySeat(), b.sourceKey(), b.sourceSeat());
        }
        List<String> chain = new ArrayList<>();
        List<String> effects = new ArrayList<>();
        boolean[] truncated = {false};
        fireAndMerge(ctx, AbilityEvent.BEFORE_RACE, -1, effects, chain, truncated);
        applyAbilityState(ctx);
        return chain;
    }

    public int turnIndex() {
        return turnIndex;
    }

    public Integer currentSeatNo() {
        return finished() ? null : turnOrder.get(turnIndex);
    }

    public boolean finished() {
        return finishCount >= 2 || finishCount >= racers.size();
    }

    public int finishCount() {
        return finishCount;
    }

    /** M2 老签名：无意图（等价于放弃全部可选能力） */
    public synchronized RollOutcome roll(int seatNo, int dice) {
        return roll(seatNo, dice, TurnIntent.NONE);
    }

    /** 服务端掷骰后调用：dice ∈ [1,6] 为最终点数（重掷已在房间层完成）；intent 为玩家回合意图 */
    public synchronized RollOutcome roll(int seatNo, int dice, TurnIntent intent) {
        if (finished()) return RollOutcome.fail(RollReason.RACE_FINISHED, seatNo);
        Integer current = currentSeatNo();
        if (current == null || current != seatNo) return RollOutcome.fail(RollReason.NOT_YOUR_TURN, seatNo);
        if (dice < 1 || dice > 6) return RollOutcome.fail(RollReason.INVALID_DICE, seatNo);

        Racer r = racers.get(seatNo);
        List<String> chain = new ArrayList<>();
        boolean[] truncated = {false};

        if (r.tripped) {
            r.tripped = false;
            List<String> effects = new ArrayList<>();
            effects.add("TRIP_SKIP");
            advanceTurn();
            return new RollOutcome(true, RollReason.TRIPPED_SKIP, seatNo, dice,
                r.position, r.position, effects, finished(), r.finishRank, chain, false);
        }

        int from = r.position;
        List<String> effects = new ArrayList<>();

        // ---- M3 能力结算：仅在启用能力注册表的对局中执行 ----
        int moveDistance = dice;
        AbilityContext ctx = null;
        if (abilities != null) {
            ctx = new AbilityContext(0, side, racers(), turnOrder, dice);
            ctx.setCurrentActor(seatNo);
            ctx.setIntent(intent);
            ctx.setTurnStartPosition(from);
            for (int passed : passedSeatsBetween(from, RaceTrack.clamp(from + dice))) {
                ctx.markPassed(passed);
            }
            // M5：先写赛前固定委托（蛋蛋/双胞胎），再按当前排名刷新模仿猫的动态委托
            for (CopyBinding b : fixedDelegations.values()) {
                CopyDelegation.bindFixed(ctx, b.copySeat(), b.sourceKey(), b.sourceSeat());
            }
            CopyDelegation.refreshMimic(ctx);

            // M5 幕后军师："首回合初预测冠军"——军师本场首次带 targetSeat 行动时锁定预测，
            // 只记一次（不强制必须是全场第一手，否则军师非先手时无机会声明）。
            AbilityContext.Seat actorSeat = ctx.seat(seatNo);
            String actorKey = actorSeat == null ? r.entrant.racerKey() : ctx.effectiveKeyOf(actorSeat);
            if (!predictionLocked && "mastermind".equals(actorKey) && intent.hasTargetSeat()) {
                championPredictions.put(seatNo, intent.targetSeat());
                predictionLocked = true;
            }

            fireAndMerge(ctx, AbilityEvent.DICE_RESULT, seatNo, effects, chain, truncated);
            fireAndMerge(ctx, AbilityEvent.ROLL_REPLACE, seatNo, effects, chain, truncated);
            fireAndMerge(ctx, AbilityEvent.BEFORE_MAIN_MOVE, seatNo, effects, chain, truncated);

            moveDistance = ctx.mainMoveDistance();
            applyAbilityState(ctx);
            from = r.position; // 能力可能已移动棋子（如西西弗斯传送回起点）
        }

        // M5 跳跳蟾蜍：委托后 key 若为 leaptoad，主移动改为"只计空格"——占据格跳过不计步，
        // 每跳过一个占据格记一次能力发动（下方补发 ON_ABILITY_FIRED 让蹭蹭怪等联动）。
        String actorKey = r.entrant.racerKey();
        if (ctx != null) {
            AbilityContext.Seat as = ctx.seat(seatNo);
            if (as != null) actorKey = ctx.effectiveKeyOf(as);
        }
        List<Integer> leapSkipped = List.of();
        int to;
        if ("leaptoad".equals(actorKey) && moveDistance != 0) {
            LeapResult lr = computeLeap(from, moveDistance, seatNo);
            to = lr.landing();
            leapSkipped = lr.skippedSeats();
            effects.add("LEAP_MOVE:" + from + "->" + to + ":skip=" + leapSkipped);
        } else {
            to = RaceTrack.clamp(from + moveDistance);
            effects.add("MAIN_MOVE:" + from + "->" + to);
        }
        r.position = to;

        applyStopEffects(r, effects);
        // M5 幕后军师：冠军一旦产生，判定预测是否命中（命中则军师立即获第二并结束比赛）
        resolveMastermind(effects);
        if (ctx != null) {
            // 主移动与停格已改变 racers，必须同步回 ctx 再结算后续事件，
            // 否则 applyAbilityState 会用旧快照覆盖新位置（探针定位到的关键坑）
            syncToContext(ctx);
            ctx.setStopSpace(r.position);
            fireAndMerge(ctx, AbilityEvent.ON_PASS, seatNo, effects, chain, truncated);
            fireAndMerge(ctx, AbilityEvent.ON_STOP, seatNo, effects, chain, truncated);
            applyAbilityState(ctx);

            // M5 跳跳蟾蜍：每跳过一个占据格算一次能力发动（补发 ON_ABILITY_FIRED）
            if (!leapSkipped.isEmpty()) {
                syncToContext(ctx);
                for (int skipped : leapSkipped) {
                    ctx.setAbilityFiredBy(seatNo);
                    fireAndMerge(ctx, AbilityEvent.ON_ABILITY_FIRED, seatNo, effects, chain, truncated);
                }
                applyAbilityState(ctx);
            }

            // M4：回合末事件（起哄者判位移）。写回后再读本回合最终位移。
            syncToContext(ctx);
            fireAndMerge(ctx, AbilityEvent.AFTER_TURN, seatNo, effects, chain, truncated);
            applyAbilityState(ctx);
        }

        int rank = r.finishRank;
        boolean raceFinished = finished();
        // M4 行动权：船长抢来的座位优先；天才猜中获得额外回合则不推进。
        if (!raceFinished) {
            Integer stolen = ctx == null ? null : ctx.stolenNextActor();
            boolean extraTurn = ctx != null && ctx.extraTurnGranted();
            if (stolen != null && racers.containsKey(stolen)
                && racers.get(stolen).finishRank == 0 && !racers.get(stolen).eliminated) {
                setNextActor(stolen);
            } else if (!extraTurn) {
                advanceTurn();
            }
        }
        return new RollOutcome(true, RollReason.OK, seatNo, dice, from, r.position, effects,
            raceFinished, rank, chain, truncated[0]);
    }

    /** 把下一个行动者直接设为指定座位（船长抢行动） */
    private void setNextActor(int seatNo) {
        for (int i = 0; i < turnOrder.size(); i++) {
            if (turnOrder.get(i) == seatNo) {
                turnIndex = i;
                return;
            }
        }
    }

    /** 触发一个能力事件：把效果指令并入 effects 与可重放链；检测截断 */
    private void fireAndMerge(AbilityContext ctx, AbilityEvent event, int actorSeat,
                              List<String> effects, List<String> chain, boolean[] truncated) {
        EffectPipeline.ChainResult result = pipeline.fire(ctx, event, actorSeat);
        for (Effect e : result.applied()) {
            chain.add(e.reason());
            effects.add("ABILITY:" + e.reason());
        }
        if (result.truncated()) {
            truncated[0] = true;
            chain.add("CHAIN_LIMIT:" + result.reason());
            effects.add("ABILITY:CHAIN_LIMIT:" + result.reason());
        }
    }

    /** 把引擎座位的当前状态同步进能力上下文（主移动/停格后调用，避免旧快照覆盖新位置） */
    private void syncToContext(AbilityContext ctx) {
        for (AbilityContext.Seat s : ctx.seats().values()) {
            Racer src = racers.get(s.seatNo);
            if (src == null) continue;
            s.position = src.position;
            s.tripped = src.tripped;
            s.bronze = src.bronze;
            s.finishRank = src.finishRank;
        }
    }

    /** 把能力上下文的状态写回引擎座位（位置/绊倒/铜币/分数/淘汰） */
    private void applyAbilityState(AbilityContext ctx) {
        for (AbilityContext.Seat s : ctx.seats().values()) {
            Racer target = racers.get(s.seatNo);
            if (target == null) continue;
            target.position = s.position;
            target.tripped = s.tripped;
            target.bronze = s.bronze;
            target.extraScore = s.score;
            target.eliminated = s.eliminated;
            if (s.finishRank > 0 && target.finishRank == 0) {
                target.finishRank = s.finishRank;
            }
        }
    }

    /** 计算从 from 到 to 之间（不含两端）越过/被越过的座位，用于 ON_PASS 能力 */
    private Set<Integer> passedSeatsBetween(int from, int to) {
        Set<Integer> passed = new HashSet<>();
        if (from == to) return passed;
        int step = to > from ? 1 : -1;
        for (int p = from + step; p != to; p += step) {
            for (Racer other : racers.values()) {
                if (other.entrant.seatNo() == currentSeatNo()) continue;
                if (other.position == p && other.finishRank == 0 && !other.eliminated) {
                    passed.add(other.entrant.seatNo());
                }
            }
        }
        return passed;
    }

    /** 跳跳蟾蜍移动结果：最终落点 + 跳过的占据座位（用于补发能力发动） */
    private record LeapResult(int landing, List<Integer> skippedSeats) {
    }

    /**
     * M5 跳跳蟾蜍"只计空格"移动：沿 direction 逐格前进，空格消耗 1 步，占据格跳过不计步。
     * 走满 distance 个空格即停；越过起点/终点按赛道边界 clamp。前后皆可（direction 可正可负）。
     */
    private LeapResult computeLeap(int from, int distance, int actorSeat) {
        int dir = distance >= 0 ? 1 : -1;
        int steps = Math.abs(distance);
        int pos = from;
        List<Integer> skipped = new ArrayList<>();
        int guard = 0;
        while (steps > 0 && guard++ < 64) {
            int next = pos + dir;
            if (next < 0 || next > 30) break;
            // 该格是否有其他未淘汰/未冲线选手占据
            boolean occupied = false;
            for (Racer other : racers.values()) {
                if (other.entrant.seatNo() == actorSeat || other.finishRank > 0 || other.eliminated) continue;
                if (other.position == next) {
                    occupied = true;
                    skipped.add(other.entrant.seatNo());
                }
            }
            pos = next;
            if (!occupied) steps--;
        }
        return new LeapResult(RaceTrack.clamp(pos), skipped);
    }

    /** M5 幕后军师判定：冠军产生后检查预测，命中则军师立即获第二（或自猜金银）并结束本场 */
    private void resolveMastermind(List<String> effects) {
        if (mastermindResolved || championPredictions.isEmpty()) return;
        Racer champion = null;
        for (Racer r : racers.values()) {
            if (r.finishRank == 1) {
                champion = r;
                break;
            }
        }
        if (champion == null) return;
        int champSeat = champion.entrant.seatNo();
        boolean hit = false;
        for (Map.Entry<Integer, Integer> e : championPredictions.entrySet()) {
            if (e.getValue() == champSeat) {
                hit = true;
                int predictor = e.getKey();
                if (predictor == champSeat) {
                    mastermindSelfWin = true; // 自猜：冠军本就 rank1，结算时补一份银
                    effects.add("MASTERMIND_SELF_GOLD_SILVER@seat" + predictor);
                } else {
                    Racer p = racers.get(predictor);
                    if (p != null && p.finishRank == 0) {
                        p.finishRank = 2;
                        effects.add("MASTERMIND_SILVER@seat" + predictor);
                    }
                }
            }
        }
        if (hit && finishCount < 2) finishCount = 2; // 命中即结束本场
        mastermindResolved = true;
    }

    /** M5：自猜命中（预测者夺冠）→ 结算时金银同人 */
    public boolean isMastermindSelfWin() {
        return mastermindSelfWin;
    }

    /** M5：本场军师预测（预测者座位 → 被预测座位），供结算/日志读取 */
    public Map<Integer, Integer> championPredictions() {
        return Map.copyOf(championPredictions);
    }

    /** 箭头格 → 独立移动 → 落点效果继续结算；同一格在一次链内不重复触发 */
    private void applyStopEffects(Racer r, List<String> effects) {
        Set<Integer> visited = new HashSet<>();
        for (int guard = 0; guard < 8; guard++) {
            if (RaceTrack.isFinish(r.position) || r.finishRank > 0) break;
            RaceTrack.StopEffect stop = RaceTrack.stopAt(side, r.position);
            if (stop == null || !visited.add(stop.space())) break;
            switch (stop.type()) {
                case "bronze" -> {
                    r.bronze += stop.bronze();
                    effects.add("BRONZE:" + stop.bronze() + "@" + stop.space());
                }
                case "trip" -> {
                    r.tripped = true;
                    effects.add("TRIP@" + stop.space());
                }
                case "move" -> {
                    int from = r.position;
                    r.position = RaceTrack.clamp(from + stop.delta());
                    effects.add("ARROW:" + from + "->" + r.position);
                }
                default -> throw new IllegalStateException("未知停格效果: " + stop.type());
            }
            if (RaceTrack.isFinish(r.position) && r.finishRank == 0) {
                r.finishRank = ++finishCount;
                effects.add("FINISH:" + r.finishRank);
                break;
            }
        }
        // 主要移动本身也可能直接冲线（平缓面无特殊格）
        if (RaceTrack.isFinish(r.position) && r.finishRank == 0) {
            r.finishRank = ++finishCount;
            effects.add("FINISH:" + r.finishRank);
        }
    }

    /** 推进到下一个尚未冲线的座位（冲线者不再行动） */
    private void advanceTurn() {
        for (int i = 1; i <= turnOrder.size(); i++) {
            turnIndex = (turnIndex + 1) % turnOrder.size();
            Racer next = racers.get(turnOrder.get(turnIndex));
            if (next != null && next.finishRank == 0) return;
        }
    }

    public List<RacerState> racers() {
        List<RacerState> list = new ArrayList<>(racers.size());
        racers.values().forEach(r -> list.add(new RacerState(
            r.entrant.seatNo(), r.entrant.name(), r.entrant.racerKey(),
            r.position, r.tripped, r.finishRank, r.bronze, r.extraScore)));
        return list;
    }

    public Map<Integer, Integer> bronzeBySeat() {
        Map<Integer, Integer> map = new HashMap<>();
        racers.forEach((seat, r) -> map.put(seat, r.bronze));
        return map;
    }

    /** 能力额外分（M3）：西西弗斯/尺蠖/跟班/蹭蹭怪/浪漫家等产出，供 ScoreBoard 计分 */
    public Map<Integer, Integer> extraScoreBySeat() {
        Map<Integer, Integer> map = new HashMap<>();
        racers.forEach((seat, r) -> map.put(seat, r.extraScore));
        return map;
    }

    /** 名次：冲线者按 finishRank，未冲线者按位置倒序，位置相同按座位号 */
    public List<Standing> standings() {
        List<Standing> list = new ArrayList<>(racers.size());
        racers.values().forEach(r -> list.add(new Standing(
            r.entrant.seatNo(), r.entrant.name(), r.entrant.racerKey(),
            r.position, r.finishRank, r.bronze)));
        list.sort(Comparator
            .comparingInt((Standing s) -> s.finishRank() == 0 ? Integer.MAX_VALUE : s.finishRank())
            .thenComparing(Comparator.comparingInt(Standing::position).reversed())
            .thenComparingInt(Standing::seatNo));
        return list;
    }
}
