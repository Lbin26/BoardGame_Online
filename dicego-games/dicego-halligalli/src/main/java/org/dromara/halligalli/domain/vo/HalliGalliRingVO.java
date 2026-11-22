package org.dromara.halligalli.domain.vo;

import java.util.List;

/**
 * 抢铃结果（下行）。status：CORRECT / WRONG / LATE / DEBOUNCED；
 * cardsTaken>0 表示有赢家收牌，beneficiarySeat 为收牌座位（抢对=抢铃者，两人残局=对手）。
 */
public record HalliGalliRingVO(
    String status,
    int seatNo,
    int cardsTaken,
    Integer beneficiarySeat,
    List<PenaltyVO> penalties,
    String reason
) {
    public record PenaltyVO(int seatNo, int cards) {
    }
}
