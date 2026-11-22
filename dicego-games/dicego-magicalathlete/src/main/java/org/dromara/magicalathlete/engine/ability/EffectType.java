package org.dromara.magicalathlete.engine.ability;

/** 效果指令类型集合：能力只通过这些指令改变比赛，便于统一排序、执行、记日志、检测循环。 */
public enum EffectType {
    /** 相对移动：目标 +amount 格（amount 可负，clamp 到 0..30） */
    MOVE,
    /** 绝对传送：目标移动到 amount 格（不算主要移动，不触发超越） */
    MOVE_TO,
    /** 绊倒：目标下次主要移动被跳过 */
    TRIP,
    /** 解除绊倒：清除目标绊倒状态 */
    UNTRIP,
    /** 铜币：目标 +amount 铜分 */
    BRONZE,
    /** 计分：目标 +amount 分（直接计入 ScoreBoard 的额外分，不占奖牌） */
    SCORE,
    /** 替换基数：把当前行动者的主移动基数替换为 amount（炼金术士/大长腿） */
    REPLACE_BASE,
    /** 距离修正：给当前行动者的主移动加 amount（飞艇/黏液怪/教练，可叠加） */
    MODIFY_MOVE,
    /** 取消主移动：当前行动者本次主要移动作废（尺蠖/较真怪） */
    CANCEL_MOVE,
    /** 淘汰：目标退出本场比赛 */
    ELIMINATE,
    /** 额外回合：目标立即获得一次额外掷骰（天才） */
    EXTRA_TURN,
    /** 抢行动权：把下一个行动者改为 sourceSeat（船长） */
    STEAL_TURN,
    /** 交换位置：sourceSeat 与 targetSeat 互换棋子（人字拖；传送不算移动） */
    SWAP_POSITION
}
