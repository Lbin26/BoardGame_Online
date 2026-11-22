package org.dromara.magicalathlete.domain.bo;

import lombok.Data;

/**
 * 入座/起身请求：seatNo 可选（1..maxPlayers），缺省自动补最小空位。
 */
@Data
public class MagicalAthleteSeatBO {
    private String playerName;
    private Integer seatNo;
}
