# 胡闹运动会（Magical Athlete）接入准备计划

日期：2026-11-21 · 状态：**Dev 进行中（M1–M5 已完成 2026-11-22）** · 模块：`dicego-games/dicego-magicalathlete`

> 进度：P1 ✅ / P2 ✅ / P3 ✅ / M1 ✅ / M2 ✅ / M3 ✅ / M4 ✅ / **M5 ✅**（委托能力 + 两条严格语义 + 3 人双角色变体；后端 120 用例、前端 259 用例全绿）。
> D1–D12 已采纳落地；D9 的 2 人局（M1）与 3 人双角色局（M5）均已实现；D3 注册表覆盖 28 名 handler，另有 3 名委托 + 2 名引擎直管进入牌池。**suckerfish 严格同步组语义按 M5 范围说明保留 M4 简化实现**，留后续里程碑。

## Goal

为「胡闹运动会」多人对战完成开工前的全部准备：规则数据集与校验、命名与模块边界、可变玩家能力的事件架构、轮抽/暗选/快照可见性、托管与连锁保护、协议与数据库登记、前端接入点与素材方案。完成标准是——后端第一个 Task 开工时不需要再查规则、定协议、想能力系统模型。

## 现状盘点（2026-11-21 核实）

| 资产 | 状态 | 可直接复用度 |
|---|---|---|
| `dicego-game-common` | `GameRoom` / `GameRoomManager` / `AbstractGameWebSocketHandler` / `GameMessageType` / `MessageTypeParser` / `GameRoom.hostName` | **高**：房间生命周期、WS、心跳、AFK、解散、关房广播 |
| `dicego-game-common` 对局落库 | `GameMatchEntity` / `GameMatchPlayerEntity` / `GameActionLogEntity` + 三个 mapper + `GameMatchHistoryService` | **高**：三款游戏共用，直接写 |
| `dicego-splendor` | config/controller/domain/engine/handler/manager/service 九层结构 | **中**：分层照抄，但它是"当前行动方单步操作"，不是事件连锁 |
| `dicego-halligalli` | 自建消息类型、房间串行化、超时调度器、音效/环形布局 | **中**：串行化与超时可借鉴；实时并发模型不适用 |
| 前端 composables | `useGameWebSocket` / `useRoomEntry` / `useGameSfx` / `useLastRoom` / `useChat` / `useAfk` | **高**：零改动接入 |
| 前端房间 UI | `src/styles/_room.scss` 的 `room-bar` / `room-waiting` / `round-info` / `afk-tag` mixin | **高**：两款游戏已共用，直接 `@use` |
| 大厅分流 | `src/constants/gameAdapters.ts` 适配器注册表（已重构完） | **高**：加一条适配器即可，不用再改 if/else |
| 数据库 | `game_definition` + `game_match` 三表 | **高**：登记一行，落库复用 |
| 规则数据集 | `design/games/magicalAthlete/{game.yaml,rules.md,journal.md}` + `tests/magical-athlete-official.spec.ts` | **已完成**（10 用例，见 P1） |

### 缺口（公共层没有、必须游戏内实现）

1. **可变玩家能力系统**：36 种能力、7 种触发器类型、同一时点四级优先级、连锁与循环检测。公共层完全没有。
2. **多阶段房间状态机**：轮抽 → 赛前暗选 → 比赛 → 单场结算 → 下一场，共四轮；每阶段有不同可见性与超时。
3. **36 名选手素材**：能力图标/头像。是本项目目前最大的内容资产工作量。

## 规则事实（已定稿，来源见 journal.md）

- 2–6 人，4 场；默认赛道顺序 **平缓 / 狂野 / 平缓 / 狂野**。
- 赛道：起点 0，有效格 1–29，到 30 冲线；第二拐角在 15。
- 奖牌逐场升值：金 **3/4/4/5**、银 **1/2/2/3**；铜币 1 或 3 分；同分并列获胜。
- 开局蛇形轮抽：展示人数 ×2 张，两批各选 2 张，每人 4 名选手。
- 每场同时暗选 1 名出场，用过不再用；上场落后最远者先手。
- 回合 = 掷一颗骰 + 主要移动 + 能力结算；结算顺序 **赛道格 → 当前选手 → 其余顺时针**。
- 绊倒只跳过下一次**主要移动**，不禁用能力；传送不算移动但会触发停格；移动 0 不算移动。
- 第二名冲线立即结束本场，只有前二得分；四场后总分最高胜。
- 36 名选手能力全集与中文译名已逐条核对（`cmyk-base-2026-08-28`）。

