package org.dromara.halligalli.domain.bo;

import lombok.Data;

/**
 * 入座/起身请求：seatNo 可选（1..maxPlayers），缺省自动补最小空位。
 */
@Data
public class HalliGalliSeatBO {
    private String playerName;
    private Integer seatNo;
}
