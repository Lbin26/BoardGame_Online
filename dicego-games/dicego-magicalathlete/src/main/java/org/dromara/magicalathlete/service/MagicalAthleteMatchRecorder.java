package org.dromara.magicalathlete.service;

import org.dromara.magicalathlete.domain.vo.MagicalAthleteSnapshotVO;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom;

import java.util.List;

/**
 * 胡闹运动会终局落库扩展点。
 *
 * <p>四场比赛结束（GAME_OVER）时由 WS 处理器调用一次，具体持久化实现可替换；
 * 当前仅有数据库实现 {@link DbMagicalAthleteMatchRecorder}，负责把每名玩家的成绩汇总进 game_player_stat。
 */
public interface MagicalAthleteMatchRecorder {

    /**
     * 对局完成。
     *
     * @param room      终局房间（用于把玩家名映射为登录用户 ID）
     * @param rankings  终局排名（来自 scoreboard，score 为总积分）
     */
    void onFinish(MagicalAthleteRoom room, List<MagicalAthleteSnapshotVO.ScoreVO> rankings);
}