## 与已实现三款游戏的关键差异

| 维度 | 象棋 | 璀璨宝石 | 德国心脏病 | 胡闹运动会 |
|---|---|---|---|---|
| 行动模型 | 红黑交替 | 座位轮转 | 实时并发抢铃 | 回合制 + **多阶段状态机** |
| 服务端关键校验 | 走法合法 | 资源/阶段 | 到达顺序 | **能力触发链 + 优先级 + 循环检测** |
| 隐藏信息 | 无 | 预留卡 | 牌堆内容 | **赛前暗选选手**（揭示前需个性化隐藏） |
| 快照策略 | 全量 | 个性化 | 全量 | **分阶段**：轮抽/比赛全量，暗选阶段只发"已准备" |
| 状态规模 | 棋盘 90 点 | 90 卡 + 资源 | 56 张 + 4 计数 | 36 选手 + 36 能力 + 4 场 × n 选手状态 |
| 随机源 | 无 | 卡牌洗牌 | 牌堆洗牌 | **服务端掷骰**（必须防篡改） |
| 超时需求 | 无 | 无 | 10s 自动翻 | **轮抽/暗选/回合三处都要** |

## 设计决策（待拍板，括号内为推荐）

- [ ] **D1 命名约定**（按既有惯例）：前端 key `magicalAthlete`；DB `game_code='magical_athlete'`、设计目录 `magicalAthlete`；REST/WS/路由 `magical-athlete`；Java 模块 `dicego-magicalathlete`、包 `org.dromara.magicalathlete`。
- [ ] **D2 选手与能力数据放哪**（推荐**代码常量 + 能力注册表，不建表**）：36 名是规则常量而非运营内容；`game.yaml` 已作为单一事实来源，启动时断言 36 条、key 唯一。若未来要皮肤/扩展再建 `magical_athlete_racer`。
- [ ] **D3 能力系统架构（本作唯一的架构级决策，推荐事件驱动 + 效果队列）**：
  - 事件类型：`BEFORE_RACE` / `BEFORE_MAIN_MOVE` / `ROLL_REPLACE` / `DICE_RESULT` / `ON_MOVE` / `ON_PASS` / `ON_STOP` / `ON_TRIP` / `AFTER_TURN` / `ON_ELIMINATE`。
  - 每个能力实现为 `AbilityHandler`，按 `racerKey + eventType` 注册；handler 只产生「效果指令」（移动/绊倒/传送/得分/改骰/淘汰/插入行动），由房间统一按 ## 优先级顺序执行：赛道格 → 当前选手 → 其余顺时针。
  - **效果指令必须可重放并记录到 action_log**，否则连锁争议无法复盘。
  - 不把能力写成 `if (racer == X)` 的大 switch；36 条会立刻失控。
- [ ] **D4 骰子由服务端生成**（推荐）：客户端只发 `ROLL`，服务端掷骰并把点数随状态广播；客户端不传点数，杜绝假骰。重掷同样由服务端执行，弃用点数不触发尺蠖/跟班/船长。
- [ ] **D5 分阶段快照可见性**（推荐）：`DRAFT` 阶段广播公开牌池和每人已选；`MUSTER` 阶段只广播"某人已准备/未准备"，**不下发出场选手 key**；公开时才广播完整状态。沿用 Splendor 的 `broadcastPersonalized` 机制即可。
- [ ] **D6 超时与托管**（推荐）：轮抽 30s、暗选 20s、回合 20s；超时自动选**牌池最左/手牌第一张**，回合自动掷骰且**放弃所有"可以"类可选能力**，只执行强制部分（替玩家做可选选择会改变角色强度，不做）。
- [ ] **D7 轮抽阶段串行化**（推荐）：房间内 `synchronized pickRacer()`；牌池剩牌与每人手牌都由服务端维护；两批蛇形顺序在开局时算好存进房间。
- [ ] **D8 连锁与循环保护**（推荐）：真实循环需「棋子位置 + 状态 + 待结算效果」全部重复才截断一次；另设服务端硬上限 200 条效果指令作为最后防线，超限记 `CHAIN_LIMIT` 并停在该点。**禁止用"每能力每回合一次"粗暴截断**（会破坏蹭蹭怪/吸盘鱼的正确行为）。
- [ ] **D9 变体范围**（推荐）：首版做普通 3–6 人 + 2 人局；3 人双角色局放 M4 之后。D2 已记录三种模式的抽牌序列，实现时直接读 `variants`。
- [ ] **D10 能力首版范围**（推荐）：**数据全量 36 名入库，牌池只开放已实现能力的选手**（`enabledRacers` 配置）。避免玩家选到无 handler 的选手后出现"能力静默失效"；每实现一类 handler 就放开一批。
- [ ] **D11 落库**（推荐）：复用 `game_match` / `game_match_player` / `action_log`；日志事件 `DRAFT_PICK` / `RACE_START` / `ROLL` / `MOVE` / `ABILITY` / `TRIP` / `RACE_END` / `SCORE`。能力日志必须带 `racerKey + eventType + effects`，供复盘。
- [ ] **D12 平局与排名**（推荐）：总分并列共享胜利，排名接口返回并列第一，不做加赛；个人中心战绩显示"并列第 1"。

