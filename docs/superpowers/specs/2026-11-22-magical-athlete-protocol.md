# 胡闹运动会（Magical Athlete）对战协议

日期：2026-11-22 · 规则集：`cmyk-base-2026-08-28` · 决策口径见 `docs/superpowers/plans/2026-11-21-magical-athlete-prep.md` D1–D12

命名：前端 key `magicalAthlete` · DB `magical_athlete` · REST/WS/路由 `magical-athlete` · Java 模块 `dicego-magicalathlete` / 包 `org.dromara.magicalathlete`。

## 1. REST（房间生命周期，与前三款同构）

前缀 `/api/magical-athlete`，路由走白名单；建房/入座按 `Authorization: Bearer` 还原 user_id（异常按游客 0，落库负座位哨兵）。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/rooms` | body `{playerName, maxPlayers?}`，容量 2–6（缺省 4），建房不自动入座 |
| POST | `/rooms/{roomId}/seat` | body `{playerName, seatNo?}`，缺省补最小空位 |
| POST | `/rooms/{roomId}/stand` | body `{playerName}`，仅 WAITING 可起身 |
| GET | `/rooms/{roomId}` | query `playerName?`，返回 `toVO(playerName)`（含 `mySeatNo`） |
| GET | `/rooms` | 房间摘要 + waiting/playing 计数 |

响应体统一 `{success, room?, error?}`；房间 VO 见 §4。

## 2. WebSocket

端点 `/ws/magical-athlete?roomId={id}&playerName={name}`。信封沿用公共 `GameMessage{type,roomId,playerName,payload,timestamp}`。心跳/聊天/AFK/解散由公共基类处理。

### 2.1 上行消息

| type | payload | 阶段 | 说明 |
|---|---|---|---|
| `START_GAME` | `{}` 或 `{variant:"THREE_DOUBLE"}` | WAITING | 房主开局，生成种子、洗牌、算蛇形序，进入 DRAFT；**M5**：显式 `THREE_DOUBLE` 且恰好 3 人时开启双角色变体 |
| `PICK_RACER` | `{racerKey}` | DRAFT | 轮抽选一名；服务端校验"当前序位==本人"且 key 在本批牌池 |
| `READY_RACER` | `{racerKey}` 或 `{racerKeys:[...]}` | MUSTER | 赛前暗选出场选手；**M5**：双角色局可一次提交 2 名（数组顺序即固定出场顺序），也兼容分次单点 |
| `ROLL` | `{}` | RACE | 掷一颗骰（点数由服务端生成，客户端不传点数，M2 已实现） |
| `USE_OPTIONAL` | `{usePrimary,targetSeat,targetSpace,guessDice}` | RACE | **M4**：声明本回合可选能力意图（意图前置，`ROLL` 时消费）；**M5**：双角色局 `targetSeat` 为参赛号 lane，见 §11 |

> M2 实现 `START_GAME` / `PICK_RACER` / `READY_RACER` / `ROLL`；M4 实现 `USE_OPTIONAL`（`TurnIntent` 四字段，不再是 M1 预留的 `{choice}`）；M5 扩展 `START_GAME.variant` 与 `READY_RACER.racerKeys`。

> **信封约束（2026-11-22 修复）**：所有上行消息一律为 `{type, payload:{...}}`，参数**必须放在 `payload` 内**。`AbstractGameWebSocketHandler.handleTextMessage` 交给子类的是整条消息的根节点，子类必须先取 `payload`（`actionArgs()`，无 payload 对象时兼容根节点平铺）。本游戏曾直接读根节点字段，导致 `PICK_RACER` / `READY_RACER` / `USE_OPTIONAL` / `START_GAME.variant` 全部收 `ACTION_REJECT`，前端表现为「轮到我了但点不选人」；当时 WS 测试用平铺格式构造消息，全绿却测不到真实协议。WS 回归用例现已统一使用真实 payload 格式，并单独保留一条平铺兼容用例。

### 2.2 下行消息

| type | payload | 说明 |
|---|---|---|
| `GAME_START` | `{room, seatOrder}` | **按收件人个性化**：`room=toVO(收件人)`，`mySeatNo` 不可为 null |
| `DRAFT_STATE` | `DraftSnapshotVO`（见 §4） | 轮抽全量状态；每次选牌/超时后广播 |
| `DRAFT_PICK` | `{seatNo, playerName, racerKey, auto}` | 某人选走一名；`auto=true` 为超时代选 |
| `MUSTER_READY` | `{seatNo, playerName, ready, auto, raceNo}` | **某人已准备（不含选手 key）**；M2 |
| `RACE_START` | `{raceNo, track, activeRacerKeys}` | 全员就绪后开赛，此时才公开出场选手；M2 |
| `STATE_SYNC` | `MagicalAthleteSnapshotVO` | 每次成功动作后**按收件人个性化**广播（阶段可见性见 §3） |
| `DICE_RESULT` | `{seatNo, playerName, value, tripSkipped, effects, auto}` | 服务端掷骰结果；M2 |
| `ABILITY_CHAIN` | `{events:[{racerKey,eventType,effects}]}` | **M3**：效果指令必须可重放、落 action_log |
| `RACE_RESULT` | `{raceNo, goldSeat, silverSeat, goldName, silverName, goldValue, silverValue, standings}` | 单场结算；M2 |
| `GAME_OVER` | `{rankings, tie}` | 第四场结束；`tie=true` 表示并列第一 |
| `ACTION_REJECT` | `{action, reason, message}` | 动作被拒，reason 见 §5 |

## 3. 分阶段快照可见性（D5，核心反泄露约束）

| 阶段 | `phase` | 快照内容 | 隐藏字段 |
|---|---|---|---|
| 等待 | `WAITING` | 只含座位视图 `room` | 无牌池、无手牌 |
| 轮抽 | `DRAFT` | 当前批牌池 + 每人已选数量；**M1 手牌公开**（轮抽阶段本就公开） | 无 |
| 赛前暗选 | `MUSTER` | **只广播"某人已准备/未准备"**，`activeRacerKey` 不下发 | `activeRacerKey`（每个玩家个性化只回自己的） |
| 比赛 | `RACE` | 赛道棋子位置、骰点、能力链 | 牌堆剩余（不可见） |
| 单场结算 | `RACE_END` | 名次、奖牌归属 | 无 |
| 终局 | `FINISHED` | 总分、并列标记 | 无 |

> M2 已覆盖全部六个阶段。MUSTER 阶段 `hands` 只下发收件人自己的手牌，`myActiveRacerKey` 只回自己的选择，`activeRacerKeys` 恒空；只有进入 RACE 才公开。四场出场时机是本作唯一策略层，泄露即作废。

## 4. VO 形状

```ts
// 房间视图（REST getRoom / SEAT_CHANGED / GAME_START）
interface MagicalAthleteRoomVO {
  roomId: string; hostName: string; maxPlayers: number;
  status: 'WAITING' | 'PLAYING' | 'FINISHED';
  seats: { seatNo: number; name: string|null; online: boolean; afk: boolean }[];
  mySeatNo: number|null;
  afkPlayers: string[];
}

