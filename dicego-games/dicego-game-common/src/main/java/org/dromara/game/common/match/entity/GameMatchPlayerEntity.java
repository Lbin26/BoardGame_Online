package org.dromara.game.common.match.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对局玩家战绩对象 game_match_player（M5 落库，全游戏共用）。
 *
 * <p>user_id 说明：登录玩家存真实 sys_user.user_id；未绑定（游客/压测）存负座位哨兵
 * （如 -1/-2）保证 (match_id, user_id) 唯一键不冲突。
 *
 * <p>字段口径按游戏解释：Splendor 为声望/购卡数/贵族数；Halli Galli 为
 * 终局持牌数（finalScore 与 cardCount 同值），nobleCount 恒 0。
 *
 * @author dicego
 */
@Data
@TableName("game_match_player")
public class GameMatchPlayerEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花算法）
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 对局ID
     */
    private Long matchId;

    /**
     * 玩家用户ID（游客为负座位哨兵）
     */
    private Long userId;

    /**
     * 本局座位号
     */
    private Integer seatNo;

    /**
     * 最终得分（各游戏口径不同）
     */
    private Integer finalScore;

    /**
     * 主计数（Splendor 为购卡数，Halli Galli 为终局持牌数）
     */
    private Integer cardCount;

    /**
     * 附属计数（Splendor 为贵族数，Halli Galli 恒 0）
     */
    private Integer nobleCount;

    /**
     * 名次（1=冠军）
     */
    private Integer rankNo;

    /**
     * 是否胜者（0否 1是）
     */
    private String winFlag;

    /**
     * 是否中途退出/掉线判负（0否 1是）
     */
    private String quitFlag;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