## 准备任务分解

### P1 规则数据集与校验 ✅ 2026-11-21 完成

- [x] `design/games/magicalAthlete/game.yaml`：36 名选手、赛道格表、奖牌表、能力摘要、三种模式、结算语义
- [x] `rules.md`：玩家可读规则（含 Wild 特殊格、术语、36 人能力表、变体、常见误区）
- [x] `journal.md`：7 个来源、6 条仲裁、4 个待拍板
- [x] `tools/boardgame-design/tests/magical-athlete-official.spec.ts`：10 用例（schema、人数、36 卡、格号、奖牌序列、能力一一对应、变体）

### P2 数据库登记 ✅ 2026-11-22

- [x] `game_definition` 追加 `game_code='magical_athlete'`、2–6 人、`sort_no=4`（`boardgame_online.sql` 种子已并入）
- [x] 新建增量脚本 `script/sql/2026-11-22-magical-athlete-definition.sql`（带 `ON DUPLICATE KEY UPDATE`）
- [x] 不建选手表（D2）；落库复用三表（D11，M6 接入）

### P3 协议定稿 ✅ 2026-11-22

- 产出 `docs/superpowers/specs/2026-11-22-magical-athlete-protocol.md`（§1–§7）
- [x] REST：建房/入座/查询房间/房间列表（与前三款同构）
- [x] WS 上行：`START_GAME` / `PICK_RACER` / `READY_RACER` / `ROLL` / `USE_OPTIONAL`
- [x] WS 下行：`GAME_START` / `DRAFT_STATE` / `RACE_START` / `STATE_SYNC` / `DICE_RESULT` / `ABILITY_CHAIN` / `RACE_RESULT` / `GAME_OVER` / `ACTION_REJECT`
- [x] 明确 `MUSTER` 阶段快照不下发选手 key（D5，协议 §3 可见性表）

### M1 房间与轮抽 ✅ 2026-11-22

- 后端模块 `dicego-magicalathlete` 注册进 `dicego-games/pom.xml` 与 `ruoyi-admin/pom.xml`；包 `org.dromara.magicalathlete` 由启动类自动扫描。
- `RacerCatalog`（36 常量）、`DraftOrder`（普通蛇形 + 2 人局 ABBAABBA/BAABBAAB）、`DraftSession`（牌池/手牌/序位）、`MagicalAthleteRoom`（座位/开局/选人/30s 代选/快照）、WS Handler + REST Controller + 超时任务。
- 前端：`api/magicalAthlete.ts`、`constants/racers.ts`、对局页（等待座位 + 牌池 + 手牌 + 选序指示 + 倒计时 + 播报），接入 catalog/rules/router/gameAdapters/useLastRoom/BackToRoom（自动覆盖）。
- 验证：后端 23 用例 + 五模块整体 BUILD SUCCESS；前端 249 用例全绿 + build 成功。

### M2 单场竞速 ✅ 2026-11-22

- 引擎：`RaceTrack`（赛道 0–30 + 狂野面 10 特殊格）、`RaceSession`（骰点注入、主移动、绊倒跳过下一次主移动、箭头链式、第二名冲线即结束）、`ScoreBoard`（四场奖牌面值 + 铜币 + 并列排名）。
- 房间：`MagicalAthleteRoom` 扩展 MUSTER（暗选 D5，个性化快照隐藏他人 key）、RACE（服务端掷骰 D4）、RACE_END/FINISHED（奖牌记分与并列）、四场推进与三处超时托管（D6）。
- 协议：新增 `MUSTER_READY`，`RACE_START` 改带 `activeRacerKeys`，`DICE_RESULT`/`RACE_RESULT`/`GAME_OVER` 定稿；WS 逐帧测试验证暗选不泄露。
- 前端：暗选页（只见自己手牌 + 准备状态）、横向赛道 + 棋子 + 掷骰、骰点/结算播报、终局排名。
- 验证：后端 38 用例、五模块整体 BUILD SUCCESS；前端 vue-tsc + 249 用例 + build 全绿。

