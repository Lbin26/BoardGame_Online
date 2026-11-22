package org.dromara.halligalli.engine;

import org.dromara.halligalli.domain.HalliGalliCard;
import org.dromara.halligalli.enums.Fruit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则引擎测试：恰好 5 才可抢、抢对收桌面、抢错赔牌、防抖、迟到丢弃、牌尽出局、两人残局终局。
 * 时间一律由测试注入（board.ring(seat, now)），不依赖真实时钟。
 */
class HalliGalliBoardTest {

    private static int seq = 0;

    private static HalliGalliCard card(Fruit fruit, int count) {
        return new HalliGalliCard(fruit.getCode() + "-" + count + "-t" + (++seq), fruit, count);
    }

    /** drawOrder 第一张为牌堆顶（最先被翻出） */
    private static HalliGalliPlayer player(int seat, String name, List<HalliGalliCard> drawOrder) {
        HalliGalliPlayer p = new HalliGalliPlayer(seat, name);
        for (HalliGalliCard card : drawOrder) p.receive(card);
        return p;
    }

    /** 只有一张明牌、牌堆为空的玩家（用于构造桌面计数） */
    private static HalliGalliPlayer topOnly(int seat, String name, HalliGalliCard top) {
        HalliGalliPlayer p = new HalliGalliPlayer(seat, name);
        p.pushFaceUp(top);
        return p;
    }

    private static HalliGalliBoard board(HalliGalliPlayer... players) {
        return new HalliGalliBoard(List.of(players), false);
    }

    @Test
    void 翻牌揭示顶牌并顺延翻牌权() {
        HalliGalliPlayer p1 = player(1, "alice", List.of(card(Fruit.APPLE, 2), card(Fruit.LEMON, 1)));
        HalliGalliPlayer p2 = player(2, "bob", List.of(card(Fruit.GRAPE, 1)));
        HalliGalliBoard board = board(p1, p2);

        HalliGalliBoard.FlipOutcome outcome = board.flip(1);
        assertTrue(outcome.ok());
        assertEquals(Fruit.APPLE, outcome.card().fruit());
        assertEquals(2, board.visibleCounts().get(Fruit.APPLE));
        assertEquals(2, board.flipSeatNo());
        assertEquals(1, p1.deckSize());

        assertEquals("NOT_YOUR_TURN", board.flip(1).reason());
    }

    @Test
    void 同种水果恰好五才可抢_六个与跨水果都不算() {
        HalliGalliBoard five = board(topOnly(1, "a", card(Fruit.APPLE, 3)),
            topOnly(2, "b", card(Fruit.APPLE, 2)));
        assertTrue(five.isRingValid());

        HalliGalliBoard six = board(topOnly(1, "a", card(Fruit.APPLE, 4)),
            topOnly(2, "b", card(Fruit.APPLE, 2)));
        assertFalse(six.isRingValid());

        HalliGalliBoard mixed = board(topOnly(1, "a", card(Fruit.APPLE, 4)),
            topOnly(2, "b", card(Fruit.LEMON, 2)));
        assertFalse(mixed.isRingValid());
    }

    @Test
    void 只有顶牌计入桌面_被覆盖的牌不参与计数() {
        HalliGalliPlayer p1 = player(1, "a", List.of(card(Fruit.APPLE, 1), card(Fruit.APPLE, 5)));
        HalliGalliPlayer p2 = player(2, "b", List.of(card(Fruit.LEMON, 1)));
        HalliGalliBoard board = board(p1, p2);

        board.flip(1); // 首张苹果 1
        assertEquals(1, board.visibleCounts().get(Fruit.APPLE));
        board.flip(2); // 对手柠檬 1
        board.flip(1); // 苹果 5 盖住苹果 1
        assertEquals(5, board.visibleCounts().get(Fruit.APPLE));
        assertEquals(1, board.visibleCounts().get(Fruit.LEMON));
    }

    @Test
    void 抢对收走全部明牌并成为下一个翻牌方() {
        HalliGalliPlayer p1 = topOnly(1, "a", card(Fruit.APPLE, 3));
        HalliGalliPlayer p2 = topOnly(2, "b", card(Fruit.APPLE, 2));
        HalliGalliBoard board = board(p1, p2);

        HalliGalliBoard.RingOutcome outcome = board.ring(2, 1_000L);
        assertEquals(HalliGalliBoard.RingStatus.CORRECT, outcome.status());
        assertEquals(2, outcome.cardsTaken());
        assertEquals(2, outcome.beneficiarySeat());
        assertEquals(0, board.visibleCounts().getOrDefault(Fruit.APPLE, 0));
        assertEquals(2, board.flipSeatNo());
        assertEquals(2, p2.totalCards());
    }

    @Test
    void 抢错给每位在场玩家各赔一张() {
        HalliGalliPlayer p1 = player(1, "a", List.of(card(Fruit.LEMON, 1), card(Fruit.LEMON, 1)));
        p1.pushFaceUp(card(Fruit.APPLE, 4));
        HalliGalliPlayer p2 = topOnly(2, "b", card(Fruit.APPLE, 2));
        HalliGalliPlayer p3 = topOnly(3, "c", card(Fruit.GRAPE, 1));
        HalliGalliBoard board = board(p1, p2, p3);

        HalliGalliBoard.RingOutcome outcome = board.ring(1, 1_000L);
        assertEquals(HalliGalliBoard.RingStatus.WRONG, outcome.status());
        assertEquals(2, outcome.penalties().size());
        assertEquals(List.of(2, 3), outcome.penalties().stream().map(HalliGalliBoard.Penalty::seatNo).toList());
        assertEquals(0, p1.deckSize());
        assertEquals(2, p2.totalCards());
        assertEquals(2, p3.totalCards());
        assertFalse(board.isFinished());
    }

