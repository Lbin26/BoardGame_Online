package org.dromara.magicalathlete.enums;

import lombok.Getter;

/**
 * 胡闹运动会专属 WS 消息（D1：各游戏自建枚举，公共层只留房间/聊天/心跳/胜负）。
 *
 * <p>client→server：START_GAME / PICK_RACER；ROLL / USE_OPTIONAL / READY_RACER 为 M2+ 预留。
 * 其余为服务端下行。wire 值取枚举 name()，与公共 GameMessage 一致。
 */
@Getter
public enum MagicalAthleteMessageType {

    // ---- 上行 ----
    START_GAME("开始对局（进入轮抽）"),
    PICK_RACER("轮抽选一名选手"),
    READY_RACER("赛前暗选出场选手（M5）"),
    ROLL("掷骰（M2）"),
    USE_OPTIONAL("使用可选能力（M3）"),

    // ---- 下行 ----
    DRAFT_STATE("轮抽状态同步"),
    DRAFT_PICK("某人选走一名选手"),
    MUSTER_READY("某人完成赛前暗选（不泄露选手）"),
    RACE_START("单场比赛开始"),
    STATE_SYNC("全量状态同步"),
    DICE_RESULT("掷骰结果"),
    ABILITY_CHAIN("能力结算链"),
    RACE_RESULT("单场结算"),
    GAME_OVER("对局结束"),
    ACTION_REJECT("动作被拒绝");

    private final String desc;

    MagicalAthleteMessageType(String desc) {
        this.desc = desc;
    }
}
