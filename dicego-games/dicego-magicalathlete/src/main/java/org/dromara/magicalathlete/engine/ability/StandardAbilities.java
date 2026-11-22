package org.dromara.magicalathlete.engine.ability;

import java.util.ArrayList;
import java.util.List;

/**
 * M3 首批 12 名代表选手的能力实现（D10：牌池只开放已注册选手）。
 *
 * <p>覆盖五类触发器：
 * <ul>
 *   <li>基数替换：alchemist / legs</li>
 *   <li>距离修正：blimp / gunk / hare</li>
 *   <li>最终骰反应：inchworm / lackey</li>
 *   <li>超越触发：banana / centaur</li>
 *   <li>连锁与计分：scoocher / sisyphus / romantic</li>
 * </ul>
 *
 * <p><b>关键约定</b>：handler 由管线按座位遍历调用，调用前 ctx.handlerSeat 指向"能力归属者"，
 * ctx.currentActor 是"本次掷骰者"。自身能力（炼金/大长腿/飞艇/野兔/西西弗斯）必须判断
 * handlerSeat == currentActor；他人反应能力（尺蠖/跟班/黏液怪/蹭蹭怪）必须判断 handlerSeat != currentActor。
 * 否则坐在场上的同名能力会被误触发（M3 测试定位到的坑）。
 */
public final class StandardAbilities {

    private StandardAbilities() {
    }

    public static AbilityRegistry registry() {
        AbilityRegistry r = new AbilityRegistry();
        registerBaseReplace(r);
        registerMoveModifiers(r);
        registerDiceReactions(r);
        registerPassInterrupts(r);
        registerChainAndScore(r);
        return r;
    }

    /** 首批开放的选手 key（供 D10 牌池过滤） */
    public static final List<String> ENABLED_KEYS = List.of(
        "alchemist", "legs", "blimp", "gunk", "hare", "inchworm", "lackey",
        "banana", "centaur", "scoocher", "sisyphus", "romantic"
    );

    /** 能力归属者是否为当前行动者 */
    private static boolean isActor(AbilityContext ctx) {
        Integer h = ctx.handlerSeat();
        Integer a = ctx.currentActor();
        return h != null && a != null && h.intValue() == a.intValue();
    }

    /** 能力归属者是否为"别人"（非当前行动者） */
    private static boolean isOther(AbilityContext ctx) {
        Integer h = ctx.handlerSeat();
        Integer a = ctx.currentActor();
        return h != null && (a == null || h.intValue() != a.intValue());
    }

    // ---- 基数替换：ROLL_REPLACE（自身能力） ----

    private static void registerBaseReplace(AbilityRegistry r) {
        // 炼金术士：自己掷出 1/2 可把移动基数替换为 4
        r.register("alchemist", AbilityEvent.ROLL_REPLACE, ctx -> {
            if (!isActor(ctx) || (ctx.finalDice() != 1 && ctx.finalDice() != 2)) return List.of();
            int h = ctx.handlerSeat();
            return List.of(new Effect(EffectType.REPLACE_BASE, h, h, 4,
                "alchemist@seat" + h + ":base=" + ctx.finalDice() + "->4"));
        });

        // 大长腿：可不掷骰，主移动改为 5（M3 按强制口径实现；可选放弃留 M4 交互层）
        r.register("legs", AbilityEvent.ROLL_REPLACE, ctx -> {
            if (!isActor(ctx)) return List.of();
            int h = ctx.handlerSeat();
            return List.of(new Effect(EffectType.REPLACE_BASE, h, h, 5,
                "legs@seat" + h + ":base=" + ctx.finalDice() + "->5"));
        });
    }

    // ---- 距离修正：BEFORE_MAIN_MOVE ----

    private static void registerMoveModifiers(AbilityRegistry r) {
        // 飞艇（自身）：回合起点在第二角(15)前 +3，否则 −1
        r.register("blimp", AbilityEvent.BEFORE_MAIN_MOVE, ctx -> {
            if (!isActor(ctx)) return List.of();
            int h = ctx.handlerSeat();
            AbilityContext.Seat self = ctx.seat(h);
            if (self == null || self.finishRank > 0) return List.of();
            int delta = self.position < 15 ? 3 : -1;
            return List.of(new Effect(EffectType.MODIFY_MOVE, h, h, delta,
                "blimp@seat" + h + ":" + delta));
        });

        // 黏液怪（他人）：其他选手主移动 −1，多个叠加
        r.register("gunk", AbilityEvent.BEFORE_MAIN_MOVE, ctx -> {
            if (!isOther(ctx)) return List.of();
            Integer actor = ctx.currentActor();
            if (actor == null) return List.of();
            AbilityContext.Seat target = ctx.seat(actor);
            if (target == null || target.finishRank > 0) return List.of();
            // 每个在场黏液怪贡献 −1：由每个 handlerSeat 各自的 handler 产出一条
            int h = ctx.handlerSeat();
            return List.of(new Effect(EffectType.MODIFY_MOVE, h, actor, -1,
                "gunk@seat" + h + ":-1"));
        });

        // 野兔（自身）：主移动 +2；独自领先则跳过主移动
        r.register("hare", AbilityEvent.BEFORE_MAIN_MOVE, ctx -> {
            if (!isActor(ctx)) return List.of();
            int h = ctx.handlerSeat();
            AbilityContext.Seat self = ctx.seat(h);
            if (self == null || self.finishRank > 0) return List.of();
            if (ctx.soleLeader() == self) {
                return List.of(new Effect(EffectType.CANCEL_MOVE, h, h, 0,
                    "hare@seat" + h + ":solo-lead-skip"));
            }
            return List.of(new Effect(EffectType.MODIFY_MOVE, h, h, 2,
                "hare@seat" + h + ":+2"));
        });
    }

