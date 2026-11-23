package org.dromara.web.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大厅展示用的游戏定义摘要：只暴露判断「新上线」所需的最小字段。
 * 用 JavaBean 而非 record，避免 MyBatis 在不同编译器参数开关下无法自动映射到构造器。
 */
@Data
public class GameDefinitionVO {

    /** 游戏编码，与前端 catalog 的 key 对应（下划线转驼峰） */
    private String gameCode;

    /** 游戏名 */
    private String gameName;

    /** 游戏定义创建时间，前端据此判断是否最近上线 */
    private LocalDateTime createTime;
}
