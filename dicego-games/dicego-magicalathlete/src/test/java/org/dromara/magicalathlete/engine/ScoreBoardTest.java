package org.dromara.magicalathlete.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScoreBoardTest {

    private static ScoreBoard board() {
        return new ScoreBoard(List.of(
            new RaceSession.Entrant(1, "alice", "a"),
            new RaceSession.Entrant(2, "bob", "b"),
            new RaceSession.Entrant(3, "carol", "c")));
    }

    @Test
    void medalsIncreasePerRace() {
        ScoreBoard b = board();
        assertEquals(new ScoreBoard.RaceAward(1, 1, 2, 3, 1), b.awardRace(1, 1, 2, Map.of(3, 1)));
        assertEquals(new ScoreBoard.RaceAward(4, 2, 1, 5, 3), b.awardRace(4, 2, 1, Map.of()));
        assertEquals(2, b.awards().size());
        ScoreBoard.Score alice = b.rankings().stream().filter(s -> s.seatNo() == 1).findFirst().orElseThrow();
        ScoreBoard.Score bob = b.rankings().stream().filter(s -> s.seatNo() == 2).findFirst().orElseThrow();
        assertEquals(3 + 3, alice.total(), "第一场金3 + 第四场银3");
        assertEquals(1 + 5, bob.total(), "第一场银1 + 第四场金5");
        ScoreBoard.Score carol = b.rankings().stream().filter(s -> s.seatNo() == 3).findFirst().orElseThrow();
        assertEquals(1, carol.bronze());
    }

    @Test
    void tiesShareRank() {
        ScoreBoard b = board();
        b.awardRace(1, 1, 2, Map.of(3, 2));
        // 此时 alice=3, bob=1, carol=2 —— 无并列；再补一场让 alice=bob
        b.awardRace(2, 2, 1, Map.of());
        // alice=3+2=5, bob=1+4=5, carol=2
        List<ScoreBoard.Score> rank = b.rankings();
        assertEquals(1, rank.get(0).rank());
        assertEquals(1, rank.get(1).rank(), "同分并列第一");
        assertEquals(3, rank.get(2).rank(), "并列两人后下一名次为 3");
    }

    @Test
    void bronzeByFaceValue() {
        ScoreBoard b = board();
        b.awardRace(1, 1, 2, Map.of(1, 3, 2, 1));
        ScoreBoard.Score alice = b.rankings().stream().filter(s -> s.seatNo() == 1).findFirst().orElseThrow();
        assertEquals(3, alice.bronze());
        assertEquals(6, alice.total());
    }

    @Test
    void abilityScoreCountsTowardTotalAndRank() {
        ScoreBoard b = board();
        b.awardRace(1, 1, 2, Map.of());
        b.awardAbilities(1, Map.of(2, 5, 3, -1));
        ScoreBoard.Score bob = b.rankings().stream().filter(s -> s.seatNo() == 2).findFirst().orElseThrow();
        assertEquals(6, bob.total(), "银1 + 能力5");
        assertEquals(1, b.abilityAwards().size());
        assertEquals(5, b.abilityAwards().get(0).extraBySeat().get(2));
    }
}
