# 德国心脏病（Halli Galli）接入准备计划

日期：2026-11-21 · 状态：待评审 · 模块：`dicego-games/dicego-halligalli`

## Goal

为"德国心脏病（Halli Galli）多人实时对战"完成开工前的全部准备：规则数据集与校验、命名与模块边界、并发抢铃的仲裁方案、消息协议草案、数据库登记方式、前端接入点与素材方案。准备完成的标准是——后端第一个 Task 开工时不需要再查规则、定协议、想并发模型。

## 现状盘点（2026-11-21 核实）

| 资产 | 状态 | 可直接复用度 |
|---|---|---|
| `dicego-game-common` | `GameRoom`（房间/状态/hostName/AFK/`participantNames`）、`GameRoomManager`（6 位房间号/TTL 清理/关房广播）、`AbstractGameWebSocketHandler`（连接/心跳/AFK/聊天/解散/关房）、`GameMessageType`（房间/聊天/心跳/胜负/ERROR）、`CreateRoomBO.maxPlayers`、`MessageTypeParser` | **高**：房间生命周期、WS 连接、AFK、关房全部现成 |
| `dicego-splendor` | config/controller/domain/enums/engine/handler/manager/mapper/service 九层完整实现 | **高**：目录结构与分层照抄，去掉卡牌缓存与个性化快照 |
| 前端 composables | `useGameWebSocket`（心跳/重连/互踢/关房）、`useRoomEntry`（进房引导）、`useLastRoom`、`useChat`、`useAfk` | **高**：零改动接入 |
| 前端大厅 | `lobby/index.vue` 按 `splendor ? splendorApi : chessApi` 二元分流；`catalog.ts` 静态注册；`rules.ts` 静态规则书 | **中**：第三个游戏会让 if/else 变嵌套，建议先抽适配器（D9） |
| 数据库 | `game_definition` 总表 + `game_match/match_player/action_log` | **高**：登记一行即可，对局落库复用现有表 |
| 规则数据集 | `design/games/halli-galli/{game.yaml,rules.md,journal.md}` + `tests/halli-galli-official.spec.ts` | **已完成**（7 用例全绿） |

### 缺口（公共层没有、必须游戏内实现）

1. **并发抢铃仲裁**：现有模型是"回合制 + 当前行动方校验"，Halli Galli 的 RING 来自任意玩家、任意时刻。
2. **回合超时**：翻牌方挂机/掉线会卡住全场，公共层无计时器。
3. **大厅多游戏分流**：当前二元 if/else 不可持续。

## 规则事实（已定稿）

- 56 张牌 = 4 种水果 × 14 张；水果：苹果 / 柠檬 / 樱桃 / 葡萄（A6 改判，见 journal）。
- 每种水果的牌面数量分布：1 个×5、2 个×3、3 个×3、4 个×2、5 个×1。
- 2–6 人；牌洗匀均分，背面朝上，不得翻看。
- 轮流翻自己牌堆顶一张到自己面前，盖住上一张；**每位玩家只有顶牌计入桌面**。
- 任一时刻，同种水果可见总数**恰好 5** → 任何人可拍铃，最快者收走桌面全部明牌并开始新一轮。
- 6 个不算；不同水果不相加；单张 5 个也算。
- 抢错：给每位仍在场的其他玩家各 1 张。
- 牌堆耗尽无法翻牌 → 出局；明牌留桌继续计数。
- 只剩 2 人后最后一轮抢铃结束；两人残局抢错则对手直接赢走桌面，游戏结束。
- 终局：持牌最多者胜。

数据来源与四处版本分歧的仲裁见 `design/games/halli-galli/journal.md`。

## 与已实现游戏的关键差异

