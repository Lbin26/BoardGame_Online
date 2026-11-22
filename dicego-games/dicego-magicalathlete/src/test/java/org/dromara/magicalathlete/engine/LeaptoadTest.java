package org.dromara.magicalathlete.engine;

import org.dromara.magicalathlete.engine.ability.FullAbilities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M5 跳跳蟾蜍：移动只计空格、跳过占据格、每跳过一次算能力发动 */
class LeaptoadTest {

    private static RaceSession.Entrant e(int seat, String key) {
        return new RaceSession.Entrant(seat, "p" + seat, key);
    }

    private static RaceSession session() {
        RaceTrack.Side mild = RaceTrack.Side.MILD;
        return new RaceSession(mild, List.of(e(1, "leaptoad"), e(2, "dummy")),
            List.of(2, 1), FullAbilities.registry(), List.of());
    }

    @Test
    void leapSkipsOccupiedSpaceAndFiresAbility() {
        RaceSession race = session();
        race.roll(2, 3); // 座位2 先手走到 pos3
        RaceSession.RollOutcome out = race.roll(1, 3); // 跳跳蟾蜍掷 3
        // 空格 1,2 各计一步；pos3 被占据跳过；pos4 空格计第三步 → 落点 4
        assertTrue(out.effects().stream().anyMatch(s -> s.equals("LEAP_MOVE:0->4:skip=[2]")),
            "跳过占据格 pos3 后落到 4，记录被跳过的座位2");
        assertEquals(4, race.racers().stream().filter(r -> r.seatNo() == 1)
            .findFirst().orElseThrow().position());
        assertTrue(out.effects().stream().anyMatch(s -> s.startsWith("ABILITY:")
                && s.contains("ON_ABILITY_FIRED")) == false,
            "本场无蹭蹭怪，跳过格只记 skip 不产生额外得分链");
    }

    @Test
    void leapCountsOnlyEmptySpacesNoObstacle() {
        // 让 leaptoad 先手，座位2 仍在 pos0 起点（不算占据）
        RaceSession race = new RaceSession(RaceTrack.Side.MILD,
            List.of(e(1, "leaptoad"), e(2, "dummy")), List.of(1, 2),
            FullAbilities.registry(), List.of());
        RaceSession.RollOutcome out = race.roll(1, 2);
        // 空格1、2 → 落点2，无跳过
        assertTrue(out.effects().stream().anyMatch(s -> s.equals("LEAP_MOVE:0->2:skip=[]")),
            "无障碍时正常按空格计数，实际 effects=" + out.effects());
        assertEquals(2, race.racers().stream().filter(r -> r.seatNo() == 1)
            .findFirst().orElseThrow().position());
    }

    @Test
    void leapFiresScoocherWhenSkipping() {
        // 座位2 挡路，座位3 蹭蹭怪：跳过座位2 算一次发动 → 蹭蹭怪 +1
        RaceSession.Entrant e3 = e(3, "scoocher");
        RaceSession race = new RaceSession(RaceTrack.Side.MILD,
            List.of(e(1, "leaptoad"), e(2, "dummy"), e3),
            List.of(2, 3, 1), FullAbilities.registry(), List.of());
        race.roll(2, 3); // 挡路者到 pos3
        race.roll(3, 1); // 蹭蹭怪挪到 pos1（不影响后续）
        race.roll(1, 3); // 跳跳蟾蜍跳过 pos3 的座位2
        assertTrue(race.extraScoreBySeat().getOrDefault(3, 0) >= 1,
            "跳过占据格算一次能力发动，蹭蹭怪 +1");
    }
}
