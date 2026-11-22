package org.dromara.magicalathlete.domain.vo;

import org.dromara.game.common.enums.RoomStatus;

import java.util.List;
import java.util.Set;

/**
 * 房间座位视图（REST / GAME_START / SEAT_CHANGED）：空座也输出，前端按 1..maxPlayers 渲染。
 */
public record MagicalAthleteRoomVO(
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
