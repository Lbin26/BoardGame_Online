package org.dromara.magicalathlete.engine.ability;

import java.util.List;

/**
 * M4 第二批能力：无需玩家二次选择、可由纯 handler + 现有事件表达的一批。
 *
 * <p>与 {@link StandardAbilities} 合并进同一注册表。涵盖停格干扰（鸡脚屋/巨婴/大嘴怪）、
 * 距离修正（教练/派对动物/较真怪/啦啦队长/火箭科学家）、骰点反应（天才/船长）、
 * 计分（起哄者/可爱倒霉蛋）。warp/copy 与重掷类见 {@code ChoiceAbilities}（M4 后半）。
 *
 * <p>约定同 M3：{@code handlerSeat} 是能力归属者，{@code currentActor} 是掷骰者。
 */
public final class ExtendedAbilities {

    private ExtendedAbilities() {
    }

    public static AbilityRegistry register(AbilityRegistry r) {
        registerStopInterrupts(r);
        registerModifiers(r);
        registerDiceReactions(r);
        registerScoring(r);
        return r;
    }

    private static boolean isActor(AbilityContext ctx) {
        Integer h = ctx.handlerSeat(), a = ctx.currentActor();
        return h != null && a != null && h.intValue() == a.intValue();
    }

    private static boolean isOther(AbilityContext ctx) {
        Integer h = ctx.handlerSeat(), a = ctx.currentActor();
        return h != null && (a == null || h.intValue() != a.intValue());
    }

    // ---- 停格干扰：ON_STOP ----

    private static void registerStopInterrupts(AbilityRegistry r) {
        // 鸡脚屋：停入恰有一名对手的格（含被停入同时到达），令对方绊倒
        r.register("baba_yaga", AbilityEvent.ON_STOP, ctx -> {
            if (ctx.stopSpace() <= 0) return List.of();
            List<AbilityContext.Seat> here = ctx.occupants(ctx.stopSpace());
            int h = ctx.handlerSeat();
            // 鸡脚屋本人必须参与该格（自己停入或被停入）
            boolean involved = here.stream().anyMatch(s -> s.seatNo == h);
            if (!involved || here.size() < 2) return List.of();
            AbilityContext.Seat victim = here.stream()
                .filter(s -> s.seatNo != h && s.finishRank == 0 && !s.eliminated)
                .findFirst().orElse(null);
            if (victim == null) return List.of();
            return List.of(new Effect(EffectType.TRIP, h, victim.seatNo, 0,
                "baba_yaga@seat" + h + ":trip@seat" + victim.seatNo));
        });

        // 大嘴怪：自己停入恰有一名对手的格时淘汰该对手
        r.register("mouth", AbilityEvent.ON_STOP, ctx -> {
            if (!isActor(ctx) || ctx.stopSpace() <= 0) return List.of();
            List<AbilityContext.Seat> here = ctx.occupants(ctx.stopSpace());
            if (here.size() != 2) return List.of();
            int h = ctx.handlerSeat();
            AbilityContext.Seat victim = here.stream()
                .filter(s -> s.seatNo != h && s.finishRank == 0 && !s.eliminated)
                .findFirst().orElse(null);
            if (victim == null) return List.of();
            return List.of(new Effect(EffectType.ELIMINATE, h, victim.seatNo, 0,
                "mouth@seat" + h + ":eat@seat" + victim.seatNo));
        });

        // 巨婴：非起点禁止共格，把后来者放到其后一格（+1）
        r.register("huge_baby", AbilityEvent.ON_STOP, ctx -> {
            if (ctx.stopSpace() <= 0) return List.of();
            int h = ctx.handlerSeat();
            List<AbilityContext.Seat> here = ctx.occupants(ctx.stopSpace());
            AbilityContext.Seat self = ctx.seat(h);
            if (self == null || here.size() < 2) return List.of();
            // 巨婴已在格中，后来者（当前行动者）被挤到后一格
            boolean selfHere = here.stream().anyMatch(s -> s.seatNo == h);
            if (!selfHere || isActor(ctx)) return List.of();
            Integer actor = ctx.currentActor();
            AbilityContext.Seat newcomer = actor == null ? null : ctx.seat(actor);
            if (newcomer == null || newcomer.position != ctx.stopSpace()) return List.of();
            return List.of(new Effect(EffectType.MOVE, h, newcomer.seatNo, 1,
                "huge_baby@seat" + h + ":push@seat" + newcomer.seatNo));
        });
    }