| 维度 | 象棋 | 璀璨宝石 | 德国心脏病 |
|---|---|---|---|
| 行动模型 | 回合制，红黑交替 | 回合制，座位轮转 | **实时并发**：翻牌有轮次，抢铃无轮次 |
| 服务端关键校验 | 走法合法 + 轮次 | 资源/费用/阶段 | **到达顺序** + 当前桌面是否恰好 5 |
| 隐藏信息 | 无 | 预留卡对对手隐藏 | 牌堆内容对所有人隐藏（连自己也不能看） |
| 快照策略 | 全量广播 | 按玩家个性化 | **全量广播**（无人可见他人手牌内容） |
| 状态规模 | 棋盘 90 点 | 90 卡 + 5 资源 + 贵族 | 56 张 + 4 个计数 |
| 延迟敏感度 | 低 | 低 | **极高**（毫秒级决定胜负） |
| 回合超时 | 无 | 无 | **必须有** |

## 设计决策（已拍板 2026-11-21，按推荐口径实现）

- [x] **D1 消息协议 → 自建 `HalliGalliMessageType`**：沿用各游戏自建枚举的既定结论，公共层不加游戏专属值。client→server：`START_GAME / FLIP / RING`；server→client：`GAME_START / STATE_SYNC / FLIP_DONE / RING_RESULT / PLAYER_OUT / GAME_OVER / ACTION_REJECT`。
- [x] **D2 抢铃仲裁 → 房间内串行 + 单调序号**：每条 RING 进入 `synchronized` 方法，分配 `ringNo` 并记录 `receivedAt`；先判当前桌面是否恰好 5，再判是否已被抢走。**以服务端收到顺序为唯一依据**，不采信客户端时间戳（防作弊、防时钟漂移）。
  - 抢对：第一个到达者收牌，广播 `RING_RESULT{ok,winner,cardCount}`，当前翻牌方改为 winner。
  - 抢错：该玩家赔牌，广播 `RING_RESULT{ok:false,ringer,penalty}`。
  - 迟到：桌面已被收走后的 RING **静默丢弃**（不回错误，避免刷屏）。
  - 防抖：同一玩家 200ms 内的重复 RING 忽略（防误触/连点）。
- [x] **D3 牌面数据 → 代码常量，不建专属表**：只有 20 种牌型（4 水果 × 5 数量）且分布是规则常量，无需运营配表；`HalliGalliDeck.build()` 按 5/3/3/2/1 生成 56 张，启动/测试断言 56 张、132 水果面。若未来要做皮肤/扩展，再建 `halli_galli_card`。
- [x] **D4 牌堆耗尽后的抢铃权**：规则存在分歧（见 journal.md A4）。**默认**：轮到自己翻牌且牌堆为空 → 出局，明牌留桌计数；保留配置项 `allowRingAfterExhausted`（默认 false），默认口径对齐 UltraBoardGames / kapiteinspel。
- [x] **D5 回合超时 → 10 秒未翻自动翻**：翻牌本身没有选择权（实体规则也必须翻），超时由服务端代翻并广播 `FLIP_DONE{auto:true}`；UI 显示倒计时。掉线玩家同样适用，避免全场卡死。
- [x] **D6 快照可见性 → 全量广播**：广播每位玩家的牌堆张数、当前顶牌、是否出局、当前翻牌方；**不广播牌堆内容**（本来也无人可见）。不需要 Splendor 的 `broadcastPersonalized`。
- [x] **D7 延迟公平与客户端反馈**：以服务端顺序为准；客户端按下后立即禁用按钮 300ms 并显示"判定中…"，结果回来再恢复。M1 不展示毫秒差（避免争论），可作为后续可选统计。
- [x] **D8 落库 → 复用现有表**：`game_match`（开始/结束/名次）、`game_match_player`（持牌数/名次）、`action_log`（FLIP / RING_OK / RING_WRONG / OUT）。不新建专属卡表（见 D3）。
- [x] **D9 大厅分流重构 → 适配器注册表**：新增 `src/constants/gameAdapters.ts`（或 `composables/useGameApi.ts`），把 `listRooms/createRoom/getRoom/route/gameKey` 收进 `Record<gameKey, adapter>`；`lobby/index.vue` 与 `BackToRoom.vue` 改为查表。**建议在大厅接第三个游戏前先做**，否则 if/else 会继续膨胀。
- [x] **D10 命名约定**（三层各随惯例，实现时不要混）：
  - 前端 catalog key / query 参数：`halliGalli`（camelCase，同 `diceThrone`）
  - DB `game_code` / 设计目录：`halli_galli`（snake_case）
  - REST / WS / 路由段：`halli-galli`（kebab-case）：`/api/halli-galli/**`、`/ws/halli-galli`、`/halli-galli/:roomId`
  - Java 模块：`dicego-games/dicego-halligalli`，包 `org.dromara.halligalli`
