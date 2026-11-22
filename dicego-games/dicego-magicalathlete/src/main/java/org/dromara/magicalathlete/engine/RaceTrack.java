package org.dromara.magicalathlete.engine;

import java.util.Map;

/**
 * 赛道（单一事实来源：design/games/magicalAthlete/game.yaml 的 board 段）。
 *
 * <p>起点 0、有效格 1–29、到 30 冲线；终点不是可停留格，超过 30 按 30 处理（默认规则，
 * 较真怪的"必须恰好冲线"是 M3 能力）。第二拐角 15 供飞艇能力使用（M3）。
 * 平缓面无特殊格；狂野面 10 个特殊格：铜币 / 绊倒 / 箭头（独立移动）。
 */
public final class RaceTrack {

    public static final int START = 0;
    public static final int LAST_CELL = 29;
    public static final int FINISH = 30;
    public static final int SECOND_CORNER = 15;

    public enum Side {
        MILD("平缓面"), WILD("狂野面");

        private final String label;

        Side(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** type: bronze / trip / move；delta 仅 move 有效；bronze 为面值（1 或 3） */
    public record StopEffect(int space, String type, int delta, int bronze) {
    }

    private static final Map<Integer, StopEffect> WILD = Map.of(
        1, new StopEffect(1, "bronze", 0, 1),
        5, new StopEffect(5, "trip", 0, 0),
        7, new StopEffect(7, "move", 3, 0),
        11, new StopEffect(11, "move", 1, 0),
        13, new StopEffect(13, "bronze", 0, 1),
        16, new StopEffect(16, "move", -4, 0),
        17, new StopEffect(17, "trip", 0, 0),
        23, new StopEffect(23, "move", 2, 0),
        24, new StopEffect(24, "move", -2, 0),
        26, new StopEffect(26, "trip", 0, 0)
    );

    private RaceTrack() {
    }

    /** 第 1/3 场平缓，第 2/4 场狂野 */
    public static Side sideOfRace(int raceNo) {
        return raceNo % 2 == 0 ? Side.WILD : Side.MILD;
    }

    public static StopEffect stopAt(Side side, int space) {
        return side == Side.WILD ? WILD.get(space) : null;
    }

    public static Map<Integer, StopEffect> wildSpecials() {
        return WILD;
    }

    /** 棋子位置夹在 [0,30]；越过量按冲线处理（终点不可停留） */
    public static int clamp(int position) {
        return Math.max(START, Math.min(FINISH, position));
    }

    public static boolean isFinish(int position) {
        return position >= FINISH;
    }
}
