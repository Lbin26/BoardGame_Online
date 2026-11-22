package org.dromara.magicalathlete.engine;

import org.dromara.magicalathlete.engine.ability.FullAbilities;
import org.dromara.magicalathlete.engine.ability.StandardAbilities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** RaceSession 能力接入：老构造子零回归、新构造子能力生效、能力链可落日志 */
class RaceSessionAbilityTest {

    private static RaceSession.Entrant e(int seat, String key) {
        return new RaceSession.Entrant(seat, "p" + seat, key);
    }

    @Test
    void oldConstructorIgnoresAbilities() {
        // 炼金术士骰 2：老构造子无能力，主移动 2 格
        RaceSession race = new RaceSession(1, List.of(e(1, "alchemist"), e(2, "hare")));
        RaceSession.RollOutcome out = race.roll(1, 2);
        assertTrue(out.effects().stream().anyMatch(s -> s.equals("MAIN_MOVE:0->2")));
        assertTrue(out.abilityChain().isEmpty());
        assertEquals(2, race.racers().get(0).position());
    }

    @Test
    void abilityConstructorAppliesAlchemistAndLogsChain() {
        RaceSession race = new RaceSession(1, List.of(e(1, "alchemist"), e(2, "hare")),
            StandardAbilities.registry());
        RaceSession.RollOutcome out = race.roll(1, 2);
        assertTrue(out.effects().stream().anyMatch(s -> s.equals("MAIN_MOVE:0->4")),
            "炼金术士骰 2 替换基数为 4");
        assertTrue(out.abilityChain().stream().anyMatch(s -> s.startsWith("alchemist@")));
        assertEquals(4, race.racers().get(0).position());
    }

    @Test
    void abilityScoresExposedForScoreBoard() {
        RaceSession race = new RaceSession(1, List.of(e(1, "hare"), e(2, "lackey")),
            StandardAbilities.registry());
        race.roll(1, 6); // 跟班在对手骰 6 时 +2
        assertEquals(2, race.extraScoreBySeat().get(2));
        assertEquals(2, race.racers().stream().filter(s -> s.seatNo() == 2)
            .findFirst().orElseThrow().extraScore());
    }

    @Test
    void fixedCopyDelegationAppliesInRealRoll() {
        // 座位1 蛋蛋复制西西弗斯：赛前 +4 应计入能力分；主骰 6 时传送回起点并 -1
        RaceSession.CopyBinding binding = new RaceSession.CopyBinding(1, "sisyphus", 2);
        RaceSession race = new RaceSession(1, List.of(e(1, "egg"), e(2, "sisyphus")),
            FullAbilities.registry(), List.of(binding));
        race.applyPreRace();
        assertEquals(4, race.extraScoreBySeat().get(1), "蛋蛋复制的赛前 +4 生效");
        RaceSession.RollOutcome out = race.roll(1, 6);
        assertTrue(out.abilityChain().stream().anyMatch(s -> s.startsWith("sisyphus@seat1")),
            "复制来的西西弗斯主骰 6 能力生效（能力归属座位=蛋蛋本人）");
        assertEquals(3, race.extraScoreBySeat().get(1), "赛前 +4 后主骰 6 再 -1 = 3");
    }

    @Test
    void mimicDelegationAppliesInRealRoll() {
        // 座位1 模仿猫，座位2 大长腿；让大长腿先手取得领先，模仿猫随后复制它
        org.dromara.magicalathlete.engine.RaceTrack.Side mild =
            org.dromara.magicalathlete.engine.RaceTrack.Side.MILD;
        RaceSession race = new RaceSession(mild, List.of(e(1, "copycat"), e(2, "legs")),
            List.of(2, 1), FullAbilities.registry(), List.of());
        race.roll(2, 4); // 大长腿先走取得领先到位置4
        RaceSession.RollOutcome out = race.roll(1, 2);
        assertTrue(out.effects().stream().anyMatch(s -> s.startsWith("MAIN_MOVE:")),
            "模仿猫回合正常结算");
        assertTrue(out.abilityChain().stream().anyMatch(s -> s.contains("legs@seat1")),
            "模仿猫复制到大长腿能力（能力归属座位=模仿猫本人）");
    }

    @Test
    void tripSkipStillWorksWithAbilities() {
        RaceSession race = new RaceSession(1, List.of(e(1, "hare"), e(2, "banana")),
            StandardAbilities.registry());
        RaceSession.RollOutcome out = race.roll(1, 4);
        // 若越过香蕉已绊倒则下回合跳过；这里验证能力接入不破坏基础移动
        assertEquals(RaceSession.RollReason.OK, out.reason());
        assertFalse(out.chainTruncated());
    }
}
