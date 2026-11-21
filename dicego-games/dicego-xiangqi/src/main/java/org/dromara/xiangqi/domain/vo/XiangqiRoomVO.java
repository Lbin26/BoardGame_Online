package org.dromara.xiangqi.domain.vo;

import org.dromara.xiangqi.enums.PlayerColor;
import org.dromara.game.common.enums.RoomStatus;

import java.util.Set;

/** 象棋房间视图（red/black 双座语义，象棋专属） */
public record XiangqiRoomVO(
    String roomId,
    String redPlayerName,
    String blackPlayerName,
    RoomStatus status,
    PlayerColor viewerColor,
    String hostName,
    Set<String> afkPlayers
) {}
