# 大厅桌游分区（三级导航）设计

日期：2026-11-19 · 状态：待批准 · 范围：仅前端 `boardgame-ui`

## Intent / Why

DiceGo 目前只有象棋一个可玩游戏，大厅是平铺的"创建房间/房间号/房间列表"三卡，没有游戏分类概念。用户要在大厅本身建立桌游分区导航：**7 大分区 + 机制标签，想玩什么就点进去**——点分区看该区游戏，点游戏进入开房/加房层。平台后续游戏增多时目录骨架已就位。同时硬要求：**任何层级都能返回大厅**（房间页已有三处返回按钮，实测可用，不动）。

## 用户已决定

- 分类放在**大厅**（不是首页、不是独立路由页）。
- 交互："想玩什么就点进去"——分区 → 游戏 → 房间操作，逐层点击。
- 导航建法选 **A：大厅内三级 + URL query 记忆**（见 Approaches）。
- 返回大厅是硬要求。
- **4 个占位游戏**（璀璨宝石/王权骰铸/阿瓦隆/伯明翰）："先把位子占着，以后再植入"——本期仅目录条目（敬请期待+用户给定机制标签），植入是独立后续工作。

## Goals

1. 大厅首屏（level 0）展示 7 张分区卡：名称 + 该区机制标签（原文照录，见 Catalog）。
2. level 1（`?zone=<key>`）：该区游戏卡列表；可玩点击进 level 2，未做显示"敬请期待"不可点；无游戏分区显示筹备中空态 + 机制标签。
3. level 2（`?zone=<key>&game=<key>`）：现有三卡（创建房间 / 输入房间号加入 / 房间列表 5s 自动刷新）原逻辑搬入，作为"该游戏的房间层"。
4. level 1/2 顶栏有「← 返回大厅」回 level 0；浏览器前进/后退沿 query 历史逐层走。
5. 刷新/分享 URL 不丢层级（query 驱动，非法 query 回落）。
6. level 0 保留「输入房间号加入」快捷卡（直达 `/game/:id`，不必先选分区）。
7. 首页象棋卡深链直达 level 2（`/lobby?zone=abstract&game=xiangqi`）；首页其余外观不变。
8. 登出按钮仍只出现在大厅（顶栏，所有层级保留）。

## Non-goals / Anti-goals

- **不做新游戏**、不编造游戏名/机制；目录条目=象棋(可玩)+围棋/五子棋+用户指定 4 占位（均敬请期待）。占位游戏的**植入不在本期**。
- **不改后端**：房间不加 zone 字段、不做分区过滤（Approach C 已排除）、不动 WS/座位逻辑。
- 不新增路由（Approach B 已排除）；不改首页视觉结构（仅象棋卡跳转目标）。
- 不丢既有大厅行为：列表 5s 轮询 + focus/visibility 静默刷新、手动刷新报错 toast、创建空名 guard、加入前 getRoom 校验、登出委托 store。
- 失败即反目标：轮询在离开 level 2 后仍跑（泄漏）、非法 query 白屏、返回按钮缺失、既有测试红。
- 不 commit（用户约定）；不动验证码开关；不碰未提交的 captcha/logo 修复。

## Constraints

- 栈沿用：Vue3 setup + Element Plus + vue-router + pinia + vitest/@vue/test-utils（jsdom，EP inline 错误需 `vi.waitFor`）。
- 后端重启独立 pending（数据源已切 `boardgame_online` + WS 中文名 UTF-8 修复），本功能**不依赖**重启；live 验证用现运行后端。
- TDD red-green：每个行为先写失败测试再实现；全量 `npm test` + `npm run build` 绿后才算完。
- 目录数据为前端静态常量（typed），不进数据库（`game_definition` 等空表无代码引用，保持占位）。

## Approaches

- **A（选定，用户批准）大厅内三级 + query 记忆**：单路由 `/lobby`，`route.query.zone/game` 驱动层级；拆 `ZoneGrid`/`GameList` 子组件控制 lobby 体积。代价：lobby 编排逻辑集中、既有 lobby.spec 需按层级重写挂载 query。收益：零新路由/守卫、刷新分享保位置、改动面最小。
- **B 独立路由 `/lobby/:zone/:game`**：每层独立视图隔离干净；但为 1 个可玩游戏加 3 路由+守卫+测试，"房间号加入"放哪层别扭——过度设计，排除。
- **C 分区作大厅页签过滤房间列表**：无目录层、6 区空页签，不符合"点进去玩"，排除。

## Catalog（原文照录用户分区）

`src/constants/catalog.ts`（新，typed 常量 + 查询 helper）：

| key | 名称 | 机制标签 |
|---|---|---|
| party | 轻策/派对区 | 同时行动、反应、猜词、吹牛、轮抽轻量版 |
| family | 家庭/聚会区 | 成套收集、板块放置、骰子轻策、交易谈判 |
| strategy | 策略核心区 | 工人放置、引擎构建、区域控制、路线构建、行动点分配 |
| card | 卡牌区 | 手牌管理、牌库构建、卡牌驱动、轮抽、多用途卡牌 |
| narrative | 美式/叙事区 | 合作、传承、叙事、隐藏身份、战斗结算 |
| abstract | 抽象/双人区 | 抽象棋、模式构建、逻辑推理 |
| heavy | 硬核/重策区 | 资源管理+引擎+路线+推轨的复合体 |

