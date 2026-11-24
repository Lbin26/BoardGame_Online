package org.dromara.magicalathlete.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.match.service.GamePlayerStatService;
import org.dromara.magicalathlete.domain.vo.MagicalAthleteSnapshotVO;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 胡闹运动会战绩落库：复用公共层 {@link GamePlayerStatService} 写 game_player_stat。
 *
 * <p>口径与宝石商人 / 德国心脏病 / 印加宝藏一致：
 * <ul>
 *   <li>score 取 scoreboard 总积分（金银铜 + 能力分）；</li>
 *   <li>rank = 1 记胜（同分并列共享胜利，全部记胜）；</li>
 *   <li>游客（boundUserId &lt;= 0）由公共服务跳过；</li>
 *   <li>本实现只记账，不中断对局——调用侧已做异常兜底。</li>
 * </ul>
 * 另三款游戏会写 game_match 三表；胡闹运动会目前没有对局开始/行动扩展点，暂不写这三表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbMagicalAthleteMatchRecorder implements MagicalAthleteMatchRecorder {

    private final GamePlayerStatService playerStatService;

    @Override
    public void onFinish(MagicalAthleteRoom room, List<MagicalAthleteSnapshotVO.ScoreVO> rankings) {
        if (rankings == null || rankings.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (MagicalAthleteSnapshotVO.ScoreVO r : rankings) {
            playerStatService.record(room.boundUserIdOf(r.name()), r.total(), r.rank() == 1, now);
        }
        log.info("房间 {} 战绩写入完成: players={}, 冠军={}", room.getRoomId(), rankings.size(),
            rankings.stream().filter(r -> r.rank() == 1).map(MagicalAthleteSnapshotVO.ScoreVO::name).toList());
    }
}