### M3 能力系统 ✅ 2026-11-22

- 新建 `engine/ability/` 包：`AbilityEvent`（10 事件）、`Effect`（可重放效果指令）、`EffectType`（12 类型）、`AbilityContext`（座位状态视图 + 顺时针/领先/垫底查询 + 循环指纹）、`AbilityHandler`（函数式）、`AbilityRegistry`（racerKey+event 注册）、`EffectPipeline`（优先级排序 + D8 循环/200 上限）、`StandardAbilities`（首批 12 名）。
- `RaceSession` 加带 `AbilityRegistry` 的构造子（老构造子零回归）：roll 内按 `ROLL_REPLACE → DICE_RESULT → BEFORE_MAIN_MOVE → 主移动 → ON_PASS → ON_STOP` 发事件，连锁 `ON_ABILITY_FIRED`；`RollOutcome` 增 `abilityChain/chainTruncated`。
- `ScoreBoard.awardAbilities()` 记能力分并入总分与并列排名；Room startRace 启用能力构造子、settleRace 记分。
- **关键坑**：① handler 用 `handlerSeat`（能力归属者）判自身/他人，不能用 `currentActor`（掷骰者）——否则场上同名能力被误触发；② 主移动后必须 `syncToContext()` 再结算停格事件，否则旧快照覆盖新位置（棋子原地不动，对局无法结束）；③ `Cycle` 需「状态+队列」全重复，200 条硬上限兜底持续增长链。
- 验证：后端 64 用例全绿（原 38 + RaceSessionAbility 4 + AbilityPipeline 7 + StandardAbilities 11 + ScoreBoard+1）；五模块整体构建通过。

### M4 可选能力交互层与能力扩展 ✅ 2026-11-22

- 交互层：`TurnIntent`（意图前置）、`USE_OPTIONAL` 从空占位做成可用（服务端权威反查座位 + 目标合法性校验）、快照 `myOptionalOptions[]`（私有）、超时放弃（D6）。
- 引擎接线：`RaceSession.roll(seat,dice,intent)` 新重载（老签名零回归）、`AFTER_TURN` 触发、消费 `stolenNextActor`/`extraTurnGranted`、`EffectType.SWAP_POSITION`、重掷在房间层完成。
- 能力：新增 `ExtendedAbilities`（纯 handler 12）+ `ChoiceAbilities`（warp 4）+ 房间层重掷（magician/dicemonger）；`FullAbilities` 合并为 28 名注册表，`StandardAbilities` 保持 12 名零回归。
- 前端：快照类型加 `myOptionalOptions`，RACE 页加可选能力面板（启用/目标座位/目标格/猜骰输入 + 使用按钮），249 用例全绿 + build 成功。
- **关键决策**：意图前置而非拆结算链（M3 零回归）；copy 类需"能力委托"架构（管线按复制 key 取 handler），牵动 Seat 构造与赛前阶段，留 M5；suckerfish 严格同步语义先简化为传送+取消移动。
- 验证：后端 91 用例全绿（原 64 + warp 6 + 扩展 13 + 引擎接线 6 + 交互层 5 + 协议覆盖），前端 249 用例 + build 全绿。

### M5 委托能力、严格语义与 3 人双角色变体 ✅ 2026-11-22

