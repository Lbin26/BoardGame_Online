package org.dromara.dicego.manager;

import org.dromara.dicego.TestCaches;
import org.dromara.dicego.TestRecorder;
import org.dromara.dicego.domain.SplendorCardDef;
import org.dromara.dicego.domain.SplendorNobleDef;
import org.dromara.dicego.domain.vo.SplendorRankVO;
import org.dromara.dicego.domain.vo.SplendorRoomVO;
import org.dromara.dicego.engine.SplendorEngine;
import org.dromara.dicego.enums.GemColor;
import org.dromara.dicego.service.SplendorCardCache;
import org.dromara.game.common.enums.RoomStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Splendor 房间座位与开局测试（M1）+ 落库回调接线（M5）：容量 2-4（D3）、自动补位/换座/满员（D2）、
 * 房主开局、开局/行动/终局三回调与 user_id 绑定。
 */
class SplendorRoomTest {

    private static final SplendorCardCache CACHE = TestCaches.cache(8, 6);

    private SplendorRoom newRoom(int maxPlayers) {
        return new SplendorRoom("R1", "alice", maxPlayers, CACHE, new TestRecorder());
    }

    @Test
    void 同一用户多开多昵称只占一个真实座位_其余降级哨兵() {
        // 回归：本机用一个账号开多个昵称，若都绑定同一 user_id，开局落库会撞
        // uk_match_user(match_id,user_id) 唯一键并回滚整局。只有最早绑定的昵称保留真实 ID。
        TestRecorder rec = new TestRecorder();
        SplendorRoom room = new SplendorRoom("R1", "alice", 3, CACHE, rec);
        room.takeSeat("alice", 1);
        room.takeSeat("bob", 2);
        room.takeSeat("carol", 3);
        room.bindUser("alice", 777L);
        room.bindUser("bob", 777L);    // 同一 user_id 第二昵称 → 拒绝
        room.bindUser("carol", 777L);  // 第三昵称 → 拒绝

        assertEquals(SplendorRoom.StartResult.SUCCESS, room.startGame("alice"));
        assertEquals(List.of(
            new org.dromara.dicego.service.SplendorPlayerRef(1, "alice", 777L),
            new org.dromara.dicego.service.SplendorPlayerRef(2, "bob", -2L),
            new org.dromara.dicego.service.SplendorPlayerRef(3, "carol", -3L)),
            rec.starts.get(0).players());
    }

    @Test
    void 自动补位_升序入座() {
        SplendorRoom room = newRoom(4);
        assertEquals(SplendorRoom.SeatResult.SUCCESS, room.takeSeat("alice", null));
        assertEquals(SplendorRoom.SeatResult.SUCCESS, room.takeSeat("bob", null));
        assertEquals(1, room.seatOf("alice"));
        assertEquals(2, room.seatOf("bob"));
    }

    @Test
    void 指定座位_越界拒绝_被占拒绝() {
        SplendorRoom room = newRoom(3);
        assertEquals(SplendorRoom.SeatResult.INVALID_SEAT, room.takeSeat("alice", 0));
        assertEquals(SplendorRoom.SeatResult.INVALID_SEAT, room.takeSeat("alice", 4));
        assertEquals(SplendorRoom.SeatResult.SUCCESS, room.takeSeat("alice", 3));
        assertEquals(SplendorRoom.SeatResult.SEAT_TAKEN, room.takeSeat("bob", 3));
        assertEquals(SplendorRoom.SeatResult.SUCCESS, room.takeSeat("bob", 1));
    }

    @Test
    void 换座_同座幂等_在线状态迁移() {
        SplendorRoom room = newRoom(4);
        room.takeSeat("alice", 1);
        room.markConnected("alice", true);
        // 换到 3 座：成功且在线标记跟随
        assertEquals(SplendorRoom.SeatResult.SUCCESS, room.takeSeat("alice", 3));
        assertEquals(3, room.seatOf("alice"));
        assertTrue(room.toVO("alice").seats().get(2).connected(), "换座后在线状态应迁移到新座位");
        // 同座再点一次：幂等成功
        assertEquals(SplendorRoom.SeatResult.SUCCESS, room.takeSeat("alice", 3));
    }