    @Test
    void 同一玩家两百毫秒内的重复抢铃被防抖() {
        HalliGalliPlayer p1 = player(1, "a", List.of(card(Fruit.LEMON, 1), card(Fruit.LEMON, 1)));
        p1.pushFaceUp(card(Fruit.APPLE, 4));
        HalliGalliPlayer p2 = topOnly(2, "b", card(Fruit.LEMON, 1));
        HalliGalliPlayer p3 = topOnly(3, "c", card(Fruit.GRAPE, 1));
        HalliGalliBoard board = board(p1, p2, p3);

        assertEquals(HalliGalliBoard.RingStatus.WRONG, board.ring(1, 1_000L).status());
        assertEquals(HalliGalliBoard.RingStatus.DEBOUNCED, board.ring(1, 1_100L).status());
    }

    @Test
    void 抢铃成功后的迟到请求被静默丢弃_超出静默窗口才算抢错() {
        HalliGalliPlayer p1 = player(1, "a", List.of(card(Fruit.LEMON, 1)));
        p1.pushFaceUp(card(Fruit.APPLE, 3));
        HalliGalliPlayer p2 = player(2, "b", List.of(card(Fruit.LEMON, 1)));
        p2.pushFaceUp(card(Fruit.APPLE, 2));
        HalliGalliBoard board = board(p1, p2);

        assertEquals(HalliGalliBoard.RingStatus.CORRECT, board.ring(1, 1_000L).status());
        assertEquals(HalliGalliBoard.RingStatus.LATE, board.ring(2, 1_100L).status());
        assertEquals(3, p1.totalCards()); // 迟到不改状态：自带明牌 3 + 明牌 2 + 牌堆 1
        assertEquals(HalliGalliBoard.RingStatus.WRONG, board.ring(2, 3_000L).status());
    }

    @Test
    void 轮到自己翻牌却无牌则出局_明牌仍留桌计数() {
        HalliGalliPlayer p1 = topOnly(1, "a", card(Fruit.APPLE, 2));
        HalliGalliPlayer p2 = player(2, "b", List.of(card(Fruit.LEMON, 1)));
        HalliGalliPlayer p3 = player(3, "c", List.of(card(Fruit.GRAPE, 1)));
        HalliGalliBoard board = board(p1, p2, p3);

        HalliGalliBoard.FlipOutcome outcome = board.flip(1);
        assertTrue(outcome.ok());
        assertTrue(outcome.playerOut());
        assertTrue(p1.isOut());
        assertEquals(2, outcome.nextSeatNo());
        assertEquals(2, board.visibleCounts().get(Fruit.APPLE));
        assertFalse(board.isFinished());
        assertEquals("PLAYER_OUT", board.ring(1, 1_000L).reason());
    }

    @Test
    void 两人残局抢错则对手收走桌面并终局() {
        HalliGalliPlayer p1 = player(1, "a", List.of(card(Fruit.LEMON, 1)));
        p1.pushFaceUp(card(Fruit.APPLE, 3));
        HalliGalliPlayer p2 = player(2, "b", List.of(card(Fruit.LEMON, 1)));
        p2.pushFaceUp(card(Fruit.GRAPE, 2));
        HalliGalliBoard board = board(p1, p2);

        HalliGalliBoard.RingOutcome outcome = board.ring(1, 1_000L);
        assertEquals(HalliGalliBoard.RingStatus.WRONG, outcome.status());
        assertEquals("TWO_PLAYER_SUDDEN_END", outcome.reason());
        assertEquals(2, outcome.beneficiarySeat());
        assertEquals(2, outcome.cardsTaken());
        assertTrue(board.isFinished());
        assertEquals(2, board.rankings().get(0).seatNo());
        assertEquals(4, board.rankings().get(0).cardCount());
    }

    @Test
    void 排名按持牌数降序同数按座位升序() {
        HalliGalliPlayer p1 = player(1, "a", List.of(card(Fruit.APPLE, 1), card(Fruit.LEMON, 1)));
        HalliGalliPlayer p2 = player(2, "b", List.of(card(Fruit.GRAPE, 1)));
        HalliGalliPlayer p3 = player(3, "c", List.of(card(Fruit.GRAPE, 1)));
        HalliGalliBoard board = board(p1, p2, p3);

        List<HalliGalliBoard.Rank> ranks = board.rankings();
        assertEquals(1, ranks.get(0).seatNo());
        assertEquals(2, ranks.get(0).cardCount());
        assertEquals(2, ranks.get(1).seatNo());
        assertEquals(3, ranks.get(2).seatNo());
        assertEquals(2, ranks.get(1).rank());
    }

    @Test
    void 发牌把五十六张全部分完且张数差不超过一() {
        java.util.Map<Integer, String> seats = new java.util.TreeMap<>();
        seats.put(1, "a");
        seats.put(2, "b");
        seats.put(3, "c");

        HalliGalliBoard board = HalliGalliBoard.deal(seats, 42L);
        int total = 0;
        for (HalliGalliPlayer p : board.players()) {
            total += p.deckSize();
        }
        assertEquals(56, total);
        assertTrue(board.players().stream().mapToInt(HalliGalliPlayer::deckSize).max().orElse(0)
            - board.players().stream().mapToInt(HalliGalliPlayer::deckSize).min().orElse(0) <= 1);
        assertEquals(1, board.flipSeatNo());
        assertFalse(board.isFinished());
    }
}
