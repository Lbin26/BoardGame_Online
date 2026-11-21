package org.dromara.dicego.engine;

import org.dromara.dicego.domain.SplendorCardDef;
import org.dromara.dicego.domain.SplendorNobleDef;
import org.dromara.dicego.domain.vo.SplendorCardVO;
import org.dromara.dicego.domain.vo.SplendorNobleVO;
import org.dromara.dicego.domain.vo.SplendorPlayerVO;
import org.dromara.dicego.domain.vo.SplendorSnapshotVO;
import org.dromara.dicego.enums.GemColor;
import org.dromara.dicego.service.SplendorCardCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Splendor 牌局状态：发牌（M1）+ 结算支撑（M2/M3，包内方法供 SplendorEngine 访问）。
 * 纯逻辑无 IO：随机只依赖注入的 seed（对齐 M1 表 random_seed 设计，复盘可重放）。
 * 线程安全：由 SplendorRoom 的 synchronized 方法串行访问。
 */
public class SplendorBoard {

    /** 每级桌面明卡数 */
    public static final int TABLE_COLS = 4;

    /** 各人数每色宝石供给：索引 = 玩家数-2（2人4、3人5、4人7） */
    private static final int[] SUPPLY_BY_PLAYERS = {4, 5, 7};

    /** 黄金恒定 5 */
    private static final int GOLD_SUPPLY = 5;

    private final long seed;
    private final int playerCount;
    private final Map<Integer, List<SplendorCardDef>> decks = new HashMap<>();
    private final Map<Integer, List<SplendorCardDef>> table = new HashMap<>();
    private List<SplendorNobleDef> nobles;
    private final Map<GemColor, Integer> bank = new EnumMap<>(GemColor.class);
    private final TreeMap<Integer, SplendorPlayer> players = new TreeMap<>();
    private volatile boolean finished;

    /** 回合内待办阶段（M2/M3）：主行动结算后按规则顺序解锁，未完成前不推进回合 */
    public enum PendingPhase { NONE, NOBLE_SELECT, DISCARD }

    private PendingPhase pending = PendingPhase.NONE;
    private List<SplendorNobleDef> pendingNobles = List.of();
    /** 有人达到 15VP 后置位：本轮走完即终局 */
    private boolean lastRound;

    private int currentSeatNo;
    private int roundNo = 1;

    private SplendorBoard(int playerCount, Map<Integer, String> seatNames, long seed) {
        this.playerCount = playerCount;
        this.seed = seed;
        for (Map.Entry<Integer, String> e : seatNames.entrySet()) {
            players.put(e.getKey(), new SplendorPlayer(e.getKey(), e.getValue()));
        }
        this.currentSeatNo = players.keySet().iterator().next();
        // 供后续 M2 结算使用的银行结构先按人数备好
        for (GemColor c : GemColor.values()) {
            bank.put(c, 0);
        }
        this.nobles = List.of();
    }

    /** 发牌：洗堆、翻明卡、定贵族、配银行。seatNames 为座位号→玩家名（已按 seatNo 升序）。 */
    public static SplendorBoard deal(int playerCount, Map<Integer, String> seatNames,
                                     SplendorCardCache cache, long seed) {
        if (playerCount < 2 || playerCount > 4) {
            throw new IllegalArgumentException("玩家数必须 2-4: " + playerCount);
        }
        if (seatNames.size() != playerCount) {
            throw new IllegalArgumentException("座位数与玩家数不符: " + seatNames.size() + "/" + playerCount);
        }
        SplendorBoard board = new SplendorBoard(playerCount, seatNames, seed);
        Random rnd = new Random(seed);

        for (int level = 1; level <= 3; level++) {
            List<SplendorCardDef> deck = new ArrayList<>(cache.cardsOfLevel(level));
            if (deck.size() < TABLE_COLS) {
                throw new IllegalStateException("等级 " + level + " 卡池不足 " + TABLE_COLS + " 张，无法开局");
            }
            Collections.shuffle(deck, rnd);
            board.table.put(level, new ArrayList<>(deck.subList(0, TABLE_COLS)));
            board.decks.put(level, new ArrayList<>(deck.subList(TABLE_COLS, deck.size())));
        }

        List<SplendorNobleDef> allNobles = new ArrayList<>(cache.getNobles());
        Collections.shuffle(allNobles, rnd);
        int nobleCount = playerCount + 1;
        if (allNobles.size() < nobleCount) {
            throw new IllegalStateException("贵族池不足 " + nobleCount + " 张");
        }
        board.nobles = List.copyOf(allNobles.subList(0, nobleCount));

        int supply = SUPPLY_BY_PLAYERS[playerCount - 2];
        for (GemColor c : List.of(GemColor.WHITE, GemColor.BLUE, GemColor.GREEN, GemColor.RED, GemColor.BLACK)) {
            board.bank.put(c, supply);
        }
        board.bank.put(GemColor.GOLD, GOLD_SUPPLY);
        return board;
    }

