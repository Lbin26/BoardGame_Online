package org.dromara.dicego.domain.vo;

import java.util.List;
import java.util.Map;

/**
 * 全量快照（协议 §4，STATE_SYNC 载荷）。唯一的对局状态出口，
 * 由 SplendorBoard.snapshotFor(viewerName) 按收件人生成（D5）。
 */
public record SplendorSnapshotVO(
    Integer viewerSeatNo,
    String status,
    int roundNo,
    int currentSeatNo,
    Map<String, Integer> bank,
    Map<String, LevelVO> levels,
    List<SplendorNobleVO> nobles,
    List<SplendorPlayerVO> players,
    /** 当前待办阶段：null / NOBLE_SELECT / DISCARD（公开） */
    String pendingType,
    /** 可拜访贵族明细：仅当前行动方本人视角非 null，其余一律缺省（D5） */
    List<SplendorNobleVO> pendingNobles
) {
    /** 某级牌行视图 */
    public record LevelVO(
        List<SplendorCardVO> cards,
        int deckLeft
    ) {
    }
}
