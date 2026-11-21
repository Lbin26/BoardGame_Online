package org.dromara.dicego.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 宝石商人-发展卡对象 splendor_card（只读：种子数据由 script/sql/boardgame_online.sql 灌入）
 *
 * @author dicego
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("splendor_card")
public class SplendorCardEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 卡牌业务编码（与 game.yaml key 一致，如 l1-001）
     */
    private String cardCode;

    /**
     * 卡牌等级 1/2/3
     */
    private Integer cardLevel;

    /**
     * 产出宝石色 0白 1蓝 2绿 3红 4黑
     */
    private Integer bonus;

    /**
     * 声望分（0-5）
     */
    private Integer prestige;

    /**
     * 购买成本-白
     */
    private Integer costWhite;

    /**
     * 购买成本-蓝
     */
    private Integer costBlue;

    /**
     * 购买成本-绿
     */
    private Integer costGreen;

    /**
     * 购买成本-红
     */
    private Integer costRed;

    /**
     * 购买成本-黑
     */
    private Integer costBlack;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;
}
