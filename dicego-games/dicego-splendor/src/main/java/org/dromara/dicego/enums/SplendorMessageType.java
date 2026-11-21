package org.dromara.dicego.enums;

/**
 * Splendor 专属消息类型（D1：各游戏自建枚举，wire 值 = 枚举 name()）。
 * 字段 schema 见 docs/superpowers/specs/2026-11-20-splendor-protocol.md。
 * 房间生命周期/聊天/心跳/胜负走公共 GameMessageType。
 */
public enum SplendorMessageType {

    // ---- 客户端 → 服务端（请求） ----
    /** 房主请求开局 */
    START_GAME,
    /** 拿宝石（三异色各1 或 同色×2） */
    TAKE_GEMS,
    /** 买发展卡（桌面 or 本人预留） */
    BUY_CARD,
    /** 预留卡（桌面明卡 or 盲留某级牌堆顶） */
    RESERVE_CARD,
    /** 回合末手牌 >10 强制弃宝石 */
    DISCARD_GEMS,
    /** 同时满足多位贵族时选其一拜访 */
    NOBLE_SELECT,

    // ---- 服务端 → 客户端（事件） ----
    /** 全量快照（按收件人个性化，唯一状态出口） */
    STATE_SYNC,
    /** 行动结算成功（中立：供动画），状态以 STATE_SYNC 为准 */
    ACTION_DONE,
    /** 行动校验失败（仅发送者，含 reason 原因码） */
    ACTION_REJECT
}
