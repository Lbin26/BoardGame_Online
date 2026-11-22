# 德国心脏病（Halli Galli）接口协议

日期：2026-11-21 · 模块：`dicego-games/dicego-halligalli` · 包：`org.dromara.halligalli`

## 0. 命名与端点

| 层 | 值 |
|---|---|
| 前端 catalog key | `halliGalli` |
| DB `game_code` | `halli_galli` |
| REST 前缀 | `/api/halli-galli` |
| WS 端点 | `/ws/halli-galli?roomId=..&playerName=..` |
| 前端路由 | `/halli-galli/:roomId` |
| Java 模块 | `dicego-games/dicego-halligalli` |

## 1. REST

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/halli-galli/rooms` | 房间列表：`{rooms:[{roomId,hostName,maxPlayers,seatedCount,status}],waitingCount,playingCount}` |
| POST | `/api/halli-galli/rooms` | 建房：`{playerName,maxPlayers?}`，容量 2–6，缺省 4；**不自动入座** |
| POST | `/api/halli-galli/rooms/{id}/seat` | 入座：`{playerName,seatNo?}`，缺省补最小空位 |
| POST | `/api/halli-galli/rooms/{id}/stand` | 起身（仅等待中） |
| GET | `/api/halli-galli/rooms/{id}?playerName=` | 房间详情，`mySeatNo` 按 playerName 计算 |

返回体统一：`{success, room?, error?}`。

`room` 结构：

```json
{
  "roomId": "A1B2C3",
  "hostName": "alice",
  "maxPlayers": 4,
  "status": "WAITING | PLAYING | FINISHED",
  "seats": [{ "seatNo": 1, "name": "alice", "online": true, "afk": false }],
  "mySeatNo": 1,
  "afkPlayers": []
}
```

## 2. WS 信封

与其余游戏一致：

```json
{ "type": "FLIP", "roomId": "A1B2C3", "playerName": "alice", "payload": {}, "timestamp": 0 }
```

公共类型（聊天/心跳/AFK/解散/关房）由 `AbstractGameWebSocketHandler` 处理，本游戏只处理业务类型。

## 3. 客户端 → 服务端

| type | 发送方 | 说明 |
|---|---|---|
| `START_GAME` | 房主 | 等待中且 ≥2 人入座；成功广播 `GAME_START` + 全量 `STATE_SYNC` |
| `FLIP` | 当前翻牌方 | 无参数；只能翻自己牌堆顶 |
| `RING` | 任意在场玩家 | 无参数；**无回合限制**，以服务端到达顺序裁决 |

## 4. 服务端 → 客户端

| type | payload |
|---|---|
| `STATE_SYNC` | `snapshot`（见 §5），全量广播 |
| `FLIP_DONE` | `{seatNo, auto, card:{fruit,count}\|null, playerOut, nextSeatNo}` |
| `RING_RESULT` | `{status, seatNo, cardsTaken, beneficiarySeat, penalties:[{seatNo,cards}], reason}` |
| `PLAYER_OUT` | `{seatNo, name}` |
| `ACTION_REJECT` | `{action, reason, message}`，仅回请求者 |
| `GAME_OVER` | `{rankings:[{seatNo,name,cardCount,rank}]}` |

`GAME_START` 复用公共类型：`{room, seatOrder}`。**`room` 必须按收件人定制**（`room.toVO(收件人)`）：
中立视角的 `mySeatNo` 恒为 null，前端拿到后无法判断"我是不是当前翻牌方"，翻牌按钮会永久不可用。
`PLAYER_JOINED` / `SEAT_CHANGED` 仍是中立广播，前端按座位表查名字自愈座位。

## 5. 快照

```json
{
  "phase": "WAITING | PLAYING | FINISHED",
  "flipSeatNo": 2,
  "flipDeadlineAt": 1763720000000,
  "totals": { "apple": 4, "lemon": 1, "cherry": 0, "grape": 0 },
  "players": [{
    "seatNo": 1, "name": "alice", "online": true, "afk": false, "out": false,
    "deckCount": 12, "faceUpCount": 3, "totalCards": 15,
    "topCard": { "fruit": "apple", "count": 2 }
  }],
  "lastRing": null
}
```

- 牌堆内容永不下行；`topCard` 是桌面明牌顶牌，对所有玩家可见。
- `totals` 恒含四种水果（缺省 0），仅供日志复盘；**前端不展示任何全局计数条**——判断"某种水果是否恰好 5 个"是玩家的工作，顶部替他算完就没得玩了。
- 不提供"当前是否可抢铃"字段：判断恰好 5 是玩家的工作。

## 6. 抢铃时序与裁决（D2）

```
客户端按下 → 120ms 本地节流（只挡误触双击），按钮不禁用
      │
服务端 handleRing → room.ring(seatNo)  [synchronized]
      ├─ 对局结束/不在场/已出局 → ACTION_REJECT
      ├─ 同玩家 200ms 内重复     → 静默丢弃（DEBOUNCED，不回包）
      ├─ 桌面恰好 5             → 收走全部明牌，抢铃者成为下一轮翻牌方
      ├─ 上一轮成交后 800ms 内   → 静默丢弃（LATE，不回包）
      └─ 其余                   → 抢错：给每位在场者各赔 1 张
