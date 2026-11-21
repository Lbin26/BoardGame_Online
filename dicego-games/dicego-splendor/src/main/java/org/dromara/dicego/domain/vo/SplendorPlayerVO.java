package org.dromara.dicego.domain.vo;

import java.util.Map;

/**
 * 快照-玩家视图（协议 §4）。
 * reservedCards 仅在收件人=卡主本人的快照中非 null（D5 可见性）；对手/旁观只见 reservedCount。
 */
public record SplendorPlayerVO(
    int seatNo,
    String name,
    Map<String, Integer> gems,
    Map<String, Integer> bonuses,
    int vp,
    int reservedCount,
    java.util.List<SplendorCardVO> reservedCards
) {
}
