# 璀璨宝石（Splendor）接入准备计划

日期：2026-11-20 · 状态：待评审 · 模块：`dicego-games/dicego-splendor`

## Goal

为"璀璨宝石（Splendor）多人对战"完成开工前的全部准备：规则数据集、M1 表结构落盘、消息协议定稿、可复用架构核对与设计决策拍板。准备完成的标准是——后端第一个 Task 开工时不需要再做任何"查规则、定协议、建表"类工作。

## 现状盘点（2026-11-20 核实）

| 资产 | 状态 |
|---|---|
| `dicego-games/dicego-splendor` | 骨架：pom（依赖 dicego-game-common）+ `org.dromara.dicego` package-info，无业务代码 |
| `dicego-game-common` 公共层 | `GameRoom`（roomId/status/连接宽限/`onConnectedChanged` 钩子，**无座位概念**）、`GameRoomManager<T>`（UUID 6 位房间号、分级清理）、`AbstractGameWebSocketHandler`（连接生命周期/UTF-8 参数）、`GameMessageType`（含象棋专属 MOVE/UNDO/REMATCH 值）、`PlayerColor`（**仅红黑双色，象棋语义**）、RoomVO/GameMessage/CreateRoomBO/SeatBO |
| 象棋参照实现 | Controller(REST) + WS Config + Room(extends GameRoom) + Engine 四层结构，可直接照此搭 Splendor |
| `design/samples/splendor.yaml` | **抽象模拟模型**（五色合并为单 gem、卡牌费用为约值、自注 confidence: medium）——不能当对局数据源，仅机制参考 |
| M1 数据库设计 | 9 张业务表已设计并建入 `boardgame_online` 库（游戏/卡牌/贵族/房间/座位/比赛/比赛玩家/行动日志等），**SQL 脚本未进 `script/sql/`，表结构没有仓库内单一事实来源** |
| 前端 | `catalog.ts` 已有 splendor 卡牌区占位（敬请期待）；`api/dicego.ts` 占位；ChessBoard 为象棋专用 |

## 设计决策（P0，已全部拍板 2026-11-20）

- [x] **D1 消息协议归属 → 各游戏自建枚举**：公共 `GameMessageType` 只留房间/聊天/心跳/胜负类值，象棋专属值（MOVE/MOVE_VALID/MOVE_INVALID/UNDO/UNDO_REQUEST/REMATCH/RESIGN）迁移到 xiangqi 模块，Splendor 自建 `SplendorMessageType`（take_gems/buy_card/reserve_card/noble_visit/state_sync 等）。迁移后须全量回归象棋 62 用例。
- [x] **D2 座位模型 → Splendor 内部 seatNo(1-4)**：公共层 `PlayerColor` 保持象棋专用不动；Splendor 用座位号 + 房主开局。
- [x] **D3 房间容量 → 公共层加 maxPlayers**：`CreateRoomBO` 增加可选 `maxPlayers` 字段，象棋默认 2、Splendor 房主可选 2-4，后续游戏通用。
- [x] **D4 卡牌/贵族数据 → DB 种子 + 启动加载**：官方卡表录入 M1 的 card/noble 表（`script/sql/boardgame_online.sql` 含种子），应用启动加载进内存缓存，每局从缓存发牌。
- [x] **D5 预留卡可见性 → 按玩家定制快照**：预留卡卡面对对手隐藏，快照按收件人过滤（本人可见自己的预留明细，对手只见数量）；公共 `GameMessage` 广播路径需确认支持按收件人差异化。
- [x] **D6 持久化时机 → 每步实时落库**：每个行动（拿宝石/买卡/预留）实时写 action_log（按 M1 表设计），掉线重连与对局回放有据可查。

## 准备任务分解

### P1 真实规则数据集（设计工作区）✅ 已完成 2026-11-20

- [x] 按 `design/games/` 规范建 `design/games/splendor/`：`game.yaml`（真实数值替换 samples 抽象值）+ `rules.md`（回合四选一、贵族拜访、弃宝石、15VP 终局的流程散文）+ `journal.md`
- [x] 逐张录入官方卡表：一级 40 / 二级 30 / 三级 20 发展卡（每张：五色费用、VP、加成色）+ 贵族 10 张（需求组合、3VP）。数据经 5 个开源 Splendor 实现（daniman/dsmiller95/BreckEmert/grandeur/k1nz）程序化交叉比对，4 处源间分歧按结构不变量仲裁（详见 journal.md）
- [x] 校验脚本（放 `tools/boardgame-design` 既有 vitest 体系）：`tests/splendor-official.spec.ts` 11 用例全绿——张数 40/30/20+10、每卡单色费用 ≤7 且为正整数、加成色合法、贵族需求=两色 4+4（合计8）或三色 3+3+3（合计9）、VP 合计对照官方 **卡牌 140（一级 5/二级 55/三级 80）+ 贵族 30**（原文档"100VP"系笔误，已修正）

### P2 M1 SQL 落盘（仓库单一事实来源）✅ 2026-11-20 完成

- [x] 从 `boardgame_online` 库导出现状 9 表 DDL → `script/sql/boardgame_online.sql`（雪花 bigint 主键、del_flag、无租户，遵循 M1 约定）
- [x] 卡牌/贵族种子 INSERT 进同一脚本（由 game.yaml 生成器产出，杜绝人工转录；card_code/noble_code 与 game.yaml key 一致）
- [x] 空库执行演练一遍成功（临时库 9 表建成、种子 2/90/10 完整后清理）
- [x] 结构补充决策（本次拍板）：`game_definition` 总表新增 `icon` / `description` / `sort_no` 大厅展示字段；象棋登记进总表（game_code='xiangqi'，2/2 人，对局本身不落库）；现有库已同步 ALTER + 灌种子
- 表结构分层约定：平台公共表 `game_` 前缀（总表/房间/座位/对局/战绩/统计/行动日志），游戏专属表 `<game>_` 前缀（splendor_card / splendor_noble），新游戏照此模式扩展

