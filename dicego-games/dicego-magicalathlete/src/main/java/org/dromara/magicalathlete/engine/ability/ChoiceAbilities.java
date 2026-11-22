package org.dromara.magicalathlete.engine.ability;

import java.util.ArrayList;
import java.util.List;

/**
 * M4 第三批：需要玩家二次选择的 warp 类能力（读 {@link TurnIntent}）。
 *
 * <p>意图在 ROLL 前由 USE_OPTIONAL 声明，房间已校验目标合法性；handler 只读意图产出效果。
 * 全部为强制效果，不含"可以放弃"的主观分支——放弃即不声明意图（TurnIntent.NONE）。
 *
 * <p><b>简化说明</b>：吸盘鱼的"同组同时到达、名次在被跟随者之后"按"本回合传送到被跟随者当前格
 * 并取消主移动"近似实现，保留传送不算移动/不触发超越的语义；严格的同步移动留后续版本。
 */
public final class ChoiceAbilities {

    private ChoiceAbilities() {
    }

    public static AbilityRegistry register(AbilityRegistry r) {
        registerWarps(r);
        return r;
    }

    private static boolean isActor(AbilityContext ctx) {
        Integer h = ctx.handlerSeat(), a = ctx.currentActor();
        return h != null && a != null && h.intValue() == a.intValue();
    }

    private static void registerWarps(AbilityRegistry r) {
        // 人字拖：声明目标座位 → 与目标交换位置，替代本次主移动（传送不算移动）
        r.register("flip_flop", AbilityEvent.BEFORE_MAIN_MOVE, ctx -> {
            if (!isActor(ctx) || !ctx.intent().hasTargetSeat()) return List.of();
            int h = ctx.handlerSeat();
            int target = ctx.intent().targetSeat();
            AbilityContext.Seat self = ctx.seat(h);
            AbilityContext.Seat other = ctx.seat(target);
            if (self == null || other == null || self.finishRank > 0 || target == h) return List.of();
            List<Effect> out = new ArrayList<>();
            out.add(new Effect(EffectType.CANCEL_MOVE, h, h, 0,
                "flip_flop@seat" + h + ":cancel-main"));
            out.add(new Effect(EffectType.SWAP_POSITION, h, target, 0,
                "flip_flop@seat" + h + ":swap@seat" + target));
            return out;
        });

        // 催眠师：回合初把一名选手传送到自己所在格（不算移动，不触发超越）
        r.register("hypnotist", AbilityEvent.DICE_RESULT, ctx -> {
            if (!isActor(ctx) || !ctx.intent().hasTargetSeat()) return List.of();
            int h = ctx.handlerSeat();
            int target = ctx.intent().targetSeat();
            AbilityContext.Seat self = ctx.seat(h);
            AbilityContext.Seat other = ctx.seat(target);
            if (self == null || other == null || other.finishRank > 0 || target == h) return List.of();
            return List.of(new Effect(EffectType.MOVE_TO, h, target, self.position,
                "hypnotist@seat" + h + ":warp@seat" + target + "->" + self.position));
        });

        // 电灯泡：回合初传送至恰有两名其他选手的格（之后仍有主移动）
        r.register("third_wheel", AbilityEvent.DICE_RESULT, ctx -> {
            if (!isActor(ctx) || !ctx.intent().hasTargetSpace()) return List.of();
            int h = ctx.handlerSeat();
            int space = ctx.intent().targetSpace();
            List<AbilityContext.Seat> occupants = ctx.occupants(space);
            long others = occupants.stream().filter(s -> s.seatNo != h && s.finishRank == 0).count();
            if (others != 2) return List.of();
            return List.of(new Effect(EffectType.MOVE_TO, h, h, space,
                "third_wheel@seat" + h + ":warp->" + space));
        });

        // 吸盘鱼：本回合传送到被跟随者当前格并取消主移动（简化版，见类注释）
        r.register("suckerfish", AbilityEvent.DICE_RESULT, ctx -> {
            if (!isActor(ctx) || !ctx.intent().hasTargetSeat()) return List.of();
            int h = ctx.handlerSeat();
            int target = ctx.intent().targetSeat();
            AbilityContext.Seat self = ctx.seat(h);
            AbilityContext.Seat other = ctx.seat(target);
            if (self == null || other == null || other.finishRank > 0 || target == h) return List.of();
            List<Effect> out = new ArrayList<>();
            out.add(new Effect(EffectType.CANCEL_MOVE, h, h, 0,
                "suckerfish@seat" + h + ":follow-cancel"));
            out.add(new Effect(EffectType.MOVE_TO, h, h, other.position,
                "suckerfish@seat" + h + ":follow@seat" + target + "->" + other.position));
            return out;
        });
    }
}