// 轮抽快照（DRAFT_STATE / STATE_SYNC.phase=DRAFT）
interface DraftSnapshotVO {
  phase: 'WAITING' | 'DRAFT' | 'MUSTER' | 'RACE' | 'RACE_END' | 'FINISHED';
  raceNo: number;                 // 0=轮抽，1..4=比赛场次
  batch: number;                  // 0/1：当前第几批轮抽
  pickOrder: number[];            // 本批蛇形选序（座位号序列）
  pickIndex: number;              // 当前进行到第几手
  pool: RacerKey[];               // 当前批牌池（可被选走的 key）
  hands: { seatNo: number; name: string; racers: RacerKey[] }[]; // 已选（M1 公开）
  currentSeatNo: number|null;     // 当前应谁选
  deadlineAt: number;             // epoch ms，0=无倒计时
  finished: boolean;              // 轮抽是否完成
}

interface MagicalAthleteSnapshotVO extends DraftSnapshotVO {
  players: { seatNo: number; name: string; online: boolean; afk: boolean }[];
}
```

`RacerKey` 为 36 名选手稳定 key（见 `design/games/magicalAthlete/game.yaml`），例：`alchemist` / `baba_yaga` / `blimp`。牌堆内容（未发到牌池的选手）永远不下行。

## 5. 失败码

| reason | 含义 |
|---|---|
| `NOT_HOST` | 只有房主可开局 |
| `NOT_WAITING` | 房间不在等待态 |
| `NOT_ENOUGH_PLAYERS` | 少于 2 人 |
| `NOT_IN_GAME` | 玩家未入座 |
| `NOT_YOUR_TURN` | 当前蛇形序位不是该玩家 |
| `RACER_NOT_IN_POOL` | 所选 key 不在当前批牌池 |
| `ALREADY_PICKED` | 该选手已被选走 |
| `PHASE_MISMATCH` | 消息与当前阶段不符 |
| `NOT_IMPLEMENTED_YET` | M2+ 消息在 M1 阶段收到 |

## 6. M1 轮抽时序（可验收）

```
房主 START_GAME
  ├─ 服务端：seed=Random.nextLong → 洗 36 张 → batch0 展 2n 张 → pickOrder=snake(n,0)
  ├─ 广播 GAME_START（逐收件人 mySeatNo）+ DRAFT_STATE
  └─ 启动 30s 轮抽倒计时
