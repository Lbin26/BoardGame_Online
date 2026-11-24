package org.dromara.magicalathlete.service;

import org.dromara.game.common.match.service.GamePlayerStatService;
import org.dromara.magicalathlete.domain.vo.MagicalAthleteSnapshotVO;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 胡闹运动会终局战绩：排名→公共战绩服务的映射口径 */
class DbMagicalAthleteMatchRecorderTest {

    @Test
    void writesEveryRankedPlayerWithTotalScoreAndWinnerFlag() {
        GamePlayerStatService statService = mock(GamePlayerStatService.class);
        MagicalAthleteRoom room = new MagicalAthleteRoom("R1", "alice", 2);
        room.bindUser("alice", 1001L);
        room.bindUser("bob", 1002L);
        List<MagicalAthleteSnapshotVO.ScoreVO> rankings = List.of(
            new MagicalAthleteSnapshotVO.ScoreVO(1, "alice", 3, 1, 2, 12, 1),
            new MagicalAthleteSnapshotVO.ScoreVO(2, "bob", 1, 2, 0, 7, 2)
        );

        new DbMagicalAthleteMatchRecorder(statService).onFinish(room, rankings);

        verify(statService).record(eq(1001L), eq(12), eq(true), any(LocalDateTime.class));
        verify(statService).record(eq(1002L), eq(7), eq(false), any(LocalDateTime.class));
    }

    @Test
    void tiedFirstPlacesAllCountAsWinners() {
        GamePlayerStatService statService = mock(GamePlayerStatService.class);
        MagicalAthleteRoom room = new MagicalAthleteRoom("R2", "alice", 2);
        room.bindUser("alice", 1001L);
        room.bindUser("bob", 1002L);
        List<MagicalAthleteSnapshotVO.ScoreVO> rankings = List.of(
            new MagicalAthleteSnapshotVO.ScoreVO(1, "alice", 4, 3, 1, 16, 1),
            new MagicalAthleteSnapshotVO.ScoreVO(2, "bob", 4, 3, 1, 16, 1)
        );

        new DbMagicalAthleteMatchRecorder(statService).onFinish(room, rankings);

        verify(statService).record(eq(1001L), eq(16), eq(true), any(LocalDateTime.class));
        verify(statService).record(eq(1002L), eq(16), eq(true), any(LocalDateTime.class));
    }

    @Test
    void guestAndEmptyRankingsAreSkippedByCommonService() {
        GamePlayerStatService statService = mock(GamePlayerStatService.class);
        MagicalAthleteRoom room = new MagicalAthleteRoom("R3", "guest", 2);

        new DbMagicalAthleteMatchRecorder(statService).onFinish(room, List.of());
        verify(statService, never()).record(anyLong(), anyInt(), anyBoolean(), any(LocalDateTime.class));
    }
}
