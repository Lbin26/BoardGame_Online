package org.dromara.dicego.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对局行动日志对象 game_action_log（M5 落库，只追加）
 *
 * <p>复盘依据：match_id → game_match.random_seed 重放 + 本表按序回放 action_params。
 *
 * @author dicego
 */
@Data
@TableName("game_action_log")
public class GameActionLogEntity implements Serializable {

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
     * 回合号
     */
    private Integer roundNo;

    /**
     * 行动者座位号
     */
    private Integer seatNo;

    /**
     * 行动者用户ID（游客为负座位哨兵）
     */
    private Long userId;

    /**
     * 动作唯一ID（客户端 actionId 透传；缺失时服务端按版本号补）
     */
    private String actionId;

    /**
     * 动作类型（TAKE_GEMS/BUY_CARD/RESERVE_CARD/DISCARD_GEMS/NOBLE_SELECT/START_GAME）
     */
    private String actionType;

    /**
     * 动作参数 JSON（原始请求参数，供复盘重放）
     */
    private String actionParams;

    /**
     * 动作执行后的状态版本号（单调递增）
     */
    private Integer stateVersion;

    /**
     * 服务端执行时间
     */
    private LocalDateTime executeTime;
}
