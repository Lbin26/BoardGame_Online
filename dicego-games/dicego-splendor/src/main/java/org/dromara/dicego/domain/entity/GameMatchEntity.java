package org.dromara.dicego.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对局对象 game_match（M5 落库）
 *
 * <p>不继承 BaseEntity：本表由 WS/房间线程写入（无登录上下文），
 * create/update 字段由 DbSplendorMatchRecorder 显式赋值，避免 MetaObjectHandler 依赖。
 *
 * @author dicego
 */
@Data
@TableName("game_match")
public class GameMatchEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花算法）
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 对局编号（对外展示，M + 雪花串）
     */
    private String matchNo;

    /**
     * 房间号快照（内存房间会被清理，冗余保存）
     */
    private String roomNo;

    /**
     * 游戏编码（splendor）
     */
    private String gameCode;

    /**
     * 本局玩家数 2-4
     */
    private Integer playerCount;

    /**
     * 随机种子（复盘重放的核心）
     */
    private Long randomSeed;

    /**
     * 座位顺序：按座位号升序的用户ID逗号串（游客为负座位哨兵）
     */
    private String seatOrder;

    /**
     * 对局状态（0初始化 1进行中 2已结束 3异常中止）
     */
    private String matchStatus;

    /**
     * 获胜用户ID
     */
    private Long winnerUserId;

    /**
     * 总回合数
     */
    private Integer totalRounds;

    /**
     * 开局时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
