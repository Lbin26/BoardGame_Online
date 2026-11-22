package org.dromara.halligalli.domain.vo;

import java.util.List;
import java.util.Map;

/**
 * 对局快照（D6：全量广播，因为没有"只有某人可见"的信息）。
 * totals 为桌面各水果可见总数（键为 fruit code），供前端显示计数条；
 * flipDeadlineAt 为当前翻牌截止时间（epoch ms），前端据此显示倒计时。
 */
public record HalliGalliSnapshotVO(
    String phase,
    Integer flipSeatNo,
    long flipDeadlineAt,
    Map<String, Integer> totals,
    List<HalliGalliPlayerVO> players,
    HalliGalliRingVO lastRing
) {
}
