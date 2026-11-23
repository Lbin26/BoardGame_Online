package org.dromara.incangold.engine;

import org.dromara.incangold.domain.vo.IncanGoldSnapshotVO;
import org.dromara.incangold.engine.IncanGoldCards.Card;
import org.dromara.incangold.engine.IncanGoldCards.HazardType;
import org.dromara.incangold.engine.IncanGoldCards.Kind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * 印加宝藏规则引擎：5 轮探险，翻牌结算 → 秘密同时决策 → 撤退结算 → 下一张牌。
 *
 * <p>纯逻辑、零外部依赖，所有公开方法由房间层在 synchronized 内调用。
 * 翻牌由引擎自动推进，玩家只提交 CONTINUE / RETREAT 暗选；
 * 决策超时视为 CONTINUE（继续探险），避免挂机卡死全场。
 *
 * <p>隐藏信息：decisions 里的选择内容不会进入快照，快照只暴露"是否已提交"。
 */
public final class IncanGoldEngine {

    public static final int TOTAL_ROUNDS = 5;
    public static final int MIN_PLAYERS = 3;
    public static final int MAX_PLAYERS = 8;
    public static final int HAZARD_COPIES = 3;
    public static final int ARTIFACT_TOTAL = 5;
    public static final int DECIDE_TIMEOUT_MS = 10_000;
    public static final int ROUND_END_DISPLAY_MS = 4_000;

    public enum Choice {
        CONTINUE, RETREAT
    }

    public enum Phase {
        DECIDE, ROUND_END, FINISHED
    }

    public enum EndCause {
        ALL_RETREAT, COLLAPSE, DECK_EMPTY
    }

    public enum SubmitResult {
        SUCCESS, NOT_IN_DECIDE, NO_SUCH_PLAYER, NOT_IN_EXPEDITION, ALREADY_SUBMITTED
    }

    public enum AutoResult {
        NONE, DECIDE_TIMEOUT, ROUND_ADVANCED
    }

    /** 单个玩家的引擎内状态；宝石分「营地安全」与「本轮临时」两部分 */
    public static final class PlayerState {
        private final int seatNo;
        private final String name;
        private int campGems;
        private int roundGems;
        private int artifacts;
        private boolean inExpedition;
        private Choice submitted;

        PlayerState(int seatNo, String name) {
            this.seatNo = seatNo;
            this.name = name;
        }

        public int seatNo() {
            return seatNo;
        }

        public String name() {
            return name;
        }

        public int campGems() {
            return campGems;
        }

        public int roundGems() {
            return roundGems;
        }

        public int artifacts() {
            return artifacts;
        }

        public boolean inExpedition() {
            return inExpedition;
        }

        public Choice submitted() {
            return submitted;
        }

        void addCamp(int n) {
            campGems += n;
        }

        void addRound(int n) {
            roundGems += n;
        }

        void clearRound() {
            roundGems = 0;
        }

        void setInExpedition(boolean value) {
            inExpedition = value;
        }

        void submit(Choice choice) {
            submitted = choice;
        }

        void clearSubmitted() {
            submitted = null;
        }

        void addArtifact() {
            artifacts++;
        }
    }

    public record Event(long seq, String code, String message, Map<String, Object> data) {
    }

    public record Ranking(int seatNo, String name, int gems, int artifacts, int score) {
    }

    private final List<PlayerState> players = new ArrayList<>();
    private final EnumMap<HazardType, Integer> hazardRemaining = new EnumMap<>(HazardType.class);
    private final EnumMap<HazardType, Integer> hazardThisRound = new EnumMap<>(HazardType.class);
    private final List<Card> artifactDeck = new ArrayList<>();
    private final List<Integer> artifactOrder = new ArrayList<>();
    private final List<Card> deck = new ArrayList<>();
    private final List<Card> field = new ArrayList<>();
    private final List<Event> history = new ArrayList<>();
    private final Random random;

    private int round;
    private int fieldArtifacts;
    private Phase phase = Phase.FINISHED;
    private EndCause endCause;
    private long phaseDeadlineAt;
    private long eventSeq;
    private List<Ranking> rankings = List.of();

