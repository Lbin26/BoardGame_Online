package org.dromara.halligalli.engine;

import org.dromara.halligalli.domain.HalliGalliCard;
import org.dromara.halligalli.domain.HalliGalliDeck;
import org.dromara.halligalli.enums.Fruit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 德国心脏病对局状态机（纯逻辑，无 IO、无锁）。
 * 并发串行化由 {@code HalliGalliRoom} 的 synchronized 方法保证（D2）。
 *
 * <p>核心规则：
 * <ul>
 *   <li>翻牌有轮次：只能翻自己牌堆顶，翻出的牌盖在面前，只有顶牌计入桌面。</li>
 *   <li>抢铃无轮次：任意在场玩家、任意时刻可抢；判定只认服务端到达顺序。</li>
 *   <li>同种水果桌面可见总数恰好 5 才成立；6 个、跨水果相加都不算。</li>
 *   <li>抢错：给每位仍在场玩家各 1 张；两人残局抢错则对手直接收走桌面并终局。</li>
 * </ul>
 */
public class HalliGalliBoard {

    /** 凑满该数量的水果才可拍铃 */
    public static final int COUNT_TO_RING = 5;

    /** 同一玩家重复抢铃的防抖窗口（误触/连点） */
    public static final long RING_DEBOUNCE_MS = 200;

    /**
     * 抢铃成功后的静默窗口：这段时间内到达的后续 RING 视为"迟到"，
     * 静默丢弃不回错误（D2），避免桌面已清空却被判成"抢错"赔牌。
     */
    public static final long LATE_GRACE_MS = 800;

    private final List<HalliGalliPlayer> players;

    /** 牌堆耗尽者是否仍可抢铃（D4 开关，默认 false：出局即失去抢铃权） */
    private final boolean ringAfterExhausted;

    private int flipIdx;

    private boolean finished;

    private long lastCorrectRingAt = Long.MIN_VALUE;

    /** seatNo → 上次抢铃时间，用于防抖 */
    private final Map<Integer, Long> lastRingAt = new HashMap<>();

    private RingOutcome lastRing;

    public HalliGalliBoard(List<HalliGalliPlayer> players, boolean ringAfterExhausted) {
        if (players == null || players.size() < 2) {
            throw new IllegalArgumentException("至少 2 名玩家才能开局");
        }
        this.players = new ArrayList<>(players);
        this.ringAfterExhausted = ringAfterExhausted;
    }

    /** 发牌：56 张按座位顺序轮流发，各家张数差不超过 1 */
    public static HalliGalliBoard deal(Map<Integer, String> seats, long seed) {
        return deal(seats, seed, false);
    }

    public static HalliGalliBoard deal(Map<Integer, String> seats, long seed, boolean ringAfterExhausted) {
        List<HalliGalliCard> cards = HalliGalliDeck.shuffled(seed);
        List<HalliGalliPlayer> players = new ArrayList<>();
        for (Map.Entry<Integer, String> e : new TreeMap<>(seats).entrySet()) {
            players.add(new HalliGalliPlayer(e.getKey(), e.getValue()));
        }
        int i = 0;
        for (HalliGalliCard card : cards) {
            players.get(i % players.size()).receive(card);
            i++;
        }
        return new HalliGalliBoard(players, ringAfterExhausted);
    }

    // ---- 查询 ----

    public boolean isFinished() {
        return finished;
    }

    public List<HalliGalliPlayer> players() {
        return List.copyOf(players);
    }

    public HalliGalliPlayer playerOf(int seatNo) {
        for (HalliGalliPlayer p : players) {
            if (p.getSeatNo() == seatNo) return p;
        }
        return null;
    }

    /** 当前该翻牌的座位号；对局结束或无人在场返回 null */
    public Integer flipSeatNo() {
        if (players.isEmpty() || flipIdx < 0 || flipIdx >= players.size()) return null;
        return players.get(flipIdx).getSeatNo();
    }

    /** 桌面可见水果数：只统计每位玩家的顶牌 */
    public Map<Fruit, Integer> visibleCounts() {
        Map<Fruit, Integer> counts = new EnumMap<>(Fruit.class);
        for (HalliGalliPlayer p : players) {
            HalliGalliCard top = p.topCard();
            if (top != null) counts.merge(top.fruit(), top.count(), Integer::sum);
        }
        return counts;
    }

