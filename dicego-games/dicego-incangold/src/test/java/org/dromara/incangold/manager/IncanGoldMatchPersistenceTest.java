package org.dromara.incangold.manager;

import org.dromara.incangold.engine.IncanGoldEngine;
import org.dromara.incangold.service.IncanGoldActionLogBO;
import org.dromara.incangold.service.IncanGoldMatchRecorder;
import org.dromara.incangold.service.IncanGoldPlayerRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 印加宝藏落库接线测试：开局座位快照、matchId 回填、落库异常不影响对局，
 * 以及「同一 user_id 多开多昵称只占一个真实座位」的唯一键冲突回归。
 */
class IncanGoldMatchPersistenceTest {

    /** 内存假实现：只记录回调，不碰 DB */
    private static class FakeRecorder implements IncanGoldMatchRecorder {
        final List<IncanGoldPlayerRef> startedRefs = new ArrayList<>();
        Long adoptedMatchId = 9001L;

        @Override
        public void onStart(IncanGoldRoom room, long seed, int playerCount, List<IncanGoldPlayerRef> players) {
            startedRefs.addAll(players);
            room.adoptMatchId(adoptedMatchId);
        }

        @Override
        public void onAction(IncanGoldRoom room, IncanGoldActionLogBO log) {
        }

        @Override
        public void onFinish(IncanGoldRoom room, List<IncanGoldEngine.Ranking> rankings) {
        }
    }

    private static IncanGoldRoom room(FakeRecorder recorder) {
        IncanGoldRoom room = new IncanGoldRoom("R1", "p1", 4, recorder);
        room.takeSeat("p1", 1);
        room.takeSeat("p2", 2);
        room.takeSeat("p3", 3);
        return room;
    }

    @Test
    void 开局写座位快照并回填matchId() {
        FakeRecorder recorder = new FakeRecorder();
        IncanGoldRoom room = room(recorder);
        room.bindUser("p1", 1001L);

        assertEquals(IncanGoldRoom.StartResult.SUCCESS, room.startGame("p1"));
        assertEquals(3, recorder.startedRefs.size());
        assertEquals(9001L, room.getMatchId());
        assertEquals(1001L, recorder.startedRefs.get(0).userId()); // 已绑定 → 真实 user_id
        assertEquals(-2L, recorder.startedRefs.get(1).userId());   // 游客 → 负座位哨兵
        assertEquals(-3L, recorder.startedRefs.get(2).userId());
    }

    @Test
    void 同一用户多开多昵称只占一个真实座位_其余降级哨兵() {
        // 回归：本机用一个账号开 3 个昵称，若都绑定同一 user_id，onStart 插入第二条
        // game_match_player 就会撞 uk_match_user(match_id,user_id) 唯一键并回滚整局，
        // 导致 matchId 不回填、终局落库跳过、战绩永远查不到。
        FakeRecorder recorder = new FakeRecorder();
        IncanGoldRoom room = room(recorder);
        room.bindUser("p1", 777L);
        room.bindUser("p2", 777L); // 同一 user_id 第二昵称 → 拒绝
        room.bindUser("p3", 777L); // 第三昵称 → 拒绝

        assertEquals(IncanGoldRoom.StartResult.SUCCESS, room.startGame("p1"));
        assertEquals(List.of(
            new IncanGoldPlayerRef(1, "p1", 777L),
            new IncanGoldPlayerRef(2, "p2", -2L),
            new IncanGoldPlayerRef(3, "p3", -3L)),
            recorder.startedRefs);
    }

    @Test
    void 落库异常不影响对局进行() {
        IncanGoldMatchRecorder exploding = new IncanGoldMatchRecorder() {
            @Override
            public void onStart(IncanGoldRoom room, long seed, int playerCount, List<IncanGoldPlayerRef> players) {
                throw new IllegalStateException("db down");
            }

            @Override
            public void onAction(IncanGoldRoom room, IncanGoldActionLogBO log) {
            }

            @Override
            public void onFinish(IncanGoldRoom room, List<IncanGoldEngine.Ranking> rankings) {
            }
        };
        IncanGoldRoom room = new IncanGoldRoom("R1", "p1", 4, exploding);
        room.takeSeat("p1", 1);
        room.takeSeat("p2", 2);
        room.takeSeat("p3", 3);

        assertEquals(IncanGoldRoom.StartResult.SUCCESS, room.startGame("p1"));
        assertNull(room.getMatchId()); // 开局落库炸了，matchId 不回填
    }
}
