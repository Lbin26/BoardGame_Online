package org.dromara.dicego.enums;

/**
 * Splendor 行动失败原因码（协议 §3，ACTION_REJECT.reason 的 wire 值 = 枚举 name()）。
 * 座位类原因码（NOT_WAITING/SEAT_TAKEN/ROOM_FULL/INVALID_SEAT）走 SplendorRoom.SeatResult，
 * 开局类（NOT_HOST/NOT_ENOUGH_PLAYERS）走 SplendorRoom.StartResult，不在本枚举内。
 */
public enum SplendorFailReason {

    /** 对局未开始或已结束 */
    NOT_PLAYING("对局未开始或已结束"),
    /** 非当前回合方行动（含旁观者） */
    NOT_YOUR_TURN("当前不是你的回合"),
    /** 拿宝石组合非法（非三异色各1/同色×2，或包含黄金） */
    INVALID_GEMS("拿宝石组合非法"),
    /** 银行该色不足（同色×2 需供给 ≥4） */
    GEM_SUPPLY_SHORT("银行该色宝石不足"),
    /** cardId 不在桌面明卡中 */
    CARD_NOT_ON_TABLE("该卡不在桌面明卡中"),
    /** source=reserved 但不在本人预留区 */
    CARD_NOT_RESERVED("该卡不在你的预留区"),
    /** 宝石+加成+黄金不足以支付 */
    CANNOT_AFFORD("宝石不足以购买该卡"),
    /** 本人预留已达 3 张 */
    RESERVE_LIMIT("预留卡已达 3 张上限"),
    /** 盲留的该级牌堆已耗尽 */
    DECK_EMPTY("该级牌堆已耗尽"),
    /** 有待办阶段（弃宝石/选贵族）时发起了主行动 */
    MUST_DISCARD("须先完成当前回合的待办操作（弃宝石/选贵族）"),
    /** 弃宝石数量或组合非法 */
    DISCARD_INVALID("弃宝石数量或组合非法"),
    /** 选中的贵族不在当前可拜访集合中 */
    NOBLE_NOT_ELIGIBLE("未满足该贵族的拜访条件"),
    /** 请求参数非法（缺字段/类型错误/未知色名） */
    INVALID_PARAM("请求参数非法"),
    /** 协议已定但引擎未实现的行动（保留兜底） */
    NOT_IMPLEMENTED("该行动尚未实现");

    private final String message;

    SplendorFailReason(String message) {
        this.message = message;
    }

    /** 默认人类可读提示（Handler 可用更具体文案覆盖） */
    public String defaultMessage() {
        return message;
    }
}
