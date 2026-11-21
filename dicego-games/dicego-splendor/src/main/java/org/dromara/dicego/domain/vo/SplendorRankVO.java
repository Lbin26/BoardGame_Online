package org.dromara.dicego.domain.vo;

/**
 * 终局名次行（GAME_OVER.rankings 载荷，中立）。
 * 排序规则：VP 降序 → 已购发展卡数升序（官方平局裁决）→ 座位号升序（稳定输出）。
 */
public record SplendorRankVO(
    int seatNo,
    String name,
    int vp,
    int cards,
    int nobleCount
) {
}
