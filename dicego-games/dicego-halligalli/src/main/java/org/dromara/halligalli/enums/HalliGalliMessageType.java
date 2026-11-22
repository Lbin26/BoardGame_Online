package org.dromara.halligalli.enums;

import lombok.Getter;

/**
 * 德国心脏病专属 WS 消息（D1：各游戏自建枚举，公共层只留房间/聊天/心跳/胜负）。
 * client→server：START_GAME / FLIP / RING；其余为服务端下行。
 */
@Getter
public enum HalliGalliMessageType {

    START_GAME("开始对局"),
    FLIP("翻一张牌"),
    RING("拍铃"),

    STATE_SYNC("全量状态同步"),
    FLIP_DONE("翻牌结果"),
    RING_RESULT("抢铃结果"),
    PLAYER_OUT("玩家出局"),
    ACTION_REJECT("动作被拒绝");

    private final String desc;

    HalliGalliMessageType(String desc) {
        this.desc = desc;
    }
}