```

要点：

1. 只认服务端接收顺序，不采信客户端时间戳（防作弊、防时钟漂移）。
2. 正确/抢错都广播 `RING_RESULT`；防抖与迟到不回包，避免刷屏与误判。
3. 抢错时若只剩 2 名未出局玩家：对手直接收走桌面全部明牌，本局结束（`reason=TWO_PLAYER_SUDDEN_END`）。
4. 出局玩家不能再抢铃（D4 默认）；`ringAfterExhausted` 开关保留。
5. 客户端**不再锁定按钮**：真实裁决在服务端，本地只做 120ms 节流；"判定中"仅 350ms 视觉反馈（防抖/迟到不回包，必须本地复位）。

## 7. 翻牌与超时（D5）

- `FLIP` 校验顺序：对局中 → 已入座 → 未出局 → 轮到自己 → 牌堆非空。
- 牌堆为空：该玩家出局，明牌留桌继续计数，翻牌权顺延。
- 服务端每 500ms 扫描，`flipDeadlineAt` 到期则代翻并置 `auto=true`；`STATE_SYNC.flipDeadlineAt` 供前端倒计时。

## 8. 失败码

| reason | 触发 |
|---|---|
| `NOT_HOST` | 非房主开局 |
| `NOT_WAITING` | 对局已开始仍尝试入座 |
| `NOT_ENOUGH_PLAYERS` | 入座不足 2 人开局 |
| `NOT_PLAYING` | 对局未开始或已结束 |
| `NOT_IN_GAME` | 未入座的旁观者发动作 |
| `NOT_YOUR_TURN` | 非当前翻牌方翻牌 |
| `PLAYER_OUT` | 出局者翻牌/抢铃 |
| `INVALID_SEAT` | 座位号越界 |
| `SEAT_TAKEN` / `ROOM_FULL` | 入座冲突 |
| `RING_DEBOUNCED` | 200ms 内重复（不回包，仅内部状态） |
| `RING_LATE` | 桌面刚被收走后的迟到请求（不回包） |
| `RING_INVALID` | 抢错，进入赔牌结算 |

## 9. 对局落库（M5）

三张共享表（实体与 mapper 已上移到 `dicego-game-common` 的 `org.dromara.game.common.match`，Splendor 与德国心脏病共用）：

| 表 | 写入时机 | 德国心脏病口径 |
|---|---|---|
| `game_match` | 开局 INSERT，终局 UPDATE | `game_code='halli_galli'`；`random_seed` 即发牌种子；`total_rounds` = 本局翻牌次数 |
| `game_match_player` | 开局批量 INSERT，终局按名次 UPDATE | `final_score` 与 `card_count` 都存终局持牌数，`noble_count` 恒 0 |
| `game_action_log` | 每次结算成功追加 | `action_type` ∈ {`FLIP`, `RING`}；`round_no` = 第几次翻牌；`action_id='srv-{stateVersion}'` |

要点：

1. 只有**已结算**的动作落库：被拒绝的 `FLIP`、防抖/迟到的 `RING` 一律不写。
2. `user_id` 由 REST 建房/入座时按 Bearer token 绑定；游客记负座位哨兵 `-seatNo`，保证 `uk(match_id, user_id)` 不冲突。
3. 落库异常由房间 `try/catch` 兜底：只记 error 日志，不中断对局（可玩性优先）。
4. 超时代翻同样记一条 `FLIP`，参数 `auto=true`，便于复盘时区分人手与系统。
5. 终局只回调一次名次；`rank_no` 按终局持牌数降序，同数按座位号升序（与 `GAME_OVER.rankings` 一致）。

## 10. 战绩查询（全游戏共用）

`GET /api/game/matches/recent?limit=10`（必须登录，userId 只从 token 取）

- 通过 `game_match_player.user_id` 取最近对局 → 批量补 `game_match` 概要，两条 SQL、无需 XML。
- 返回 `{ success, matches:[{ matchNo, gameCode, playerCount, seatNo, rankNo, score, win, status, startTime, endTime }] }`。
- `limit` 收敛到 1–50；对局主体已被清理的行直接跳过；未登录返回 `success=false, error=请先登录`。
- 前端在个人中心"最近对局"卡片展示，`gameCode` 经 `gameAdapters.adapterByGameCode` 反查中文名（`halli_galli → halliGalli`）。

## 11. 交互约束：翻牌与拍铃只能用鼠标

| 动作 | 输入方式 | 鼠标落点 |
|---|---|---|
| `FLIP` | 仅指针按下（`pointerdown`），无键盘路径 | **自己的卡堆** |
| `RING` | 仅指针按下，无快捷键 | **屏幕正中心的铃** |

两条约束服务于同一个反作弊目标：

- 翻牌瞬间鼠标必然在自己面板上，指针不可能同时停在中心的铃上；
- 想抢铃就必须把指针从自己卡堆移到中心，**无法"预瞄铃 + 键盘/按钮翻牌"**；
- 因此前端不注册空格/回车拍铃，也不把翻牌做成独立按钮——卡堆本身就是按钮；
- `RING` 无回合限制，对局中随时可点（出局后禁用），前端只有 120ms 节流，胜负仍由服务端到达顺序裁决。

配套的自座位解析：`GAME_START` 按收件人下发 `room.toVO(收件人)`；其余广播是中立视角，
前端用 `resolveMySeat(seats, mySeatNo, playerName, fallback)` 依次回退，避免 `mySeatNo=null` 导致翻牌永久不可用。
