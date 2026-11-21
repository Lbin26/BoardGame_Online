# 导入新桌游 skill（分区判定 + 复杂度评测）设计

日期：2026-11-20 · 状态：待批准 · 范围：项目 skill（markdown）+ `boardgame-ui` 小改

## Intent / Why

平台后续会不断导入新桌游。目前登记靠手工：分区归属拍脑袋、没有复杂度信息。用户要一个可复用的 skill：输入新桌游名，自动判定它属于大厅 7 大分区中的哪一个，并**自主评测其复杂程度**（规则多重、上手多难，即 BGG weight 概念，1~5，不是"赢的难度"），产出可复核的报告，经用户确认后登记进目录。复杂度同时展示在大厅游戏卡上，帮玩家选游戏。

## 用户已决定

- 路线选 **C：规则手册 + 证据佐证**（见 Approaches）。
- 评测对象是**复杂程度**（weight），不是难度/胜率。
- "导入"= 在 `catalog.ts` 登记目录条目（占位"敬请期待"），**不含可玩植入**。

## Goals

1. 项目 skill `import-boardgame`（`.claude/skills/import-boardgame/`），可 `/import-boardgame <游戏名>` 或自然语言触发。
2. 工作流：收集信息 → 调研佐证 → 分区判定 → 复杂度评测 → 结构化报告 → **用户确认** → 写入 catalog + 测试 → `npm test` + `npm run build` 绿。
3. 分区判定有据可依：7 区收录标准 + 机制映射 + 边界裁决规则 + 既有判例，写成 `references/zones.md`。
4. 复杂度评测有锚点：1~5 量表 + 维度清单 + 用目录既有游戏做校准锚点，写成 `references/complexity.md`。
5. 知名游戏 WebSearch 查 BGG weight/机制作佐证；查不到纯量表推理并注明置信度。BGG 分区体系与 7 区不同，**不照搬**，最终由 rubric 裁决。
6. `Game` 接口加 `weight?: number`（1~5，一位小数）；既有 7 个游戏全部回填；GameList 游戏卡显示复杂度。
7. 报告含：分区 + 复杂度 + 理由 + 证据来源 + 置信度；用户可改判，改判以用户为准。

## Non-goals / Anti-goals

- 不做可玩植入、不动后端/数据库（目录仍是前端静态常量）。
- 不新增/修改 7 个分区本身；游戏哪个区都不吻合时**报告给用户裁决**，不擅自造区。
- 不照抄 BGG weight 当结论（佐证≠裁决）；不编造机制标签（来自调研或用户输入，报告注明来源）。
- 不 commit（用户约定）；不动大厅三级导航/query 逻辑/轮询等既有行为。
- 失败即反目标：未经用户确认就写 catalog；评测无理由无锚点（拍脑袋）；既有测试红。

## Constraints

- skill 是纯 markdown（SKILL.md + references），无脚本、无新依赖；联网调研用内置 WebSearch，可离线降级。
- 代码侧沿用 Vue3 setup + Element Plus + vitest；TDD：weight 渲染先写失败测试再实现。
- 目录既有判例必须遵守（Splendor→card、Dice Throne/Avalon→narrative、Brass→heavy），新裁决不得与判例冲突，除非用户明确改判。

## Approaches

- **A 纯规则手册**：零依赖离线可用，但冷门游戏评不准、跨会话判断易漂移。
- **B 脚本抓 BGG**：数据真实可复现，但国内访问不稳、关键词映射脆、违背"自主评测"、维护成本高。排除。
- **C（选定）规则手册 + 证据佐证**：rubric + 锚点做裁决框架，BGG 数据做佐证，查不到降级纯推理并标注置信度。准、稳、可复核、保留用户裁决权。

## Skill 结构

```
.claude/skills/import-boardgame/
├── SKILL.md                    # 触发条件 + 7 步工作流 + 报告模板 + 写入规范
└── references/
    ├── zones.md                # 7 区收录标准、机制关键词→区映射、平局裁决规则、既有判例表
    └── complexity.md           # 1~5 锚定量表、评测维度、校准锚点表、置信度规则
```

- **zones.md 要点**：每区一段收录标准（原文机制标签 + 解释）；主机制主导原则（一个游戏只进一个区，按其**主导机制**归属）；边界规则（如"卡牌驱动的中策：引擎/成套为主→card，工人放置/行动点为主→strategy"）；判例表照录现有 7 游戏。
- **complexity.md 要点**：评测维度（规则书体量、子系统数量、单回合决策空间、卡牌文本量、上手局数）；锚定量表（1=5 分钟讲完规则≈五子棋；2=轻规则少量文本≈璀璨宝石；3=中量多子系统≈围棋/象棋之上；4=重策多系统耦合≈伯明翰；5=传承/战役级规则负荷）；校准锚点表（既有 7 游戏的 weight 参考值，注明约值）；置信度（有 BGG 佐证=高；仅知识推理=中；冷门无资料=低，建议用户提供规则书）。
- **SKILL.md 写入规范**：key 用 camelCase 英文、查重（findGame 已存在则提示改判/更新而非重复添加）；新条目 `playable: false, desc: '敬请期待'`；weight 一位小数；mechanisms 注明来源；同步补 catalog.spec.ts 断言；跑全量测试 + build。

## 代码改动（小）

- `catalog.ts`：`Game` 加 `weight?: number`；7 个既有游戏回填（参考锚点：五子棋 1.4、象棋 2.6、围棋 3.4、璀璨宝石 2.0、王权骰铸 2.2、阿瓦隆 2.0、伯明翰 3.9——实现时按 complexity.md 量表核定）。
- `GameList.vue`：游戏卡显示复杂度（如「复杂度 2.0」徽标，样式沿用卡面现有标签风格；具体排版留给 builder）。
- 测试：`catalog.spec.ts` 增 weight 存在/范围断言；`GameList.spec.ts` 增复杂度渲染断言；既有测试保持绿。

## 留给 builder

weight 徽标的具体文案/样式、zones.md 边界规则的措辞细化、SKILL.md description 触发词、报告模板排版、回填值的最终核定（按量表）。
