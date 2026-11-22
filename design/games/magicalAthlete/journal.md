# 胡闹运动会（Magical Athlete）数据核对记录

建档：2026-11-21 · 状态：数据集初稿完成，待实现前平台口径拍板

## 结论速览

| 项 | 定案值 | 置信度 |
|---|---|---|
| 规则版本 | CMYK 基础盒 `cmyk-base-2026-08-28` | 高（S3 明确标注并核对 FAQ） |
| 人数 / 时长 | 2–6 人 / 约 30 分钟 | 高（多源一致） |
| 场次 | 4 场，平缓/狂野/平缓/狂野 | 高（S1/S2/S3 一致） |
| 赛道 | 起点 0、有效格 1–29、到达 30 冲线；第二拐角 15 | 高（S3 实物核对） |
| 奖牌 | 金 3/4/4/5、银 1/2/2/3；铜币 1 或 3 分 | 高（S3 目视组件照片 + S2 递增口径） |
| 选手数 | 36 名，每名全局仅用一场 | 高（S3/S4 交叉验证） |
| 轮抽 | 展 2n 张、蛇形两批、每人 4 张 | 高（S1/S2/S3 一致） |
| 单场结束 | 第二名冲线立即结束，只有前二得分 | 高 |
| 平局 | 同分并列获胜，无加赛 | 高（S3） |
| 能力全集 | 36 条，含赛前/替代/修正/触发/传送/复制六类 | 高（S3 逐条核对，S1 原文互证） |

## 来源清单

| # | 来源 | 提供的关键信息 |
|---|---|---|
| S1 | RulesPal — Magical Athlete Rulebook | 完整流程、术语、Wild 特殊格、36 人能力英文原文、2/3 人变体 |
| S2 | Gamerules — Magical Athlete | 奖牌逐场递增、特殊格效果、变体细节、重点选手 |
| S3 | GitHub `pedestrian6/magical-athlete` — 规则核对记录 | **主要依据**：规则集 `cmyk-base-2026-08-28`，基于 IELLO 官方 28 页 PDF + 2026-08 FAQ 逐条目视核对；给出赛道格号、奖牌数值、36 人中文能力、边界裁定 |
| S4 | Sporcle — Magical Athlete Characters | 36 名选手名单独立交叉验证 |
| S5 | 桌游库 — 胡闹运动会 | 中文译名、人数时长、轮抽与四场流程 |
| S6 | 鱼人桌游公众号 — 胡闹运动会 | 9 名重点选手中文能力与体验描述 |
| S7 | RulesPal — Magical Athlete (2003) Rulebook | **仅作旧版对照**：2003 版为五场、金银 4/2、4/2、5/3，含 Assassin/Spy 等旧角色，不得与 CMYK 版混用 |

## 仲裁记录

### A1 规则版本：以 2026-08 修订为准

S3 明确指出官方 PDF 存在**错位或过时的隐藏英文层**：飞艇隐藏英文写 +2、可见法文写 +3；野兔隐藏英文含领先铜分、可见卡面没有。同时旧 PDF 关于浪漫家同时到达、天才/船长顺序、双角色任意行动顺序的说明被新版 FAQ 覆盖。

**定案**：采用 S3 的 `cmyk-base-2026-08-28`，禁止从 PDF 全文抽取直接生成实现数据。S7（2003 版）只用于理解机制沿革，不作为本项目的数值来源。

### A2 赛道与奖牌数值

S3 以无遮挡实物照片核对：起点 0、有效格 1–29、到达 30 即冲线；第一拐角 12、第二拐角 15。狂野面特殊格逐格给出（1/13 得铜、5/17/26 绊倒、7 +3、11 +1、16 −4、23 +2、24 −2）。
S2 只说"金牌 3 in race one, rising in later races"，未给逐场数值；S3 给出精确序列。

**定案**：金银 `[3,1] / [4,2] / [4,2] / [5,3]`，铜币按印刷面值。与 S2 的"逐场递增"方向一致，无冲突。

### A3 36 名选手名单

S3 给出 36 条带 ID 的中文能力表；S4 的测验统计页独立列出完全相同的 36 个名字；S1 的 Racer Info 段落逐条给出英文能力文案（前 24 条已直接核对，后 12 条由 S3/S4 补足）。S6 的 9 名重点选手（大长腿、飞艇、火箭科学家、香蕉、大嘴巴、催眠师、蹭蹭怪、可爱的倒霉蛋、西西弗斯）与 S3 译名可一一对应。

