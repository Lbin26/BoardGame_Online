package org.dromara.dicego.domain.vo;

import org.dromara.game.common.enums.RoomStatus;

import java.util.List;
import java.util.Set;

/**
 * Splendor 房间视图（协议 §1 room 载荷）。
 * status 为 RoomStatus 枚举，wire 值 = name()（WAITING/PLAYING/FINISHED）。
 */
public record SplendorRoomVO(
    String roomId,
    String hostName,
    int maxPlayers,
    RoomStatus status,
    List<SeatVO> seats,
    Integer viewerSeatNo,
    Set<String> afkPlayers
) {
    /** 座位视图：seatNo 1..maxPlayers，空座 playerName=null */
    public record SeatVO(
        int seatNo,
        String playerName,
        boolean connected,
        boolean afk
    ) {
    }
}
