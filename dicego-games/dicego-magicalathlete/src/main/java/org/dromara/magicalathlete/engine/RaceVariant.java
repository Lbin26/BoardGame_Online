package org.dromara.magicalathlete.engine;

import java.util.List;

/**
 * M5 人数变体（D9 / game.yaml.variants）。
 *
 * <p>规则里 3 人局同时存在「普通局」与「3 人双角色局」两种口径
 * （{@code normal.players:[3,6]} 与 {@code threePlayerDouble.players:[3]}），
 * 因此双角色必须是<b>显式开关</b>，默认 {@link #NORMAL} 保证 M1–M4 零回归。
 *
 * <p>双角色局把每个真实座位展开为 {@code racersPerSeat()} 个「参赛号 lane」：
 * seat1→lane1,2；seat2→lane3,4；seat3→lane5,6。{@link RaceSession} 只认 seatNo，
 * 这里把 lane 当作它的 seatNo 传入，引擎与全部能力 handler 无需改动；
 * 房间层负责 lane↔seat 的映射与奖牌/铜币/能力分归并。
 *
 * <p>2 人局轮抽（8 张/人、两批 ABBAABBA）沿用 M1 既有实现，每场仍派 1 名，
 * 属计划内已知简化，不在 M5 范围。
 */
public enum RaceVariant {
    /** 2–6 人普通局：每人 4 名、每场 1 名、两批蛇形 */
    NORMAL,
    /** 2 人局：每人 8 名、两批 ABBAABBA；每场仍 1 名（M1 既有简化） */
    TWO_PLAYER,
    /** 3 人双角色局：每人 8 名、四批各 6 张、每场 2 名、赛前固定顺序 */
    THREE_DOUBLE;

    /** 每座位每场派出的选手数 */
    public int racersPerSeat() {
        return this == THREE_DOUBLE ? 2 : 1;
    }

    /** 轮抽批次 */
    public int batchCount() {
        return this == THREE_DOUBLE ? 4 : 2;
    }

    /** 每批展示的牌数 */
    public int poolSize(int playerCount) {
        return this == THREE_DOUBLE ? 6 : DraftOrder.poolSize(playerCount);
    }

    /** 轮抽结束每人手牌数 */
    public int racersPerPlayer(int playerCount) {
        return this == THREE_DOUBLE ? 8 : DraftOrder.racersPerPlayer(playerCount);
    }

    /** 本批选序（座位号列表） */
    public List<Integer> pickOrder(int playerCount, int batch) {
        return this == THREE_DOUBLE
            ? DraftOrder.threeDoubleSnake(batch)
            : DraftOrder.snake(playerCount, batch);
    }

    /** 参赛者总数（= 真实座位数 × 每座位选手数） */
    public int laneCount(int playerCount) {
        return playerCount * racersPerSeat();
    }

    /** 真实座位 + 该座位的第 slot 名选手（slot 从 0 起）→ 参赛号 lane */
    public int laneOf(int seatNo, int slot) {
        if (this != THREE_DOUBLE) return seatNo;
        return (seatNo - 1) * 2 + slot + 1;
    }

    /** 参赛号 lane → 归属的真实座位 */
    public int seatOfLane(int lane) {
        if (this != THREE_DOUBLE) return lane;
        return (lane - 1) / 2 + 1;
    }

    /** 参赛号 lane → 该座位的第几名选手（0 起） */
    public int slotOfLane(int lane) {
        if (this != THREE_DOUBLE) return 0;
        return (lane - 1) % 2;
    }

    /** 按人数推断默认变体（无显式开关时使用） */
    public static RaceVariant defaultOf(int playerCount) {
        return playerCount == 2 ? TWO_PLAYER : NORMAL;
    }

    /** 解析 START_GAME 的 variant 参数；非法值按默认处理，仅 3 人局允许双角色 */
    public static RaceVariant of(int playerCount, String raw) {
        if (raw != null && "THREE_DOUBLE".equalsIgnoreCase(raw.trim()) && playerCount == 3) {
            return THREE_DOUBLE;
        }
        return defaultOf(playerCount);
    }
}