    public IncanGoldEngine(long seed) {
        this.random = new Random(seed);
    }

    // ---- 生命周期 ----

    public void startGame(List<String> names) {
        if (names == null || names.size() < MIN_PLAYERS || names.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("印加宝藏需要 3-8 名玩家");
        }
        players.clear();
        for (int i = 0; i < names.size(); i++) {
            players.add(new PlayerState(i + 1, names.get(i)));
        }
        hazardRemaining.clear();
        for (HazardType type : HazardType.values()) {
            hazardRemaining.put(type, HAZARD_COPIES);
        }
        artifactDeck.clear();
        for (int i = 0; i < ARTIFACT_TOTAL; i++) {
            artifactDeck.add(Card.artifact(i + 1));
        }
        shuffle(artifactDeck);
        artifactOrder.clear();
        history.clear();
        eventSeq = 0;
        rankings = List.of();
        round = 0;
        emit("GAME_START", "火把依次点亮，神庙石门轰然开启——五轮探险，就此启程。", Map.of("playerCount", names.size()));
        beginRound();
    }

    private void beginRound() {
        round++;
        endCause = null;
        deck.clear();
        field.clear();
        fieldArtifacts = 0;
        hazardThisRound.clear();
        for (PlayerState p : players) {
            p.clearRound();
            p.setInExpedition(true);
            p.clearSubmitted();
        }

        // 每轮重新洗入 15 张宝藏 + 尚未永久移除的灾难 + 1 张神器
        for (int value = 1; value <= 15; value++) {
            deck.add(Card.treasure(value));
        }
        for (HazardType type : HazardType.values()) {
            int copies = hazardRemaining.getOrDefault(type, 0);
            for (int i = 0; i < copies; i++) {
                deck.add(Card.hazard(type));
            }
        }
        if (!artifactDeck.isEmpty()) {
            deck.add(artifactDeck.remove(0));
        }
        shuffle(deck);

        emit("ROUND_START", "第 " + round + " 轮：探险家们鱼贯而入，甬道深处传来金币相碰的脆响。", Map.of("round", round));
        flipNext();
    }

    /** 翻一张牌并结算；未触发轮次结束时进入 DECIDE 阶段 */
    private void flipNext() {
        if (deck.isEmpty()) {
            endRound(EndCause.DECK_EMPTY, null);
            return;
        }
        Card card = deck.remove(deck.size() - 1);
        List<PlayerState> active = activePlayers();
        switch (card.kind()) {
            case TREASURE -> {
                int n = active.size();
                int share = n == 0 ? 0 : card.value() / n;
                int remainder = n == 0 ? card.value() : card.value() % n;
                for (PlayerState p : active) {
                    p.addRound(share);
                }
                card.addRemainder(remainder);
                field.add(card);
                emit("TREASURE", "石缝里滚出一袋宝石：" + card.value() + " 颗被在场 " + n
                    + " 人平分，每人兜进 " + share + " 颗，剩下 " + remainder + " 颗还留在牌上。",
                    Map.of("value", card.value(), "share", share, "remainder", remainder));
            }
            case HAZARD -> {
                HazardType type = card.hazard();
                int count = hazardThisRound.getOrDefault(type, 0) + 1;
                hazardThisRound.put(type, count);
                field.add(card);
                if (count >= 2) {
                    // 团灭：本轮所得清零；引发结束的这张牌永久移出
                    for (PlayerState p : active) {
                        p.clearRound();
                        p.setInExpedition(false);
                    }
                    int left = Math.max(0, hazardRemaining.getOrDefault(type, 0) - 1);
                    hazardRemaining.put(type, left);
                    emit("COLLAPSE", hazardLabel(type) + "再度现身！穹顶轰然塌落，尚未撤离者的宝石尽数埋进尘土；"
                        + "这张灾难牌也被永远封存在石堆下。",
                        Map.of("hazard", type.code(), "remaining", left));
                    endRound(EndCause.COLLAPSE, null);
                    return;
                }
                emit("HAZARD_FIRST", hazardLabel(type) + "的影子第一次掠过甬道，众人屏住呼吸——石壁只是微微一颤，暂无异样。",
                    Map.of("hazard", type.code()));
            }
            case ARTIFACT -> {
                fieldArtifacts++;
                field.add(card);
                emit("ARTIFACT", "沙尘中露出一尊金像——神器现世，此刻它还不属于任何人。",
                    Map.of("artifact", card.value()));
            }
        }
        phase = Phase.DECIDE;
        phaseDeadlineAt = System.currentTimeMillis() + DECIDE_TIMEOUT_MS;
    }