- [x] **D11 房间容量 2–6**：`CreateRoomBO.maxPlayers` 已支持；业务层校验 2–6（Splendor 的 2–4 是它自己的限制，不复用）。

## 准备任务分解

### P1 规则数据集与校验 ✅ 2026-11-21 完成

- [x] `design/games/halli-galli/game.yaml`：56 张牌型数据（count=牌型张数，gain=牌面水果数）、回合阶段、终局条件、analysis
- [x] `design/games/halli-galli/rules.md`：玩家可读规则（含常见误区与版本差异）
- [x] `design/games/halli-galli/journal.md`：5 个来源、4 处仲裁（张数/分布/水果命名/出局规则）
- [x] `tools/boardgame-design/tests/halli-galli-official.spec.ts`：7 用例全绿（schema、56 张、每种 14、分布 5/3/3/2/1、单水果面、132 水果面、20 牌型唯一）

### P2 数据库登记 ✅ 2026-11-21 完成

- [x] `script/sql/boardgame_online.sql` 的 `game_definition` 追加：`game_code='halli_galli'`、名称、2–6 人、`sort_no=3`
- [x] 新建增量脚本 `script/sql/2026-11-21-halli-galli-definition.sql`（已带 `ON DUPLICATE KEY UPDATE`，可重复执行）
- [x] 不建专属表（D3）；对局落库复用 `game_match/match_player/action_log`

### P3 消息协议定稿 ✅ 2026-11-21 完成

见 `docs/superpowers/specs/2026-11-21-halli-galli-protocol.md`（REST 5 个端点、WS 3 个上行类型、6 个下行类型、快照结构、抢铃时序、失败码）。

### P4 引擎签名清单（纯逻辑，无 IO）

- `HalliGalliDeck.build()` → `List<HalliGalliCard>`（56 张）
- `countVisible(tableTopCards)` → `Map<Fruit,Integer>`（只算每人顶牌）
- `isRingValid(counts)` → `boolean`（任一种恰好 5）
- `ring(room, playerName, now)` → `RingResult`（ok/罚牌/迟到）
- `flip(room, playerName)` → `FlipResult`（牌、是否触发淘汰）
- `settleCorrect(room, ringer)`（收牌、重置桌面、转移翻牌权）
- `settleWrong(room, ringer)`（按在场人数赔牌、可能淘汰）
- `checkGameOver(room)` → `Optional<Rankings>`（仅剩 2 人且最后一轮完成 / 一人收走全部牌）
- 全部可单测：构造固定牌序，覆盖 4→5、5→6、覆盖导致 5→4、抢错赔牌、牌尽出局、两人残局。

### P5 前端清单与素材方案

- `catalog.ts` 登记 `halliGalli`：`zone: 'party'`、`playable: true`、`players: 6`、机制标签 `['反应', '同时行动']`
- `rules.ts` 增加 `halliGalli` 规则书（复用 `rules.md` 的章节结构）
- `api/halliGalli.ts`：五个 REST 接口 + 类型
- `views/halliGalli/index.vue`：中央桌面（各玩家顶牌环形/网格布局）、大号铃按钮（键盘 Space）、倒计时、抢铃结果浮层、牌堆张数
- 路由 `/halli-galli/:roomId`；`BackToRoom` 增加该游戏；`lobby` 走 D9 适配器
- **素材方案（推荐）**：只生成 4 张水果基础图（苹果/柠檬/樱桃/葡萄），牌面用 CSS 重复排列 1–5 个 + 数字角标。理由：20 张整图成本高、风格易漂移、改数量要重生成。用 `game-art-prompt-pack` 写提示词包：统一风格锚点、纯色底、无阴影、单方向、等距排列；用 `buddy-image-processing` 去底/裁切/转 WebP（与 Splendor 宝石 token 同流程）。

