<p align="center">
  <img src="boardgame-ui/src/assets/logo-wordmark.png" width="280" alt="DiceGo" />
</p>

<h1 align="center">DiceGo · 线上桌游对战平台</h1>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F.svg" alt="Spring Boot 4.1"></a>
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/JDK-21-blue.svg" alt="JDK 21"></a>
  <a href="https://cn.vuejs.org/"><img src="https://img.shields.io/badge/Vue-3-4FC08D.svg" alt="Vue 3"></a>
  <a href="https://vitejs.dev/"><img src="https://img.shields.io/badge/Vite-5-646CFF.svg" alt="Vite 5"></a>
  <a href="https://vitest.dev/"><img src="https://img.shields.io/badge/Vitest-292%20passing-2ECC71.svg" alt="Vitest"></a>
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="MIT License"></a>
</p>

DiceGo 是一个面向多人实时对战的**线上桌游平台**：登录后进入大厅，按"分区 → 游戏 → 房间大厅"逐层点进去，创建或加入房间，通过 WebSocket 与好友实时对局和聊天。

**6 大桌游分区收录 10 款游戏，其中 5 款已可开房间对战**——从两人对弈的中国象棋，到 8 人互相算计的印加宝藏，覆盖抽象棋类、引擎构筑、即时反应、骰子竞速与推运气博弈五种玩法类型。

平台采用**游戏模块化架构**：每款桌游是一个独立 Maven 模块，公共的房间管理、WebSocket 连接、聊天、对局落库沉淀在 `game-common` 公共层，新增一款游戏只需实现规则引擎本身。除对战功能外，还内建**桌游设计工作台**（规则结构化建模、数值模拟、规则书导出），设计稿可直接反哺平台的游戏接入。

## 🎮 游戏目录

| 游戏 | 分区 | 人数 | 状态 | 玩法亮点 |
|---|---|---|---|---|
| 🀄 中国象棋 | 抽象/双人 | 2 | ✅ 已上线 | 完整走子规则引擎、将军判定、悔棋、认输、重新开局 |
| 💎 璀璨宝石 Splendor | 卡牌构筑 | 2–4 | ✅ 已上线 | 宝石/发展卡/贵族全流程，预留卡隐藏视角 |
| 🔔 德国心脏病 Halli Galli | 派对/聚会 | 2–6 | ✅ 已上线 | 实时抢铃：服务端到达顺序仲裁、10s 超时代翻、抢错赔牌；四果图标 + 合成音效 |
| 🏃 胡闹运动会 Magical Athlete | 派对/聚会 | 2–6 | ✅ 已上线 | 蛇形轮抽 4 名选手 + 四场掷骰竞速；20+ 种能力（香蕉绊人、飞艇传送、观众起哄…），选手图鉴可查 |
| 🗿 印加宝藏 Diamant | 派对/聚会 | 3–8 | ✅ 已上线 | 5 轮探险"见好就收"：翻牌平分宝石、灾难二现团灭、秘密同时暗选撤退、独吞/平分余数结算、神器计分 |
| ♟️ 围棋 / 五子棋 | 抽象/双人 | 2 | 🚧 敬请期待 | 已占位 |
| 🎲 王权骰铸 Dice Throne | 叙事/身份 | 2–6 | 🚧 敬请期待 | 已占位 |
| 🎭 阿瓦隆 Avalon | 叙事/身份 | 5–10 | 🚧 敬请期待 | 已占位 |
| 🏭 伯明翰 Brass: Birmingham | 策略/重策 | 2–4 | 🚧 敬请期待 | 已占位 |

**印加宝藏 Diamant（本期新上线）** 的对局引擎严格按官方规则实现：30 张探索牌（15 宝藏 + 5 种灾难各 3 张）+ 每轮洗入 1 张神器牌；宝藏按在场人数平分、余数留在牌上；同种灾难第二次出现即团灭，引发牌永久移出游戏；翻牌由服务端自动推进，玩家只做"继续/撤退"秘密暗选（10s 超时视为继续），1 人撤退独吞全部余数与神器、多人同撤平分且神器作废；他人宝石以"帐篷"遮挡，终局按宝石 + 神器先后计分（前 3 张各 5 分、第 4/5 张各 10 分）。全流程播报采用情景化文案，对局落库写入战绩。

## ✨ 核心功能