    // ---- 距离修正：BEFORE_MAIN_MOVE ----

    private static void registerModifiers(AbilityRegistry r) {
        // 教练：所在格全部运动员（含自己）主移动 +1
        r.register("coach", AbilityEvent.BEFORE_MAIN_MOVE, ctx -> {
            Integer actor = ctx.currentActor();
            if (actor == null) return List.of();
            AbilityContext.Seat coach = ctx.seat(ctx.handlerSeat());
            AbilityContext.Seat mover = ctx.seat(actor);
            if (coach == null || mover == null || coach.finishRank > 0) return List.of();
            if (coach.position != mover.position) return List.of();
            return List.of(new Effect(EffectType.MODIFY_MOVE, coach.seatNo, actor, 1,
                "coach@seat" + coach.seatNo + ":+1@seat" + actor));
        });

        // 派对动物：同格每位其他选手使主移动 +1（多在 BEFORE_MAIN_MOVE 叠加）
        r.register("party_animal", AbilityEvent.BEFORE_MAIN_MOVE, ctx -> {
            if (!isOther(ctx)) return List.of();
            Integer actor = ctx.currentActor();
            AbilityContext.Seat self = ctx.seat(ctx.handlerSeat());
            AbilityContext.Seat mover = actor == null ? null : ctx.seat(actor);
            if (self == null || mover == null || self.position != mover.position) return List.of();
            return List.of(new Effect(EffectType.MODIFY_MOVE, self.seatNo, actor, 1,
                "party_animal@seat" + self.seatNo + ":+1@seat" + actor));
        });

        // 较真怪：别人任何移动若会越过终点（过量冲线）则整次取消
        r.register("stickler", AbilityEvent.BEFORE_MAIN_MOVE, ctx -> {
            if (!isOther(ctx)) return List.of();
            Integer actor = ctx.currentActor();
            AbilityContext.Seat mover = actor == null ? null : ctx.seat(actor);
            if (mover == null || mover.finishRank > 0) return List.of();
            int landing = mover.position + ctx.mainMoveDistance();
            if (landing <= 30) return List.of();
            int h = ctx.handlerSeat();
            return List.of(new Effect(EffectType.CANCEL_MOVE, h, actor, 0,
                "stickler@seat" + h + ":cancel-overshoot@seat" + actor));
        });

        // 火箭科学家：主骰翻倍（基数 ×2）；完成后自己绊倒（禁用于大长腿等重复翻倍的叠加）
        r.register("rocket_scientist", AbilityEvent.ROLL_REPLACE, ctx -> {
            if (!isActor(ctx) || !ctx.intent().usePrimary()) return List.of();
            int h = ctx.handlerSeat();
            int doubled = ctx.finalDice() * 2;
            return List.of(new Effect(EffectType.REPLACE_BASE, h, h, doubled,
                "rocket_scientist@seat" + h + ":double=" + ctx.finalDice() + "->" + doubled));
        });
        r.register("rocket_scientist", AbilityEvent.AFTER_TURN, ctx -> {
            if (!isActor(ctx) || !ctx.intent().usePrimary()) return List.of();
            int h = ctx.handlerSeat();
            return List.of(new Effect(EffectType.TRIP, h, h, 0, "rocket_scientist@seat" + h + ":self-trip"));
        });

        // 啦啦队长：可令所有并列最后名 +2，然后自己 +1 主移动（只加一次）
        r.register("cheerleader", AbilityEvent.BEFORE_MAIN_MOVE, ctx -> {
            if (!isActor(ctx) || !ctx.intent().usePrimary()) return List.of();
            int h = ctx.handlerSeat();
            AbilityContext.Seat self = ctx.seat(h);
            if (self == null) return List.of();
            List<AbilityContext.Seat> ranked = ctx.ranked();
            if (ranked.isEmpty()) return List.of();
            int lastPos = ranked.get(ranked.size() - 1).position;
            java.util.List<Effect> out = new java.util.ArrayList<>();
            int lastRank = ranked.get(ranked.size() - 1).finishRank;
            for (AbilityContext.Seat s : ranked) {
                if (s.seatNo == h || s.finishRank > 0) continue;
                if (s.position == lastPos && lastRank == 0) {
                    out.add(new Effect(EffectType.MOVE, h, s.seatNo, 2,
                        "cheerleader@seat" + h + ":cheer@seat" + s.seatNo));
                }
            }
            out.add(new Effect(EffectType.MODIFY_MOVE, h, h, 1,
                "cheerleader@seat" + h + ":self+1"));
            return out;
        });
    }

