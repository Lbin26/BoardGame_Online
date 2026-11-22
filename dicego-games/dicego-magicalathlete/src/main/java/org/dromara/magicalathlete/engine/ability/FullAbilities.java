package org.dromara.magicalathlete.engine.ability;

import java.util.ArrayList;
import java.util.List;

/**
 * M4 全量注册表：M3 首批 12 名 + M4 第二批 12 名 + 重掷/复制类标记。
 *
 * <p>房间实际对局使用本工厂；{@link StandardAbilities#registry()} 保持 M3 的 12 名
 * 不动，确保 M3 断言（coveredRacerCount==12）零回归。
 *
 * <p>需要玩家二次选择的 warp 类（人字拖/催眠师/电灯泡/吸盘鱼）与 copy 类
 * （模仿猫/蛋蛋/双胞胎）在 {@code ChoiceAbilities} 注册（M4 后半）；重掷类
 * （魔术师/骰子商人）的最终骰点在房间层根据意图决定，不在此处产出效果。
 */
public final class FullAbilities {

    private FullAbilities() {
    }

    public static AbilityRegistry registry() {
        AbilityRegistry r = StandardAbilities.registry();
        ExtendedAbilities.register(r);
        ChoiceAbilities.register(r);
        // M5：copy 三类通过委托实现（无独立 handler），登记为"支持"以开放进牌池；
        // coveredRacerCount 仍按 handler 统计，保持 28 的里程碑断言零回归。
        r.registerDelegated(CopyDelegation.COPYCAT)
         .registerDelegated(CopyDelegation.EGG)
         .registerDelegated(CopyDelegation.TWIN)
         // M5 跳跳蟾蜍：特殊移动在 RaceSession 引擎里实现，无事件 handler
         .registerEngineManaged("leaptoad")
         // M5 幕后军师：预测冠军跨回合判定也在 RaceSession 引擎里实现
         .registerEngineManaged("mastermind");
        return r;
    }

    /** 当前开放的全部选手 key（D10 牌池过滤）：M3 12 + M4 12 + warp 4 */
    public static final List<String> ENABLED_KEYS = buildKeys();

    private static List<String> buildKeys() {
        List<String> out = new ArrayList<>(StandardAbilities.ENABLED_KEYS);
        out.addAll(List.of(
            "baba_yaga", "mouth", "huge_baby", "coach", "party_animal", "stickler",
            "rocket_scientist", "cheerleader", "genius", "skipper", "heckler", "lovable_loser",
            "flip_flop", "hypnotist", "third_wheel", "suckerfish",
            // M5 copy 三类开放（委托实现）+ 跳跳蟾蜍（引擎特殊移动）
            CopyDelegation.COPYCAT, CopyDelegation.EGG, CopyDelegation.TWIN, "leaptoad", "mastermind"
        ));
        return List.copyOf(out);
    }
}
