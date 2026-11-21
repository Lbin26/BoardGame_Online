package org.dromara.dicego.engine;

import org.dromara.dicego.domain.SplendorCardDef;
import org.dromara.dicego.domain.SplendorNobleDef;
import org.dromara.dicego.domain.vo.SplendorRankVO;
import org.dromara.dicego.domain.vo.SplendorSnapshotVO;
import org.dromara.dicego.enums.GemColor;
import org.dromara.dicego.enums.SplendorFailReason;
import org.dromara.dicego.service.SplendorCardCache;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 行动引擎测试（M2/M3）：三异色/同色二拿取、买卡支付（黄金补差）、明卡/盲留预留、
 * 弃宝石与贵族选择待办、15VP 终局与名次，及各失败原因码。
 */
class SplendorEngineTest {

    private static final Map<Integer, String> TWO_PLAYERS = Map.of(1, "alice", 2, "bob");

    // ---- 卡池工具 ----

    private static SplendorCardDef card(String code, int level, GemColor bonus, int vp, Map<GemColor, Integer> cost) {
        return new SplendorCardDef(code, level, bonus, vp, cost);
    }

    private static List<SplendorCardDef> uniform(int level, int count, GemColor bonus, int vp,
                                                 Map<GemColor, Integer> cost) {
        List<SplendorCardDef> cards = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            cards.add(card("l" + level + "-" + String.format("%03d", i), level, bonus, vp, cost));
        }
        return List.copyOf(cards);
    }

    private static SplendorNobleDef noble(String code, Map<GemColor, Integer> need) {
        return new SplendorNobleDef(code, null, 3, need);
    }

    private static SplendorCardCache cache(List<SplendorCardDef> l1, List<SplendorCardDef> l2,
                                          List<SplendorCardDef> l3, List<SplendorNobleDef> nobles) {
        return new SplendorCardCache(Map.of(1, l1, 2, l2, 3, l3), nobles);
    }

    /** 三个互不相交需求的贵族（2 人局发牌需 ≥3） */
    private static List<SplendorNobleDef> threeNobles() {
        return List.of(noble("n1", Map.of(GemColor.WHITE, 4)),
            noble("n2", Map.of(GemColor.BLUE, 4)),
            noble("n3", Map.of(GemColor.GREEN, 4)));
    }

    /** 永不满足的贵族集合（终局测试用，避免拜访干扰 VP 断言） */
    private static List<SplendorNobleDef> unreachableNobles() {
        return List.of(noble("n1", Map.of(GemColor.WHITE, 99)),
            noble("n2", Map.of(GemColor.BLUE, 99)),
            noble("n3", Map.of(GemColor.GREEN, 99)));
    }

    /** 通用廉价卡池：l1 费 blue1 / l2、l3 随意 */
    private static SplendorCardCache cheapCache() {
        return cache(
            uniform(1, 12, GemColor.WHITE, 0, Map.of(GemColor.BLUE, 1)),
            uniform(2, 12, GemColor.BLUE, 1, Map.of(GemColor.BLUE, 2)),
            uniform(3, 12, GemColor.GREEN, 2, Map.of(GemColor.BLUE, 3)),
            threeNobles());
    }

    private static SplendorBoard board2p(SplendorCardCache cache) {
        return SplendorBoard.deal(2, TWO_PLAYERS, cache, 42L);
    }

    private static void passTurn(SplendorBoard board, String player) {
        var r = SplendorEngine.takeGems(board, player, Map.of(GemColor.WHITE, 1, GemColor.BLUE, 1, GemColor.GREEN, 1));
        assertTrue(r.isOk(), "让手动作应成功: " + r.reason());
    }

    // ==================== TAKE_GEMS ====================

    @Test
    void 拿宝石_三异色与同色二_组合非法与供给不足() {
        SplendorBoard board = board2p(cheapCache());

        // 三异色各 1：银行扣、手牌加、回合推进
        var r1 = SplendorEngine.takeGems(board, "alice", Map.of(GemColor.WHITE, 1, GemColor.BLUE, 1, GemColor.GREEN, 1));
        assertTrue(r1.isOk());
        assertEquals(3, board.getBank().get(GemColor.WHITE));
        assertEquals(1, board.getPlayers().get(1).getGems().get(GemColor.WHITE));
        assertEquals(2, board.getCurrentSeatNo(), "回合应推进到 bob");

        // 同色二：选未被动过的红色（供给 4 恰好允许）
        var r2 = SplendorEngine.takeGems(board, "bob", Map.of(GemColor.RED, 2));
        assertTrue(r2.isOk());
        assertEquals(2, board.getBank().get(GemColor.RED));
        assertEquals(2, board.getPlayers().get(2).getGems().get(GemColor.RED));

        // 同色二但供给 < 4（白色仅剩 3）
        var r3 = SplendorEngine.takeGems(board, "alice", Map.of(GemColor.WHITE, 2));
        assertFalse(r3.isOk());
        assertEquals(SplendorFailReason.GEM_SUPPLY_SHORT, r3.reason());

        // 两异色（银行凑得齐 3 色）
        var r4 = SplendorEngine.takeGems(board, "alice", Map.of(GemColor.WHITE, 1, GemColor.BLUE, 1));
        assertEquals(SplendorFailReason.INVALID_GEMS, r4.reason());

        // 2+1 混合
        var r5 = SplendorEngine.takeGems(board, "alice", Map.of(GemColor.BLUE, 2, GemColor.GREEN, 1));
        assertEquals(SplendorFailReason.INVALID_GEMS, r5.reason());

        // 黄金不可直接拿取
        var r6 = SplendorEngine.takeGems(board, "alice", Map.of(GemColor.GOLD, 1, GemColor.BLUE, 1, GemColor.GREEN, 1));
        assertEquals(SplendorFailReason.INVALID_GEMS, r6.reason());

        // 非当前回合方
        var r7 = SplendorEngine.takeGems(board, "bob", Map.of(GemColor.BLUE, 1, GemColor.GREEN, 1, GemColor.RED, 1));
        assertEquals(SplendorFailReason.NOT_YOUR_TURN, r7.reason());

        // 对局不存在
        assertEquals(SplendorFailReason.NOT_PLAYING, SplendorEngine.takeGems(null, "alice", Map.of()).reason());
    }

    @Test
    void 拿宝石_银行凑不齐三色时可拿少数() {
        SplendorBoard board = board2p(cheapCache());
        // 排空绿/红/黑，银行只剩白 4 蓝 4 两色
        board.bankRemove(GemColor.GREEN, 4);
        board.bankRemove(GemColor.RED, 4);
        board.bankRemove(GemColor.BLACK, 4);

        var r = SplendorEngine.takeGems(board, "alice", Map.of(GemColor.WHITE, 1, GemColor.BLUE, 1));
        assertTrue(r.isOk(), "银行不足 3 异色时应允许拿 2 颗异色");
        assertEquals(3, board.getBank().get(GemColor.WHITE));
        assertEquals(2, board.getCurrentSeatNo());
    }

    // ==================== BUY_CARD ====================

    @Test
    void 买卡_加成前的现金支付与黄金补差() {
        // l1 统一费 white3 blue2、加成 WHITE、VP 2
        SplendorCardCache cache = cache(
            uniform(1, 12, GemColor.WHITE, 2, Map.of(GemColor.WHITE, 3, GemColor.BLUE, 2)),
            uniform(2, 12, GemColor.BLUE, 1, Map.of(GemColor.BLUE, 2)),
            uniform(3, 12, GemColor.GREEN, 2, Map.of(GemColor.BLUE, 3)),
            threeNobles());
        SplendorBoard board = board2p(cache);
        SplendorPlayer alice = board.getPlayers().get(1);

        // 持有 w2 b1 gold2：需求 w3 b2 → 实付 w2 b1，黄金补差 2
        alice.addGem(GemColor.WHITE, 2);
        alice.addGem(GemColor.BLUE, 1);
        alice.addGem(GemColor.GOLD, 2);
        String cardId = board.tableOf(1).get(0).code();
        int bankGold = board.getBank().get(GemColor.GOLD);

        var r = SplendorEngine.buyCard(board, "alice", cardId, "table");
        assertTrue(r.isOk(), "w2+b1+gold2 应恰好支付 w3b2");
        assertEquals(0, alice.getGems().get(GemColor.WHITE));
        assertEquals(0, alice.getGems().get(GemColor.BLUE));
        assertEquals(0, alice.getGems().get(GemColor.GOLD), "黄金应全部用于补差");
        assertEquals(1, alice.getBonuses().get(GemColor.WHITE));
        assertEquals(2, alice.getVp());
        assertEquals(6, board.getBank().get(GemColor.WHITE), "实付宝石回银行");
        assertEquals(5, board.getBank().get(GemColor.BLUE));
        assertEquals(bankGold + 2, board.getBank().get(GemColor.GOLD));
        assertEquals(4, board.tableOf(1).size(), "桌面应从牌堆补位");
        assertEquals(7, board.deckLeftOf(1));
        assertEquals(2, board.getCurrentSeatNo(), "买卡后回合推进");
        assertEquals(2, ((Map<?, ?>) r.detail().get("paid")).get("gold"));

        // bob 无宝石：余额不足（含黄金）
        String bobCardId = board.tableOf(1).get(0).code();
        assertEquals(SplendorFailReason.CANNOT_AFFORD, SplendorEngine.buyCard(board, "bob", bobCardId, "table").reason());

        // 不存在的卡 / 预留区为空
        assertEquals(SplendorFailReason.CARD_NOT_ON_TABLE, SplendorEngine.buyCard(board, "bob", "l1-999", "table").reason());
        assertEquals(SplendorFailReason.CARD_NOT_RESERVED, SplendorEngine.buyCard(board, "bob", bobCardId, "reserved").reason());
        assertEquals(SplendorFailReason.INVALID_PARAM, SplendorEngine.buyCard(board, "bob", bobCardId, "nowhere").reason());
    }

    @Test
    void 买卡_预留卡用黄金支付() {
        SplendorBoard board = board2p(cheapCache());
        SplendorPlayer alice = board.getPlayers().get(1);

        String cardId = board.tableOf(1).get(0).code();
        assertTrue(SplendorEngine.reserveCard(board, "alice", cardId, null).isOk());
        assertEquals(1, alice.getReserved().size());
        assertEquals(4, board.getBank().get(GemColor.GOLD), "预留应领 1 黄金");
        assertEquals(1, alice.getGems().get(GemColor.GOLD));

        passTurn(board, "bob");

        // 卡已不在桌面，只能从预留区购买（黄金支付 blue1）
        assertEquals(SplendorFailReason.CARD_NOT_ON_TABLE, SplendorEngine.buyCard(board, "alice", cardId, "table").reason());
        var r = SplendorEngine.buyCard(board, "alice", cardId, "reserved");
        assertTrue(r.isOk());
        assertEquals(0, alice.getReserved().size(), "购买后预留区应清空对应卡");
        assertEquals(0, alice.getGems().get(GemColor.GOLD));
        assertEquals(5, board.getBank().get(GemColor.GOLD));
        assertEquals(2, board.getCurrentSeatNo());
    }

    // ==================== RESERVE_CARD ====================

    @Test
    void 预留_盲留空堆_上限_与黄金分配() {
        // 每级仅 5 张：发牌后堆底仅剩 1
        SplendorCardCache cache = cache(
            uniform(1, 5, GemColor.WHITE, 0, Map.of(GemColor.BLUE, 1)),
            uniform(2, 5, GemColor.BLUE, 1, Map.of(GemColor.BLUE, 2)),
            uniform(3, 5, GemColor.GREEN, 2, Map.of(GemColor.BLUE, 3)),
            threeNobles());
        SplendorBoard board = board2p(cache);
        SplendorPlayer alice = board.getPlayers().get(1);
        SplendorPlayer bob = board.getPlayers().get(2);

        // alice 盲留 1 级堆底
        var r1 = SplendorEngine.reserveCard(board, "alice", null, 1);
        assertTrue(r1.isOk());
        assertEquals("deck", r1.detail().get("source"));
        assertEquals(0, board.deckLeftOf(1));
        assertEquals(1, alice.getReserved().size());

        // bob 再盲留同级：堆已空
        assertEquals(SplendorFailReason.DECK_EMPTY, SplendorEngine.reserveCard(board, "bob", null, 1).reason());

        // 交替明卡预留直至双方各 3 张
        assertTrue(SplendorEngine.reserveCard(board, "bob", board.tableOf(1).get(0).code(), null).isOk());
        assertTrue(SplendorEngine.reserveCard(board, "alice", board.tableOf(1).get(0).code(), null).isOk());
        assertTrue(SplendorEngine.reserveCard(board, "bob", board.tableOf(1).get(0).code(), null).isOk());
        var r5 = SplendorEngine.reserveCard(board, "alice", board.tableOf(1).get(0).code(), null);
        assertTrue(r5.isOk());
        assertEquals(1, r5.detail().get("goldGained"), "第 5 次预留恰好拿走最后一颗黄金");
        // 1 级明卡已取空（无堆可补），后续明卡预留用 2 级
        var r6 = SplendorEngine.reserveCard(board, "bob", board.tableOf(2).get(0).code(), null);
        assertTrue(r6.isOk(), "无黄金也应允许预留");
        assertEquals(0, r6.detail().get("goldGained"));

        // 双方各 3 张，第 4 次被拒
        assertEquals(3, alice.getReserved().size());
        assertEquals(3, bob.getReserved().size());
        assertEquals(SplendorFailReason.RESERVE_LIMIT,
            SplendorEngine.reserveCard(board, "alice", board.tableOf(2).get(0).code(), null).reason());

        // 黄金守恒：银行 0，玩家合计 5
        assertEquals(0, board.getBank().get(GemColor.GOLD));
        assertEquals(5, alice.getGems().get(GemColor.GOLD) + bob.getGems().get(GemColor.GOLD));
    }

    @Test
    void 预留_参数校验() {
        SplendorBoard board = board2p(cheapCache());
        assertEquals(SplendorFailReason.INVALID_PARAM, SplendorEngine.reserveCard(board, "alice", null, null).reason());
        assertEquals(SplendorFailReason.INVALID_PARAM, SplendorEngine.reserveCard(board, "alice", null, 4).reason());
        assertEquals(SplendorFailReason.CARD_NOT_ON_TABLE, SplendorEngine.reserveCard(board, "alice", "l1-999", null).reason());
    }

    // ==================== DISCARD_GEMS ====================

    @Test
    void 弃宝石_超十颗进入待办_须恰好弃到十() {
        SplendorBoard board = board2p(cheapCache());
        SplendorPlayer alice = board.getPlayers().get(1);

        // 预置 9 颗，同色拿二到 11 颗
        alice.addGem(GemColor.WHITE, 3);
        alice.addGem(GemColor.BLUE, 3);
        alice.addGem(GemColor.GREEN, 3);
        var take = SplendorEngine.takeGems(board, "alice", Map.of(GemColor.WHITE, 2));
        assertTrue(take.isOk(), "拿宝石本身合法（超限在回合末处理）");
        assertEquals(11, alice.gemCount());
        assertEquals(SplendorBoard.PendingPhase.DISCARD, board.getPending());
        assertEquals(1, board.getCurrentSeatNo(), "弃宝石完成前不推进回合");

        // 待办期间：他人与其主行动均被拒
        assertEquals(SplendorFailReason.NOT_YOUR_TURN,
            SplendorEngine.takeGems(board, "bob", Map.of(GemColor.BLUE, 1, GemColor.GREEN, 1, GemColor.RED, 1)).reason());
        assertEquals(SplendorFailReason.MUST_DISCARD,
            SplendorEngine.buyCard(board, "alice", board.tableOf(1).get(0).code(), "table").reason());

        // 弃错数量 / 无待办时乱发
        assertEquals(SplendorFailReason.DISCARD_INVALID,
            SplendorEngine.discardGems(board, "alice", Map.of(GemColor.WHITE, 2)).reason());
        assertEquals(SplendorFailReason.DISCARD_INVALID,
            SplendorEngine.discardGems(board, "alice", Map.of(GemColor.BLUE, 5)).reason());

        // 恰好弃 1：银行回补、待办清除、回合推进
        var r = SplendorEngine.discardGems(board, "alice", Map.of(GemColor.WHITE, 1));
        assertTrue(r.isOk());
        assertEquals(10, alice.gemCount());
        assertEquals(3, board.getBank().get(GemColor.WHITE), "弃置宝石应回银行：4-2+1");
        assertEquals(SplendorBoard.PendingPhase.NONE, board.getPending());
        assertEquals(2, board.getCurrentSeatNo());

        // 无待办时再发 DISCARD
        assertEquals(SplendorFailReason.DISCARD_INVALID,
            SplendorEngine.discardGems(board, "bob", Map.of(GemColor.WHITE, 1)).reason());
    }

    // ==================== NOBLE ====================

    @Test
    void 贵族_唯一满足自动拜访() {
        SplendorBoard board = board2p(cheapCache());
        SplendorPlayer alice = board.getPlayers().get(1);
        alice.addGem(GemColor.BLUE, 1);
        alice.addBonus(GemColor.WHITE, 4); // 恰好满足 n1(white4)

        var r = SplendorEngine.buyCard(board, "alice", board.tableOf(1).get(0).code(), "table");
        assertTrue(r.isOk());
        assertEquals(3, alice.getVp(), "卡 VP0 + 贵族 3");
        assertEquals(2, board.getNobles().size(), "n1 应被移出桌面");
        assertEquals(SplendorBoard.PendingPhase.NONE, board.getPending(), "唯一满足应自动拜访，无待办");
        assertEquals(2, board.getCurrentSeatNo());
    }

    @Test
    void 贵族_多选待办_选择非法与生效() {
        SplendorBoard board = board2p(cheapCache());
        SplendorPlayer alice = board.getPlayers().get(1);
        alice.addGem(GemColor.BLUE, 1);
        alice.addBonus(GemColor.WHITE, 4);
        alice.addBonus(GemColor.BLUE, 4); // 同时满足 n1、n2

        var r = SplendorEngine.buyCard(board, "alice", board.tableOf(1).get(0).code(), "table");
        assertTrue(r.isOk());
        assertEquals(SplendorBoard.PendingPhase.NOBLE_SELECT, board.getPending(), "两位满足进入选择待办");
        assertEquals(1, board.getCurrentSeatNo(), "选择前不推进回合");
        assertEquals(2, board.getPendingNobles().size());

        // 可见性（D5）：本人见候选明细，对手只见类型
        SplendorSnapshotVO self = board.snapshotFor("alice");
        SplendorSnapshotVO opp = board.snapshotFor("bob");
        assertEquals("NOBLE_SELECT", self.pendingType());
        assertEquals("NOBLE_SELECT", opp.pendingType());
        assertEquals(2, self.pendingNobles().size());
        assertNull(opp.pendingNobles(), "候选贵族明细不得泄漏给对手");

        // 选择集合外的贵族被拒；主行动待办期间被拒
        assertEquals(SplendorFailReason.NOBLE_NOT_ELIGIBLE, SplendorEngine.selectNoble(board, "alice", "n3").reason());
        assertEquals(SplendorFailReason.MUST_DISCARD, SplendorEngine.reserveCard(board, "alice", null, 1).reason());

        var sel = SplendorEngine.selectNoble(board, "alice", "n1");
        assertTrue(sel.isOk());
        assertEquals(3, alice.getVp());
        assertEquals(2, board.getNobles().size(), "被拜访贵族移出，其余留存桌面");
        assertEquals(SplendorBoard.PendingPhase.NONE, board.getPending());
        assertEquals(2, board.getCurrentSeatNo());

        // 无待办时选择
        assertEquals(SplendorFailReason.NOBLE_NOT_ELIGIBLE, SplendorEngine.selectNoble(board, "bob", "n2").reason());
    }

    // ==================== 终局 ====================

    @Test
    void 终局_十五VP触发最后一轮_轮末结束并排名() {
        // l1 卡 VP15：买一张即达 15
        SplendorCardCache cache = cache(
            uniform(1, 12, GemColor.WHITE, 15, Map.of(GemColor.BLUE, 1)),
            uniform(2, 12, GemColor.BLUE, 1, Map.of(GemColor.BLUE, 2)),
            uniform(3, 12, GemColor.GREEN, 2, Map.of(GemColor.BLUE, 3)),
            unreachableNobles());
        SplendorBoard board = board2p(cache);
        SplendorPlayer alice = board.getPlayers().get(1);
        SplendorPlayer bob = board.getPlayers().get(2);
        alice.addGem(GemColor.BLUE, 1);

        var r = SplendorEngine.buyCard(board, "alice", board.tableOf(1).get(0).code(), "table");
        assertTrue(r.isOk());
        assertEquals(15, alice.getVp());
        assertTrue(board.isLastRound(), "达 15VP 应标记终局轮");
        assertFalse(board.isFinished(), "本轮其余玩家行动完才终局");
        assertEquals(2, board.getCurrentSeatNo());

        // bob 行动后轮回到先手 → 终局
        passTurn(board, "bob");
        assertTrue(board.isFinished());
        assertEquals("FINISHED", board.snapshotFor("alice").status());
        assertEquals(SplendorFailReason.NOT_PLAYING,
            SplendorEngine.takeGems(board, "bob", Map.of(GemColor.WHITE, 1, GemColor.BLUE, 1, GemColor.GREEN, 1)).reason());

        // 名次：VP 降序；bob 未购卡
        List<SplendorRankVO> rankings = SplendorEngine.rankingsOf(board);
        assertEquals("alice", rankings.get(0).name());
        assertEquals(15, rankings.get(0).vp());
        assertEquals(1, rankings.get(0).cards());
        assertEquals("bob", rankings.get(1).name());
        assertEquals(0, rankings.get(1).cards());
        // 轮数：第 1 轮内结束
        assertEquals(2, board.snapshotFor("alice").roundNo());
    }

    @Test
    void 终局_平局按购卡数裁决() {
        SplendorCardCache cache = cache(
            uniform(1, 12, GemColor.WHITE, 15, Map.of(GemColor.BLUE, 1)),
            uniform(2, 12, GemColor.BLUE, 1, Map.of(GemColor.BLUE, 2)),
            uniform(3, 12, GemColor.GREEN, 2, Map.of(GemColor.BLUE, 3)),
            unreachableNobles());
        SplendorBoard board = board2p(cache);
        SplendorPlayer alice = board.getPlayers().get(1);
        SplendorPlayer bob = board.getPlayers().get(2);
        alice.addGem(GemColor.BLUE, 1);
        bob.addGem(GemColor.BLUE, 1);

        assertTrue(SplendorEngine.buyCard(board, "alice", board.tableOf(1).get(0).code(), "table").isOk());
        assertTrue(SplendorEngine.buyCard(board, "bob", board.tableOf(1).get(0).code(), "table").isOk());
        // 两人先后达 15VP，bob 行动后轮回到 alice → 终局
        assertTrue(board.isFinished());

        // VP 同为 15、购卡数同为 1：按座位号稳定排序
        List<SplendorRankVO> rankings = SplendorEngine.rankingsOf(board);
        assertEquals(15, rankings.get(0).vp());
        assertEquals(15, rankings.get(1).vp());
        assertEquals("alice", rankings.get(0).name());
        assertEquals("bob", rankings.get(1).name());

        // 同 VP 平局裁决：购卡数少者胜（bob 模拟多购一张）
        bob.addBonus(GemColor.BLACK, 1);
        List<SplendorRankVO> after = SplendorEngine.rankingsOf(board);
        assertEquals("alice", after.get(0).name(), "同 VP 时购卡少者胜");
        assertEquals("bob", after.get(1).name());
        assertEquals(2, after.get(1).cards());
    }
}
