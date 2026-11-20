package org.dromara.game.common.domain.bo;

import lombok.Data;

/** 选座/起身请求体（起身时 color 为空） */
@Data
public class SeatBO {
    private String playerName;
    private String color;
}