**定案**：36 名全量入库，`key` 采用 S3 的 snake_case ID，中文名采用 S3 译名；平台显示名用「胡闹运动会」，保留英文 Magical Athlete 便于对照卡面。

### A4 人数变体

S1/S2/S3 对 2 人局与 3 人双角色局的抽牌数与选序一致；S3 额外明确"新版双角色在赛前固定两名角色顺序，不每回合重排"，采用该口径。

**定案**：普通 3–6 人 4 选 1；2 人局 8 选 2；3 人双角色 8 选 2（可选变体）。首版是否实现两个变体列为准备计划 D9。

### A5 绊倒与移动的语义

S1/S3 一致：绊倒跳过的是**下一次主要移动**，不是整个回合；被绊倒者仍可触发能力、仍可被其他能力移动。S3 补充：重掷点数不触发尺蠖/跟班/船长；传送不算移动；"移动 0"不算移动；真正循环需位置、状态、待结算效果全部重复。

**定案**：全部写入 `rules.md` 术语段与 `game.yaml` 的 `racerAbilities`，作为引擎事件系统的硬约束。

### A6 中文译名

S5/S6 使用「胡闹运动会」；S3 使用「魔法运动员」并保留英文 ID。两者指同一款游戏（CMYK 2025，Richard Garfield / Takashi Ishida）。

**定案**：UI 与目录显示名「胡闹运动会」，数据集 `name` 字段写「胡闹运动会（Magical Athlete）」，选手译名沿用 S3，便于与 FAQ 对照。

## Dev 阶段记录（2026-11-22，M1 房间与轮抽）

### A7 M1 实现口径（按准备计划推荐口径落地）
- **D1 命名**：前端 key `magicalAthlete` / DB `magical_athlete` / REST·WS·路由 `magical-athlete` / 模块 `dicego-magicalathlete` / 包 `org.dromara.magicalathlete`。
- **D2 数据**：36 名选手做成 `RacerCatalog` 代码常量（构造器断言 36 条、key 唯一），不建表；前端 `constants/racers.ts` 镜像同一份 key/中文名/能力分类，M6 换头像不改 key。
- **D6 超时**：轮抽 30s 未选由服务端代选牌池最左一张；500ms tick 自建调度器（同 halli，不依赖全局 @EnableScheduling）。代选广播 `DRAFT_PICK{auto:true}`。
- **D7 串行**：`MagicalAthleteRoom.pickRacer` synchronized，牌池与手牌全由服务端 `DraftSession` 维护，客户端只提交 racerKey。
- **D10 牌池过滤**：M1 是轮抽阶段、尚无能力 handler，36 名全部可抽；M3 接入能力注册表后按 `enabledRacers` 收窄，避免选到无 handler 选手。
- **D3/D5 预留**：`MagicalAthleteMessageType` 已定义 ROLL/READY_RACER/USE_OPTIONAL（收到回 `NOT_IMPLEMENTED_YET`）；`phase` 字段与 MUSTER/RACE 等枚举已就位。M5 实现赛前暗选时必须改 `broadcastPersonalized` 隐藏 `activeRacerKey`——协议 §3 已写死这条红线。
- **M1 止点**：两批轮抽走完 → phase 置 MUSTER、倒计时归零，等 M2 实现比赛。终局/计分/落库（D11/D12）均未开始。

### A8 本轮验证
- 后端：`mvn -o -pl dicego-magicalathlete -am test` 新增 23 用例全绿（RacerCatalog 4 / DraftOrder 5 / DraftSession 6 / Room 6 / WS 2）；五模块（common+四游戏）整体 BUILD SUCCESS，旧三款无回归。
- 前端：`vue-tsc` 通过、34 文件 249 用例全绿、`vite build` 成功；新增第四款游戏后同步更新了 catalog/gameAdapters/lobby 三处测试期望。

### A9 M2 实现口径（单场竞速，2026-11-22）
- **范围**：MUSTER 暗选 → RACE 服务端掷骰 → 主移动 → 停格 → 冲线 → 奖牌 → 四场推进 → 终局排名。能力系统（D3）与循环保护（D8）仍留 M3。
- **新引擎**：`RaceTrack`（0–30、狂野面 10 特殊格）、`RaceSession`（纯逻辑、骰点外部注入、绊倒只跳过下一次主移动、第二名冲线即结束）、`ScoreBoard`（金 3/4/4/5、银 1/2/2/3、铜币面值、同分并列）。
- **暗选隐藏（D5）**：MUST `STATE_SYNC` 改 `broadcastPersonalized`；MUSTER 只下发本人手牌 + `myActiveRacerKey`，`activeRacerKeys` 恒空；`MUSTER_READY` 只报座位/姓名，直到 `RACE_START` 才公开选手 key。WS 测试逐帧验证。
- **超时（D6）**：DRAFT 30s / MUSTER 20s / RACE 20s，全部 `auto=true` 广播。M2 无可选能力，天然满足"托管不替玩家做可选选择"。
- **终点语义**：到 30 即冲线，超过按 30 处理；"较真怪：必须恰好冲线"是 M3 能力，不在基础移动里实现。
- **验证**：后端新增 RaceSessionTest 7 / ScoreBoardTest 3 / Room +5 / WS +1，模块 38 用例；五模块整体 BUILD SUCCESS。前端新增暗选页/赛道/掷骰/排名，`vue-tsc`、249 用例、`vite build` 全绿。