    // ---- 骰点反应：DICE_RESULT ----

    private static void registerDiceReactions(AbilityRegistry r) {
        // 天才：猜中最终主骰点数获得额外回合（猜 1 时船长抢先——M4 简化为同回合判定）
        r.register("genius", AbilityEvent.DICE_RESULT, ctx -> {
            if (!isActor(ctx) || !ctx.intent().hasGuess()) return List.of();
            if (ctx.intent().guessDice() != ctx.finalDice()) return List.of();
            int h = ctx.handlerSeat();
            return List.of(new Effect(EffectType.EXTRA_TURN, h, h, 0,
                "genius@seat" + h + ":guess=" + ctx.finalDice()));
        });

        // 船长：任一最终主骰为 1 时，下一个行动者改为自己
        r.register("skipper", AbilityEvent.DICE_RESULT, ctx -> {
            if (!isOther(ctx) || ctx.finalDice() != 1) return List.of();
            int h = ctx.handlerSeat();
            return List.of(new Effect(EffectType.STEAL_TURN, h, h, 0,
                "skipper@seat" + h + ":steal@" + ctx.finalDice()));
        });
    }

    // ---- 计分 ----

    private static void registerScoring(AbilityRegistry r) {
        // 起哄者：任一选手回合结束位移 ≤1 时自己 +2
        r.register("heckler", AbilityEvent.AFTER_TURN, ctx -> {
            Integer actor = ctx.currentActor();
            if (actor == null) return List.of();
            AbilityContext.Seat mover = ctx.seat(actor);
            if (mover == null || mover.finishRank > 0) return List.of();
            int start = ctx.turnStartPosition();
            if (Math.abs(mover.position - start) > 1) return List.of();
            int h = ctx.handlerSeat();
            return List.of(new Effect(EffectType.SCORE, h, h, 2, "heckler@seat" + h + ":+2"));
        });

        // 可爱的倒霉蛋：自己回合初独自垫底获 1 分（绊倒不影响）
        // 必须限定 handlerSeat == currentActor，否则别人每次掷骰都会触发，变成每轮多次得分
        r.register("lovable_loser", AbilityEvent.DICE_RESULT, ctx -> {
            if (!isActor(ctx)) return List.of();
            int h = ctx.handlerSeat();
            AbilityContext.Seat self = ctx.seat(h);
            if (self == null || self.finishRank > 0) return List.of();
            AbilityContext.Seat trailer = ctx.soleTrailer();
            if (trailer == null || trailer.seatNo != h) return List.of();
            return List.of(new Effect(EffectType.SCORE, h, h, 1, "lovable_loser@seat" + h + ":+1"));
        });
    }
}
