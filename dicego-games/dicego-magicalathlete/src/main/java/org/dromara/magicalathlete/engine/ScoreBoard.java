package org.dromara.magicalathlete.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 四场累计记分（D12：金 3/4/4/5、银 1/2/2/3、铜币面值；同分并列共享胜利，不加赛）。
 */
public class ScoreBoard {

    /** 奖牌逐场升值，索引即 raceNo（1..4），0 位占位 */
    public static final int[] GOLD_BY_RACE = {0, 3, 4, 4, 5};
    public static final int[] SILVER_BY_RACE = {0, 1, 2, 2, 3};

    public record Score(int seatNo, String name, int gold, int silver, int bronze, int total, int rank) {
    }

    /** 单场能力额外分明细（M3，供落库/播报） */
    public record AbilityAward(int raceNo, Map<Integer, Integer> extraBySeat) {
    }

    public record RaceAward(int raceNo, int goldSeat, int silverSeat, int goldValue, int silverValue) {
    }

    private static class Line {
        String name;
        int gold;
        int silver;
        int bronze;

        int ability;

        int total() {
            return gold + silver + bronze + ability;
        }
    }

    private final Map<Integer, Line> lines = new LinkedHashMap<>();
    private final List<RaceAward> awards = new ArrayList<>();
    private final List<AbilityAward> abilityAwards = new ArrayList<>();

    public ScoreBoard(List<RaceSession.Entrant> entrants) {
        for (RaceSession.Entrant e : entrants) {
            Line line = new Line();
            line.name = e.name();
            lines.put(e.seatNo(), line);
        }
    }

    /** 记一场成绩：冠军取金牌、亚军取银牌，其余人不得分；铜币按面值入账 */
    public synchronized RaceAward awardRace(int raceNo, int goldSeat, int silverSeat,
                                            Map<Integer, Integer> bronzeBySeat) {
        if (raceNo < 1 || raceNo > 4) throw new IllegalArgumentException("场次必须 1-4: " + raceNo);
        int goldValue = GOLD_BY_RACE[raceNo];
        int silverValue = SILVER_BY_RACE[raceNo];
        if (goldSeat > 0 && lines.containsKey(goldSeat)) {
            lines.get(goldSeat).gold += goldValue;
        }
        if (silverSeat > 0 && lines.containsKey(silverSeat)) {
            lines.get(silverSeat).silver += silverValue;
        }
        if (bronzeBySeat != null) {
            bronzeBySeat.forEach((seat, bronze) -> {
                Line line = lines.get(seat);
                if (line != null) line.bronze += bronze;
            });
        }
        RaceAward award = new RaceAward(raceNo, goldSeat, silverSeat, goldValue, silverValue);
        awards.add(award);
        return award;
    }

    /**
     * M5 幕后军师自猜命中：冠军座位同时获金牌与银牌（金银同人），铜币照常入账。
     */
    public synchronized RaceAward awardRaceSelfGoldSilver(int raceNo, int goldSeat,
                                                           Map<Integer, Integer> bronzeBySeat) {
        if (raceNo < 1 || raceNo > 4) throw new IllegalArgumentException("场次必须 1-4: " + raceNo);
        int goldValue = GOLD_BY_RACE[raceNo];
        int silverValue = SILVER_BY_RACE[raceNo];
        if (goldSeat > 0 && lines.containsKey(goldSeat)) {
            Line line = lines.get(goldSeat);
            line.gold += goldValue;
            line.silver += silverValue;
        }
        if (bronzeBySeat != null) {
            bronzeBySeat.forEach((seat, bronze) -> {
                Line line = lines.get(seat);
                if (line != null) line.bronze += bronze;
            });
        }
        RaceAward award = new RaceAward(raceNo, goldSeat, goldSeat, goldValue, silverValue);
        awards.add(award);
        return award;
    }

    /** M3：记录一场能力额外分（西西弗斯/尺蠖/跟班/蹭蹭怪/浪漫家），并入总分与排名 */
    public synchronized AbilityAward awardAbilities(int raceNo, Map<Integer, Integer> extraBySeat) {
        Map<Integer, Integer> safe = extraBySeat == null ? Map.of() : extraBySeat;
        safe.forEach((seat, value) -> {
            Line line = lines.get(seat);
            if (line != null) line.ability += value;
        });
        AbilityAward award = new AbilityAward(raceNo, Map.copyOf(safe));
        abilityAwards.add(award);
        return award;
    }

    public List<RaceAward> awards() {
        return List.copyOf(awards);
    }

    public List<AbilityAward> abilityAwards() {
        return List.copyOf(abilityAwards);
    }

    /** 排名：同分同名次（并列第一不打破）；未参赛者不出现 */
    public List<Score> rankings() {
        List<Score> list = new ArrayList<>(lines.size());
        lines.forEach((seat, line) -> list.add(
            new Score(seat, line.name, line.gold, line.silver, line.bronze, line.total(), 0)));
        list.sort(Comparator.comparingInt(Score::total).reversed().thenComparingInt(Score::seatNo));

        List<Score> ranked = new ArrayList<>(list.size());
        int lastTotal = Integer.MIN_VALUE;
        int lastRank = 0;
        for (int i = 0; i < list.size(); i++) {
            Score s = list.get(i);
            int rank = s.total() == lastTotal ? lastRank : i + 1;
            lastTotal = s.total();
            lastRank = rank;
            ranked.add(new Score(s.seatNo(), s.name(), s.gold(), s.silver(), s.bronze(), s.total(), rank));
        }
        return ranked;
    }
}
