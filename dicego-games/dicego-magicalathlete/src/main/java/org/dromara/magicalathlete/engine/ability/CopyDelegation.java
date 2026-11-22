package org.dromara.magicalathlete.engine.ability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M5 copy 委托解析器：把"谁复制谁"的规则与效果管线解耦（D3 事件注册表的自然延伸）。
 *
 * <p>三种 copy 语义（来源 game.yaml.abilities）：
 * <ul>
 *   <li><b>copycat 模仿猫</b>：持续复制当前领先者能力，并列自选（服务端按座位号最小者定，确定性），
 *       不复制赛前能力（BEFORE_RACE 不委托），冲突时原能力优先（自己已是该能力持有者时不叠加）。</li>
 *   <li><b>egg 蛋蛋</b>：赛前抽三张选一复制其能力（含赛前能力）；候选不回牌堆——由房间在赛前确定，
 *       本类只负责把已选目标写进委托表。</li>
 *   <li><b>twin 双胞胎</b>：赛前复制上一场冠军能力（含赛前能力）——同样由房间确定后写入。</li>
 * </ul>
 *
 * <p><b>防止递归委托</b>：copy 类之间不互相委托（领先者若是 copy 类则跳过），否则会出现
 * copycat→twin→copycat 的键循环，虽然效果管线有 200 条上限也不该让无意义链消耗预算。
 */
public final class CopyDelegation {

    public static final String COPYCAT = "copycat";
    public static final String EGG = "egg";
    public static final String TWIN = "twin";

    private CopyDelegation() {
    }

    /** 判断某 racerKey 是否为 copy 类能力（委托解析时用于防止递归） */
    public static boolean isCopyKey(String key) {
        return COPYCAT.equals(key) || EGG.equals(key) || TWIN.equals(key);
    }

    /**
     * 按当前上下文刷新所有 copycat 座位的动态委托：指向当前领先者的 racerKey。
     * 模仿猫自己不能是领先者时跳过（即自己是第一则不委托，冲突原能力优先）；
     * 领先者本身是 copy 类时找下一名，避免递归。
     *
     * @return 本次实际发生委托的座位 → 被复制者 key（供日志/播报）
     */
    public static Map<Integer, String> refreshMimic(AbilityContext ctx) {
        Map<Integer, String> applied = new HashMap<>();
        // 全场领先者（含 copy 类，用于判断"自己是否第一"）；ranked 已含并列按座位号兜底
        AbilityContext.Seat overallFirst = firstActive(ctx);
        // 可复制的领先者（排除 copy 类，防递归委托）
        AbilityContext.Seat leader = currentLeader(ctx);
        for (AbilityContext.Seat seat : ctx.seats().values()) {
            if (!COPYCAT.equals(seat.racerKey) || seat.eliminated) continue;
            // 冲突时原能力优先：自己是全场第一则不委托；无可复制目标也不委托
            if (leader == null || (overallFirst != null && overallFirst.seatNo == seat.seatNo)) continue;
            seat.effectiveKey = leader.racerKey;
            seat.copyFromSeat = leader.seatNo;
            applied.put(seat.seatNo, leader.racerKey);
        }
        return applied;
    }

    /** 全场第一个未冲线/淘汰者（可能是 copy 类），用于判断模仿猫自己是否已领先 */
    private static AbilityContext.Seat firstActive(AbilityContext ctx) {
        for (AbilityContext.Seat s : ctx.ranked()) {
            if (!s.eliminated && s.finishRank == 0) return s;
        }
        return null;
    }

    /**
     * 可复制的领先者：排除已冲线/淘汰者与 copy 类（防递归委托）；
     * ranked 已按「冲线→位置→座位号」排好，取第一个合格者，并列时座位号兜底保证确定性。
     */
    private static AbilityContext.Seat currentLeader(AbilityContext ctx) {
        for (AbilityContext.Seat s : ctx.ranked()) {
            if (s.eliminated || s.finishRank > 0) continue;
            if (isCopyKey(s.racerKey)) continue;
            return s;
        }
        return null;
    }

    /** 赛前固定委托（蛋蛋/双胞胎）：把已选定的来源 key 写入座位委托 */
    public static void bindFixed(AbilityContext ctx, int seatNo, String sourceKey, int sourceSeat) {
        if (sourceKey == null || isCopyKey(sourceKey)) return;
        ctx.delegate(seatNo, sourceKey, sourceSeat);
    }
}
