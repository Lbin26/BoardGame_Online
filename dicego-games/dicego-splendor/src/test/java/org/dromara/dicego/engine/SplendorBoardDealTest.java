package org.dromara.dicego.engine;

import org.dromara.dicego.TestCaches;
import org.dromara.dicego.domain.vo.SplendorPlayerVO;
import org.dromara.dicego.domain.vo.SplendorSnapshotVO;
import org.dromara.dicego.enums.GemColor;
import org.dromara.dicego.service.SplendorCardCache;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发牌与快照测试（M1）：张数/供给/贵族数/先手/可见性（D5）/seed 确定性。
 */
class SplendorBoardDealTest {

    private static final int PER_LEVEL = 12;

    @Test
    void 两人局发牌_银行供给与牌堆正确() {
        SplendorCardCache cache = TestCaches.cache(PER_LEVEL, 6);
        SplendorBoard board = SplendorBoard.deal(2,
            Map.of(1, "alice", 2, "bob"), cache, 42L);

        // 桌面明卡：每级 4 张；牌堆余量 = 12-4
        for (int level = 1; level <= 3; level++) {
            assertEquals(4, board.tableOf(level).size(), "等级 " + level + " 明卡数");
            assertEquals(PER_LEVEL - 4, board.deckLeftOf(level), "等级 " + level + " 牌堆余量");
        }
        // 2 人：每色 4，黄金 5；贵族 3 张
        assertEquals(4, board.getBank().get(GemColor.WHITE));
        assertEquals(4, board.getBank().get(GemColor.BLACK));
        assertEquals(5, board.getBank().get(GemColor.GOLD));
        assertEquals(3, board.getNobles().size());
        // 先手 = 最小座位号
        assertEquals(1, board.getCurrentSeatNo());
        assertEquals(2, board.getPlayerCount());
    }

    @Test
    void 四人局发牌_供给七色贵族五张() {
        SplendorCardCache cache = TestCaches.cache(PER_LEVEL, 6);
        SplendorBoard board = SplendorBoard.deal(4,
            Map.of(1, "a", 2, "b", 3, "c", 4, "d"), cache, 7L);

        assertEquals(7, board.getBank().get(GemColor.GREEN));
        assertEquals(5, board.getBank().get(GemColor.GOLD));
        assertEquals(5, board.getNobles().size());
    }

    @Test
    void 同seed重放一致_异seed不同() {
        SplendorCardCache cache = TestCaches.cache(PER_LEVEL, 6);
        SplendorBoard b1 = SplendorBoard.deal(2, Map.of(1, "alice", 2, "bob"), cache, 99L);
        SplendorBoard b2 = SplendorBoard.deal(2, Map.of(1, "alice", 2, "bob"), cache, 99L);
        SplendorBoard b3 = SplendorBoard.deal(2, Map.of(1, "alice", 2, "bob"), cache, 100L);

        assertEquals(b1.tableOf(1), b2.tableOf(1), "同 seed 明卡序列应一致（复盘可重放）");
        assertEquals(b1.getNobles(), b2.getNobles());
        assertNotEquals(b1.tableOf(1), b3.tableOf(1), "异 seed 明卡应不同");
    }

    @Test
    void 快照可见性_预留卡仅本人可见() {
        SplendorCardCache cache = TestCaches.cache(PER_LEVEL, 6);
        SplendorBoard board = SplendorBoard.deal(2,
            Map.of(1, "alice", 2, "bob"), cache, 42L);

        // alice 预留一张牌（包内访问模拟 M2 引擎结算结果）
        SplendorPlayer alice = board.getPlayers().get(1);
        alice.addReserved(board.tableOf(2).get(0));

        // 本人视角：可见明细
        SplendorSnapshotVO self = board.snapshotFor("alice");
        assertEquals(1, self.viewerSeatNo());
        SplendorPlayerVO aliceVO = self.players().stream()
            .filter(p -> p.name().equals("alice")).findFirst().orElseThrow();
        assertEquals(1, aliceVO.reservedCount());
        assertNotNull(aliceVO.reservedCards(), "本人快照必须含预留卡明细");
        assertEquals(1, aliceVO.reservedCards().size());

        // 对手视角：只见数量，字段缺省
        SplendorSnapshotVO opp = board.snapshotFor("bob");
        SplendorPlayerVO aliceFromBob = opp.players().stream()
            .filter(p -> p.name().equals("alice")).findFirst().orElseThrow();
        assertEquals(1, aliceFromBob.reservedCount(), "预留数公开");
        assertNull(aliceFromBob.reservedCards(), "对手快照不得泄漏预留卡明细（D5）");

        // 旁观视角：所有玩家均无明细
        SplendorSnapshotVO spectator = board.snapshotFor("eve");
        assertNull(spectator.viewerSeatNo());
        assertTrue(spectator.players().stream().allMatch(p -> p.reservedCards() == null),
            "旁观者不持有任何人的预留明细");
    }

    @Test
    void 快照基础字段_协议口径() {
        SplendorCardCache cache = TestCaches.cache(PER_LEVEL, 6);
        SplendorBoard board = SplendorBoard.deal(2,
            Map.of(1, "alice", 2, "bob"), cache, 42L);
        SplendorSnapshotVO snap = board.snapshotFor("alice");

        assertEquals("PLAYING", snap.status());
        assertEquals(1, snap.roundNo());
        assertEquals(1, snap.currentSeatNo());
        // 银行六色齐全，键为色名 wire 值
        assertEquals(6, snap.bank().size());
        assertEquals(4, snap.bank().get("white"));
        assertEquals(5, snap.bank().get("gold"));
        // 三级行齐全，各 4 明卡
        assertEquals(3, snap.levels().size());
        assertEquals(4, snap.levels().get("1").cards().size());
        assertEquals(8, snap.levels().get("1").deckLeft());
        // 每张明卡含五色费用与加成 wire 值
        SplendorSnapshotVO.LevelVO l1 = snap.levels().get("1");
        assertEquals(5, l1.cards().get(0).cost().size());
        assertNotNull(GemColor.fromCode(l1.cards().get(0).bonus()));
    }
}
