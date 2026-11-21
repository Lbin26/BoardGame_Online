package org.dromara.dicego.engine;

import org.dromara.dicego.domain.SplendorCardDef;
import org.dromara.dicego.domain.SplendorNobleDef;
import org.dromara.dicego.domain.vo.SplendorRankVO;
import org.dromara.dicego.enums.GemColor;
import org.dromara.dicego.enums.SplendorFailReason;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.dromara.dicego.engine.SplendorActionResult.fail;
import static org.dromara.dicego.engine.SplendorActionResult.ok;

/**
 * Splendor 行动引擎（M2/M3）：TAKE_GEMS / BUY_CARD / RESERVE_CARD / DISCARD_GEMS / NOBLE_SELECT
 * 的校验与结算，全部为纯静态函数（协议 §2.1 校验函数化）。
 *
 * 结算管道（每个主行动成功后统一走）：
 *   主行动生效 → 贵族检查（唯一满足自动拜访；多满足进入 NOBLE_SELECT 待办）
 *   → 15VP 触发终局轮标记 → 手牌 &gt;10 进入 DISCARD 待办 → 推进回合
 * 待办未完成前：不推进回合，主行动被拒（MUST_DISCARD）。
 * 线程安全：静态无状态，串行性由 SplendorRoom 的 synchronized 包装保证。
 */
public final class SplendorEngine {

    /** 胜利 VP（官方规则） */
    public static final int WIN_VP = 15;
    /** 手牌宝石上限（含黄金） */
    public static final int GEM_HAND_LIMIT = 10;
    /** 预留卡上限 */
    public static final int RESERVE_LIMIT = 3;
    /** 同色拿二所需最低供给（官方规则） */
    public static final int DOUBLE_TAKE_SUPPLY = 4;

    private static final List<GemColor> FIVE_COLORS =
        List.of(GemColor.WHITE, GemColor.BLUE, GemColor.GREEN, GemColor.RED, GemColor.BLACK);

    private SplendorEngine() {
    }

    // ==================== TAKE_GEMS ====================

