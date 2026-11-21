package org.dromara.dicego.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对局玩家战绩对象 game_match_player（M5 落库）
 *
 * <p>user_id 说明：登录玩家存真实 sys_user.user_id；未绑定（游客/压测）存负座位哨兵
 * （如 -1/-2）保证 (match_id, user_id) 唯一键不冲突。
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
     * 最终声望分
     */
    private Integer finalScore;

    /**
     * 购买卡牌数（同分判定依据）
     */
    private Integer cardCount;

    /**
     * 获得贵族数
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
