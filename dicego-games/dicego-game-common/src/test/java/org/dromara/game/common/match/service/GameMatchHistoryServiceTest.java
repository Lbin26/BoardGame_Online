package org.dromara.game.common.match.service;

import org.dromara.game.common.match.entity.GameMatchEntity;
import org.dromara.game.common.match.entity.GameMatchPlayerEntity;
import org.dromara.game.common.match.mapper.GameMatchMapper;
import org.dromara.game.common.match.mapper.GameMatchPlayerMapper;
import org.dromara.game.common.match.vo.GameMatchRecentVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 个人战绩查询（全游戏共用）：未登录直接空、字段映射、已清理对局跳过、条数收敛。
 */
@ExtendWith(MockitoExtension.class)
class GameMatchHistoryServiceTest {

    @Mock
    private GameMatchPlayerMapper matchPlayerMapper;

    @Mock
    private GameMatchMapper matchMapper;

    @InjectMocks
    private GameMatchHistoryService service;

    private static GameMatchPlayerEntity playerRow(long matchId, int seatNo, int rank, int score, String winFlag) {
        GameMatchPlayerEntity row = new GameMatchPlayerEntity();
        row.setId(matchId * 10 + seatNo);
        row.setMatchId(matchId);
        row.setUserId(1001L);
        row.setSeatNo(seatNo);
        row.setRankNo(rank);
        row.setFinalScore(score);
        row.setWinFlag(winFlag);
        return row;
    }

    private static GameMatchEntity match(long id, String gameCode, String status) {
        GameMatchEntity match = new GameMatchEntity();
        match.setId(id);
        match.setMatchNo("M" + id);
        match.setGameCode(gameCode);
        match.setPlayerCount(3);
        match.setMatchStatus(status);
        match.setStartTime(LocalDateTime.of(2026, 11, 21, 20, 0));
        match.setEndTime(LocalDateTime.of(2026, 11, 21, 20, 12));
        return match;
    }

    @Test
    void 未登录或游客id直接返回空且不查库() {
        assertTrue(service.recent(null, 10).isEmpty());
        assertTrue(service.recent(0L, 10).isEmpty());
        assertTrue(service.recent(-3L, 10).isEmpty());
        verify(matchPlayerMapper, never()).selectList(any());
    }

    @Test
    void 按玩家行取最近对局并合并主体() {
        when(matchPlayerMapper.selectList(any())).thenReturn(List.of(
            playerRow(2L, 2, 2, 31, "0"),
            playerRow(1L, 1, 1, 45, "1")));
        when(matchMapper.selectByIds(any())).thenReturn(List.of(
            match(1L, "halli_galli", "2"),
            match(2L, "splendor", "2")));

        List<GameMatchRecentVO> list = service.recent(1001L, 10);

        assertEquals(2, list.size());
        GameMatchRecentVO latest = list.get(0);
        assertEquals("M2", latest.matchNo());
        assertEquals("splendor", latest.gameCode());
        assertEquals(3, latest.playerCount());
        assertEquals(2, latest.seatNo());
        assertEquals(2, latest.rankNo());
        assertEquals(31, latest.score());
        assertFalse(latest.win());
        assertEquals("2", latest.status());
        assertEquals(LocalDateTime.of(2026, 11, 21, 20, 12), latest.endTime());

        assertTrue(list.get(1).win());
        assertEquals("halli_galli", list.get(1).gameCode());
    }

    @Test
    void 对局主体已被清理时跳过该行() {
        when(matchPlayerMapper.selectList(any())).thenReturn(List.of(
            playerRow(9L, 1, 1, 20, "1"),
            playerRow(1L, 2, 1, 30, "1")));
        when(matchMapper.selectByIds(any())).thenReturn(List.of(match(1L, "xiangqi", "2")));

        List<GameMatchRecentVO> list = service.recent(1001L, 10);

        assertEquals(1, list.size());
        assertEquals("xiangqi", list.get(0).gameCode());
    }

    @Test
    void 条数收敛到一到五十() {
        assertEquals(1, GameMatchHistoryService.clampLimit(0));
        assertEquals(1, GameMatchHistoryService.clampLimit(-5));
        assertEquals(10, GameMatchHistoryService.clampLimit(10));
        assertEquals(50, GameMatchHistoryService.clampLimit(999));
    }
}