### A10 M5 实现口径与验证（2026-11-22）
- **M5a 能力委托**：`AbilityContext.Seat` 加 `effectiveKey`/`copyFromSeat`，管线按复制后的 key 取 handler，`handlerSeat` 仍是实际座位（沿用 M3 归属者口径）。模仿猫每次 roll 动态复制当前领先者（不复制赛前能力、排除 copy 防递归、自己全场第一时不委托）；蛋蛋/双胞胎由房间在开赛前注入固定委托。`RaceSession.applyPreRace()` 补 `BEFORE_RACE`，修复赛前能力与其被复制后从未结算的缺口。`registerDelegated()` / `registerEngineManaged()` 让无 handler 的 5 名选手也能进牌池，`coveredRacerCount()` 仍只数真实 handler，28 名断言不回归。
- **M5b 严格语义**：mastermind 首回合锁定预测冠军 → 命中立即夺银并结束本场 → 自猜命中走 `awardRaceSelfGoldSilver()` 金银同人；leaptoad 主移动只计空格，占据格跳过不耗步数且每跳过一个补发 `ON_ABILITY_FIRED`。
- **范围校正**：suckerfish 严格同步组语义按 M5 计划正式口径明确**不在本轮范围**，保留 M4 的"传送 + 取消移动"简化实现，不记为缺陷；需要同步组时另开里程碑。
- **M5c 3 人双角色变体**：`RaceVariant` + 四批 `ABCCBA/BCAACB/CABBAC/ABCCBA`（每人 8 张、每场 2 名）。**核心决策是不改 `RaceSession`**——引擎 `Map<Integer,Racer>` 禁止同座位两名选手，故房间把 3 个真实座位展开为 6 个 lane，引擎与全部 handler 看到的 `seatNo` 即 lane；普通局 `lane==seat` 零回归。对外 `currentSeatNo`/奖牌/分数/排名全部归并回真实座位，`RacerStateVO.ownerSeatNo` 承载映射，前端"轮到我"判断无需改动。
- **协议细节**：`START_GAME.variant` 仅 3 人局生效；`READY_RACER` 支持 `racerKeys[]` 一次提交 2 名（顺序即固定出场顺序）；`USE_OPTIONAL.targetSeat` 在双角色局是 lane（1..6）而非真实座位，前端改用"指定目标选手"下拉。MUSTER 仍不泄露他人 key，只新增 `musterProgress: seat→已选数`。
- **验证**：后端 `Tests run: 120, Failures: 0, Errors: 0, Skipped: 0`，`dicego-games` 五模块整体 `BUILD SUCCESS`；前端 `vue-tsc --noEmit` 通过、Vitest 35 文件 259 用例全绿（新增 5 个 M5 组件用例）、`vite build` 成功。协议追加 §11 并修正 §2.1 过时定义；准备计划标 M5 完成。改动未提交 Git。

## 待拍板（不阻塞数据集完成）

1. **D9 变体范围**（已定，2026-11-22）：普通 3–6 人 + 2 人局（M1）+ 3 人双角色局（M5）均已实现；双角色局用 lane 展开，引擎零改动。
2. **D10 能力首版范围**：36 名全量还是先做 12 名代表？建议数据全量入库，引擎能力处理器按事件类型分组实现，先覆盖六类触发器共 12 名，其余用同一注册表补数据。
3. **D11 托管策略**：回合超时如何自动行动？掷骰无选择权可自动掷；但"可以"类能力（如火箭科学家翻倍、蛋蛋复制）替玩家做选择会改变强度，建议默认放弃可选能力、只执行强制部分。
4. **D12 平台规则版本开关**：若未来接入 Second Wind（新赛道与新选手），需要在 `game.yaml` 的 `board.ruleset` 上做多版本，提前留好字段。