    /**
     * 按收件人生成个性化全量快照（D5）：reservedCards 仅出现在收件人本人的视图。
     * 旁观者/对手视图中该字段为 null，公开信息（宝石/加成/VP/预留数）全量可见。
     */
    public SplendorSnapshotVO snapshotFor(String viewerName) {
        Map<String, Integer> bankVO = new LinkedHashMap<>();
        for (GemColor c : GemColor.values()) {
            bankVO.put(c.getCode(), bank.getOrDefault(c, 0));
        }

        Map<String, SplendorSnapshotVO.LevelVO> levelsVO = new LinkedHashMap<>();
        for (int level = 1; level <= 3; level++) {
            levelsVO.put(String.valueOf(level), new SplendorSnapshotVO.LevelVO(
                table.getOrDefault(level, List.of()).stream().map(SplendorBoard::toCardVO).toList(),
                decks.getOrDefault(level, List.of()).size()));
        }

        List<SplendorNobleVO> noblesVO = nobles.stream().map(n -> new SplendorNobleVO(
            n.code(), n.name(), n.vp(), toStringMap(n.need()))).toList();

        Integer viewerSeat = null;
        List<SplendorPlayerVO> playersVO = new ArrayList<>();
        for (SplendorPlayer p : players.values()) {
            if (p.getName().equals(viewerName)) {
                viewerSeat = p.getSeatNo();
            }
            playersVO.add(toPlayerVO(p, viewerName));
        }

        // 待办阶段：类型对所有人公开（前端提示"等待 X 弃宝石/选贵族"），
        // 可选贵族明细仅当前行动方本人可见（D5）
        String pendingType = pending == PendingPhase.NONE ? null : pending.name();
        List<SplendorNobleVO> pendingNoblesVO = null;
        SplendorPlayer current = players.get(currentSeatNo);
        if (pending == PendingPhase.NOBLE_SELECT && current != null && current.getName().equals(viewerName)) {
            pendingNoblesVO = pendingNobles.stream().map(n -> new SplendorNobleVO(
                n.code(), n.name(), n.vp(), toStringMap(n.need()))).toList();
        }

        return new SplendorSnapshotVO(viewerSeat, finished ? "FINISHED" : "PLAYING",
            roundNo, currentSeatNo, bankVO, levelsVO, noblesVO, playersVO, pendingType, pendingNoblesVO);
    }

    private static SplendorPlayerVO toPlayerVO(SplendorPlayer p, String viewerName) {
        boolean self = p.getName().equals(viewerName);
        List<SplendorCardVO> reserved = self
            ? p.getReserved().stream().map(SplendorBoard::toCardVO).toList()
            : null;
        return new SplendorPlayerVO(p.getSeatNo(), p.getName(),
            toStringMapWithGold(p.getGems()), toStringMap(p.getBonuses()),
            p.getVp(), p.getReserved().size(), reserved);
    }

    private static SplendorCardVO toCardVO(SplendorCardDef c) {
        return new SplendorCardVO(c.code(), c.level(), c.bonus().getCode(), c.vp(), toStringMap(c.cost()));
    }

    /** 协议规定 cost/need/bonuses 恒为五色全量键（含 0 值，不含 gold） */
    private static final List<GemColor> FIVE_COLORS =
        List.of(GemColor.WHITE, GemColor.BLUE, GemColor.GREEN, GemColor.RED, GemColor.BLACK);

