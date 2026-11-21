package org.dromara.xiangqi.domain.bo;

import lombok.Data;

/** 象棋选座/起身请求体（起身时 color 为空；红黑语义象棋专属） */
@Data
public class SeatBO {
    private String playerName;
    private String color;
}
