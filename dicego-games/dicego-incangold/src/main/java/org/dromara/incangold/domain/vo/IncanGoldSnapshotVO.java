package org.dromara.incangold.domain.vo;

import java.util.List;
import java.util.Map;

/**
 * 印加宝藏全量快照。由服务端按收件人定制：只有本人能看到自己的宝石数，
 * 其他玩家的 campGems / roundGems 为 null（桌面上的帐篷遮挡）。
 * "是否已提交决策"是公开信息，但选择内容绝不下发。
 */
public record IncanGoldSnapshotVO(
    String phase,
    int round,
    String endCause,
    long phaseDeadlineAt,
    List<FieldCardVO> field,
    int fieldArtifacts,
    Map<String, Integer> hazardRemaining,
    List<PlayerVO> players,
    List<EventVO> events,
    List<RankingVO> rankings
) {
    public record FieldCardVO(String kind, int value, String hazard, int remainder) {
    }

    public record PlayerVO(
        int seatNo,
        String name,
        boolean online,
        boolean afk,
        boolean inExpedition,
        boolean submitted,
        Integer campGems,
        Integer roundGems,
        int artifacts
    ) {
    }

    public record EventVO(long seq, String code, String message, Map<String, Object> data) {
    }

    public record RankingVO(int seatNo, String name, int gems, int artifacts, int score) {
    }
}
