# 璀璨宝石（Splendor）消息协议规格

日期：2026-11-20 · 状态：定稿（P3），M1-M5 全部实现 · 模块：`dicego-games/dicego-splendor` · WebSocket 端点：`/ws/splendor`

## 0. 信封与总则

- 所有消息使用公共信封 `GameMessage`：`{ "type", "roomId", "playerName", "timestamp", "payload" }`
- `type` 为字符串：公共类型走 `GameMessageType`（房间生命周期/聊天/心跳/胜负/ERROR），
  Splendor 专属类型走 `SplendorMessageType`（本模块自建枚举，wire 值 = 枚举 name()，对齐 D1 决策）
- 连接方式与象棋一致：`ws://host/ws/splendor?roomId=XXX&playerName=XXX`；房间创建/入座/起身走 REST
- **可见性总则（D5）**：任何携带对局状态的消息若发给具体某个人，必须是个性化快照；
  群发消息（GAME_START/SEAT_CHANGED/ACTION_DONE）只携带中立信息，不含任何人的预留卡明细

## 1. REST 接口（房间生命周期，对齐象棋模式）

| 方法 | 路径 | 入参 | 出参 |
|---|---|---|---|
| POST | `/api/splendor/rooms` | `{ "playerName", "maxPlayers" }`（maxPlayers 可选 2-4，缺省 4） | `{ success, room }` |
| POST | `/api/splendor/rooms/{roomId}/seat` | `{ "playerName", "seatNo" }`（seatNo 可选 1-4，缺省自动补最小空位） | `{ success, room }`（失败含 `error` 原因码） |
| POST | `/api/splendor/rooms/{roomId}/stand` | `{ "playerName" }` | `{ success, room }` |
| GET | `/api/splendor/rooms/{roomId}` | `?playerName=` | `{ success, room }` |
| GET | `/api/splendor/rooms` | - | `{ rooms, waitingCount, playingCount }` |

座位失败原因码：`NOT_WAITING`（对局已开始）、`SEAT_TAKEN`（座位已被占）、`ROOM_FULL`（满员）、`INVALID_SEAT`（座位号非法）。

`room` 载荷（SplendorRoomVO）：

```json
{ "roomId": "A1B2C3", "hostName": "alice", "maxPlayers": 4, "status": "WAITING|PLAYING|FINISHED",
  "seats": [ { "seatNo": 1, "playerName": "alice", "connected": true },
             { "seatNo": 2, "playerName": null, "connected": false } ],
  "viewerSeatNo": 1 }
```

## 2. SplendorMessageType 清单

### 2.1 客户端 → 服务端（请求类）

| type | payload | 说明 |
|---|---|---|
| `START_GAME` | `{}` | 房主请求开局。失败回 `ACTION_REJECT` |
| `TAKE_GEMS` | `{ "gems": { "white": 0-2, "blue": 0-2, "green": 0-2, "red": 0-2, "black": 0-2 } }` | 拿宝石。合法组合：三异色各 1，或同色 2（该色供给 ≥4）。总数恒为 3（拿不满 3 也必须凑满，除非全场宝石不足的规则边缘情况由引擎判定） |
| `BUY_CARD` | `{ "cardId": "l2-013", "source": "table" 或 "reserved" }` | 买发展卡。黄金自动补差，客户端不指定支付方案 |
| `RESERVE_CARD` | `{ "cardId": "l1-005" }` 或 `{ "level": 2 }` | 预留桌面明卡，或盲留某级牌堆顶。成功领 1 黄金（银行有则必领） |
| `DISCARD_GEMS` | `{ "gems": { ...五色... } }` | 回合末手牌 >10 时强制弃宝石 |
| `NOBLE_SELECT` | `{ "nobleId": "noble-04" }` | 同回合加成同时满足多位贵族时，玩家选其一拜访（其余留存桌面） |

### 2.2 服务端 → 客户端（事件类）

| type | payload | 发送对象 | 说明 |
|---|---|---|---|
| `STATE_SYNC` | 完整快照（见 §4） | 每人一份**个性化** | 开局后、每次行动结算后全量下发；连接建立时随公共 `PLAYER_JOINED` 的 selfSync 载荷下发 |
| `ACTION_DONE` | `{ "seatNo": 1, "action": "TAKE_GEMS", "actionId": "...", "detail": { ...行动参数与结果摘要... } }` | 全房间群发（中立） | 供前端做动画；状态以 `STATE_SYNC` 为准 |
| `ACTION_REJECT` | `{ "action": "TAKE_GEMS", "actionId": "...", "reason": "NOT_YOUR_TURN", "message": "人类可读提示" }` | 仅发送者 | 行动校验失败；M1 阶段对未实现的行动类型回 `NOT_IMPLEMENTED` |