| 功能 | 说明 |
|---|---|
| 用户体系 | 注册 / 登录（图形验证码）、Sa-Token + JWT 会话、个人资料、头像上传 |
| 大厅导航 | 6 大分区 + 机制标签 + 游戏海报剪影；URL query 记忆层级，刷新/分享不丢位置；离开对局页后"返回房间"悬浮卡一键回去 |
| 房间系统 | 创建房间 / 房间号快捷加入 / 房间列表 5s 轮询；按等待/对局/结束状态分级自动清理 |
| 实时对局 | WebSocket 长连接：走子、聊天、心跳；断线重连不误清在线状态，同名玩家跨房间不互踢 |
| 对局落库与战绩 | `game_match` / `game_match_player` / `game_action_log` 全游戏共用；`game_player_stat` 按真实账号累计战绩，个人中心展示"最近对局" |
| 📮 消息中心 | 站内通知 / 玩家私信 / 意见反馈，未读角标；管理员可查看全体意见并标记处理 |
| 🛠 管理员体系 | 平台超级管理员名单（前端配置），管理员视角的消息中心与反馈处理 |
| 🎲 桌游设计工作台 | 前端 `/design` 页面 + `tools/boardgame-design` 工具包：`game.yaml` 结构化规则建模、平衡性模拟、规则书与植入要点导出 |

## 🧩 架构

```mermaid
graph LR
    UI["boardgame-ui<br/>Vue3 + TS + Element Plus"] -->|REST /auth /system| Admin["ruoyi-admin<br/>Spring Boot 启动模块"]
    Admin --> System["ruoyi-system 等<br/>框架基础模块"]
    subgraph Game["dicego-games 游戏模块群（可插拔）"]
        Common["dicego-game-common<br/>房间/WS/聊天/落库公共层"]
        Common --> Xiangqi["dicego-xiangqi<br/>中国象棋"]
        Common --> Splendor["dicego-splendor<br/>璀璨宝石"]
        Common --> HalliGalli["dicego-halligalli<br/>德国心脏病"]
        Common --> MagicalAthlete["dicego-magicalathlete<br/>胡闹运动会"]
        Common --> IncanGold["dicego-incangold<br/>印加宝藏"]
    end
    UI -->|WebSocket /ws/*| Common
    DB[("MySQL<br/>game_match / player_stat ...")]
    Common --> DB
    Tools["tools/boardgame-design<br/>规则建模/模拟工具包"] -.生成设计稿.-> Common
```

**游戏模块分层**：`GameRoom` / `GameRoomManager` 泛型基类负责房间生命周期与定时清理，`AbstractGameWebSocketHandler` 统一 WS 连接管理与消息协议，`game-common` 的 match 包统一对局落库；具体游戏模块只实现规则引擎与座位逻辑，互不感知——印加宝藏整模块（引擎 + 房间 + WS + 落库）即按此模式新增，未改动任何公共层行为。

**关键模块一览**

| 模块 | 职责 |
|---|---|
| `ruoyi-admin` | 后端启动入口（Jetty :8080），聚合所有模块 |
| `dicego-games/dicego-game-common` | 游戏公共层：房间生命周期、WebSocket 基类、聊天、领域对象、对局落库（match 包） |
| `dicego-games/dicego-xiangqi` | 中国象棋：走子引擎、座位、悔棋/认输/重开 |
| `dicego-games/dicego-splendor` | 璀璨宝石：卡牌/贵族缓存、回合引擎、个性化快照 |
| `dicego-games/dicego-halligalli` | 德国心脏病：56 张牌引擎、并发抢铃仲裁、超时代翻 |
| `dicego-games/dicego-magicalathlete` | 胡闹运动会：轮抽 + 四场竞速、20+ 能力事件系统 |
| `dicego-games/dicego-incangold` | 印加宝藏：纯函数规则引擎（5 轮状态机）、秘密暗选、帐篷视角、战绩落库 |
| `boardgame-ui` | 前端 SPA：登录注册、大厅导航、对局页、消息中心、设计工作台 |
| `tools/boardgame-design` | 桌游规则结构化建模（TypeScript schema）与模拟工具包 |
| `design/` | 设计工作区：每款游戏 `game.yaml`/规则书/模拟报告；`samples/` 收录 7 款成熟桌游的结构化拆解 |

