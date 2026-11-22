package org.dromara.magicalathlete.domain.vo;

import java.util.List;
import java.util.Map;

/**
 * 阶段快照。可见性（D5）：
 *
 * <ul>
 * <li>DRAFT：牌池与每人已选手牌公开；</li>
 * <li>MUSTER：只广播"某人已准备/未准备"，{@code activeRacerKey} 仅按收件人个性化下发自己的；</li>
 * <li>RACE/RACE_END/FINISHED：赛道棋子、名次、得分全量可见。</li>
 * </ul>
 *
 * @param phase           WAITING / DRAFT / MUSTER / RACE / RACE_END / FINISHED
 * @param raceNo           0=轮抽，1..4=比赛场次
 * @param batch            当前轮抽批次 0/1
 * @param pickOrder        本批蛇形选序（座位号）
 * @param pickIndex        当前进行到第几手
 * @param pool             当前批牌池（Racer key，仅 DRAFT 非空）
 * @param hands            座位 → 已选中的 Racer key（DRAFT 公开；MUSTER 仅自己的手牌）
 * @param currentSeatNo    当前应谁选（DRAFT）/ 当前行动座位（RACE）
 * @param deadlineAt       当前阶段截止时间 epoch ms，0=无倒计时
 * @param finished         轮抽是否完成
 * @param players          座位玩家视图（在线/离线/AFK）
 * @param readySeats        MUSTER 已准备的座位（不含选手 key）
 * @param activeRacerKeys   座位 → 已公开的出场选手（仅 RACE/RACE_END/FINISHED；MUSTER 恒空）
 * @param myActiveRacerKey  仅收件人自己的暗选 key（MUSTER 个性化字段）
 * @param racers            RACE/RACE_END 赛道棋子状态
 * @param lastDice          最近一次骰点
 * @param diceSeatNo        最近一次掷骰座位
 * @param lastEffects       最近一次结算效果指令（可落日志）
 * @param goldSeat          本场金牌座位
 * @param silverSeat        本场银牌座位
 * @param scores            累计得分（座位 → 总分）
 * @param rankings          终局排名（同分并列）
 * @param myOptionalOptions  收件人当前回合可用的可选能力项（仅 RACE 且轮到自己时非空，M4）
 * @param variant            人数变体名（NORMAL / TWO_PLAYER / THREE_DOUBLE，M5）
 * @param racersPerSeat      每座位每场派出选手数（普通 1 / 双角色 2，M5）
 * @param currentLane        RACE 阶段当前行动的参赛号（普通局等于 currentSeatNo，M5）
 * @param laneToSeat         参赛号 → 归属真实座位（双角色快照，M5）
 * @param activeRacerKeysByLane 参赛号 → 出场选手 key（RACE 公开；M5 双角色精确视图）
 * @param myActiveRacerKeys  MUSTER 阶段收件人已暗选的选手（按提交顺序；M5 双角色含 2 名）
 * @param musterProgress     真实座位 → 本场已暗选数（MUSTER 公开，不含 key，M5）
 */
public record MagicalAthleteSnapshotVO(
    String phase,
    int raceNo,
    int batch,
    List<Integer> pickOrder,
    int pickIndex,
    List<String> pool,
    Map<Integer, List<String>> hands,
    Integer currentSeatNo,
    long deadlineAt,
    boolean finished,
    List<PlayerVO> players,
    List<Integer> readySeats,
    Map<String, String> activeRacerKeys,
    String myActiveRacerKey,
    List<RacerStateVO> racers,
    Integer lastDice,
    Integer diceSeatNo,
    List<String> lastEffects,
    Integer goldSeat,
    Integer silverSeat,
    Map<Integer, Integer> scores,
    List<ScoreVO> rankings,
    List<String> myOptionalOptions,
    String variant,
    int racersPerSeat,
    Integer currentLane,
    Map<Integer, Integer> laneToSeat,
    Map<String, String> activeRacerKeysByLane,
    List<String> myActiveRacerKeys,
    Map<Integer, Integer> musterProgress
) {
    /** 兼容 M1–M4 的旧构造（新增字段全部取普通局默认值），保证老测试零回归 */
    public MagicalAthleteSnapshotVO(String phase, int raceNo, int batch,
                                    List<Integer> pickOrder, int pickIndex, List<String> pool,
                                    Map<Integer, List<String>> hands, Integer currentSeatNo,
                                    long deadlineAt, boolean finished, List<PlayerVO> players,
                                    List<Integer> readySeats, Map<String, String> activeRacerKeys,
                                    String myActiveRacerKey, List<RacerStateVO> racers,
                                    Integer lastDice, Integer diceSeatNo, List<String> lastEffects,
                                    Integer goldSeat, Integer silverSeat, Map<Integer, Integer> scores,
                                    List<ScoreVO> rankings, List<String> myOptionalOptions) {
        this(phase, raceNo, batch, pickOrder, pickIndex, pool, hands, currentSeatNo, deadlineAt,
            finished, players, readySeats, activeRacerKeys, myActiveRacerKey, racers,
            lastDice, diceSeatNo, lastEffects, goldSeat, silverSeat, scores, rankings,
            myOptionalOptions, "NORMAL", 1, null, Map.of(), Map.of(), List.of(), Map.of());
    }

    public record PlayerVO(int seatNo, String name, boolean online, boolean afk) {
    }

    /** seatNo 为参赛号 lane（唯一标识棋子）；ownerSeatNo 为归属真实座位（前端高亮用） */
    public record RacerStateVO(int seatNo, String name, String racerKey, int position,
                               boolean tripped, int finishRank, int bronze, int ownerSeatNo) {
        /** 兼容 M2 老构造：普通局 ownerSeatNo 等于 seatNo */
        public RacerStateVO(int seatNo, String name, String racerKey, int position,
                            boolean tripped, int finishRank, int bronze) {
            this(seatNo, name, racerKey, position, tripped, finishRank, bronze, seatNo);
        }
    }

    public record ScoreVO(int seatNo, String name, int gold, int silver, int bronze, int total, int rank) {
    }
}
