# 设计工作区

每款新桌游设计一个目录 `design/games/<key>/`（key 为 camelCase 英文）：

| 文件 | 职责 |
|---|---|
| `game.yaml` | 结构化规则（schema 见工具包 src/schema/game.ts）：元信息、机制、组件、回合阶段、可参数化子系统。**数值单一事实来源**，模拟与平台导出都消费它 |
| `rules.md` | 回合流程散文规则——结构化格式收不下的流程性描述 |
| `journal.md` | 设计决策与迭代记录：每阶段决定了什么、为什么、借鉴了哪个样本的哪个特征、用户改判记录 |
| `sims/` | 模拟配置（yaml）+ 报告（md + json）。平衡论断的证据库 |
| `exports/` | 导出产物：`rulebook.md`（实体规则书）、`implementation-notes.md`（平台植入要点） |

工作流由 `.claude/skills/design-boardgame/SKILL.md` 承载；工具用法见其 `references/toolbox.md`。
样本库在 `design/samples/`（成熟桌游的结构化拆解档案，供 remix 参考）。