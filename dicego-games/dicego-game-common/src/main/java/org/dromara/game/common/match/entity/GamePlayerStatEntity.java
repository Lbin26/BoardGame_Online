package org.dromara.game.common.match.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 玩家战绩统计对象 game_player_stat（全游戏共用）。
 *
 * <p>与 game_match_player 的明细分工：明细存每局每人一行，本表按 user_id 汇总，
 * 供个人主页/大厅展示总场次、胜率、最高分等。游客（负座位哨兵）不写本表。
 *
 * @author dicego
 */
@Data
@TableName("game_player_stat")
public class GamePlayerStatEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** 玩家用户ID（真实 sys_user.user_id） */
    private Long userId;

    /** 总场次 */
    private Integer totalMatches;

    /** 胜场 */
    private Integer winMatches;

    /** 负场（含未夺冠） */
    private Integer loseMatches;

    /** 单局最高分 */
    private Integer highestScore;

    /** 累计得分 */
    private Long totalScore;

    /** 最近一次对局时间 */
    private LocalDateTime lastPlayTime;

    /** 删除标志（0存在 1删除） */
    private String delFlag;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
