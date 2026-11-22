package org.dromara.magicalathlete.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 轮抽选序（D7）。两种口径来自 game.yaml.variants 与 rules.md：
 *
 * <ul>
 *   <li>普通 3–6 人：两批，每批展 2n 张，蛇形序 [1,2,…,n,n,…,2,1]，每人每批 2 张、共 4 张；</li>
 *   <li>2 人局：两批各 8 张，选序 A B B A A B B A / B A A B B A A B，每人共 8 张。</li>
 *   <li>3 人双角色局（M5）：四批各 6 张，ABCCBA / BCAACB / CABBAC / ABCCBA，每人共 8 张。</li>
 * </ul>
 *
 * <p>输入是"座位序号 1..n"，输出是每一次选牌由哪个座位执行。纯逻辑，无随机、无 IO。
 */
public final class DraftOrder {

    /** M5 3 人双角色四批选序（座位 A=1 / B=2 / C=3） */
    private static final int[][] THREE_DOUBLE = {
        {1, 2, 3, 3, 2, 1}, // batch 0: ABCCBA
        {2, 3, 1, 1, 3, 2}, // batch 1: BCAACB
        {3, 1, 2, 2, 1, 3}, // batch 2: CABBAC
        {1, 2, 3, 3, 2, 1}, // batch 3: ABCCBA
    };

    private DraftOrder() {
    }

    /** M5：3 人双角色局第 batch 批的选序（每批 6 手，四批共 24 手 = 3 人 × 8 张） */
    public static List<Integer> threeDoubleSnake(int batch) {
        if (batch < 0 || batch >= THREE_DOUBLE.length) {
            throw new IllegalArgumentException("3 人双角色轮抽批次必须 0-3: " + batch);
        }
        List<Integer> order = new ArrayList<>(6);
        for (int seat : THREE_DOUBLE[batch]) order.add(seat);
        return order;
    }

    /** M5：3 人双角色局总批次 */
    public static int threeDoubleBatchCount() {
        return THREE_DOUBLE.length;
    }

    /**
     * 生成指定批次的选序（座位号列表，1-based）。
     *
     * @param playerCount 玩家数 2–6
     * @param batch       批次：普通模式 0/1；2 人局 0/1
     */
    public static List<Integer> snake(int playerCount, int batch) {
        if (playerCount < 2 || playerCount > 6) {
            throw new IllegalArgumentException("轮抽人数必须 2-6: " + playerCount);
        }
        List<Integer> order = new ArrayList<>();
        if (playerCount == 2) {
            // 2 人局：每人 8 张，两批各 8 手，ABBAABBA / BAABBAAB
            int a = (batch % 2 == 0) ? 1 : 2;
            int b = (batch % 2 == 0) ? 2 : 1;
            order.add(a); order.add(b); order.add(b); order.add(a);
            order.add(a); order.add(b); order.add(b); order.add(a);
            return order;
        }
        // 普通模式：每批每人 2 张，蛇形 [1..n, n..1]
        for (int i = 1; i <= playerCount; i++) order.add(i);
        for (int i = playerCount; i >= 1; i--) order.add(i);
        return order;
    }

    /** 每批展示的牌数：普通 2n；2 人局 8 */
    public static int poolSize(int playerCount) {
        return playerCount == 2 ? 8 : 2 * playerCount;
    }

    /** 轮抽结束每人手牌数：普通 4；2 人局 8 */
    public static int racersPerPlayer(int playerCount) {
        return playerCount == 2 ? 8 : 4;
    }

    /** 轮抽总批次，恒为 2 */
    public static int batchCount() {
        return 2;
    }
}
