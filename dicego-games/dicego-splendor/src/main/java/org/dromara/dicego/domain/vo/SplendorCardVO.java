package org.dromara.dicego.domain.vo;

import java.util.Map;

/**
 * 快照-发展卡视图（协议 §4：code/level/bonus/wire 色名/vp/cost）。
 */
public record SplendorCardVO(
    String code,
    int level,
    String bonus,
    int vp,
    Map<String, Integer> cost
) {
}
