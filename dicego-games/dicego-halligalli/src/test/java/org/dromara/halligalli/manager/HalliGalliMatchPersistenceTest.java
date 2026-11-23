package org.dromara.halligalli.manager;

import org.dromara.halligalli.engine.HalliGalliBoard;
import org.dromara.halligalli.service.HalliGalliActionLogBO;
import org.dromara.halligalli.service.HalliGalliMatchRecorder;
import org.dromara.halligalli.service.HalliGalliPlayerRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对局落库（M5）房间侧测试：开局回填 matchId、翻牌/抢铃只记有效动作、终局只回调一次、
 * user_id 绑定与游客哨兵、落库异常不影响对局。
 */
class HalliGalliMatchPersistenceTest {

    /** 内存假实现：只记录回调，不碰 DB */
    private static class FakeRecorder implements HalliGalliMatchRecorder {
        final List<HalliGalliPlayerRef> startedRefs = new ArrayList<>();
        final List<HalliGalliActionLogBO> actions = new ArrayList<>();
        final List<List<HalliGalliBoard.Rank>> finishes = new ArrayList<>();
        Long adoptedMatchId = 9001L;
        long seed;
        int playerCount;

        @Override
        public void onStart(HalliGalliRoom room, long seed, int playerCount, List<HalliGalliPlayerRef> players) {
            this.seed = seed;
            this.playerCount = playerCount;
            startedRefs.addAll(players);
            room.adoptMatchId(adoptedMatchId);
        }

        @Override
        public void onAction(HalliGalliRoom room, HalliGalliActionLogBO log) {
            actions.add(log);
        }

        @Override
        public void onFinish(HalliGalliRoom room, List<HalliGalliBoard.Rank> rankings) {
            finishes.add(rankings);
        }

        List<String> actionTypes() {
            return actions.stream().map(HalliGalliActionLogBO::actionType).toList();
        }
    }

    /** 落库全部炸掉：对局必须照常进行 */
    private static class ExplodingRecorder implements HalliGalliMatchRecorder {
        @Override
        public void onStart(HalliGalliRoom room, long seed, int playerCount, List<HalliGalliPlayerRef> players) {
            throw new IllegalStateException("db down");
        }

        @Override
        public void onAction(HalliGalliRoom room, HalliGalliActionLogBO log) {
            throw new IllegalStateException("db down");
        }

        @Override
        public void onFinish(HalliGalliRoom room, List<HalliGalliBoard.Rank> rankings) {
            throw new IllegalStateException("db down");
        }
    }

    private static HalliGalliRoom room(int players, HalliGalliMatchRecorder recorder) {
        HalliGalliRoom room = new HalliGalliRoom("R1", "p1", Math.max(players, 2), false, recorder);
        for (int i = 1; i <= players; i++) {
            room.takeSeat("p" + i, i);
        }
        return room;
    }

    @Test
    void 开局写种子与全员座位快照并回填matchId() {
        FakeRecorder recorder = new FakeRecorder();
        HalliGalliRoom room = room(3, recorder);
        room.bindUser("p1", 1001L);

        assertEquals(HalliGalliRoom.StartResult.SUCCESS, room.startGame("p1"));

        assertEquals(3, recorder.playerCount);
        assertEquals(3, recorder.startedRefs.size());
        assertEquals(9001L, room.getMatchId());
        assertEquals(1001L, recorder.startedRefs.get(0).userId());   // 已绑定 → 真实 user_id
        assertEquals(-2L, recorder.startedRefs.get(1).userId());     // 游客 → 负座位哨兵
        assertEquals(-3L, recorder.startedRefs.get(2).userId());
        assertNotNull(recorder.startedRefs.get(0).name());
    }

