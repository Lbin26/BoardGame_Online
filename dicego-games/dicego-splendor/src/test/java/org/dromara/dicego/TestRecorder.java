package org.dromara.dicego;

import org.dromara.dicego.domain.vo.SplendorRankVO;
import org.dromara.dicego.manager.SplendorRoom;
import org.dromara.dicego.service.SplendorActionLogBO;
import org.dromara.dicego.service.SplendorMatchRecorder;
import org.dromara.dicego.service.SplendorPlayerRef;

import java.util.ArrayList;
import java.util.List;

/**
 * 内存假记录器（M5 测试用）：捕获三个回调并模拟 matchId 回填（9001）。
 * 房间/引擎/REST 测试借此完全不依赖 DB。
 */
public class TestRecorder implements SplendorMatchRecorder {

    public record Start(long seed, int playerCount, List<SplendorPlayerRef> players) {
    }

    public static final long FAKE_MATCH_ID = 9001L;

    public final List<Start> starts = new ArrayList<>();
    public final List<SplendorActionLogBO> actions = new ArrayList<>();
    public final List<List<SplendorRankVO>> finishes = new ArrayList<>();

    @Override
    public void onStart(SplendorRoom room, long seed, int playerCount, List<SplendorPlayerRef> players) {
        starts.add(new Start(seed, playerCount, players));
        room.adoptMatchId(FAKE_MATCH_ID);
    }

    @Override
    public void onAction(SplendorRoom room, SplendorActionLogBO log) {
        actions.add(log);
    }

    @Override
    public void onFinish(SplendorRoom room, List<SplendorRankVO> rankings) {
        finishes.add(rankings);
    }
}