## 🛠 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 4.1 · RuoYi-Vue-Plus 6.0 脚手架 · JDK 21 · Jetty |
| ORM / 数据库 | MyBatis-Plus · MySQL · 雪花 ID · 逻辑删除 |
| 认证 / 权限 | Sa-Token + JWT · Redis 会话（Redisson） |
| 接口文档 | springdoc-openapi（`/swagger-ui/index.html`） |
| 实时通信 | Spring WebSocket（自研消息协议 + 心跳 + 连接生命周期管理） |
| 前端 | Vue 3 · TypeScript · Element Plus · Pinia · Vue Router · Sass |
| 构建 / 测试 | Vite 5 · vue-tsc · Vitest（前端 292 用例）· Maven 多模块 |
| 工程化 | IDEA 复合运行配置一键起前后端 · vitest 全量回归 · 规格驱动开发（docs/superpowers） |

## 🚀 快速开始

**环境要求**：JDK 21+、Node.js 18+、MySQL 8、Redis 6+

```bash
# 1. 初始化数据库（RuoYi 基础库 + boardgame_online 业务库）
#    全新安装执行 script/sql/boardgame_online.sql 即可；
#    已有库升级按日期执行增量脚本（如 2026-11-23-incan-gold-definition.sql）

# 2. 启动后端（:8080）
mvnw -pl ruoyi-admin -am spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 启动前端（:5173，代理 /api /auth /system /ws → 8080）
cd boardgame-ui
npm install
npm run dev
```

打开 http://localhost:5173 注册登录即可进入大厅。

> **IDEA 用户**：仓库内置三个共享运行配置——`DiceGo Backend`、`DiceGo Frontend`、`DiceGo All`（复合配置）。选 `DiceGo All` 点一次运行，前后端同时启动，控制台分别输出访问地址。

## 📁 目录结构

```
BoardGame_Online
├── ruoyi-admin              # 启动模块（DromaraApplication）
├── ruoyi-api                # 远程调用 API 定义
├── ruoyi-common             # 通用组件（core/web/mybatis/redis/satoken...）
├── dicego-games             # 游戏模块（每款桌游一个子模块）
│   ├── dicego-game-common   # 游戏公共层：房间/WS/聊天/落库
│   ├── dicego-xiangqi       # 中国象棋（可玩）
│   ├── dicego-splendor      # 璀璨宝石（可玩）
│   ├── dicego-halligalli    # 德国心脏病（可玩）
│   ├── dicego-magicalathlete# 胡闹运动会（可玩）
│   └── dicego-incangold     # 印加宝藏（可玩）
├── ruoyi-modules            # 框架基础模块（system/gen/workflow 等）
├── ruoyi-extend             # 可选扩展（监控、任务调度）
├── boardgame-ui             # Vue3 前端（Vite + TS + Vitest）
├── tools/boardgame-design   # 桌游设计工具包（规则 schema/模拟/导出）
├── design                   # 设计工作区 + 样本库 + 落地页
├── docs/superpowers         # 规格与实施计划文档（spec-driven）
└── script                   # SQL / Docker 等脚本
```

## 🗺 路线图

- [x] 用户体系 + 大厅分区导航
- [x] 游戏模块化拆分（game-common 公共层，游戏可插拔）
- [x] 中国象棋多人实时对战
- [x] 桌游设计工作台（建模 + 模拟 + 导出）
- [x] 璀璨宝石（Splendor）多人对战
- [x] 德国心脏病（Halli Galli）实时抢铃对战
- [x] 胡闹运动会（Magical Athlete）轮抽 + 掷骰竞速对战
- [x] 印加宝藏（Diamant）推运气暗选对战
- [x] 对局落库（game_match / game_match_player / game_action_log / game_player_stat，全游戏共用）
- [x] 个人中心"最近对局"战绩
- [x] 消息中心 + 意见反馈 + 管理员体系
- [ ] 接入更多占位分区游戏（围棋 / 五子棋 / 王权骰铸 / 阿瓦隆 / 伯明翰）

## 🙏 致谢

- [RuoYi-Vue-Plus](https://gitee.com/dromara/RuoYi-Vue-Plus)：本项目基于其 6.0 版本脚手架构建，在其用户/权限/基础设施之上实现游戏业务模块。

## License

[MIT](./LICENSE)
