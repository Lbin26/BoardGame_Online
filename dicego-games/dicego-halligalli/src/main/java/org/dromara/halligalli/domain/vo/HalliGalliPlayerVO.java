package org.dromara.halligalli.domain.vo;

/**
 * 单个玩家的公开状态：牌堆张数、明牌张数与顶牌，出局/在线/待机。
 * 牌堆内容永远不下行（连本人也看不到）。
 */
public record HalliGalliPlayerVO(
    int seatNo,
    String name,
    boolean online,
    boolean afk,
    boolean out,
    int deckCount,
    int faceUpCount,
    int totalCards,
    HalliGalliCardVO topCard
) {
}