- **M5a 委托架构**：`AbilityContext.Seat` 加 `effectiveKey` / `copyFromSeat`，`EffectPipeline.collect()` 按复制后的 key 取 handler，但 `handlerSeat` 仍是实际座位；`CopyDelegation` 集中模仿猫动态委托（每次 roll 刷新到当前领先者，排除 copy 防递归/自己全场第一不委托）与蛋蛋/双胞胎固定委托。`AbilityRegistry.registerDelegated()` / `registerEngineManaged()` 让无 handler 能力也能进牌池而不破坏 28 名断言；`RaceSession.applyPreRace()` 补上 `BEFORE_RACE`，修复西西弗斯 +4 与复制赛前能力从未结算的缺口。
- **M5b 两条严格语义**：mastermind 预测冠军→命中夺银并结束比赛→自猜金银同人（`ScoreBoard.awardRaceSelfGoldSilver()`）；leaptoad 主移动只计空格、每跳过一个占据格补发 `ON_ABILITY_FIRED`。**suckerfish 严格同步组语义按计划口径明确不在 M5 范围**，保留 M4 简化实现（见协议 §11.2）。
- **M5c 3 人双角色变体（lane 架构）**：`RaceVariant` 枚举 + `DraftOrder.threeDoubleSnake()` 四批选序 + `DraftSession` 参数化；`START_GAME{variant:"THREE_DOUBLE"}` 仅 3 人局生效。
  - **关键决策：不改 `RaceSession`**。引擎用 `Map<Integer,Racer>` 明确禁止同一座位两名选手，因此房间把 3 个真实座位展开为 6 个内部参赛号 lane（`lane=(seat-1)*2+slot+1`），引擎/28 个 handler/上下文/管线全部只认 `seatNo`，此时它就是 lane；普通局 `lane==seat` 零回归。
  - 房间负责 lane↔seat 映射、记分归并、对外协议仍保持真实座位语义（`currentSeatNo` / `goldSeat` / `scores` / `rankings`），`RacerStateVO.ownerSeatNo` 承载映射。前端"轮到我"判断与名字播报因此不需要改。
  - 暗选每人按提交顺序锁定 2 名（固定出场顺序），MUSTER 快照只新增 `musterProgress: seat→已选数`，不泄露他人 key。
  - **协议坑**：`USE_OPTIONAL.targetSeat` 由房间按 `race.racers()` 校验，双角色局是 lane（1..6）而非真实座位；前端改为"指定目标选手"下拉，普通局保持数字输入。
- 前端：等待页 3 人变体开关、动态 2/4 批标题、MUSTER 双选与固定出场顺序展示、RACE 按 `ownerSeatNo` 高亮、当前出场选手提示；新增 5 个 M5 组件用例。
- 验证：后端 `Tests run: 120, Failures: 0, Errors: 0, Skipped: 0` + `dicego-games` 五模块 `BUILD SUCCESS`（父 + 5 模块全 SUCCESS）；前端 `vue-tsc --noEmit` 通过、Vitest 35 文件 259 用例全绿、`vite build` 成功。
- 文档：协议追加 §11（并修正 §2.1 已过时的上行消息定义），journal 追加 A10；改动未提交 Git。

### P4 引擎签名清单（纯逻辑，无 IO）

- `MagicalAthleteDeck.build()` → 36 张选手卡（断言 key 唯一）
- `DraftOrder.snake(playerCount, batch)` → `int[]` 选序
- `RaceTrack.resolveStop(space, side)` → `List<Effect>`（狂野面特殊格）
- `AbilityRegistry.of(racerKey, eventType)` → `AbilityHandler`
- `AbilityHandler.apply(ctx)` → `List<Effect>`（只产出效果指令，不改房间状态）
- `EffectPipeline.run(effects)` → `ChainResult`（按赛道格→当前→顺时针执行 + 循环检测）
- `Movement.compute(base, modifiers)` → `int`（先替换/翻倍，再累加修正；clamp 到 ≥0）
- `checkFinish(racers)` → `FinishResult`（第一名/第二名/无人能完赛）
- `scoreRace(trophy, raceNo, bronzeChips)` → `ScoreDelta`
- 必测：超越触发、绊倒不中止当前移动、传送不触发超越、移动 0 不算移动、重掷弃点不触发、蹭蹭怪连锁、循环截断一次、恰好冲线与超额冲线、同时到达修订

### P5 前端清单与素材方案

- `catalog.ts` 登记 `magicalAthlete`：`zone:'party'`、`playable:true`、机制标签 `['轮抽','骰子','角色能力']`
- `rules.ts` 增加规则书；路由 `/magical-athlete/:roomId`；`gameAdapters.ts` 加一条适配器；`BackToRoom` 自动覆盖
- 对局页三段式：轮抽牌池 + 手牌 / 赛道横向格子 + 选手棋子 / 能力链日志；复用 `_room.scss` 顶栏与等待座位
- **素材方案（建议分两步）**：
  - M1–M5 先用**编号色块 + 中文名 + emoji 图标**占位，保证玩法可测；
  - 上线前用 `game-art-prompt-pack` 写 36 人提示词包：统一风格锚点、纯色底、无阴影、单方向、位置锚定；`ImageGen` 批量出图；`buddy-image-processing` 去底、裁方、转 WebP。先锁 1 张参数再批量，避免 36 张风格漂移。

