package org.dromara.magicalathlete.engine;

import org.dromara.magicalathlete.domain.Racer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轮抽会话：服务端维护牌堆、当前批牌池、每人已选手牌与选序（D7）。
 *
 * <p>纯逻辑、无 IO、无锁；房间负责 synchronized 包裹对本对象的方法调用。
 * 洗牌用 seed 确定性生成，便于复盘与测试。
 */
public class DraftSession {

    /** 选牌结果：ok=true 时 racer 为被选走的选手；否则 reason 为失败码 */
    public record PickOutcome(boolean ok, String reason, Racer racer, boolean batchAdvanced, boolean finished) {
        static PickOutcome reject(String reason) {
            return new PickOutcome(false, reason, null, false, false);
        }
    }

    private final int playerCount;
    /** M5：人数变体；默认按人数推断，普通局/2 人局行为与 M1 完全一致 */
    private final RaceVariant variant;
    private final Deque<Racer> deck;
    private final Map<Integer, List<Racer>> hands = new LinkedHashMap<>();

    private int batch;
    private List<Racer> pool = List.of();
    private List<Integer> pickOrder = List.of();
    private int pickIndex;
    private boolean finished;

    /** M1 原构造子：按人数推断默认变体，行为零回归 */
    public DraftSession(long seed, int playerCount) {
        this(seed, playerCount, RaceVariant.defaultOf(playerCount));
    }

    /** M5：显式指定人数变体（3 人双角色局用 {@link RaceVariant#THREE_DOUBLE}） */
    public DraftSession(long seed, int playerCount, RaceVariant variant) {
        this.playerCount = playerCount;
        this.variant = variant == null ? RaceVariant.defaultOf(playerCount) : variant;
        this.deck = new ArrayDeque<>(RacerCatalog.shuffled(seed));
        for (int i = 1; i <= playerCount; i++) {
            hands.put(i, new ArrayList<>());
        }
        loadBatch(0);
    }

    public RaceVariant variant() {
        return variant;
    }

    private void loadBatch(int batchNo) {
        this.batch = batchNo;
        int size = variant.poolSize(playerCount);
        List<Racer> batchPool = new ArrayList<>(size);
        for (int i = 0; i < size && !deck.isEmpty(); i++) {
            batchPool.add(deck.pollFirst());
        }
        this.pool = batchPool;
        this.pickOrder = variant.pickOrder(playerCount, batchNo);
        this.pickIndex = 0;
    }

    /** 当前应执行的座位号；轮抽完成返回 null */
    public Integer currentSeatNo() {
        if (finished || pickIndex >= pickOrder.size()) return null;
        return pickOrder.get(pickIndex);
    }

    public int batch() {
        return batch;
    }

    public int pickIndex() {
        return pickIndex;
    }

    public boolean finished() {
        return finished;
    }

    public List<Integer> pickOrder() {
        return Collections.unmodifiableList(pickOrder);
    }

    /** 当前批可被选走的牌池（不可变视图） */
    public List<Racer> pool() {
        return Collections.unmodifiableList(pool);
    }

    /** 各座位已选中的选手（值为不可变视图） */
    public Map<Integer, List<Racer>> hands() {
        Map<Integer, List<Racer>> view = new LinkedHashMap<>();
        hands.forEach((seat, list) -> view.put(seat, Collections.unmodifiableList(list)));
        return Collections.unmodifiableMap(view);
    }

    /** 当前牌池里是否存在该 key */
    public boolean inPool(String racerKey) {
        for (Racer r : pool) {
            if (r.key().equals(racerKey)) return true;
        }
        return false;
    }

    /**
     * 选一名选手。校验顺序 → 校验 key 在牌池 → 落入手牌 → 推进序位。
     * 一批走完自动装载下一批；两批走完置 finished。
     */
    public PickOutcome pick(int seatNo, String racerKey) {
        if (finished) return PickOutcome.reject("DRAFT_FINISHED");
        Integer expected = currentSeatNo();
        if (expected == null || expected != seatNo) return PickOutcome.reject("NOT_YOUR_TURN");
        if (racerKey == null) return PickOutcome.reject("RACER_NOT_IN_POOL");

        Racer picked = null;
        for (Racer r : pool) {
            if (r.key().equals(racerKey)) {
                picked = r;
                break;
            }
        }
        if (picked == null) return PickOutcome.reject("RACER_NOT_IN_POOL");

        pool.remove(picked);
        hands.get(seatNo).add(picked);
        pickIndex++;

        boolean advanced = false;
        if (pickIndex >= pickOrder.size()) {
            if (batch + 1 < variant.batchCount()) {
                loadBatch(batch + 1);
                advanced = true;
            } else {
                finished = true;
            }
        }
        return new PickOutcome(true, null, picked, advanced, finished);
    }

    /**
     * 超时代选（D6）：为当前序位玩家自动选牌池最左一张。
     * 无当前序位（已结束）返回 reject。
     */
    public PickOutcome autoPick() {
        Integer seat = currentSeatNo();
        if (seat == null) return PickOutcome.reject("DRAFT_FINISHED");
        String key = pool.isEmpty() ? null : pool.get(0).key();
        return pick(seat, key);
    }
}