### P3 消息协议定稿 ✅ 2026-11-20 完成

- [x] 依赖 D1/D5 结论，写 `SplendorMessageType` 清单 + 每条消息字段 schema（行动请求 / 校验失败原因码 / 全量快照 / 增量事件）→ `docs/superpowers/specs/2026-11-20-splendor-protocol.md`
- [x] 快照 schema：桌面（三级各 4 翻牌 + 牌堆余量）、宝石供给（2人4/色、3人5、4人7 + 黄金 5）、每位玩家（宝石持有、加成、VP、公开预留数、**自己的预留卡明细仅本人可见**）、贵族区、当前回合方
- [x] 确认公共 `GameMessage` 广播路径：原基类仅支持同消息群发，已补 `broadcastPersonalized`（按收件人定制，D5 依赖）
- [x] 象棋回归：若选 D1-B，先跑通"迁移象棋专属枚举值"并全量测试
  （2026-11-20 已完成：公共层剔除 MOVE/MOVE_VALID/MOVE_INVALID/UNDO/UNDO_REQUEST/REMATCH/RESIGN → 新建 `XiangqiMessageType`；`PlayerColor`/`RoomVO`→`XiangqiRoomVO`/`SeatBO` 一并迁入 xiangqi；`GameMessage.type` 字符串化（wire 兼容）；基类 `handleGameMessage` 签名改 String，错误码新增公共 `ERROR` 替代原 MOVE_INVALID 挪用）

### P4 引擎接口清单（签名先行，实现属开工后）

- `SplendorEngine.validateTakeGems(player, colors[])` — 三异色或双拿同色（供给≥4）
- `SplendorEngine.validateBuyCard(player, cardId, useGold)` — 费用减加成、黄金补差
- `SplendorEngine.validateReserve(player, cardId)` — 预留 + 领黄金（若有）
- `applyNobleVisit(player)` — 加成达标自动触发、每回合至多 1、不耗行动
- `checkDiscard(player)` — 回合末手牌 >10 强制弃
- `checkEndRound()` — 任一玩家 ≥15VP，完成本轮后终局、平 VP 比较少**发展卡数**（官方规则为比卡数而非比行动数，原文档有误已修正）
- 纯逻辑无 IO，全部可单测（对齐 XiangqiEngine 风格）

### P5 前端准备清单（实施属开工后）

- `catalog.ts` splendor 条目置可玩（route `?zone=card&game=splendor`）
- `SplendorBoard` 组件拆解草案：宝石堆区 / 三级卡行 / 贵族区 / 玩家面板（手牌宝石、加成条、预留区）
- `api/dicego.ts` 按 P3 协议补全签名；复用 `useRoom`/`useChat`

## 里程碑（准备完成后）

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M1 房间走通 | 创建/加入/座位(2-4)/开局发牌，复用公共层，WS 连通 | ✅ 2026-11-20 完成（见下） |
| M2 核心回合 | 拿宝石/买卡/预留 + 快照同步 + 校验失败提示 | 待开工 |
| M3 完整规则 | 贵族拜访、弃宝石、15VP 终局判胜 | 待开工 |
| M4 前端界面 | SplendorBoard 全量交互 + 联调 | 待开工 |
| M5 持久化 | match / match_player / action_log 落库（按 D6 结论） | 待开工 |

### M1 完成记录（2026-11-20）

- 公共层扩展：`CreateRoomBO.maxPlayers`（D3）、`GameRoomManager.createRoom(name, factory)` 按次工厂重载、
  `AbstractGameWebSocketHandler.broadcastPersonalized`（D5 按收件人差异化广播）、`GameRoom.isConnected`
- dicego-splendor 模块（参照 xiangqi 四层结构）：`GemColor`/`SplendorMessageType` 枚举、
  card/noble Entity+Mapper+启动缓存（D4，fail-fast 校验 40/30/20+10）、
  `SplendorBoard`（seed 驱动发牌+个性化快照）、`SplendorRoom`（座位 1-4/房主开局）、
  `SplendorController`（REST 房间生命周期）、`SplendorWebSocketHandler`（/ws/splendor，START_GAME + STATE_SYNC）
- admin 依赖 dicego-splendor；`application.yml` 鉴权白名单加 `/api/splendor/**`、`/ws/splendor/**`
- 测试：模块 15 用例全绿（发牌 5 + 房间 7 + REST 3），公共层 16 + 象棋 62 回归全绿
- 全链路冒烟（jar 启动、18080 端口、Node WS 客户端）：建房→入座→非房主拒绝(NOT_HOST)→
  房主开局→GAME_START+STATE_SYNC→快照校验（90 卡真实数据/供给 4+金5/牌堆 36-26-16/贵族 3 张/D5 可见性）全部通过

## M-prep 验收标准

1. P0 六个决策点全部拍板并记录在本文档
2. 数据集校验脚本全绿（张数、费用、加成、贵族组合）
3. `script/sql/boardgame_online.sql` 空库可执行，卡牌/贵族种子齐全
4. 消息协议文档含全部类型与字段，快照可见性规则明确
5. 象棋既有测试（62 用例）在协议改动后（若 D1-B）仍全绿

## Review Focus（评审时重点看）

- D1 是本次唯一的架构级决策：公共层污染 vs 一次性迁移成本，想清楚再动
- Splendor 预留卡隐藏性（D5）是快照设计与象棋最大的差异点，别照抄象棋全量广播
- `samples/splendor.yaml` 的数值仅供模拟参考，P1 必须重新录官方卡表，禁止从 samples 复制费用