    @Test
    void 满员拒绝新人_起身后可再入() {
        SplendorRoom room = newRoom(2);
        room.takeSeat("alice", null);
        room.takeSeat("bob", null);
        assertEquals(SplendorRoom.SeatResult.ROOM_FULL, room.takeSeat("carol", null));

        assertTrue(room.standUp("bob"));
        assertEquals(SplendorRoom.SeatResult.SUCCESS, room.takeSeat("carol", null));
        assertFalse(room.standUp("dave"), "未入座者不能起身");
    }

    @Test
    void 房主开局_校验链() {
        SplendorRoom room = newRoom(4);
        // 非房主
        room.takeSeat("bob", null);
        assertEquals(SplendorRoom.StartResult.NOT_HOST, room.startGame("bob"));
        // 房主但人不够
        assertEquals(SplendorRoom.StartResult.NOT_ENOUGH_PLAYERS, room.startGame("alice"));
        // 房主未入座同样不满足人数（人数按入座算）
        room.takeSeat("alice", null);
        assertEquals(SplendorRoom.StartResult.SUCCESS, room.startGame("alice"));

        assertNotNull(room.getBoard());
        assertEquals(RoomStatus.PLAYING, room.getStatus());
        assertEquals(2, room.getBoard().getPlayerCount());
        assertEquals(java.util.List.of("bob", "alice"), room.seatOrder(), "座位顺序按座位号升序（bob 先入座坐 1 号）");

        // 开局后：重复开局、入座、起身均拒绝
        assertEquals(SplendorRoom.StartResult.NOT_WAITING, room.startGame("alice"));
        assertEquals(SplendorRoom.SeatResult.NOT_WAITING, room.takeSeat("carol", null));
        assertFalse(room.standUp("alice"));
    }

    @Test
    void 断线不清座位_仅清在线标记() {
        SplendorRoom room = newRoom(4);
        room.takeSeat("alice", 1);
        room.markConnected("alice", true);
        assertTrue(room.toVO("alice").seats().get(0).connected());

        room.markConnected("alice", false);
        assertEquals(1, room.seatOf("alice"), "断线后座位保留（重连可恢复）");
        assertFalse(room.toVO("alice").seats().get(0).connected());
    }

    @Test
    void 房间VO_空座也渲染_旁观者视角() {
        SplendorRoom room = newRoom(3);
        room.takeSeat("alice", 2);
        SplendorRoomVO vo = room.toVO("alice");

        assertEquals(3, vo.seats().size(), "VO 渲染 1..maxPlayers 全部座位");
        assertNull(vo.seats().get(0).playerName());
        assertEquals("alice", vo.seats().get(1).playerName());
        assertEquals(2, vo.viewerSeatNo());

        SplendorRoomVO spectatorVO = room.toVO("eve");
        assertNull(spectatorVO.viewerSeatNo());
    }

    // ---- M5：落库回调接线 ----