## 里程碑（准备完成后）

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M1 房间与发牌 | 创建/加入/2–6 座位/开局洗牌均分/STATE_SYNC 初始快照 | ✅ 2026-11-21 |
| M2 翻牌与桌面 | FLIP 校验、顶牌覆盖、可见计数、10s 自动翻、FLIP_DONE 广播 | ✅ 2026-11-21 |
| M3 抢铃与结算 | 并发仲裁、RING_RESULT、抢对收牌、抢错赔牌、防抖/迟到 | ✅ 2026-11-21 |
| M4 淘汰与终局 | 牌尽出局、明牌留桌、两人残局特判、牌数排名 | ✅ 2026-11-21 |
| M5 前端与落库 | 对战界面、倒计时、抢铃浮层、大厅适配器（D9）；action_log/战绩落库 | ✅ 界面与落库完成，战绩展示待做 |

## 风险清单

1. **抢铃公平性（最高）**：网络延迟直接决定胜负。必须在协议里写明"服务端接收顺序为准"，UI 上让玩家看到"已按下、判定中"，避免争议。
2. **并发串行化**：多个 RING 同时到达，必须保证只有一个赢家、其余静默丢弃；用房间级锁 + 单调序号，禁止在锁外改状态。
3. **翻牌方挂机**：10s 自动翻是兜底；若玩家断线，仍按超时推进，避免全场等待。
4. **6 人局信息密度**：每人一张顶牌 + 4 种计数，UI 要能在 1080p 内一屏放下；建议顶牌用环形/两行网格，计数用常驻小条。
5. **素材一致性**：4 张水果图必须同风格、同视角、同光照；先出 1 张锁参数，再批量（见技能方法论）。
6. **规则版本差异**：D4 出局时机若选错，终局判定会差一整轮；先按默认口径实现，留开关。

## 技能与工具检查（本轮结论）

| 能力 | 用途 | 结论 |
|---|---|---|
| `game-art-prompt-pack`（技能，已加载） | 写 4 张水果素材的批量提示词包：风格锚点逐字复用、纯色底、无阴影、单方向、位置锚定 | **用**：P5 素材阶段直接按它的 12 条硬规则产出 |
| `buddy-image-processing`（技能） | 生成图去白底、按 bbox 裁切、转 WebP | **用**：与 Splendor 宝石 token 同一套后处理 |
| `ImageGen`（工具） | 实际出图 | **用**：素材阶段 |
| `design-master`（技能） | 对战界面视觉、配色、布局诊断 | 实现阶段（M5）用，准备阶段不需要 |
| `dev-expert-pro`（技能） | 架构、根因调试、审查、测试 | 实现阶段（M2/M3 并发问题）用 |
| `workbuddy-docx` / `workbuddy-pptx` / `tdoc-orchestrator` | 文档/演示 | **不用**：项目文档走 markdown |
| `tencent-xmind` | 脑图 | 可选，不必要 |
| `workbuddy-sheetagent` | 表格 | 不用（牌型表已由 yaml 承载） |
| `workbuddy-finance-skill` 等 | 金融 | 无关 |

> 未发现可直接复用的"桌游实现"技能；项目自带 `design/games` 工作流与 `tools/boardgame-design` 校验体系仍是主路径。

## M-prep 验收标准

1. 六个以上决策点（D1–D11）拍板并记录
2. 数据集校验 7 用例全绿，56/14/分布/132 全部可断言
3. `game_definition` 登记 SQL 可在空库与现有库执行
4. 协议文档含全部消息、字段、失败码、抢铃时序
5. 引擎签名清单可直接转成测试用例
6. 前端接入点（catalog/rules/route/lobby/BackToRoom）全部列明

## Review Focus

- **D2 是本次唯一的架构级决策**：并发仲裁放房间内还是抽公共层？建议先放游戏内，等第二个实时游戏出现再上移。
- **D7 别做客户端时间戳**：看似公平，实则给作弊开门；服务端顺序 + 即时反馈已经够用。
- **D3 不建卡表**：20 种牌型是规则常量，不是内容资产；建表只会增加种子维护成本。
- **D9 先重构再接第三个游戏**：否则大厅三元嵌套，后面每加一个游戏都要改三处。