    private static Map<String, Integer> toStringMap(Map<GemColor, Integer> m) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (GemColor c : FIVE_COLORS) {
            out.put(c.getCode(), m.getOrDefault(c, 0));
        }
        return out;
    }

    /** 宝石持有含黄金，保持 WHITE..GOLD 顺序输出 */
    private static Map<String, Integer> toStringMapWithGold(Map<GemColor, Integer> m) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (GemColor c : GemColor.values()) {
            out.put(c.getCode(), m.getOrDefault(c, 0));
        }
        return out;
    }

    // ---- 只读访问器 ----

    public long getSeed() {
        return seed;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public int getCurrentSeatNo() {
        return currentSeatNo;
    }

    /** 当前回合数（M5 action_log.round_no 用） */
    public int getRoundNo() {
        return roundNo;
    }

    public Map<Integer, SplendorPlayer> getPlayers() {
        return players;
    }

    public Map<GemColor, Integer> getBank() {
        return bank;
    }

    public List<SplendorCardDef> tableOf(int level) {
        return table.getOrDefault(level, List.of());
    }

    public int deckLeftOf(int level) {
        return decks.getOrDefault(level, List.of()).size();
    }

    public List<SplendorNobleDef> getNobles() {
        return nobles;
    }

    public boolean isFinished() {
        return finished;
    }

    public PendingPhase getPending() {
        return pending;
    }

    public List<SplendorNobleDef> getPendingNobles() {
        return pendingNobles;
    }

    public boolean isLastRound() {
        return lastRound;
    }

    // ---- 引擎结算用包内方法（SplendorEngine 同包访问） ----

    /** 设置/清除回合内待办阶段；NOBLE_SELECT 时传入本回合可选贵族集合 */
    void setPending(PendingPhase phase, List<SplendorNobleDef> nobles) {
        this.pending = phase;
        this.pendingNobles = phase == PendingPhase.NOBLE_SELECT ? List.copyOf(nobles) : List.of();
    }

    void markLastRound() {
        this.lastRound = true;
    }

    /** 推进回合：座位号升序轮转；回到首位时轮数+1，且若已触发终局轮则终局 */
    void advanceTurn() {
        Integer next = players.higherKey(currentSeatNo);
        if (next == null) {
            next = players.firstKey();
            roundNo++;
            if (lastRound) {
                finished = true;
            }
        }
        currentSeatNo = next;
    }

    /** 从桌面明卡移除并从牌堆顶补位；未找到返回 null */
    SplendorCardDef takeFromTable(int level, String code) {
        List<SplendorCardDef> row = table.get(level);
        if (row == null) return null;
        for (int i = 0; i < row.size(); i++) {
            if (row.get(i).code().equals(code)) {
                SplendorCardDef removed = row.remove(i);
                List<SplendorCardDef> deck = decks.get(level);
                if (deck != null && !deck.isEmpty()) {
                    row.add(deck.remove(0));
                }
                return removed;
            }
        }
        return null;
    }

    /** 抽某级牌堆顶（盲留用）；已空返回 null */
    SplendorCardDef drawDeckTop(int level) {
        List<SplendorCardDef> deck = decks.get(level);
        return (deck == null || deck.isEmpty()) ? null : deck.remove(0);
    }

    /** 桌面卡查找（跨三级行） */
    SplendorCardDef findOnTable(String code) {
        for (int level = 1; level <= 3; level++) {
            for (SplendorCardDef c : table.getOrDefault(level, List.of())) {
                if (c.code().equals(code)) return c;
            }
        }
        return null;
    }

    /** 贵族拜访后移出桌面 */
    void removeNoble(SplendorNobleDef noble) {
        nobles = nobles.stream().filter(n -> n != noble).toList();
    }

    void bankAdd(GemColor color, int amount) {
        bank.merge(color, amount, Integer::sum);
    }

    /** 银行支出（引擎须先校验足额，此处 fail-fast 兜底） */
    void bankRemove(GemColor color, int amount) {
        int left = bank.getOrDefault(color, 0) - amount;
        if (left < 0) throw new IllegalStateException("银行宝石不足: " + color + " " + amount);
        bank.put(color, left);
    }
}
