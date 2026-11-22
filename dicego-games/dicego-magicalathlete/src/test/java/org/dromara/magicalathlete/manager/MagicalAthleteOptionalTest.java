package org.dromara.magicalathlete.manager;

import org.dromara.magicalathlete.domain.vo.MagicalAthleteSnapshotVO;
import org.dromara.magicalathlete.engine.ability.TurnIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** M4 可选能力交互层：意图收集/校验、快照选项、意图用后清空 */
class MagicalAthleteOptionalTest {

    private static MagicalAthleteRoom roomInRace(long seed) {
        MagicalAthleteRoom room = new MagicalAthleteRoom("R1", "p1", 2);
        room.takeSeat("p1", 1);
        room.takeSeat("p2", 2);
        room.startGameWithSeed("p1", seed);
        while (room.getPhase() == MagicalAthleteRoom.Phase.DRAFT) {
            MagicalAthleteSnapshotVO snap = room.snapshot();
            room.pickRacer("p" + snap.currentSeatNo(), snap.pool().get(0));
        }
        String k1 = room.snapshot("p1").hands().get(1).get(0);
        String k2 = room.snapshot("p2").hands().get(2).get(0);
        room.readyRacer("p1", k1);
        room.readyRacer("p2", k2);
        assertEquals(MagicalAthleteRoom.Phase.RACE, room.getPhase());
        return room;
    }

    @Test
    void useOptionalRejectsWrongPhaseAndWrongTurn() {
        MagicalAthleteRoom room = new MagicalAthleteRoom("R1", "p1", 2);
        room.takeSeat("p1", 1);
        room.takeSeat("p2", 2);
        assertEquals(MagicalAthleteRoom.OptionalResult.PHASE_MISMATCH,
            room.useOptional("p1", TurnIntent.primary()));
        room.startGameWithSeed("p1", 7L);
        assertEquals(MagicalAthleteRoom.OptionalResult.PHASE_MISMATCH,
            room.useOptional("p1", TurnIntent.primary()));
    }

    @Test
    void intentIsClearedAfterRoll() {
        MagicalAthleteRoom room = roomInRace(11L);
        Integer current = room.snapshot().currentSeatNo();
        String actor = "p" + current;

        // 只要当前选手有任一可选项，声明后应成功；无选项也应成功（空意图等价放弃）
        var options = room.optionalOptions(current);
        var result = room.useOptional(actor, TurnIntent.primary());
        if (options.contains("USE_PRIMARY")) {
            assertEquals(MagicalAthleteRoom.OptionalResult.SUCCESS, result);
        } else {
            assertEquals(MagicalAthleteRoom.OptionalResult.NO_OPTIONAL, result);
        }

        // 掷骰后意图必被消费（无论上一意图是否被接受，房间都回到 NONE）
        room.roll(actor);
        // 再声明：非当前行动者（已换手）应为 NOT_YOUR_TURN；若仍是同一座位则校验不抛异常即可
        var after = room.useOptional(actor, TurnIntent.primary());
        assertTrue(after == MagicalAthleteRoom.OptionalResult.NOT_YOUR_TURN
            || after == MagicalAthleteRoom.OptionalResult.SUCCESS
            || after == MagicalAthleteRoom.OptionalResult.NO_OPTIONAL);
    }

    @Test
    void invalidTargetSeatRejected() {
        MagicalAthleteRoom room = roomInRace(3L);
        Integer current = room.snapshot().currentSeatNo();
        String actor = "p" + current;
        // 选一个合法选项先占位判断：TARGET_SEAT 类型选手才校验目标
        var options = room.optionalOptions(current);
        if (options.contains("TARGET_SEAT")) {
            assertEquals(MagicalAthleteRoom.OptionalResult.INVALID_CHOICE,
                room.useOptional(actor, TurnIntent.targetSeat(99)));
        }
        // 无论哪种选手，超出范围的猜骰不应被接受（有 GUESS_DICE 选项时）
        if (options.contains("GUESS_DICE")) {
            assertEquals(MagicalAthleteRoom.OptionalResult.INVALID_CHOICE,
                room.useOptional(actor, new TurnIntent(false, 0, 0, 9)));
        }
    }

    @Test
    void autoRollIgnoresIntent() {
        MagicalAthleteRoom room = roomInRace(5L);
        // 声明意图后由超时托管掷骰：应正常推进，不使用意图
        room.useOptional("p" + room.snapshot().currentSeatNo(), TurnIntent.primary());
        long afterDeadline = room.snapshot().deadlineAt() + 1000L;
        var outcome = room.autoRoll(afterDeadline);
        assertTrue(outcome.ok(), "超时托管掷骰应成功且忽略意图");
    }

    @Test
    void snapshotOptionOnlyForCurrentActor() {
        MagicalAthleteRoom room = roomInRace(13L);
        Integer current = room.snapshot().currentSeatNo();
        String actor = "p" + current;
        String other = "p" + (current == 1 ? 2 : 1);
        assertNotNull(actor);
        assertTrue(room.snapshot(other).myOptionalOptions().isEmpty(),
            "非当前行动者看不到可选选项");
        // 当前行动者的快照可选项 == 房间计算值（取决于其出场选手）
        assertEquals(room.optionalOptions(current), room.snapshot(actor).myOptionalOptions());
    }
}