每次 PICK_RACER{racerKey}（synchronized pickRacer）
  ├─ 校验 phase=DRAFT、currentSeatNo==本人、key∈pool
  ├─ 从 pool 移除 → 记入手牌 → pickIndex++
  ├─ pickIndex 走完本批 → loadBatch(1) → pickOrder=snake(n,1)
  ├─ 两批走完 → phase 推进（M1 结束于轮抽完成，M2 接 MUSTER）
  └─ 广播 DRAFT_PICK + DRAFT_STATE
超时 30s（MagicalAthleteTimeoutTask，500ms tick）
  └─ 为当前序位玩家自动选 pool[0]（D6：选最左，不做主观选择）→ 同样广播 auto=true
```

轮抽选序（D7，`DraftOrder.snake`）：普通 n 人两批各 `[1,2,…,n,n,…,2,1]`（每人每批 2 张、共 4 张）；2 人局两批为 `ABBAABBA` / `BAABBAAB`（每人 8 张）。服务端维护牌池与每人手牌，客户端只提交 key。

## 7. 落库（D11，M6 复用三表）

事件 `DRAFT_PICK` / `RACE_START` / `ROLL` / `MOVE` / `ABILITY` / `TRIP` / `RACE_END` / `SCORE`。能力日志带 `racerKey + eventType + effects`。M1 阶段不落库（轮抽数据仅内存），M6 接入 `game_match` 三表。

## 8. M2 单场竞速语义（已实现）

```
轮抽两批完成 → MUSTER（20s，暗选手牌中未用过的选手）
  ├─ READY_RACER{racerKey}：校验在手牌、未在往场出场、未重复准备
  ├─ 只广播 MUSTER_READY{seatNo} + 个性化快照（他人 key 不下发）
  └─ 全员就绪 → startRace() → raceNo++ → RACE_START（公开选手）
RACE（20s/回合，服务端掷骰）
  ├─ ROLL{} → 服务端 1+Random.nextInt(6) → RaceSession.roll(seat, dice)
  ├─ 主移动 → 停格结算（狂野面：铜币/绊倒/箭头独立移动可链式）
  ├─ 绊倒只跳过下一次主移动：返回 DICE_RESULT{tripSkipped:true}，不推进棋子
  ├─ 到 30 即冲线（超过按 30），第二名冲线立即结束本场
  └─ 广播 DICE_RESULT + 个性化 STATE_SYNC
结算
  ├─ 金/银按 raceNo 面值 3/4/4/5 与 1/2/2/3 记分，铜币按面值 1 累加
  ├─ 广播 RACE_RESULT（含金银姓名与面值）
  ├─ raceNo<4 → 下一场 MUSTER（用过的选手永久离场）
  └─ raceNo==4 → FINISHED + GAME_OVER{rankings, tie}，同分并列第一不加赛
