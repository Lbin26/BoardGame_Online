package org.dromara.dicego.domain.vo;

import java.util.Map;

/**
 * 快照-贵族视图（协议 §4：需求为五色加成卡数量）。
 */
public record SplendorNobleVO(
    String code,
    String name,
    int vp,
    Map<String, Integer> need
) {
}