游戏条目（`mechanisms` 可选；有则在游戏卡面展示）：

| key | 名称 | zone | playable | 机制标签（用户原文） |
|---|---|---|---|---|
| xiangqi | 象棋 | abstract | true | —（2 人，desc 楚河汉界，运筹帷幄） |
| go | 围棋 | abstract | false | — |
| gomoku | 五子棋 | abstract | false | — |
| splendor | 璀璨宝石 Splendor | card | false | 引擎构建、成套收集 |
| diceThrone | 王权骰铸 Dice Throne | narrative | false | 骰子驱动、角色能力（不对称）、卡牌辅助 |
| avalon | 阿瓦隆 Avalon | narrative | false | 隐藏身份、推理/演绎、投票 |
| brass | 伯明翰 Brass: Birmingham | heavy | false | 网络构建、经济/资源管理、手牌管理（轻卡驱） |

**我的裁决（类名→7 分区映射）**：用户给的类名与 7 分区不同词，按机制吻合度映射——卡牌/手牌类→**卡牌区**（ Splendor ）；对抗/冲突类→**美式/叙事区**（该区含隐藏身份+战斗结算，容纳 Avalon 与 Dice Throne）；策略/规划类→**硬核/重策区**（Brass 的"资源管理+网络+引擎"复合体重策吻合该区定义；策略核心区留给中量级工人放置/行动点类）。批准时可纠正。

分区层机制标签仍原文照录（卡面与 level 1 头部）；游戏级标签**只用用户给定的**，不编造。库中 `splendor_card`/`splendor_noble` 空表是未来植入的既有钩子，本期不动。

## 层级与导航规则

- 层级推导：`game` 合法（**存在于目录 && 属于该 zone && playable=true**）→ 2；否则 `zone` 合法 → 1；否则 → 0。非法/缺失 query 静默回落，不报错、不改写 URL。
  - **我的裁决（builder check #1）**：目录已知但不可玩的 game（如 `?zone=abstract&game=go`）回落 **level 1**——该区游戏列表里该卡显示"敬请期待"。理由：level 2 三卡是象棋专属后端能力，挂在未实现游戏名下即错误呈现；与"未做不可点"意图一致。UI 路径到不了该状态（不可玩卡不 emit），但手输/分享 URL 与浏览器历史可以。
- 层间跳转用 `router.push`（带/清 query），浏览器 back 逐层回退；「← 返回大厅」= push `/lobby`（无 query）。
- level 0：顶栏(logo+昵称+登出) + ZoneGrid + 房间号加入卡。
- level 1：顶栏 + 返回按钮 + 分区头部(名称+标签) + GameList / 空态。
- level 2：顶栏 + 返回按钮 + 现有三卡（逻辑不变）。
- 轮询/focus/visibility 生命周期绑定 level 2：进入启动、离开清理、unmount 清理（watch 层级）。
- 组件：`components/ZoneGrid.vue`（props zones，emit `select(zoneKey)`）、`components/GameList.vue`（props zone，emit `select(gameKey)`；不可玩卡不 emit）；lobby 负责编排与 query。

## 测试计划

- 新 `catalog.spec.ts`：7 区 key/名称/标签原文、象棋 playable 且属 abstract、go/gomoku 不可玩、4 占位游戏的 zone 映射与机制标签原文、helper 非法 key 回落。
- 新 `ZoneGrid.spec.ts`：渲染 7 卡含标签、点击 emit select。
- 新 `GameList.spec.ts`：象棋卡 emit、敬请期待卡不 emit、有机制标签的游戏卡渲染标签、空分区空态（party/family/strategy 仍无游戏）。
- `lobby.spec.ts` 重写挂载方式：level 0 无创建卡/有分区卡与房间号卡；`?zone=abstract` 见游戏卡；`?zone=abstract&game=xiangqi` 见三卡且轮询跑；离开 level 2 轮询停；非法 query 回落 level 0；返回按钮清 query；登出/品牌/加入校验/创建 guard/静默轮询等既有用例改挂对应层级后保持断言。
- `home.spec.ts` 增：象棋卡 push 深链 query。
- 验证：全量 vitest + build 为硬门槛；另用 **Python Playwright 一次性脚本**（沿用本会话既有做法，存 `logs/`，**不新增 @playwright/test 依赖/配置/e2e 目录**——builder check #2 澄清）实走 0→1→2→返回→创建房间→列表刷新→房间页返回大厅，截图目检。实测产生的空房由既有清理规则（WAITING 无人 30s 宽限）自动回收，无需手动清理。
- 回落用例补：`?zone=abstract&game=go` → level 1（见层级推导裁决）。

## 留给 builder

分区 emoji/卡面排版/scss、空态文案措辞、query 校验 helper 的具体签名与位置、测试 helper 复用方式、level 2 头部是否显示游戏名（建议显示）。
