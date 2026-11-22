package org.dromara.magicalathlete.engine.ability;

import org.dromara.magicalathlete.engine.RaceTrack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 效果队列执行器（D3 + D8 的核心）。
 *
 * <p>流程：事件触发 → 收集所有 handler 产出的效果 → 按「赛道格 → 当前选手 → 其余顺时针」优先级入队
 * → 逐条执行并修改上下文 → 每次"实际发动"补发 ON_ABILITY_FIRED 形成连锁 →
 * 每步比对「状态指纹 + 剩余队列」→ 全部重复才截断一次，另设 200 条硬上限兜底。
 *
 * <p>绝不按"每能力每回合一次"粗暴截断——那会破坏蹭蹭怪/吸盘鱼的长期联动（D8）。
 */
public final class EffectPipeline {

    /** 服务端硬上限：单条事件链最多执行的效果指令数 */
    public static final int CHAIN_LIMIT = 200;

    private final AbilityRegistry registry;

    public EffectPipeline(AbilityRegistry registry) {
        this.registry = registry;
    }

    /** 链式执行结果：applied 为已执行效果（可直接落 action_log / 播报），truncated 标识是否触发保护 */
    public record ChainResult(List<Effect> applied, boolean truncated, String reason) {
        public boolean empty() {
            return applied.isEmpty();
        }
    }

    /**
     * 触发一个事件并完整结算其连锁。
     *
     * @param ctx       可变上下文（会被原地修改）
     * @param event     触发事件
     * @param actorSeat 当前行动座位（决定优先级归属；赛前事件可传 -1）
     */
    public ChainResult fire(AbilityContext ctx, AbilityEvent event, int actorSeat) {
        List<Effect> applied = new ArrayList<>();
        Deque<Effect> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();

        // 1) 收集本事件所有 handler 产出的效果
        collect(ctx, event, actorSeat, queue);
        // 稳定排序，保证同一优先级内可预测
        List<Effect> initial = new ArrayList<>(queue);
        initial.sort(priorityComparator(ctx, actorSeat));
        queue = new ArrayDeque<>();
        queue.addAll(initial);

        int executed = 0;
        while (!queue.isEmpty()) {
            if (executed >= CHAIN_LIMIT) {
                return new ChainResult(List.copyOf(applied), true, "CHAIN_LIMIT");
            }
            if (isCycle(ctx, queue, seen)) {
                return new ChainResult(List.copyOf(applied), true, "CYCLE");
            }

            Effect effect = queue.removeFirst();
            boolean changed = execute(ctx, effect);
            applied.add(effect);
            executed++;

            // 2) 实际发动才补发 ON_ABILITY_FIRED（蹭蹭怪链）；TRACK/SYSTEM 不视作选手能力
            if (changed && effect.sourceSeat() >= 1) {
                ctx.setAbilityFiredBy(effect.sourceSeat());
                List<Effect> followUps = new ArrayList<>();
                collect(ctx, AbilityEvent.ON_ABILITY_FIRED, effect.sourceSeat(), followUps, null);
                followUps.sort(priorityComparator(ctx, actorSeat));
                queue.addAll(followUps);
            }
        }
        return new ChainResult(List.copyOf(applied), false, null);
    }

    /**
     * 循环判定：只有「完整状态指纹 + 尚未结算的队列」都出现过才算循环（D8）。
     * 这样蹭蹭怪 +1 这种状态持续变化的联动不会被误判。
     */
    private boolean isCycle(AbilityContext ctx, Deque<Effect> queue, Set<String> seen) {
        String signature = ctx.stateSignature() + "#" + queueSignature(queue);
        return !seen.add(signature);
    }

    private String queueSignature(Deque<Effect> queue) {
        StringBuilder sb = new StringBuilder();
        for (Effect e : queue) {
            sb.append(e.type()).append(':').append(e.sourceSeat()).append(':')
              .append(e.targetSeat()).append(':').append(e.amount()).append('|');
        }
        return sb.toString();
    }

    /** 优先级：TRACK(0) → 当前选手(1) → 其余按顺时针位次(2+) → 系统兜底(末尾) */
    private Comparator<Effect> priorityComparator(AbilityContext ctx, int actorSeat) {
        return Comparator.comparingInt((Effect e) -> rank(e.sourceSeat(), ctx, actorSeat))
            .thenComparingInt(Effect::targetSeat)
            .thenComparingInt(Effect::amount);
    }

