package org.dromara.magicalathlete.domain;

/**
 * 选手定义（D2：36 名是规则常量，代码展开为牌堆，不建表）。
 *
 * <p>key 与 design/games/magicalAthlete/game.yaml 一一对应，是协议与落库的稳定标识。
 * abilityType 仅用于实现分流（见准备计划 D3 事件注册表）：
 * fixed-move / mod-move / interrupt / reactive / scoring / warp / copy。
 *
 * @param key           稳定英文标识
 * @param name          中文译名
 * @param abilityType   能力触发器分类
 * @param abilitySummary 能力摘要（规则原文见 rules.md，M1 仅供展示）
 */
public record Racer(String key, String name, String abilityType, String abilitySummary) {
}
