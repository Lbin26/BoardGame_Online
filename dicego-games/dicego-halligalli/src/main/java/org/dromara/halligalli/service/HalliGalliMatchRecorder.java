package org.dromara.halligalli.service;

import org.dromara.halligalli.engine.HalliGalliBoard;
import org.dromara.halligalli.manager.HalliGalliRoom;

import java.util.List;

/**
 * 对局落库记录器（M5）：开局写 match + 全员行，行动写 action_log，终局写名次。
 *
 * <p>实现必须自担异常（或由房间侧兜底捕获）：落库失败不得影响对局进行。
 * 测试以内存假实现替换，保证房间/引擎测试不依赖 DB。
 */
public interface HalliGalliMatchRecorder {

    /**
     * 开局：写 game_match（进行中）+ game_match_player 全员初始行，并向房间回填 matchId。
     */
    void onStart(HalliGalliRoom room, long seed, int playerCount, List<HalliGalliPlayerRef> players);

    /**
     * 翻牌/抢铃结算成功：写 game_action_log 一行（只追加）。
     */
    void onAction(HalliGalliRoom room, HalliGalliActionLogBO log);

    /**
     * 终局：更新 game_match（状态/胜者/翻牌次数）与 game_match_player（持牌数/名次）。
     */
    void onFinish(HalliGalliRoom room, List<HalliGalliBoard.Rank> rankings);

    /**
     * 空实现：单测与无 DB 场景（房间默认使用）。
     */
    HalliGalliMatchRecorder NOOP = new HalliGalliMatchRecorder() {
        @Override
        public void onStart(HalliGalliRoom room, long seed, int playerCount, List<HalliGalliPlayerRef> players) {
        }

        @Override
        public void onAction(HalliGalliRoom room, HalliGalliActionLogBO log) {
        }

        @Override
        public void onFinish(HalliGalliRoom room, List<HalliGalliBoard.Rank> rankings) {
        }
    };
}