    @Test
    void 同一用户多开多昵称只占一个真实座位_其余降级哨兵() {
        // 回归：本机用一个账号开多个昵称，若都绑定同一 user_id，开局落库会撞
        // uk_match_user(match_id,user_id) 唯一键并回滚整局。只有最早绑定的昵称保留真实 ID。
        FakeRecorder recorder = new FakeRecorder();
        HalliGalliRoom room = room(3, recorder);
        room.bindUser("p1", 777L);
        room.bindUser("p2", 777L); // 同一 user_id 第二昵称 → 拒绝
        room.bindUser("p3", 777L); // 第三昵称 → 拒绝

        assertEquals(HalliGalliRoom.StartResult.SUCCESS, room.startGame("p1"));
        assertEquals(3, recorder.startedRefs.size());
        assertEquals(777L, recorder.startedRefs.get(0).userId());
        assertEquals(-2L, recorder.startedRefs.get(1).userId());
        assertEquals(-3L, recorder.startedRefs.get(2).userId());
    }

    @Test
    void 翻牌成功写FLIP且回合号递增_被拒绝的翻牌不写() {
        FakeRecorder recorder = new FakeRecorder();
        HalliGalliRoom room = room(3, recorder);
        room.startGame("p1");

        int current = room.getBoard().flipSeatNo();
        int other = current == 1 ? 2 : 1;
        assertTrue(!room.flip(other).ok()); // 不是自己的回合
        assertTrue(recorder.actions.isEmpty());

        assertTrue(room.flip(current).ok());
        assertEquals(List.of("FLIP"), recorder.actionTypes());
        assertEquals(1, recorder.actions.get(0).roundNo());
        assertEquals(1, room.getFlipCount());
        assertEquals(9001L, recorder.actions.get(0).matchId());
        assertTrue(recorder.actions.get(0).paramsJson().contains("\"auto\":false"));
        assertEquals("srv-1", recorder.actions.get(0).actionId());
    }

    @Test
    void 超时代翻按auto标记落库() {
        FakeRecorder recorder = new FakeRecorder();
        HalliGalliRoom room = room(3, recorder);
        room.startGame("p1");

        room.autoFlip(System.currentTimeMillis() + HalliGalliRoom.FLIP_TIMEOUT_MS + 1);

        assertEquals(1, recorder.actions.size());
        assertTrue(recorder.actions.get(0).paramsJson().contains("\"auto\":true"));
    }

    @Test
    void 抢铃只在结算时落库_防抖不落库() {
        FakeRecorder recorder = new FakeRecorder();
        HalliGalliRoom room = room(3, recorder);
        room.startGame("p1");

        // 桌面没有 5 张同果 → 抢错，赔牌，但 3 人局不会立刻终局
        assertEquals(HalliGalliBoard.RingStatus.WRONG, room.ring(1).status());
        assertEquals(List.of("RING"), recorder.actionTypes());
        String params = recorder.actions.get(0).paramsJson();
        assertTrue(params.contains("\"status\":\"WRONG\""));
        assertTrue(params.contains("\"penalties\""));
        assertTrue(params.contains("\"RING_INVALID\""));

        // 200ms 内重复抢铃被防抖，不落库
        assertEquals(HalliGalliBoard.RingStatus.DEBOUNCED, room.ring(1).status());
        assertEquals(1, recorder.actions.size());
        assertEquals(1, recorder.actions.get(0).stateVersion());
    }

    @Test
    void 终局只回调一次名次() {
        FakeRecorder recorder = new FakeRecorder();
        HalliGalliRoom room = room(2, recorder);
        room.startGame("p1");

        // 两人残局：抢错 → 对手收桌面并终局
        room.ring(1);

        assertEquals(1, recorder.finishes.size());
        List<HalliGalliBoard.Rank> ranks = recorder.finishes.get(0);
        assertEquals(2, ranks.size());
        assertEquals(1, ranks.get(0).rank());
        assertEquals(2, ranks.get(0).seatNo());
        assertEquals(0, room.getFlipCount()); // 本局没人翻过牌，抢错直接终局

        // 终局后再抢铃直接被拒，不会重复回调
        room.ring(1);
        assertEquals(1, recorder.finishes.size());
    }

    @Test
    void 落库异常不影响对局进行() {
        HalliGalliRoom room = room(2, new ExplodingRecorder());

        assertEquals(HalliGalliRoom.StartResult.SUCCESS, room.startGame("p1"));
        assertNull(room.getMatchId()); // 开局落库炸了，matchId 不回填
        assertTrue(room.flip(room.getBoard().flipSeatNo()).ok());
        room.ring(1);
        assertNotNull(room.getBoard());
    }
}
