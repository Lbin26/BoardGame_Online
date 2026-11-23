package org.dromara.incangold.enums;

import lombok.Getter;

/**
 * 印加宝藏专属 WS 消息。公共类型（房间/聊天/心跳/胜负/错误）走 GameMessageType。
 * client→server：START_GAME / CHOICE / NEXT_ROUND；其余为服务端下行。
 */
@Getter
public enum IncanGoldMessageType {

    START_GAME("开始探险"),
    CHOICE("提交继续/撤退暗选"),
    NEXT_ROUND("房主跳过结算展示"),

    STATE_SYNC("按人定制的全量快照"),
    ACTION_REJECT("动作被拒绝");

    private final String desc;

    IncanGoldMessageType(String desc) {
        this.desc = desc;
    }
}
