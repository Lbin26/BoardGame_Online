package org.dromara.web.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大厅游戏列表用的游戏定义摘要：直接映射 game_definition 表。
 * 用 JavaBean 而非 record，避免 MyBatis 在不同编译器参数开关下无法自动映射到构造器。
 */
@Data
public class GameDefinitionVO {

    /** 游戏编码，库里用下划线（halli_galli），前端 key 用驼峰（halliGalli） */
    private String gameCode;

    /** 游戏名 */
    private String gameName;

    /** 大厅分区编码：party/family/card/narrative/abstract/strategy */
    private String zoneCode;

    /** 是否可玩：true 才进房间层 */
    private Boolean playable;

    /** 最少开局人数 */
    private Integer minPlayers;

    /** 最多开局人数 */
    private Integer maxPlayers;

    /** 游戏简介（大厅卡片展示） */
    private String description;

    /** 机制标签，逗号分隔 */
    private String mechanisms;

    /** 房间层背景故事 */
    private String story;

    /** 海报路径 */
    private String icon;

    /** 游戏定义创建时间，前端据此判断是否最近上线 */
    private LocalDateTime createTime;
}