    /** l1 全 15VP 一费卡卡池：双方各买一张即达 15VP 触发终局 */
    private static SplendorCardCache winCache() {
        Map<Integer, List<SplendorCardDef>> byLevel = new HashMap<>();
        for (int level = 1; level <= 3; level++) {
            List<SplendorCardDef> cards = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                cards.add(new SplendorCardDef("l" + level + "-" + i, level, GemColor.WHITE,
                    level == 1 ? 15 : 0, Map.of(GemColor.BLUE, 1)));
            }
            byLevel.put(level, List.copyOf(cards));
        }
        List<SplendorNobleDef> nobles = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            nobles.add(new SplendorNobleDef("n" + i, null, 3, Map.of(GemColor.WHITE, 99)));
        }
        return new SplendorCardCache(byLevel, nobles);
    }

    private static final Map<GemColor, Integer> THREE_DISTINCT =
        Map.of(GemColor.WHITE, 1, GemColor.BLUE, 1, GemColor.GREEN, 1);

    @Test
    void 落库回调_开局回填matchId_每成功行动一条日志() {
        TestRecorder rec = new TestRecorder();
        SplendorRoom room = new SplendorRoom("R1", "alice", 2, CACHE, rec);
        room.takeSeat("alice", 1);
        room.bindUser("alice", 7L); // alice 绑定真实用户
        room.takeSeat("bob", 2);    // bob 游客：哨兵 -2
        assertEquals(SplendorRoom.StartResult.SUCCESS, room.startGame("alice"));

        // 开局回调：座位升序快照 + matchId 回填
        assertEquals(1, rec.starts.size());
        assertEquals(2, rec.starts.get(0).playerCount());
        assertEquals(List.of(
            new org.dromara.dicego.service.SplendorPlayerRef(1, "alice", 7L),
            new org.dromara.dicego.service.SplendorPlayerRef(2, "bob", -2L)),
            rec.starts.get(0).players());
        assertEquals(TestRecorder.FAKE_MATCH_ID, room.getMatchId());

        // alice 行动：actionId 透传、版本号 1、绑定 userId
        var r1 = room.applyAction("alice", "TAKE_GEMS", "a1", "{\"gems\":{}}",
            b -> SplendorEngine.takeGems(b, "alice", THREE_DISTINCT));
        assertTrue(r1.isOk());
        assertEquals(1, rec.actions.size());
        var log1 = rec.actions.get(0);
        assertEquals(TestRecorder.FAKE_MATCH_ID, log1.matchId());
        assertEquals("TAKE_GEMS", log1.actionType());
        assertEquals("a1", log1.actionId());
        assertEquals(1, log1.stateVersion());
        assertEquals(1, log1.seatNo());
        assertEquals(7L, log1.userId());

        // bob 行动（无 actionId）：服务端按版本号补、游客记哨兵
        var r2 = room.applyAction("bob", "TAKE_GEMS", null, null,
            b -> SplendorEngine.takeGems(b, "bob", THREE_DISTINCT));
        assertTrue(r2.isOk());
        assertEquals(2, rec.actions.size());
        assertEquals("srv-2", rec.actions.get(1).actionId());
        assertEquals(2, rec.actions.get(1).stateVersion());
        assertEquals(-2L, rec.actions.get(1).userId());

        // 校验失败不写日志
        var bad = room.applyAction("bob", "BUY_CARD", "bad", null,
            b -> SplendorEngine.buyCard(b, "bob", "l1-999", "table"));
        assertFalse(bad.isOk());
        assertEquals(2, rec.actions.size(), "失败行动不应落日志");
        assertEquals(0, rec.finishes.size());
    }

    @Test
    void 落库回调_终局写名次_房间置FINISHED() {
        TestRecorder rec = new TestRecorder();
        SplendorRoom room = new SplendorRoom("R2", "alice", 2, winCache(), rec);
        room.takeSeat("alice", null);
        room.takeSeat("bob", null);
        assertEquals(SplendorRoom.StartResult.SUCCESS, room.startGame("alice"));

        // A 拿 B 拿，A 买（15VP 触发终局轮），B 买后轮回先手 → 终局
        assertTrue(room.applyAction("alice", "TAKE_GEMS", "1", null,
            b -> SplendorEngine.takeGems(b, "alice", THREE_DISTINCT)).isOk());
        assertTrue(room.applyAction("bob", "TAKE_GEMS", "2", null,
            b -> SplendorEngine.takeGems(b, "bob", THREE_DISTINCT)).isOk());
        assertTrue(room.applyAction("alice", "BUY_CARD", "3", null,
            b -> SplendorEngine.buyCard(b, "alice", b.tableOf(1).get(0).code(), "table")).isOk());
        assertTrue(room.applyAction("bob", "BUY_CARD", "4", null,
            b -> SplendorEngine.buyCard(b, "bob", b.tableOf(1).get(0).code(), "table")).isOk());

        assertEquals(RoomStatus.FINISHED, room.getStatus());
        assertEquals(1, rec.finishes.size(), "终局应恰好回调一次");
        List<SplendorRankVO> rankings = rec.finishes.get(0);
        assertEquals(2, rankings.size());
        assertEquals("alice", rankings.get(0).name(), "同 VP 同卡数按座位号，先手 alice 第一");
        assertEquals(15, rankings.get(0).vp());
    }
}