    /**
     * 拿宝石。合法组合（协议 §2.1）：
     * 三异色各 1；或同色 ×2（银行该色 ≥4）。
     * 边缘规则：银行不足 3 个异色时，允许拿 2 或 1 颗异色各 1。
     */
    public static SplendorActionResult takeGems(SplendorBoard board, String playerName, Map<GemColor, Integer> gems) {
        SplendorPlayer actor = preflightMain(board, playerName);
        if (actor == null) return preflightFail(board, playerName);

        if (gems == null || gems.isEmpty()) {
            return fail(SplendorFailReason.INVALID_GEMS, "未选择宝石");
        }
        // 黄金不可直接拿取
        if (gems.getOrDefault(GemColor.GOLD, 0) != 0) {
            return fail(SplendorFailReason.INVALID_GEMS, "黄金不可直接拿取，仅能通过预留获得");
        }
        for (Map.Entry<GemColor, Integer> e : gems.entrySet()) {
            if (e.getValue() < 0 || e.getValue() > 2) {
                return fail(SplendorFailReason.INVALID_GEMS, "单色拿取数须在 0-2 之间");
            }
        }

        Map<GemColor, Integer> take = new EnumMap<>(GemColor.class);
        int total = 0;
        int distinct = 0;
        for (GemColor c : FIVE_COLORS) {
            int v = gems.getOrDefault(c, 0);
            if (v > 0) {
                take.put(c, v);
                total += v;
                distinct++;
            }
        }

        if (distinct == 1 && total == 2) {
            // 同色 ×2：供给须 ≥4
            GemColor c = take.keySet().iterator().next();
            if (board.getBank().getOrDefault(c, 0) < DOUBLE_TAKE_SUPPLY) {
                return fail(SplendorFailReason.GEM_SUPPLY_SHORT, "该色供给不足 4，不能同色拿二");
            }
        } else if (distinct >= 1 && take.values().stream().allMatch(v -> v == 1)) {
            // 异色各 1：满 3 颗；不足 3 颗时仅当银行凑不齐 3 个异色
            if (distinct < 3 && distinctColorsInBank(board) >= 3) {
                return fail(SplendorFailReason.INVALID_GEMS, "须拿满 3 颗异色宝石");
            }
        } else {
            return fail(SplendorFailReason.INVALID_GEMS, "合法组合为三异色各 1 或同色 ×2");
        }

        // 银行足额校验（异色组合逐色 ≥1）
        for (Map.Entry<GemColor, Integer> e : take.entrySet()) {
            if (board.getBank().getOrDefault(e.getKey(), 0) < e.getValue()) {
                return fail(SplendorFailReason.GEM_SUPPLY_SHORT, "银行 " + e.getKey().getCode() + " 供给不足");
            }
        }

        for (Map.Entry<GemColor, Integer> e : take.entrySet()) {
            board.bankRemove(e.getKey(), e.getValue());
            actor.addGem(e.getKey(), e.getValue());
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("gems", toWireMap(take));
        endTurn(board, actor);
        return ok(detail);
    }

    // ==================== BUY_CARD ====================

    /**
     * 买发展卡：桌面明卡或本人预留。支付方案由引擎计算（协议 §2.1：黄金自动补差，
     * 客户端不指定），实付 = 各色 min(需求-加成, 持有)，差额用黄金。
     */
    public static SplendorActionResult buyCard(SplendorBoard board, String playerName, String cardId, String source) {
        SplendorPlayer actor = preflightMain(board, playerName);
        if (actor == null) return preflightFail(board, playerName);

        if (cardId == null || cardId.isBlank()) {
            return fail(SplendorFailReason.INVALID_PARAM, "缺少 cardId");
        }
        boolean fromReserved = "reserved".equals(source);
        if (source != null && !fromReserved && !"table".equals(source)) {
            return fail(SplendorFailReason.INVALID_PARAM, "source 须为 table 或 reserved");
        }

        SplendorCardDef card = fromReserved
            ? actor.getReserved().stream().filter(c -> c.code().equals(cardId)).findFirst().orElse(null)
            : board.findOnTable(cardId);
        if (card == null) {
            return fail(fromReserved ? SplendorFailReason.CARD_NOT_RESERVED : SplendorFailReason.CARD_NOT_ON_TABLE,
                fromReserved ? "该卡不在你的预留区" : "该卡不在桌面明卡中");
        }

        // 支付计算：先扣加成，再按各色实持有支付，缺口黄金补
        Map<GemColor, Integer> payment = new EnumMap<>(GemColor.class);
        int totalNeed = 0;
        int payable = 0;
        for (GemColor c : FIVE_COLORS) {
            int need = Math.max(0, card.costOf(c) - actor.getBonuses().getOrDefault(c, 0));
            if (need == 0) continue;
            int pay = Math.min(need, actor.getGems().getOrDefault(c, 0));
            payment.put(c, pay);
            totalNeed += need;
            payable += pay;
        }
        int goldUsed = totalNeed - payable;
        if (goldUsed > actor.getGems().getOrDefault(GemColor.GOLD, 0)) {
            return fail(SplendorFailReason.CANNOT_AFFORD, "宝石 + 黄金不足以支付该卡");
        }

        for (Map.Entry<GemColor, Integer> e : payment.entrySet()) {
            if (e.getValue() > 0) {
                actor.removeGem(e.getKey(), e.getValue());
                board.bankAdd(e.getKey(), e.getValue());
            }
        }
        if (goldUsed > 0) {
            actor.removeGem(GemColor.GOLD, goldUsed);
            board.bankAdd(GemColor.GOLD, goldUsed);
            payment.put(GemColor.GOLD, goldUsed);
        }

        if (fromReserved) {
            actor.removeReserved(card);
        } else {
            board.takeFromTable(card.level(), cardId);
        }
        actor.addBonus(card.bonus());
        actor.addVp(card.vp());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("cardId", cardId);
        detail.put("source", fromReserved ? "reserved" : "table");
        detail.put("paid", toWireMapWithGold(payment));
        detail.put("vp", card.vp());
        endTurn(board, actor);
        return ok(detail);
    }

    // ==================== RESERVE_CARD ====================

    /**
     * 预留卡：桌面明卡（cardId）或某级牌堆顶盲留（level）。
     * 成功后银行有黄金则必得 1 颗（无黄金也允许预留，协议 §3 NO_GOLD_LEFT 不作拒绝码）。
     */
    public static SplendorActionResult reserveCard(SplendorBoard board, String playerName, String cardId, Integer level) {
        SplendorPlayer actor = preflightMain(board, playerName);
        if (actor == null) return preflightFail(board, playerName);

        if (actor.getReserved().size() >= RESERVE_LIMIT) {
            return fail(SplendorFailReason.RESERVE_LIMIT, "预留卡已达 " + RESERVE_LIMIT + " 张上限");
        }
        if (cardId == null && level == null) {
            return fail(SplendorFailReason.INVALID_PARAM, "须指定 cardId（明卡）或 level（盲留）");
        }
        if (level != null && (level < 1 || level > 3)) {
            return fail(SplendorFailReason.INVALID_PARAM, "level 须为 1-3");
        }

        String source;
        SplendorCardDef card;
        if (cardId != null) {
            card = board.findOnTable(cardId);
            if (card == null) {
                return fail(SplendorFailReason.CARD_NOT_ON_TABLE, "该卡不在桌面明卡中");
            }
            board.takeFromTable(card.level(), cardId);
            source = "table";
        } else {
            card = board.drawDeckTop(level);
            if (card == null) {
                return fail(SplendorFailReason.DECK_EMPTY, "等级 " + level + " 牌堆已耗尽");
            }
            source = "deck";
        }
        actor.addReserved(card);

        int goldGained = 0;
        if (board.getBank().getOrDefault(GemColor.GOLD, 0) > 0) {
            board.bankRemove(GemColor.GOLD, 1);
            actor.addGem(GemColor.GOLD, 1);
            goldGained = 1;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        // 盲留不泄漏卡面（隐藏信息）；明卡预留的 cardId 本就公开
        if ("table".equals(source)) {
            detail.put("cardId", cardId);
        } else {
            detail.put("level", level);
        }
        detail.put("source", source);
        detail.put("goldGained", goldGained);
        endTurn(board, actor);
        return ok(detail);
    }

    // ==================== DISCARD_GEMS ====================

    /** 弃宝石：仅 DISCARD 待办阶段可发；数量须恰好 = 手牌 - 10，且不超各色持有 */
    public static SplendorActionResult discardGems(SplendorBoard board, String playerName, Map<GemColor, Integer> gems) {
        SplendorPlayer actor = preflightTurn(board, playerName);
        if (actor == null) return preflightFail(board, playerName);

        if (board.getPending() != SplendorBoard.PendingPhase.DISCARD) {
            return fail(SplendorFailReason.DISCARD_INVALID, "当前无需弃宝石");
        }
        if (gems == null) {
            return fail(SplendorFailReason.DISCARD_INVALID, "未指定弃置宝石");
        }
        int overflow = actor.gemCount() - GEM_HAND_LIMIT;
        int total = 0;
        for (Map.Entry<GemColor, Integer> e : gems.entrySet()) {
            if (e.getValue() < 0) {
                return fail(SplendorFailReason.DISCARD_INVALID, "弃置数不能为负");
            }
            if (e.getValue() > actor.getGems().getOrDefault(e.getKey(), 0)) {
                return fail(SplendorFailReason.DISCARD_INVALID, "弃置数超过该色持有");
            }
            total += e.getValue();
        }
        if (total != overflow) {
            return fail(SplendorFailReason.DISCARD_INVALID, "须恰好弃置 " + overflow + " 颗宝石");
        }

        for (Map.Entry<GemColor, Integer> e : gems.entrySet()) {
            if (e.getValue() > 0) {
                actor.removeGem(e.getKey(), e.getValue());
                board.bankAdd(e.getKey(), e.getValue());
            }
        }
        board.setPending(SplendorBoard.PendingPhase.NONE, List.of());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("discarded", toWireMapWithGold(gems));
        afterAction(board, actor);
        return ok(detail);
    }

    // ==================== NOBLE_SELECT ====================

    /** 多位贵族同时满足时选其一拜访（其余留存桌面），其余时候 NOBLE_NOT_ELIGIBLE */
    public static SplendorActionResult selectNoble(SplendorBoard board, String playerName, String nobleId) {
        SplendorPlayer actor = preflightTurn(board, playerName);
        if (actor == null) return preflightFail(board, playerName);

        if (board.getPending() != SplendorBoard.PendingPhase.NOBLE_SELECT) {
            return fail(SplendorFailReason.NOBLE_NOT_ELIGIBLE, "当前无需选择贵族");
        }
        if (nobleId == null || nobleId.isBlank()) {
            return fail(SplendorFailReason.INVALID_PARAM, "缺少 nobleId");
        }
        SplendorNobleDef noble = board.getPendingNobles().stream()
            .filter(n -> n.code().equals(nobleId)).findFirst().orElse(null);
        if (noble == null) {
            return fail(SplendorFailReason.NOBLE_NOT_ELIGIBLE, "该贵族不在本回合可拜访集合中");
        }

        grantNoble(board, actor, noble);
        board.setPending(SplendorBoard.PendingPhase.NONE, List.of());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("nobleId", nobleId);
        detail.put("vp", noble.vp());
        afterAction(board, actor);
        return ok(detail);
    }

    // ==================== 终局名次 ====================

    /** GAME_OVER 名次：VP 降序 → 已购卡数升序（官方平局裁决）→ 座位号升序 */
    public static List<SplendorRankVO> rankingsOf(SplendorBoard board) {
        List<SplendorRankVO> rankings = new ArrayList<>();
        for (SplendorPlayer p : board.getPlayers().values()) {
            rankings.add(new SplendorRankVO(p.getSeatNo(), p.getName(), p.getVp(), p.cardCount(), p.getNobleCount()));
        }
        rankings.sort(Comparator
            .comparingInt(SplendorRankVO::vp).reversed()
            .thenComparingInt(SplendorRankVO::cards)
            .thenComparingInt(SplendorRankVO::seatNo));
        return rankings;
    }

    // ==================== 结算管道（包内） ====================

    /** 主行动前置校验：进行中、本人回合、且无待办阶段 */
    private static SplendorPlayer preflightMain(SplendorBoard board, String playerName) {
        SplendorPlayer actor = preflightTurn(board, playerName);
        if (actor == null) return null;
        if (board.getPending() != SplendorBoard.PendingPhase.NONE) return null;
        return actor;
    }

    /** 回合前置校验：对局进行中且为当前行动方 */
    private static SplendorPlayer preflightTurn(SplendorBoard board, String playerName) {
        if (board == null || board.isFinished()) return null;
        SplendorPlayer actor = board.getPlayers().get(board.getCurrentSeatNo());
        if (actor == null || !actor.getName().equals(playerName)) return null;
        return actor;
    }

    /** 与 preflight 配对的具体失败原因（有明确校验顺序，见调用处） */
    private static SplendorActionResult preflightFail(SplendorBoard board, String playerName) {
        if (board == null || board.isFinished()) {
            return fail(SplendorFailReason.NOT_PLAYING, "对局未开始或已结束");
        }
        SplendorPlayer actor = board.getPlayers().get(board.getCurrentSeatNo());
        if (actor == null || !actor.getName().equals(playerName)) {
            return fail(SplendorFailReason.NOT_YOUR_TURN, "当前不是你的回合");
        }
        return fail(SplendorFailReason.MUST_DISCARD, "须先完成当前回合的待办操作（弃宝石/选贵族）");
    }

    /**
     * 主行动生效后的统一管道：
     * 贵族检查 → （唯一满足自动拜访）→ 15VP 标记终局轮 → 超 10 颗进入弃牌待办 → 推进回合。
     */
    private static void endTurn(SplendorBoard board, SplendorPlayer actor) {
        List<SplendorNobleDef> satisfied = satisfiedNobles(board, actor);
        if (satisfied.size() == 1) {
            // 唯一满足：自动拜访，无需选择
            grantNoble(board, actor, satisfied.get(0));
            afterAction(board, actor);
        } else if (satisfied.size() > 1) {
            // 多位满足：进入选择待办，回合暂停
            board.setPending(SplendorBoard.PendingPhase.NOBLE_SELECT, satisfied);
        } else {
            afterAction(board, actor);
        }
    }

    /** 贵族拜访/选择完成后：终局轮标记 + 弃宝石检查 + 回合推进 */
    private static void afterAction(SplendorBoard board, SplendorPlayer actor) {
        if (actor.getVp() >= WIN_VP) {
            board.markLastRound();
        }
        if (actor.gemCount() > GEM_HAND_LIMIT) {
            board.setPending(SplendorBoard.PendingPhase.DISCARD, List.of());
            return;
        }
        board.advanceTurn();
    }

    private static void grantNoble(SplendorBoard board, SplendorPlayer actor, SplendorNobleDef noble) {
        actor.addVp(noble.vp());
        actor.addNoble();
        board.removeNoble(noble);
    }

    /** 当前置为满足的贵族集合（加成逐色 ≥ 需求） */
    private static List<SplendorNobleDef> satisfiedNobles(SplendorBoard board, SplendorPlayer actor) {
        List<SplendorNobleDef> out = new ArrayList<>();
        for (SplendorNobleDef n : board.getNobles()) {
            boolean all = true;
            for (GemColor c : FIVE_COLORS) {
                if (actor.getBonuses().getOrDefault(c, 0) < n.needOf(c)) {
                    all = false;
                    break;
                }
            }
            if (all) out.add(n);
        }
        return out;
    }

    private static int distinctColorsInBank(SplendorBoard board) {
        int count = 0;
        for (GemColor c : FIVE_COLORS) {
            if (board.getBank().getOrDefault(c, 0) > 0) count++;
        }
        return count;
    }

    // ---- wire 映射（与快照口径一致） ----

    private static Map<String, Integer> toWireMap(Map<GemColor, Integer> m) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (GemColor c : FIVE_COLORS) {
            out.put(c.getCode(), m.getOrDefault(c, 0));
        }
        return out;
    }

    private static Map<String, Integer> toWireMapWithGold(Map<GemColor, Integer> m) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (GemColor c : GemColor.values()) {
            out.put(c.getCode(), m.getOrDefault(c, 0));
        }
        return out;
    }
}
