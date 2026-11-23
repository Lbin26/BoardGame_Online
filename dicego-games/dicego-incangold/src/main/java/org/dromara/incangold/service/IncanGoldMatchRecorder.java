package org.dromara.incangold.service;

import org.dromara.incangold.engine.IncanGoldEngine;
import org.dromara.incangold.manager.IncanGoldRoom;

import java.util.List;

/**
 * 印加宝藏对局落库：开局写 match + 全员行，暗选写 action_log，终局写名次与玩家汇总战绩。
 *
 * <p>实现自担异常，房间侧另有 try/catch 兜底：DB 失败不得中断对局。
 */
public interface IncanGoldMatchRecorder {

    void onStart(IncanGoldRoom room, long seed, int playerCount, List<IncanGoldPlayerRef> players);

    void onAction(IncanGoldRoom room, IncanGoldActionLogBO log);

    void onFinish(IncanGoldRoom room, List<IncanGoldEngine.Ranking> rankings);

    /** 单测与无 DB 场景 */
    IncanGoldMatchRecorder NOOP = new IncanGoldMatchRecorder() {
        @Override
        public void onStart(IncanGoldRoom room, long seed, int playerCount, List<IncanGoldPlayerRef> players) {
        }

        @Override
        public void onAction(IncanGoldRoom room, IncanGoldActionLogBO log) {
        }

        @Override
        public void onFinish(IncanGoldRoom room, List<IncanGoldEngine.Ranking> rankings) {
        }
    };
}
