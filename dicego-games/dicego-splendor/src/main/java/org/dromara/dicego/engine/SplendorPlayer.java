package org.dromara.dicego.engine;

import lombok.Getter;
import org.dromara.dicego.domain.SplendorCardDef;
import org.dromara.dicego.enums.GemColor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Splendor 玩家对局内状态。仅引擎与房间包内访问，快照对外只经 SplendorBoard.snapshotFor。
 */
@Getter
public class SplendorPlayer {

    private final int seatNo;
    private final String name;

    /** 宝石持有（含黄金 GOLD） */
    private final Map<GemColor, Integer> gems = new EnumMap<>(GemColor.class);

    /** 永久加成（五色，无黄金） */
    private final Map<GemColor, Integer> bonuses = new EnumMap<>(GemColor.class);

    @Getter
    private int vp;

    /** 获得贵族数（match_player.noble_count 落库用） */
    private int nobleCount;

    /** 预留卡（对对手隐藏，D5） */
    private final List<SplendorCardDef> reserved = new ArrayList<>();

    SplendorPlayer(int seatNo, String name) {
        this.seatNo = seatNo;
        this.name = name;
        for (GemColor c : GemColor.values()) {
            gems.put(c, 0);
        }
        for (GemColor c : List.of(GemColor.WHITE, GemColor.BLUE, GemColor.GREEN, GemColor.RED, GemColor.BLACK)) {
            bonuses.put(c, 0);
        }
    }

    void addBonus(GemColor color) {
        addBonus(color, 1);
    }

    void addBonus(GemColor color, int amount) {
        bonuses.merge(color, amount, Integer::sum);
    }

    void addVp(int amount) {
        this.vp += amount;
    }

    void addNoble() {
        this.nobleCount++;
    }

    void addGem(GemColor color, int amount) {
        gems.merge(color, amount, Integer::sum);
    }

    void removeGem(GemColor color, int amount) {
        int left = gems.getOrDefault(color, 0) - amount;
        if (left < 0) throw new IllegalStateException("宝石持有不足: " + color + " " + amount);
        gems.put(color, left);
    }

    void addReserved(SplendorCardDef card) {
        reserved.add(card);
    }

    /** 购买预留卡后移出；不在预留区时 fail-fast（引擎须先校验） */
    void removeReserved(SplendorCardDef card) {
        if (!reserved.remove(card)) {
            throw new IllegalStateException("卡不在预留区: " + card.code());
        }
    }

    /** 手牌宝石总数（黄金计入 10 上限） */
    public int gemCount() {
        return gems.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** 已购发展卡数（每张卡恰 1 加成，加成合计即购卡数，官方平局裁决用） */
    public int cardCount() {
        return bonuses.values().stream().mapToInt(Integer::intValue).sum();
    }
}
