package org.dromara.magicalathlete.engine.ability;

/**
 * 能力触发事件（D3 事件注册表的时间轴锚点）。
 *
 * <p>一个回合的事件顺序固定为：
 * {@code BEFORE_RACE}（赛前）→ {@code ROLL_REPLACE}（改基数）→ {@code DICE_RESULT}（最终骰点）
 * → {@code BEFORE_MAIN_MOVE}（距离修正）→ 主要移动 → {@code ON_PASS}/{@code ON_STOP}
 * → {@code AFTER_TURN} → {@code ON_ELIMINATE}。
 * {@code ON_ABILITY_FIRED} 由效果管线在任一能力实际发动后自动补发，供蹭蹭怪类能力监听。
 */
public enum AbilityEvent {
    /** 赛前准备（西西弗斯 +4、倒霉蛋垫底判定等） */
    BEFORE_RACE,
    /** 替换主移动基数（炼金术士 1/2→4、大长腿→5、火箭翻倍） */
    ROLL_REPLACE,
    /** 最终主骰点数已确定（尺蠖/跟班/船长/西西弗斯/骰子商人） */
    DICE_RESULT,
    /** 主要移动前的距离修正（飞艇/黏液怪/野兔/教练/啦啦队长） */
    BEFORE_MAIN_MOVE,
    /** 一次移动越过某座位（香蕉/半人马/跳跳蟾蜍） */
    ON_PASS,
    /** 棋子停在某格（鸡脚屋/大嘴怪/巨婴/浪漫家） */
    ON_STOP,
    /** 棋子被绊倒 */
    ON_TRIP,
    /** 回合结束（起哄者位移≤1、蹭蹭怪跟随） */
    AFTER_TURN,
    /** 棋子被淘汰 */
    ON_ELIMINATE,
    /** 任一能力实际发动后自动补发（蹭蹭怪） */
    ON_ABILITY_FIRED
}