### 2.3 公共类型（GameMessageType，基类/本模块复用）

`PLAYER_JOINED`（selfSync 含个性化快照 + 广播中立 room）、`PLAYER_LEAVE`、`SEAT_CHANGED`（中立 room）、
`GAME_START`（中立：room + seatOrder，随后紧接每人一份 `STATE_SYNC`）、`GAME_OVER`（中立：名次列表）、
`CHAT`、`HEARTBEAT`、`ERROR`。

## 3. 行动失败原因码（ACTION_REJECT.reason）

| reason | 场景 |
|---|---|
| `NOT_PLAYING` | 对局未开始或已结束 |
| `NOT_HOST` | 非房主请求开局 |
| `NOT_ENOUGH_PLAYERS` | 开局时入座人数 < 2 |
| `NOT_YOUR_TURN` | 非当前回合方行动 |
| `ROOM_FULL` / `SEAT_TAKEN` / `INVALID_SEAT` / `NOT_WAITING` | 座位类（REST 与 WS 共用） |
| `INVALID_GEMS` | 拿宝石组合非法（非三异色/同色二，或包含黄金） |
| `GEM_SUPPLY_SHORT` | 银行该色不足（同色二需供给 ≥4） |
| `CARD_NOT_ON_TABLE` | cardId 不在桌面明卡中 |
| `CARD_NOT_RESERVED` | source=reserved 但不在本人预留区 |
| `CANNOT_AFFORD` | 宝石+加成+黄金不足以支付 |
| `RESERVE_LIMIT` | 本人预留已达 3 张 |
| `DECK_EMPTY` | 盲留的该级牌堆已耗尽 |
| `MUST_DISCARD` | 有待办阶段（弃宝石/选贵族）时发起主行动 |
| `DISCARD_INVALID` | 弃宝石数量/组合非法，或无弃宝石待办时乱发 |
| `NOBLE_NOT_ELIGIBLE` | 选中的贵族不在当前可拜访集合中，或无选择待办时乱发 |
| `INVALID_PARAM` | 请求参数非法（缺字段/类型错误/未知色名） |
| `NO_GOLD_LEFT` | 预留时银行黄金为 0（此时代价照付，规则允许无黄金预留——拒绝码仅在变体规则用） |
| `NOT_IMPLEMENTED` | 协议已定但引擎未实现的行为（当前仅兜底保留） |

## 4. STATE_SYNC 快照 schema（核心，逐字段）

```json
{
  "viewerSeatNo": 1,
  "status": "PLAYING",
  "roundNo": 1,
  "currentSeatNo": 1,
  "bank": { "white": 4, "blue": 4, "green": 4, "red": 4, "black": 4, "gold": 5 },
  "levels": {
    "1": { "cards": [ { "code": "l1-001", "level": 1, "bonus": "white", "vp": 0,
                         "cost": { "white": 0, "blue": 3, "green": 0, "red": 0, "black": 0 } } /* ×4 */ ],
           "deckLeft": 36 },
    "2": { "cards": [ /* ×4 */ ], "deckLeft": 26 },
    "3": { "cards": [ /* ×4 */ ], "deckLeft": 16 }
  },
  "nobles": [ { "code": "noble-01", "name": null, "vp": 3,
                  "need": { "white": 4, "blue": 4, "green": 0, "red": 0, "black": 0 } } /* × 人数+1 */ ],
  "players": [
    { "seatNo": 1, "name": "alice",
      "gems":     { "white": 0, "blue": 0, "green": 0, "red": 0, "black": 0, "gold": 0 },
      "bonuses":  { "white": 0, "blue": 0, "green": 0, "red": 0, "black": 0 },
      "vp": 0,
      "reservedCount": 0,
      "reservedCards": [ /* SplendorCardVO ×0..3；字段仅出现在收件人本人 */ ] }
  ],
  "pendingType": null,
  "pendingNobles": null
}
```

### 4.0 回合内待办阶段（M2/M3）

主行动结算后，引擎按规则顺序检查并可能进入待办（`pendingType`，公开）：

- `NOBLE_SELECT`：行动后加成同时满足 **多位** 贵族 → 回合暂停，行动方须发 `NOBLE_SELECT`
  选其一（唯一满足时自动拜访，不进入待办）。候选贵族明细 `pendingNobles`（SplendorNobleVO 数组）
  **仅当前行动方本人快照非 null**，对手/旁观一律缺省（D5）。
- `DISCARD`：行动后手牌宝石（含黄金）> 10 → 回合暂停，行动方须发 `DISCARD_GEMS`
  弃到恰好 10 颗，数量错/超持有均为 `DISCARD_INVALID`。