    // ---- 最终骰反应：DICE_RESULT ----

    private static void registerDiceReactions(AbilityRegistry r) {
        // 尺蠖（他人）：别人最终主骰为 1 时取消其主移动，自己 +1
        r.register("inchworm", AbilityEvent.DICE_RESULT, ctx -> {
            if (!isOther(ctx) || ctx.finalDice() != 1) return List.of();
            Integer actor = ctx.currentActor();
            int h = ctx.handlerSeat();
            return List.of(
                new Effect(EffectType.CANCEL_MOVE, h, actor, 0,
                    "inchworm@seat" + h + ":cancel@seat" + actor),
                new Effect(EffectType.SCORE, h, h, 1, "inchworm@seat" + h + ":+1"));
        });

        // 跟班（他人）：别人最终主骰为 6 时自己先 +2
        r.register("lackey", AbilityEvent.DICE_RESULT, ctx -> {
            if (!isOther(ctx) || ctx.finalDice() != 6) return List.of();
            int h = ctx.handlerSeat();
            return List.of(new Effect(EffectType.SCORE, h, h, 2, "lackey@seat" + h + ":+2"));
        });
    }

    // ---- 超越触发：ON_PASS ----

    private static void registerPassInterrupts(AbilityRegistry r) {
        // 香蕉（被动）：被越过时令移动者绊倒
        r.register("banana", AbilityEvent.ON_PASS, ctx -> {
            int h = ctx.handlerSeat();
            if (!ctx.passedSeats().contains(h)) return List.of();
            Integer actor = ctx.currentActor();
            if (actor == null) return List.of();
            return List.of(new Effect(EffectType.TRIP, h, actor, 0,
                "banana@seat" + h + ":trip@seat" + actor));
        });

        // 半人马（被动）：被超越时后退 2 格（不越过起点）
        r.register("centaur", AbilityEvent.ON_PASS, ctx -> {
            int h = ctx.handlerSeat();
            if (!ctx.passedSeats().contains(h)) return List.of();
            AbilityContext.Seat self = ctx.seat(h);
            if (self == null) return List.of();
            int delta = -Math.min(2, self.position);
            if (delta == 0) return List.of();
            return List.of(new Effect(EffectType.MOVE, h, h, delta,
                "centaur@seat" + h + ":" + delta));
        });
    }

    // ---- 连锁与计分 ----

    private static void registerChainAndScore(AbilityRegistry r) {
        // 蹭蹭怪（他人）：别人能力每次实际发动自己 +1，不限每回合一次
        r.register("scoocher", AbilityEvent.ON_ABILITY_FIRED, ctx -> {
            Integer firedBy = ctx.abilityFiredBy();
            int h = ctx.handlerSeat();
            if (firedBy != null && h == firedBy) return List.of();
            return List.of(new Effect(EffectType.SCORE, h, h, 1, "scoocher@seat" + h + ":+1"));
        });

        // 西西弗斯赛前 +4
        r.register("sisyphus", AbilityEvent.BEFORE_RACE, ctx -> {
            int h = ctx.handlerSeat();
            return List.of(new Effect(EffectType.SCORE, h, h, 4, "sisyphus@seat" + h + ":pre+4"));
        });
        // 西西弗斯（自身）：主骰为 6 则代替移动传送回起点并 −1 分
        r.register("sisyphus", AbilityEvent.DICE_RESULT, ctx -> {
            if (!isActor(ctx) || ctx.finalDice() != 6) return List.of();
            int h = ctx.handlerSeat();
            AbilityContext.Seat self = ctx.seat(h);
            List<Effect> out = new ArrayList<>();
            if (self != null && self.position != 0) {
                out.add(new Effect(EffectType.MOVE_TO, h, h, 0, "sisyphus@seat" + h + ":warp0"));
            }
            out.add(new Effect(EffectType.SCORE, h, h, -1, "sisyphus@seat" + h + ":-1"));
            return out;
        });

        // 浪漫家（被动）：有人停入恰有一人的格时自己 +2
        r.register("romantic", AbilityEvent.ON_STOP, ctx -> {
            if (ctx.stopSpace() < 0 || ctx.occupants(ctx.stopSpace()).size() != 1) return List.of();
            int h = ctx.handlerSeat();
            return List.of(new Effect(EffectType.SCORE, h, h, 2,
                "romantic@seat" + h + ":+2@" + ctx.stopSpace()));
        });
    }
}