```

### 超时托管（D6）
- DRAFT 30s：代选牌池最左；MUSTER 20s：代选手牌第一张未用过的；RACE 20s：代掷骰。
- 三种托管都带 `auto=true` 广播；能力尚未实现，因此"放弃所有可选能力"在 M2 天然成立。

### 新增失败码
`RACER_NOT_IN_HAND`（暗选 key 不在自己手牌）、`ALREADY_READY`、`RACER_USED`（往场已出场）、`TRIPPED_SKIP`（成功但跳过主移动，非失败）。

### 数据边界
- 骰点只由服务端生成，`ROLL` payload 即使携带 `value` 也被忽略。
- `RaceSession` / `RaceTrack` / `ScoreBoard` 为纯逻辑无 IO；骰点由外部注入，便于测试与复盘。
- M2 不含能力系统：`RaceTrack` 已实现 10 个狂野特殊格（1/13 铜币、5/17/26 绊倒、7/11/16/23/24 箭头），飞艇/野兔等 36 条能力留 M3。

## 9. M3 能力系统语义（已实现 2026-11-22）

### 架构（D3 + D8）
- **事件注册表**：`AbilityRegistry` 以 `racerKey + AbilityEvent` 为键挂 `AbilityHandler`，新增选手只注册不改引擎；不用 switch。
- **效果指令**：handler 只读 `AbilityContext`、产出 `List<Effect>`，绝不直接改房间状态；每条 Effect 带 `type/sourceSeat/targetSeat/amount/reason`，可原样落 `action_log` 复盘。
- **效果管线**：`EffectPipeline.fire()` → 收集效果 → 按「赛道格(0) → 当前选手(1) → 其余顺时针(2+) → 系统(末尾)」优先级排序执行；实际发动补发 `ON_ABILITY_FIRED` 形成连锁（蹭蹭怪）。
- **循环保护**：仅当「全部座位状态指纹 + 待结算队列」完全重复才截断一次（`CYCLE`）；另设 200 条硬上限（`CHAIN_LIMIT`）。**禁止**「每能力每回合一次」的粗暴截断——那会破坏蹭蹭怪/吸盘鱼的长期联动。
- **handler 归属**：`ctx.handlerSeat`（能力归属者）与 `ctx.currentActor`（掷骰者）分离；自身能力须判 `handlerSeat==currentActor`，他人反应能力判 `handlerSeat!=currentActor`，避免同名能力被场上其他座位误触发。

### 事件顺序
`ROLL_REPLACE`（炼金/大长腿）→ `DICE_RESULT`（尺蠖/跟班/西西弗斯）→ `BEFORE_MAIN_MOVE`（飞艇/黏液怪/野兔，产出基数与修正）→ 主移动 → `ON_PASS`（香蕉/半人马）→ `ON_STOP`（浪漫家）→ 连锁 `ON_ABILITY_FIRED`（蹭蹭怪）。`BEFORE_RACE`（西西弗斯赛前 +4）在赛前准备阶段。

### 首批 12 名（D10 牌池只开放已实现能力）
| 类别 | 选手 |
|---|---|
| 基数替换 | alchemist（骰1/2→4）、legs（→5） |
| 距离修正 | blimp（<15 +3 / ≥15 −1）、gunk（他人 −1 叠加）、hare（+2 / 独自领先跳过） |
| 最终骰反应 | inchworm（他人骰1→取消+自身+1）、lackey（他人骰6→+2） |
| 超越触发 | banana（被越过→移动者绊倒）、centaur（被超越→后退2） |
| 连锁与计分 | scoocher（他人能力发动→+1，不限次数）、sisyphus（赛前+4 / 骰6回起点−1）、romantic（他人单独停格→+2） |

### 数据边界与兼容
- `RaceSession.roll()` 的 `RollOutcome` 增加 `abilityChain[]`（可重放 reason 列表）与 `chainTruncated` 标志；保留 M2 老构造子重载，老路径能力链恒空。
- `ScoreBoard.awardAbilities(raceNo, extraBySeat)` 记录能力额外分并入总分与并列排名；`RaceSession.extraScoreBySeat()` 提供来源。
- 主移动/停格后先 `syncToContext()` 再结算 `ON_PASS/ON_STOP`，避免旧快照覆盖新位置（探针定位的关键坑）。
- M3 未实现：warp/copy 类（人字拖/催眠师/吸盘鱼/电灯泡/模仿猫/蛋蛋/双胞胎，需玩家二次选择）、可选能力交互层（M4）、18 名其它 interrupt/reactive/scoring 能力（M4）。

## 10. M4 可选能力交互层与能力扩展（已实现 2026-11-22）

### 交互模型：意图前置 + 同步结算
- 玩家在 `ROLL` 前发 `USE_OPTIONAL{payload}` 声明本回合意图，**不拆 M3 的同步结算链**；`ROLL` 时意图随服务端骰点带入 `RaceSession.roll(seat, dice, intent)`，结算后清空。
- `TurnIntent{usePrimary, targetSeat, targetSpace, guessDice}`：座位号由房间从玩家名反查（客户端传的 seatNo 一律忽略）；targetSeat 必须在场未冲线、targetSpace∈[1,29]、guessDice∈[1,6]，非法回 `ACTION_REJECT`。
- 超时 `autoRoll` 用 `TurnIntent.NONE`：自动放弃全部可选能力（D6，不替玩家做主观选择）。
- 意图是私有信息：`USE_OPTIONAL` 只给声明者回一条个性化 `STATE_SYNC`，不广播、不下发他人意图；快照新增 `myOptionalOptions[]`（仅轮到自己时非空）。

### 引擎接线（M3 定义但未消费的字段在 M4 生效）
- `AbilityContext` 增加 `intent` 与 `turnStartPosition`；`RaceSession.roll` 在停格结算后触发 `AFTER_TURN`（起哄者判位移≤1），并消费 `stolenNextActor`（船长抢行动）与 `extraTurnGranted`（天才猜中保留当前行动者）。
- `EffectType.SWAP_POSITION` 支持人字拖交换（传送语义：不算移动、不触发超越）。
- 重掷类（魔术师两次/骰子商人一次）在房间层服务端完成，弃用点数不进能力链，`RaceSession` 只见最终点数——尺蠖/跟班/船长判最终骰点。

### 本批实现的能力（M3 12 + M4 16 = 28）
| 类别 | 选手 |
|---|---|
| 停格干扰 | baba_yaga（同格绊倒）、mouth（同格淘汰）、huge_baby（挤后一格） |
| 距离修正 | coach（同格+1）、party_animal（同格+1）、cheerleader（垫底+2/自己+1）、stickler（过量取消）、rocket_scientist（翻倍+自绊） |
| 骰点反应 | genius（猜中额外回合）、skipper（骰1抢行动） |
| 计分 | heckler（位移≤1时+2）、lovable_loser（独自垫底+1） |
| warp（读意图） | flip_flop（交换替代移动）、hypnotist（传送目标到己格）、third_wheel（传送至恰两人格）、suckerfish（跟随，简化为传送+取消移动） |
| 重掷（房间层） | magician、dicemonger |

### 未实现（留 M5）
- copy 类（copycat/egg/twin）：需要"能力委托"——管线按复制来的 racerKey 取 handler，牵动 Seat 构造与赛前阶段。
- mastermind（预测冠军）、leaptoad（空格计数移动）、suckerfish 严格同步语义（当前为简化版）。

### 数据结构
- 注册表分三层：`StandardAbilities.registry()`（M3 12，保持不变以零回归）、`ExtendedAbilities`（M4 纯 handler 12）、`ChoiceAbilities`（warp 4），由 `FullAbilities.registry()` 合并为房间实际使用的 28 名注册表。
- `RollOutcome` 老构造子兼容 M2；`roll(seat,dice)` 老签名委托 `roll(seat,dice,TurnIntent.NONE)`，M2/M3 用例零回归。

## 11. M5 委托能力、严格语义与 3 人双角色变体（已实现 2026-11-22）

### 11.1 M5a 能力委托架构（copy 三名）

- `AbilityContext.Seat` 增加 `effectiveKey` / `copyFromSeat`：座位自身 `racerKey` 不变，但事件管线按 `effectiveKey` 取 handler；`handlerSeat` 仍为**实际座位**，保证能力归属（M3 的关键坑）不被委托打乱。循环指纹纳入委托字段。
- `EffectPipeline.collect()` 从按 `seat.racerKey` 改为按 `ctx.effectiveKeyOf(seat)` 取 handler。
- `CopyDelegation` 集中三类规则：`bindFixed()` 固定委托（蛋蛋复制本人手牌中一名非 copy 能力、双胞胎复制上场冠军 `lastWinnerKey`）；`refreshMimic()` 在每次 `roll()` 开始时把模仿猫动态指向当前领先者——不复制赛前能力、排除 copy 类防递归、自己是全场第一时不委托、并列按座位号确定性兜底。
- `AbilityRegistry.registerDelegated()` / `registerEngineManaged()`：让无事件 handler 的能力也能 `supports()` 并开放进牌池，但 `coveredRacerCount()` 仍只统计真实 handler，保持 M4 的 28 名断言不回归。
- `RaceSession.applyPreRace()` 新增 `BEFORE_RACE` 结算，修复赛前能力（西西弗斯 +4）与被复制的赛前能力从未结算的缺口；构造子增 `List<CopyBinding>`，老构造子零回归。`Seat.score` 改为从 `extraScore()` 回读，避免跨回合/赛前能力分丢失。
- M4 的 28 名之外，牌池放开 `copycat` / `egg` / `twin` / `leaptoad` / `mastermind`。

### 11.2 M5b 两条严格语义

- **mastermind（幕后军师）**：首回合带 `TARGET_SEAT` 意图时锁定"预测冠军"（`predictionLocked`，仅锁一次）；冠军产生后若命中，军师立即获得第二名并结束本场（`finishCount=2`）；自猜命中标记 `mastermindSelfWin`，房间结算走 `ScoreBoard.awardRaceSelfGoldSilver()` 实现金银同人。未命中不介入。
- **leaptoad（跳跳蟾蜍）**：主移动只计**空格**，占据格跳过且不消耗步数；每跳过一个占据格补发一次 `ON_ABILITY_FIRED`，驱动蹭蹭怪等联动；支持正负方向、赛道边界截断、guard 64 防死循环。效果日志 `LEAP_MOVE:from->to:skip=[...]`。
- **suckerfish 严格同步组语义**：按 M5 计划口径明确**不在本轮范围**，保留 M4 的"传送 + 取消移动"简化实现；后续需要同步组时单独开里程碑。

### 11.3 M5c 3 人双角色变体（lane / 参赛号架构）

- **开关**：`START_GAME{variant:"THREE_DOUBLE"}`，仅恰好 3 人入座时生效（`RaceVariant.of()` 兜底），否则按人数默认 `NORMAL` / `TWO_PLAYER`。
- **轮抽**：`DraftOrder.threeDoubleSnake()` 四批 `ABCCBA / BCAACB / CABBAC / ABCCBA`，每批每座位 2 张，共 24 手、每人 8 张；`DraftSession` 按 `RaceVariant.poolSize()/pickOrder()/batchCount()` 参数化，普通局仍两批、零回归。
- **核心：lane 展开**。`RaceSession` 用 `Map<Integer,Racer>` + `putIfAbsent` 明确禁止同一座位两名选手，因此**不改引擎**：房间把 3 个真实座位展开为 6 个内部参赛号 lane，`lane = (seat-1)*2 + slot + 1`。`RaceSession` / 28 个 handler / `AbilityContext` / `EffectPipeline` 完全不改，它们看到的 `seatNo` 就是 lane；普通局 `lane == seat`。
- **暗选**：每人按提交顺序锁定 2 名（`musterKeys: seat → List<key>`），`bindMusterKeys()` 按 slot 绑到 lane；`autoReady()` 补满 2 名。MUSTER 快照仍隐藏他人 key，只新增 `musterProgress: seat → 已选数`。
- **对外协议保持真实座位语义**：`currentSeatNo` / `diceSeatNo` / `RollOutcome.seatNo` / `goldSeat` / `silverSeat` / `scores` / `rankings` 全部由 `variant.seatOfLane()` + `mergeLanes()` 归并回 3 个真实座位；`RacerStateVO` 新增 `ownerSeatNo` 承载 lane→seat 映射。前端"轮到我"判断与名字播报因此零改动。
- **快照新增字段**（普通局下发默认值，兼容 M1–M4 老用例）：`variant` / `racersPerSeat` / `currentLane` / `laneToSeat` / `activeRacerKeysByLane` / `myActiveRacerKeys` / `musterProgress`；`RACE_START` 补发 `variant` / `racersPerSeat` / `activeRacerKeysByLane` / `laneToSeat`。`activeRacerKeys` 兼容保留"真实座位 → 该座位第一名选手"。
- **`USE_OPTIONAL.targetSeat` 的 lane 语义**：房间按 `race.racers()` 的 `seatNo()` 校验目标，双角色局即 lane（1..6）。前端在双角色局把 `TARGET_SEAT` 从数字输入改为"指定目标选手"下拉（选项取自赛道棋子 `seatNo` + 玩家名/选手名），普通局保持数字输入（lane==seat，零回归）。mastermind 的预测冠军同理按 lane 提交。

### 11.4 前端适配与验证

- `index.vue`：等待页恰好 3 人时房主可见变体开关；轮抽标题 `/{{totalBatches}}` 动态显示 2/4 批；MUSTER 用 `myActiveKeys` / `musterDone` / `musterHint` / `musterProgressMap` 支持累计选 2 名并展示固定出场顺序；RACE 棋子按 `ownerSeatNo ?? seatNo` 高亮；新增当前出场选手提示与 lane 目标下拉。
- 座位号比较继续 `Number()` 归一化（防服务端字符串 `"2"` 导致"轮到我了却点不动"）。
- **验证**：后端 `Tests run: 120, Failures: 0, Errors: 0, Skipped: 0`、`dicego-games` 五模块 `BUILD SUCCESS`；前端 `vue-tsc --noEmit` 通过、Vitest 35 文件 259 用例全绿（新增 5 个 M5 组件用例）、`vite build` 成功。