    private int rank(int sourceSeat, AbilityContext ctx, int actorSeat) {
        if (sourceSeat == Effect.TRACK_SOURCE) return 0;
        if (sourceSeat == actorSeat) return 1;
        if (sourceSeat == Effect.SYSTEM_SOURCE) return 1_000;
        int idx = ctx.clockwiseOrder().indexOf(sourceSeat);
        return 2 + (idx < 0 ? 1_000 : idx);
    }

    /** 收集指定座位集合在 event 上的效果；本重载收集全部座位 */
    private void collect(AbilityContext ctx, AbilityEvent event, int actorSeat, Collection<Effect> out) {
        collect(ctx, event, actorSeat, out, null);
    }

    /**
     * @param onlyRacer 非 null 时只收集该座位（用于 ON_ABILITY_FIRED：只让"别人"响应，排除发动者自己）
     */
    private void collect(AbilityContext ctx, AbilityEvent event, int actorSeat, Collection<Effect> out, Integer onlyRacer) {
        for (AbilityContext.Seat seat : ctx.seats().values()) {
            if (seat.eliminated) continue;
            if (onlyRacer != null && seat.seatNo != onlyRacer) continue;
            // M5 copy 委托：按委托后的 effectiveKey 取 handler，能力归属座位保持不变
            String handlerKey = ctx.effectiveKeyOf(seat);
            for (AbilityHandler handler : registry.get(handlerKey, event)) {
                ctx.setHandlerSeat(seat.seatNo);
                List<Effect> effects = handler.apply(ctx);
                if (effects != null) out.addAll(effects);
            }
        }
    }

    /** 实际执行一条效果并写回上下文；返回是否真的改变了状态 */
    private boolean execute(AbilityContext ctx, Effect e) {
        AbilityContext.Seat target = ctx.seat(e.targetSeat());
        switch (e.type()) {
            case MOVE -> {
                if (target == null || target.eliminated) return false;
                int from = target.position;
                target.position = RaceTrack.clamp(from + e.amount());
                return from != target.position;
            }
            case MOVE_TO -> {
                if (target == null || target.eliminated) return false;
                int to = RaceTrack.clamp(e.amount());
                boolean changed = target.position != to;
                target.position = to;
                return changed;
            }
            case TRIP -> {
                if (target == null || target.eliminated || target.tripped) return false;
                target.tripped = true;
                return true;
            }
            case UNTRIP -> {
                if (target == null || !target.tripped) return false;
                target.tripped = false;
                return true;
            }
            case BRONZE -> {
                if (target == null || target.eliminated) return false;
                target.bronze += e.amount();
                return e.amount() != 0;
            }
            case SCORE -> {
                if (target == null) return false;
                target.score += e.amount();
                return e.amount() != 0;
            }
            case REPLACE_BASE -> {
                if (ctx.base() == e.amount()) return false;
                ctx.replaceBase(e.amount());
                return true;
            }
            case MODIFY_MOVE -> {
                ctx.addModifier(e.amount());
                return e.amount() != 0;
            }
            case CANCEL_MOVE -> {
                if (ctx.mainMoveCancelled()) return false;
                ctx.cancelMainMove();
                return true;
            }
            case ELIMINATE -> {
                if (target == null || target.eliminated) return false;
                target.eliminated = true;
                return true;
            }
            case EXTRA_TURN -> {
                if (ctx.extraTurnGranted()) return false;
                ctx.grantExtraTurn();
                return true;
            }
            case STEAL_TURN -> {
                if (ctx.stolenNextActor() != null) return false;
                ctx.stealNextActor(e.sourceSeat());
                return true;
            }
            case SWAP_POSITION -> {
                AbilityContext.Seat source = ctx.seat(e.sourceSeat());
                AbilityContext.Seat other = ctx.seat(e.targetSeat());
                if (source == null || other == null || source.eliminated || other.eliminated) return false;
                if (source.seatNo == other.seatNo || source.position == other.position) return false;
                int tmp = source.position;
                source.position = other.position;
                other.position = tmp;
                return true;
            }
            default -> throw new IllegalStateException("未知效果类型: " + e.type());
        }
    }
}
