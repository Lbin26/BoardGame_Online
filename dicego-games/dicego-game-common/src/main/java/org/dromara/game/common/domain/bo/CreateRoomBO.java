package org.dromara.game.common.domain.bo;

import lombok.Data;

@Data
public class CreateRoomBO {
    private String playerName;

    /**
     * 房间容量上限（可选，游戏自释：象棋固定 2 忽略此字段，Splendor 可选 2-4，缺省 4）。
     * 对应 D3 决策：容量由创建时锁定，入座校验由具体游戏房实现。
     */
    private Integer maxPlayers;
}
