package org.dromara.incangold.domain.vo;

import org.dromara.game.common.enums.RoomStatus;

import java.util.List;
import java.util.Set;

/**
 * 房间座位视图（REST / 加入广播）：空座也输出，前端按 1..maxPlayers 渲染。
 */
public record IncanGoldRoomVO(
    String roomId,
    String hostName,
    int maxPlayers,
    RoomStatus status,
    List<SeatVO> seats,
    Integer mySeatNo,
    Set<String> afkPlayers
) {
    public record SeatVO(int seatNo, String name, boolean online, boolean afk) {
    }
}