待办期间：不推进回合（`currentSeatNo` 不变），主行动一律 `MUST_DISCARD`；
顺序恒为 贵族选择 → 弃宝石 → 回合推进。

### 4.1 可见性规则（D5 落地）

| 信息 | 收件人=卡主 | 收件人=对手/旁观 |
|---|---|---|
| `players[i].reservedCards` | **有**（本人预留卡明细） | **字段缺省（null）** |
| `players[i].reservedCount` | 有 | 有（公开） |
| `pendingType` | 有 | 有（公开） |
| `pendingNobles`（候选贵族明细） | **有**（当前行动方本人） | **字段缺省（null）** |
| 宝石/加成/VP/贵族/桌面卡/牌堆余量 | 全公开 | 全公开 |

规则不变式：**群发消息绝不出现 `reservedCards` / `pendingNobles` 字段**；快照生成函数 `snapshotFor(viewerName)`
是唯一的快照出口，对手与旁观者视角中所有玩家的该字段一律缺省。

### 4.2 随人数变化的初始值（发牌规则）

| 项 | 2 人 | 3 人 | 4 人 |
|---|---|---|---|
| 每色宝石供给 | 4 | 5 | 7 |
| 黄金 | 5 | 5 | 5 |
| 贵族张数（玩家数+1） | 3 | 4 | 5 |

桌面三级明卡各 4 张，牌堆余量 = 40/30/20 − 4 = **36/26/16**。开局座位顺序 = 座位号升序，先手 = 最小座位号。

## 5. 时序约定

```
建房(REST) → 入座(REST, 自动补位) → [满员或房主决定] 房主 WS 发 START_GAME
  ├─ 失败 → ACTION_REJECT(NOT_HOST / NOT_ENOUGH_PLAYERS / NOT_WAITING)
  └─ 成功 → 群发 GAME_START(中立) → 逐人 STATE_SYNC(个性化)
此后每次行动：校验 → 结算 → 落 action_log（M5，含原始请求参数与 state_version）→ 群发 ACTION_DONE(中立，含 actionId/detail) → 逐人 STATE_SYNC
  ├─ 结算产生待办 → STATE_SYNC 携带 pendingType（本人另含 pendingNobles），回合暂停
  │   → 玩家补发 NOBLE_SELECT / DISCARD_GEMS → 同上 ACTION_DONE + STATE_SYNC → 回合推进
  └─ 达 15VP 标记终局轮 → 轮回到先手席位时终局 → 群发 GAME_OVER(中立名次 rankings)
GAME_OVER 载荷：{ "rankings": [ { "seatNo", "name", "vp", "cards" } ] }
  排序：VP 降序 → 已购发展卡数升序（官方平局裁决）→ 座位号升序
断线重连：重连即收 PLAYER_JOINED(selfSync 含 room + 个性化 STATE_SYNC)，座位与手牌不丢
```

## 6. 实现范围声明

- M1（已实现）：REST 房间/座位、`START_GAME`、开局发牌、`STATE_SYNC`（含可见性）
- M2（已实现）：TAKE_GEMS / BUY_CARD / RESERVE_CARD 引擎校验与结算（`SplendorEngine`，即 §2.1 的校验函数化）
- M3（已实现）：NOBLE_SELECT、DISCARD_GEMS、15VP 终局轮与 GAME_OVER 名次
- M5（已实现）：对局落库——game_match / game_match_player / game_action_log 三表
  （`SplendorMatchRecorder` 接口 + `DbSplendorMatchRecorder` 实现；建房/入座按 Bearer token 绑定
  user_id，游客记负座位哨兵保证唯一键；落库失败仅记日志不中断对局；端到端冒烟 56 步对局已验证）。
  game_room / game_room_seat 属内存态生命周期暂不持久化（表结构保留给未来跨进程恢复）；game_player_stat 聚合表待战绩 UI 时接入。
- M4（已实现）：boardgame-ui 对战界面——`/splendor/:roomId` 路由与 `views/splendor/index.vue`
  （STATE_SYNC 快照渲染、五行动入口、NOBLE_SELECT/DISCARD 待办弹层、GAME_OVER 名次弹层；
  大厅 level 2 按游戏分流，splendor 建房带容量选择；契约冒烟 `.workbuddy/tmp/splendor-m4-contract.cjs`
  已按前端消费方式校验全部消息形状）。
- 后续待办：战绩 UI（game_player_stat）、观战视角完善、复盘回放（action_log 消费）
- wire 值均为枚举 name() 大写蛇形，与象棋现状一致，前端解析方式不变