## 里程碑

| 里程碑 | 内容 |
|---|---|
| M1 房间与轮抽 | 建房/2–6 座位、蛇形两批轮抽、手牌公开、超时代选 |
| M2 单场竞速 | 服务端掷骰、主要移动、冲线判定、奖牌结算 |
| M3 能力系统 ✅ | 事件注册表 + 效果队列 + 优先级 + 连锁日志（先 12 名代表选手）|
| M4 能力扩展 ✅ | 可选能力交互层（USE_OPTIONAL 意图）+ 16 名能力 + 引擎接线，28 名覆盖 |
| M5 委托与变体 ✅ | copy 3 名（能力委托）+ mastermind/leaptoad 严格语义 + 3 人双角色局变体（lane 架构）；suckerfish 严格同步组留后续 |
| M6 素材与落库（待做） | 36 人头像（提示词包 + ImageGen + 去底）、三表对局落库（D11）、个人中心战绩、能力链日志 UI；对战页/轮抽页/适配器已在 M1–M5 完成 |

## 风险清单

1. **能力连锁复杂度（最高）**：36 能力互相触发可能出现指数级链。D3 的效果队列 + D8 的循环检测必须在 M3 就做对，不能后期补。
2. **隐藏信息泄露**：`MUSTER` 阶段任何全量广播都会暴露对手出场选手，直接破坏核心决策。D5 要有专门的协议用例。
3. **可选能力的托管**：自动替玩家选"可以"类能力会改变角色强度；D6 的默认放弃是保守但正确的口径。
4. **规则版本陷阱**：官方 PDF 隐藏英文层错位；实现只能读 `game.yaml` / `rules.md`，禁止二次从 PDF 全文抽取。
5. **素材工作量**：36 人头像远超水果 4 张。先用占位图跑通玩法，避免素材阻塞开发。
6. **能力覆盖不全**：D10 的牌池过滤必须实现，否则玩家选到无 handler 选手会出现静默失效。

## 技能与工具检查（本轮结论）

| 能力 | 用途 | 结论 |
|---|---|---|
| `game-art-prompt-pack`（技能） | 36 名选手批量出图的提示词包：风格锚点逐字复用、纯色底、单方向 | **用**：M6 素材阶段；先 1 张锁参数 |
| `ImageGen`（工具） | 实际出图 | **用**：M6；36 张建议分 4 批，每批 9 张 |
| `buddy-image-processing`（技能） | 去底、bbox 裁方、转 WebP | **用**：M6 后处理 |
| `design-master`（技能） | 赛道横向布局、能力日志可读性诊断 | 实现阶段（M6）用 |
| `dev-expert-pro`（技能） | 能力系统架构审查、连锁调试 | **用**：M3 开工前复核 D3/D8 |
| `workbuddy-docx` / `pptx` / `xmind` / `sheetagent` | 文档/演示/脑图/表格 | **不用**：项目文档走 markdown，数据由 yaml 承载 |
| 项目自带 `tools/boardgame-design` | yaml schema 校验 + 工作台 | **用**：P1 已完成 10 用例 |

> 未发现可直接复用的"桌游能力引擎"技能；`design/games` 工作流 + `tools/boardgame-design` 校验仍是主路径。

## M-prep 验收标准

1. 12 个决策点 D1–D12 全部拍板（当前 0/12，括号推荐可直接采纳）
2. 数据集 10 用例全绿，36 选手/赛道/奖牌/能力一一对应可断言
3. 协议文档含全部消息、阶段可见性、失败码与能力链字段
4. 引擎签名清单可直接转成测试用例
5. 前端接入点（catalog/rules/route/adapters/BackToRoom）全部列明
6. 素材方案明确"先占位后批量"，不阻塞 M1–M5

## Review Focus

- **D3 决定这个游戏能不能做**：能力系统用事件注册表，别用 switch；效果指令要能落日志复盘。
- **D8 的循环定义要严格**：位置+状态+待结算效果全重复才算循环；蹭蹭怪/吸盘鱼的长期联动不是循环。
- **D5 的隐藏窗口只有几秒**，但一旦泄露，四场出场的策略层就废了——协议层单独测。
- **D10 与 D9 联动**：先缩小牌池而不是先砍能力实现，玩家永远不会选到"没能力的选手"。