    /** 是否可抢铃：任一种水果恰好 5 */
    public boolean isRingValid() {
        for (int count : visibleCounts().values()) {
            if (count == COUNT_TO_RING) return true;
        }
        return false;
    }

    public RingOutcome lastRing() {
        return lastRing;
    }

    public int activeCount() {
        int n = 0;
        for (HalliGalliPlayer p : players) {
            if (!p.isOut()) n++;
        }
        return n;
    }

    /** 排名：持牌（牌堆 + 明牌）多者在前，同数按座位号升序 */
    public List<Rank> rankings() {
        List<HalliGalliPlayer> sorted = new ArrayList<>(players);
        sorted.sort(Comparator.<HalliGalliPlayer>comparingInt(HalliGalliPlayer::totalCards).reversed()
            .thenComparingInt(HalliGalliPlayer::getSeatNo));
        List<Rank> ranks = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            HalliGalliPlayer p = sorted.get(i);
            ranks.add(new Rank(p.getSeatNo(), p.getName(), p.totalCards(), i + 1));
        }
        return ranks;
    }

    // ---- 动作：翻牌 ----

    /**
     * 翻牌：仅当前翻牌方、且牌堆非空时可翻。
     * 牌堆为空 → 该玩家出局（明牌留桌），翻牌权顺延；仅剩一人则终局。
     */
    public FlipOutcome flip(int seatNo) {
        if (finished) return FlipOutcome.rejected(seatNo, "NOT_PLAYING");
        HalliGalliPlayer p = playerOf(seatNo);
        if (p == null) return FlipOutcome.rejected(seatNo, "NOT_IN_GAME");
        if (p.isOut()) return FlipOutcome.rejected(seatNo, "PLAYER_OUT");
        if (!Integer.valueOf(seatNo).equals(flipSeatNo())) return FlipOutcome.rejected(seatNo, "NOT_YOUR_TURN");

        HalliGalliCard card = p.drawTop();
        if (card == null) {
            p.setOut(true);
            Integer next = advanceFlipTurn();
            refreshFinished();
            return new FlipOutcome(FlipStatus.OK, seatNo, null, null, true, next);
        }
        p.pushFaceUp(card);
        Integer next = advanceFlipTurn();
        return new FlipOutcome(FlipStatus.OK, seatNo, null, card, false, next);
    }

    // ---- 动作：抢铃 ----

    /**
     * 抢铃仲裁：先防抖，再判是否恰好 5，命中则收走桌面；
     * 未命中且处在上一轮抢铃成功的静默窗口内 → 迟到（静默丢弃）；
     * 否则判抢错，赔牌（两人残局直接终局）。
     */
    public RingOutcome ring(int seatNo, long now) {
        if (finished) return RingOutcome.rejected(seatNo, "NOT_PLAYING");
        HalliGalliPlayer p = playerOf(seatNo);
        if (p == null) return RingOutcome.rejected(seatNo, "NOT_IN_GAME");
        if (p.isOut() && !ringAfterExhausted) return RingOutcome.rejected(seatNo, "PLAYER_OUT");

        Long last = lastRingAt.get(seatNo);
        if (last != null && now - last < RING_DEBOUNCE_MS) {
            return lastRing = new RingOutcome(RingStatus.DEBOUNCED, seatNo, 0, null, List.of(), "RING_DEBOUNCED");
        }
        lastRingAt.put(seatNo, now);

        if (isRingValid()) {
            List<Penalty> none = List.of();
            int taken = sweepTableTo(p);
            lastCorrectRingAt = now;
            flipIdx = indexOf(p);
            refreshFinished();
            return lastRing = new RingOutcome(RingStatus.CORRECT, seatNo, taken, seatNo, none, null);
        }

        if (lastCorrectRingAt != Long.MIN_VALUE && now - lastCorrectRingAt <= LATE_GRACE_MS) {
            return lastRing = new RingOutcome(RingStatus.LATE, seatNo, 0, null, List.of(), "RING_LATE");
        }

        List<Penalty> penalties = payPenalty(p);
        RingOutcome outcome = new RingOutcome(RingStatus.WRONG, seatNo, 0, null, penalties, "RING_INVALID");
        // 两人残局：抢错即终局，对手直接收走桌面
        if (activeCount() == 2) {
            HalliGalliPlayer opponent = otherActiveThan(p);
            if (opponent != null) {
                int taken = sweepTableTo(opponent);
                finished = true;
                outcome = new RingOutcome(RingStatus.WRONG, seatNo, taken, opponent.getSeatNo(),
                    penalties, "TWO_PLAYER_SUDDEN_END");
            }
        }
        return lastRing = outcome;
    }

    // ---- 内部 ----

    private int sweepTableTo(HalliGalliPlayer winner) {
        int taken = 0;
        for (HalliGalliPlayer p : players) {
            List<HalliGalliCard> pile = p.takeFaceUp();
            for (HalliGalliCard card : pile) {
                winner.receive(card);
            }
            taken += pile.size();
        }
        return taken;
    }

    /** 抢错赔牌：给每位仍在场的其他玩家各 1 张，牌不够就赔到没有为止 */
    private List<Penalty> payPenalty(HalliGalliPlayer ringer) {
        List<Penalty> penalties = new ArrayList<>();
        for (HalliGalliPlayer other : players) {
            if (other == ringer || other.isOut()) continue;
            HalliGalliCard card = ringer.payOne();
            if (card == null) break;
            other.receive(card);
            penalties.add(new Penalty(other.getSeatNo(), 1));
        }
        return penalties;
    }

    private HalliGalliPlayer otherActiveThan(HalliGalliPlayer self) {
        for (HalliGalliPlayer p : players) {
            if (p != self && !p.isOut()) return p;
        }
        return null;
    }

    /** 翻牌权顺延到下一个未出局玩家；全员出局返回 null */
    private Integer advanceFlipTurn() {
        for (int step = 1; step <= players.size(); step++) {
            int idx = (flipIdx + step) % players.size();
            if (!players.get(idx).isOut()) {
                flipIdx = idx;
                return players.get(idx).getSeatNo();
            }
        }
        return null;
    }

    private void refreshFinished() {
        if (finished) return;
        if (activeCount() <= 1) {
            finished = true;
            return;
        }
        // 一人收走全部 56 张：其余人已无牌可翻，直接终局
        if (players.stream().anyMatch(p -> p.totalCards() == HalliGalliDeck.TOTAL_CARDS)) {
            finished = true;
        }
    }

    private int indexOf(HalliGalliPlayer p) {
        return players.indexOf(p);
    }

    // ---- 结果类型 ----

    public enum FlipStatus { OK, REJECTED }

    /**
     * 翻牌结果。reason 非空即被拒绝（NOT_PLAYING / NOT_IN_GAME / PLAYER_OUT / NOT_YOUR_TURN）；
     * card 为 null 且 playerOut=true 表示牌尽出局。
     */
    public record FlipOutcome(FlipStatus status, int seatNo, String reason,
                              HalliGalliCard card, boolean playerOut, Integer nextSeatNo) {
        public static FlipOutcome rejected(int seatNo, String reason) {
            return new FlipOutcome(FlipStatus.REJECTED, seatNo, reason, null, false, null);
        }

        public boolean ok() {
            return status == FlipStatus.OK;
        }
    }

    public enum RingStatus { CORRECT, WRONG, LATE, DEBOUNCED, REJECTED }

    /** 抢错时某位玩家收到的罚牌（目前固定 1 张/人，保留字段便于扩展） */
    public record Penalty(int seatNo, int cards) {
    }

    /**
     * 抢铃结果。cardsTaken>0 表示有赢家收牌：CORRECT 时 beneficiary=抢铃者；
     * WRONG 且 reason=TWO_PLAYER_SUDDEN_END 时 beneficiary=对手。
     */
    public record RingOutcome(RingStatus status, int seatNo, int cardsTaken, Integer beneficiarySeat,
                              List<Penalty> penalties, String reason) {
        public static RingOutcome rejected(int seatNo, String reason) {
            return new RingOutcome(RingStatus.REJECTED, seatNo, 0, null, List.of(), reason);
        }

        public boolean settled() {
            return status == RingStatus.CORRECT || status == RingStatus.WRONG;
        }
    }

    public record Rank(int seatNo, String name, int cardCount, int rank) {
    }
}