    // ---- 秘密决策 ----

    /**
     * 提交暗选。只有本人视角能知道自己选了什么；快照仅暴露"是否已提交"。
     * 全部在场玩家提交后立即揭示并推进。
     */
    public SubmitResult submitChoice(int seatNo, Choice choice) {
        if (phase != Phase.DECIDE || choice == null) {
            return SubmitResult.NOT_IN_DECIDE;
        }
        PlayerState player = playerOf(seatNo);
        if (player == null) {
            return SubmitResult.NO_SUCH_PLAYER;
        }
        if (!player.inExpedition()) {
            return SubmitResult.NOT_IN_EXPEDITION;
        }
        if (player.submitted() != null) {
            return SubmitResult.ALREADY_SUBMITTED;
        }
        player.submit(choice);
        emit("CHOICE_MADE", player.name() + " 把羊皮纸扣在胸口，写下只有自己看得见的去留。", Map.of("seatNo", seatNo));
        List<PlayerState> active = activePlayers();
        if (!active.isEmpty() && active.stream().allMatch(p -> p.submitted() != null)) {
            resolveDecisions();
        }
        return SubmitResult.SUCCESS;
    }

    /** 决策揭示：撤退者落袋、余数与神器结算，然后自动翻下一张或结束本轮 */
    private void resolveDecisions() {
        List<PlayerState> active = activePlayers();
        List<PlayerState> retirees = new ArrayList<>();
        List<PlayerState> stay = new ArrayList<>();
        for (PlayerState p : active) {
            (p.submitted() == Choice.RETREAT ? retirees : stay).add(p);
        }

        // 先落袋：撤退者本轮宝石永久安全
        for (PlayerState p : retirees) {
            p.addCamp(p.roundGems());
            p.clearRound();
            p.setInExpedition(false);
            p.clearSubmitted();
        }
        for (PlayerState p : stay) {
            p.clearSubmitted();
        }

        int totalRemainder = 0;
        for (Card card : field) {
            totalRemainder += card.remainder();
        }

        if (retirees.size() == 1) {
            PlayerState solo = retirees.get(0);
            int artifactsTaken = fieldArtifacts;
            solo.addCamp(totalRemainder);
            for (Card card : field) {
                card.clearRemainder();
            }
            for (int i = 0; i < artifactsTaken; i++) {
                artifactOrder.add(solo.seatNo());
                solo.addArtifact();
            }
            fieldArtifacts = 0;
            emit("RETREAT_SOLO", solo.name() + " 独自拨开石门：卷走牌上 " + totalRemainder
                + " 颗余数宝石" + (artifactsTaken > 0 ? "，还抱走 " + artifactsTaken + " 张神器" : "")
                + "，身后只留下一串急促的脚步声。",
                Map.of("seatNo", solo.seatNo(), "remainder", totalRemainder, "artifacts", artifactsTaken));
        } else if (retirees.size() > 1) {
            int share = totalRemainder / retirees.size();
            int leftover = totalRemainder % retirees.size();
            for (PlayerState p : retirees) {
                p.addCamp(share);
            }
            for (Card card : field) {
                card.clearRemainder();
            }
            // 除不尽的余数留在场上，等待后续撤退者或下一轮结算
            if (leftover > 0) {
                for (Card card : field) {
                    if (card.kind() == Kind.TREASURE) {
                        card.addRemainder(leftover);
                        break;
                    }
                }
            }
            // 神器不归任何人：留在场上，直到有人独走或本轮结束作废
            emit("RETREAT_MULTI", retirees.size() + " 人不约而同转身撤离，余数宝石被平分，各得 " + share
                + " 颗" + (leftover > 0 ? "（还有 " + leftover + " 颗遗落在牌上）" : "")
                + "；神像无人认领，被重新埋进神庙深处。",
                Map.of("count", retirees.size(), "share", share, "leftover", leftover));
        }

        if (stay.isEmpty()) {
            endRound(EndCause.ALL_RETREAT, null);
        } else {
            flipNext();
        }
    }

