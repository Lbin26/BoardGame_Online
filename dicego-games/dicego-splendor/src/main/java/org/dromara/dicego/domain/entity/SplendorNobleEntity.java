package org.dromara.dicego.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 宝石商人-贵族对象 splendor_noble（只读：种子数据由 script/sql/boardgame_online.sql 灌入）
 *
 * @author dicego
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("splendor_noble")
public class SplendorNobleEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 贵族业务编码（与 game.yaml key 一致，如 noble-01）
     */
    private String nobleCode;

    /**
     * 贵族名称（展示用，可空）
     */
    private String nobleName;

    /**
     * 声望分（固定 3）
     */
    private Integer prestige;

    /**
     * 要求白卡数量
     */
    private Integer needWhite;

    /**
     * 要求蓝卡数量
     */
    private Integer needBlue;

    /**
     * 要求绿卡数量
     */
    private Integer needGreen;

    /**
     * 要求红卡数量
     */
    private Integer needRed;

    /**
     * 要求黑卡数量
     */
    private Integer needBlack;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;
}
