package org.dromara.magicalathlete.engine.ability;

/**
 * 效果指令（D3：能力 handler 只产出指令，不改房间状态；由 {@link EffectPipeline} 统一执行）。
 *
 * <p>每条指令都带 sourceSeat（能力归属座位）与 reason（可读原因），因此天然可重放、可落 action_log：
 * 争议时按 {@code reason + type + sourceSeat + targetSeat + amount} 原样复盘。
 * TRACK_SOURCE 表示效果来自赛道格而非某名选手，优先级排序时它最高。
 *
 * @param type       效果类型
 * @param sourceSeat 能力归属座位（TRACK_SOURCE 表示赛道格；-2 表示系统/托管）
 * @param targetSeat 效果作用座位（部分全局效果用 -1 表示全体）
 * @param amount     数值（移动格数 / 铜币面值 / 骰点 / 分数；含义随 type 而定）
 * @param reason     可读原因，用于日志与前端播报，例如 "blimp@seat2:+3"
 */
public record Effect(EffectType type, int sourceSeat, int targetSeat, int amount, String reason) {

    public static final int TRACK_SOURCE = -1;
    public static final int SYSTEM_SOURCE = -2;
    public static final int ALL_TARGETS = -1;

    /** 效果实际改动了状态（区别于"判定后无变化"），用于循环签名与蹭蹭怪计数 */
    public boolean changesState() {
        return switch (type) {
            case MOVE, MOVE_TO, TRIP, BRONZE, SCORE, REPLACE_BASE, MODIFY_MOVE,
                 CANCEL_MOVE, UNTRIP, ELIMINATE, EXTRA_TURN, STEAL_TURN, SWAP_POSITION -> true;
            default -> false;
        };
    }
}