    /** 超时兜底：决策超时未提交者视为 CONTINUE；结算展示超时后自动进入下一轮 */
    public AutoResult autoAdvance(long now) {
        if (phase == Phase.DECIDE && now >= phaseDeadlineAt) {
            List<PlayerState> active = activePlayers();
            for (PlayerState p : active) {
                if (p.submitted() == null) {
                    p.submit(Choice.CONTINUE);
                    emit("AUTO_CONTINUE", p.name() + " 迟疑太久，羊皮纸被黑暗卷走——只能跟着队伍继续深入。",
                        Map.of("seatNo", p.seatNo()));
                }
            }
            if (!active.isEmpty()) {
                resolveDecisions();
            }
            return AutoResult.DECIDE_TIMEOUT;
        }
        if (phase == Phase.ROUND_END && now >= phaseDeadlineAt) {
            advanceRound();
            return AutoResult.ROUND_ADVANCED;
        }
        return AutoResult.NONE;
    }

    /** 手动推进结算展示（正常由 autoAdvance 定时调用） */
    public void advanceRound() {
        if (phase != Phase.ROUND_END) {
            return;
        }
        if (round >= TOTAL_ROUNDS) {
            return;
        }
        beginRound();
    }

    private void endRound(EndCause cause, String message) {
        phase = Phase.ROUND_END;
        endCause = cause;
        // 场上神器无人独走则本轮作废
        fieldArtifacts = 0;
        for (PlayerState p : players) {
            p.setInExpedition(false);
            p.clearSubmitted();
        }
        phaseDeadlineAt = System.currentTimeMillis() + ROUND_END_DISPLAY_MS;
        String text = message != null ? message : switch (cause) {
            case ALL_RETREAT -> "最后一人也走出石门，门在身后合拢——本轮落幕，无人再冒险。";
            case DECK_EMPTY -> "甬道尽头的石壁已无路可走；还留在洞中的人只能空手而返。";
            case COLLAPSE -> "尘土缓缓落定，塌方封死去路——本轮到此为止。";
        };
        emit("ROUND_END", text, Map.of("round", round, "cause", cause.name()));
        if (round >= TOTAL_ROUNDS) {
            finish();
        }
    }

    private void finish() {
        phase = Phase.FINISHED;
        phaseDeadlineAt = 0;

        // 神器按全场获得先后计分：前 3 张各 5 分，第 4、5 张各 10 分
        Map<Integer, Integer> artifactPoints = new HashMap<>();
        for (int i = 0; i < artifactOrder.size(); i++) {
            int seat = artifactOrder.get(i);
            artifactPoints.merge(seat, i < 3 ? 5 : 10, Integer::sum);
        }
        List<Ranking> result = new ArrayList<>();
        for (PlayerState p : players) {
            int gems = p.campGems();
            result.add(new Ranking(p.seatNo(), p.name(), gems, p.artifacts(),
                gems + artifactPoints.getOrDefault(p.seatNo(), 0)));
        }
        // 同分则神器多者胜，再按座位号稳定排序
        result.sort((a, b) -> {
            int c = Integer.compare(b.score(), a.score());
            if (c != 0) return c;
            c = Integer.compare(b.artifacts(), a.artifacts());
            if (c != 0) return c;
            return Integer.compare(a.seatNo(), b.seatNo());
        });
        rankings = List.copyOf(result);
        String winner = rankings.isEmpty() ? "" : rankings.get(0).name();
        emit("GAME_OVER", "第五轮的火把熄灭，" + winner + " 捧着最多的宝石走出神庙，赢得这场探险。", Map.of("winner", winner));
    }

