package org.dromara.magicalathlete.engine.ability;

/**
 * 回合意图（M4 可选能力交互层）。
 *
 * <p>玩家在 ROLL 之前通过 USE_OPTIONAL 声明本回合的选择，ROLL 时随骰点带入引擎，
 * 结算完即清空。这样 M3 的同步结算链无需拆成两阶段；超时 autoRoll 不带意图即
 * 自动放弃所有可选能力（D6：不替玩家做主观选择）。
 *
 * <p><b>服务端权威</b>：座位号不由本对象携带，一律由房间从玩家名反查；
 * targetSeat / targetSpace / guessDice 全部由房间校验范围后才写入。
 *
 * @param usePrimary  是否启用出场选手的"可"类主能力（大长腿免掷/火箭翻倍/啦啦队长等）
 * @param targetSeat  意图目标座位（人字拖交换、催眠师/吸盘鱼跟随）；0=未指定
 * @param targetSpace 意图目标格（电灯泡传送）；0=未指定
 * @param guessDice   猜骰点数（天才/幕后军师）；0=未猜
 */
public record TurnIntent(boolean usePrimary, int targetSeat, int targetSpace, int guessDice) {

    public static final TurnIntent NONE = new TurnIntent(false, 0, 0, 0);

    public boolean hasTargetSeat() {
        return targetSeat >= 1;
    }

    public boolean hasTargetSpace() {
        return targetSpace >= 1;
    }

    public boolean hasGuess() {
        return guessDice >= 1 && guessDice <= 6;
    }

    public static TurnIntent primary() {
        return new TurnIntent(true, 0, 0, 0);
    }

    public static TurnIntent targetSeat(int seat) {
        return new TurnIntent(true, seat, 0, 0);
    }

    public static TurnIntent guess(int dice) {
        return new TurnIntent(true, 0, 0, dice);
    }
}
