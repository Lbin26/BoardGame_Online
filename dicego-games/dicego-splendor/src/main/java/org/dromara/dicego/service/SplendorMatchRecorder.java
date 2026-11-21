package org.dromara.dicego.service;

import org.dromara.dicego.domain.vo.SplendorRankVO;
import org.dromara.dicego.manager.SplendorRoom;

import java.util.List;

/**
 * 对局落库记录器（M5，D6：每步实时写 action_log）。
 *
 * <p>实现必须自担异常（或由房间侧兜底捕获）：落库失败不得影响对局进行。
 * 测试以内存假实现替换，保证房间/引擎测试不依赖 DB。
 */
public interface SplendorMatchRecorder {

    /**
     * 开局：写 game_match（进行中）+ game_match_player 全员初始行，并向房间回填 matchId。
     */
    void onStart(SplendorRoom room, long seed, int playerCount, List<SplendorPlayerRef> players);

    /**
     * 行动结算成功：写 game_action_log 一行（只追加）。
     */
    void onAction(SplendorRoom room, SplendorActionLogBO log);

    /**
     * 终局：更新 game_match（状态/胜者/回合数）与 game_match_player（分数/名次）。
     */
    void onFinish(SplendorRoom room, List<SplendorRankVO> rankings);
}