    // ---- 查询 ----

    /**
     * 按收件人生成快照：他人宝石为 null（帐篷遮挡），本人可见真实数。
     * choice 内容永不进入快照。
     */
    public IncanGoldSnapshotVO snapshot(String viewerName, Predicate<String> online, Predicate<String> afk) {
        Integer viewerSeat = null;
        for (PlayerState p : players) {
            if (p.name().equals(viewerName)) {
                viewerSeat = p.seatNo();
                break;
            }
        }

        List<IncanGoldSnapshotVO.FieldCardVO> fieldVO = new ArrayList<>();
        for (Card card : field) {
            fieldVO.add(new IncanGoldSnapshotVO.FieldCardVO(card.kind().name(), card.value(),
                card.hazard() == null ? null : card.hazard().code(), card.remainder()));
        }

        List<IncanGoldSnapshotVO.PlayerVO> playerVO = new ArrayList<>();
        for (PlayerState p : players) {
            boolean self = viewerSeat != null && viewerSeat == p.seatNo();
            playerVO.add(new IncanGoldSnapshotVO.PlayerVO(p.seatNo(), p.name(),
                online.test(p.name()), afk.test(p.name()), p.inExpedition(), p.submitted() != null,
                self ? p.campGems() : null, self ? p.roundGems() : null, p.artifacts()));
        }

        Map<String, Integer> hazards = new LinkedHashMap<>();
        for (HazardType type : HazardType.values()) {
            hazards.put(type.code(), hazardRemaining.getOrDefault(type, 0));
        }

        int from = Math.max(0, history.size() - 12);
        List<IncanGoldSnapshotVO.EventVO> events = new ArrayList<>();
        for (int i = from; i < history.size(); i++) {
            Event e = history.get(i);
            events.add(new IncanGoldSnapshotVO.EventVO(e.seq(), e.code(), e.message(), e.data()));
        }

        List<IncanGoldSnapshotVO.RankingVO> rankVO = new ArrayList<>();
        for (Ranking r : rankings) {
            rankVO.add(new IncanGoldSnapshotVO.RankingVO(r.seatNo(), r.name(), r.gems(), r.artifacts(), r.score()));
        }

        return new IncanGoldSnapshotVO(phase.name(), round,
            endCause == null ? null : endCause.name(), phaseDeadlineAt,
            fieldVO, fieldArtifacts, hazards, playerVO, events, rankVO);
    }

    public Phase phase() {
        return phase;
    }

    public int round() {
        return round;
    }

    public long eventSeq() {
        return eventSeq;
    }

    public long phaseDeadlineAt() {
        return phaseDeadlineAt;
    }

    public List<Ranking> rankings() {
        return rankings;
    }

    public List<PlayerState> players() {
        return List.copyOf(players);
    }

    private PlayerState playerOf(int seatNo) {
        for (PlayerState p : players) {
            if (p.seatNo() == seatNo) return p;
        }
        return null;
    }

    private List<PlayerState> activePlayers() {
        List<PlayerState> active = new ArrayList<>();
        for (PlayerState p : players) {
            if (p.inExpedition()) active.add(p);
        }
        return active;
    }

    private void emit(String code, String message, Map<String, Object> data) {
        history.add(new Event(++eventSeq, code, message, data == null ? Map.of() : data));
        if (history.size() > 50) {
            history.remove(0);
        }
    }

    private void shuffle(List<?> list) {
        java.util.Collections.shuffle(list, random);
    }

    private static String hazardLabel(HazardType type) {
        return switch (type) {
            case FIRE -> "火灾";
            case MUMMY -> "木乃伊";
            case ROCK -> "落石";
            case SNAKE -> "蛇";
            case SPIDER -> "蜘蛛";
        };
    }
}
