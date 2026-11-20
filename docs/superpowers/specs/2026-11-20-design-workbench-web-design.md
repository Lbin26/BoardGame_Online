# 桌游设计助手·网页工作台（无状态 v1）设计文档

日期：2026-11-20
状态：待批准
上游：`docs/superpowers/specs/2026-11-20-boardgame-design-agent-tools-design.md`（设计助手 agent 工具链，已交付）

## 意图与背景

设计助手 agent（`/design-boardgame` 技能 + `tools/boardgame-design/` 数值工具箱 + `design/samples/` 样本库）已建成，但只能在 Claude Code 里用。用户要求：**在大厅显示入口，供网站用户在浏览器里使用，将来部署到服务器**。

对话式设计（出创意、打磨点子、remix 样本）依赖大模型，留在 Claude Code；网页端定位是**数值验证台 + 资料浏览**——把工具箱的模拟/校验能力包成 HTTP 接口，配一个工作台页面。

用户已确认的决策：
1. **B 方案**：入口 + 网页模拟工作台（不是纯指南页）。
2. **无状态 v1**：不存任何用户资料，跑完即走，报告可下载；工作区存档是二期。
3. **部署到服务器 = 本机当服务器**（用户确认）：这台 Windows 机器就是部署目标，不做镜像远程交付。现有 compose 的 host 网络模式仅适用于 Linux，本机不沿用。
4. **项目启动时在控制台输出工作台网址**。

## 目标

- G1 大厅首页（level 0）出现「桌游设计助手」入口卡片，点击进入 `/design` 工作台。
- G2 工作台可浏览 7 份样本档案（结构/机制/whyFun/remixable/pitfalls/置信度）与 42 条机制库（五要素）。
- G3 工作台可跑全部五类模拟（dice/cards/economy/conflict/sweep）：粘贴/编辑 game.yaml + sim 配置 → 运行 → 页面渲染报告（标量表 + 复杂结果块）→ 下载 md/json。
- G4 工作台可校验游戏文档（粘贴 game.yaml → 错误列表或 OK）。
- G5 一键预置：选样本或示例配置，自动填入编辑器，降低上手门槛。**预置裁定（builder check Q3）**：xiangqi/go/gomoku/avalon 四份样本只有 scoring、无可模拟子系统——它们的预置 = 样本 game.yaml 原样 + 一份**明确标注「演示数据，非样本数值」**的演示 sim 配置（经 overrides 注入演示子系统）；splendor/diceThrone/brass 用样本自身数值的可跑配置。样本 yaml 文件一律不改。演示配置的产物在 UI 与报告配置回显中均可见来源，不构成样本平衡证据。
- G6 Node 服务与前端 dev 启动时，控制台打印工作台网址。
- G7 本机一键生产运行：构建前端 + 编译服务后，**Node 单进程（8090）托管一切**——前端静态文件（vite build 产物）、`/design-api`、以及把既有 `/api` `/auth` `/system` `/ws`（含 WebSocket upgrade）反代到 Java 后端 8080。启动控制台打印 `http://localhost:8090/design`。dev 模式不变（vite 5173 + proxy）。Docker/compose/nginx 接线移出 v1（将来迁 Linux 服务器再启用）。

## 非目标

- 网页端对话式 AI 设计（无 LLM 服务）。
- 用户工作区/存档/历史报告（二期）。
- 设计 API 的账号鉴权（页面本身沿用大厅登录门槛；API 无状态、不触用户数据）。
- 导入已有桌游（另有 import-boardgame 技能）。
- 改动象棋后端（ruoyi Java）与数据库。

## 反目标

- **假证据**：网页只暴露配置驱动模拟（game.yaml 为数值单一事实来源 + seed/runs 落报告），与 CLI 证据级口径一致；不做绕过配置的裸参数接口（ad-hoc 探索留在 CLI，标注非证据）。
- **服务器被算挂**：公开部署必须有输入上限（见约束）。
- **破坏既有功能**：前端既有 113 测试、工具箱 78 测试、象棋对局流程不受影响。
- 样本内容出现规则书原文/美术复制（现有样本已合规，接口只读传输）。

## 方案取舍（Approaches）

1. **A 入口+指南页**（纯前端）：便宜但用户只能看不能跑，不满足「供用户使用」。用户已否。
2. **B1 Node 旁路服务 + 工作台页面**（✅ 选定）：新 Node HTTP 服务直接复用工具箱源码，nginx 反代 `/design-api`。复用全部已验证数值逻辑，符合 Node-only 约束，部署与现有 host 网络拓扑一致。
3. **B2 用 Java 重写模拟逻辑进 ruoyi**：违背 Node-only 约束、重复实现已验证数值、双份维护。否。
4. **B3 前端纯浏览器跑模拟**（把 TS 编译进前端）：无服务器成本，但样本/机制文件要打包进前端、报告口径与 CLI 分叉、后续二期存档无处落。否。

工作台输入形态取舍：**YAML 双编辑器（game.yaml + sim 配置）+ 预置下拉**，一套 UI 覆盖五类模拟；不做逐模拟表单（YAGNI，预置已覆盖探索场景）。

## 架构与组件

### 1. 工具箱增量（`tools/boardgame-design/`，只增不改既有行为）

- `validateText(text: string)`：与 `validateDoc` 同一校验逻辑的纯文本入口（validateDoc 改为读文件后调它）。
- `resolveParamsFromText(gameText: string, config: SimConfigInput)`：与 `resolveParams` 同逻辑，game 来自文本而非文件路径。
- `renderReport(data): { md: string; json: string }`：从 `writeReport` 抽出的纯函数（不落盘）；writeReport 调它再写文件。
- 既有 CLI/测试行为不变；新增函数各有测试。

### 2. Node 服务（新包 `tools/design-server/`）

- **零新运行时依赖**：`node:http` 手写路由（端点少，不值得引框架）；TS 编写，dev 用 tsx 跑，prod 用 tsc 编译（与工具箱源码一起编译，模拟逻辑单一来源，不复制）。
- 端点（全部 JSON，前缀 `/design-api`）：
  - `GET /health` → `{ ok: true }`
  - `GET /samples` → 7 份样本解析结果（key/name/zone/mechanisms/analysis…）
  - `GET /mechanisms` → 42 条机制（key/中文名/五要素）。**数据源裁定（builder check Q1）**：五要素唯一权威是 `.claude/skills/design-boardgame/references/mechanisms.md`（规整 `### key · 中文名 (English)` + 五要素格式）；`data/mechanisms.yaml` 只是词汇表（key/name/zones/aliases）。服务端解析 md 提取五要素（格式由测试钉死），不迁移内容、不造 md↔yaml 双份同步义务；Dockerfile 须 COPY 该 references 目录。
  - `POST /validate` `{ text }` → `{ ok, errors }`
  - `POST /run` `{ gameText, configText }` → 解析配置 → resolveParamsFromText → runSim → renderReport → `{ result, md, json, seed, runs }`
  - `POST /sweep` `{ gameText, configText }` → runSweep 同构文本版 → `{ rows, md, json }`
- 错误口径与 CLI 一致：参数/校验错误 400 + 点名 sim/字段的信息；未知路径 404。
- **服务器护栏（我的裁定，builder check Q2 后收紧）**：
  - 字段上限：请求体 ≤ 512KB；runs ≤ 50000；economy turns ≤ 500；dice count ≤ 200 且 faces 长度 ≤ 64；conflict maxRounds ≤ 200、每方 diceCount ≤ 20、hp ≤ 1000；sweep values ≤ 20。超限 400 并说明上限值。
  - **上限在 resolveParams/overrides 合并后的最终参数上校验，sweep 逐行校验**（sweep.param 可能指向 count/turns/maxRounds 把值推过上限）。
  - **模拟在 worker_threads 工作线程跑**（零依赖），单请求墙钟预算 15s，超时 terminate 并返回 408 + 「请降低 runs/回合数」指引；**并发闸 1**（同时只跑一个模拟，其余 503 + Retry-After）。主线程只做 I/O，/health 与资料浏览永不被模拟阻塞。
  - 将来若经 nginx 暴露，`/design-api` 的 proxy_read_timeout 须 ≥ 30s（> worker 预算，避免代理先 504）；本机直跑无此层。
- 样本/机制文件从仓库 `design/samples/`、`data/mechanisms.yaml` 读取（根目录可经 env 配置，Docker 内 COPY 进镜像）。
- **启动日志（G6）**：打印 `桌游设计助手工作台 → <url>`；dev 默认 `http://localhost:5173/design`，prod 取 env `PUBLIC_URL`（缺省 `http://localhost/design`）。

### 3. 前端（`boardgame-ui/`）

- 路由 `/design`（`meta.requiresAuth`，与大厅一致），新视图 `views/design/index.vue`，三页签：
  - **样本库**：7 卡片 → 详情（whyFun/remixable/pitfalls/机制标签/置信度与来源）。
  - **机制库**：42 条，搜索过滤，五要素展开。
  - **模拟工作台**：game.yaml 与 sim 配置双编辑器（textarea 即可，YAGNI 代码编辑器库）、预置下拉（7 样本 + 示例 sim 配置）、运行/校验按钮、结果区（标量表 + yaml 块，镜像 writeReport 版式）、md/json 下载（客户端 Blob，服务端不落盘）。
- 大厅入口：level 0 分区网格下方一张「桌游设计助手」卡片（沿用 .op/.rise 视觉语言），点击 `router.push('/design')`。**不进七分区、不动 catalog.ts**。
- API 层：`api/design.ts`（axios，沿用现有拦截器惯例）；vite dev proxy 增 `/design-api → http://localhost:8090`。
- **启动日志（G6）**：vite dev 启动时控制台追加打印工作台网址（小插件，一行）。
- 文案中文；页面明示「对话式设计请在 Claude Code 使用 /design-boardgame」，避免用户误以为网页能聊天。

### 4. 本机生产运行（部署接线）

- 生产模式（env `NODE_ENV=production` 或 `--serve-ui`）：服务读取 `boardgame-ui/dist` 托管静态文件（SPA fallback 到 index.html），`/design-api` 走本地路由，`/api` `/auth` `/system` 反代 `http://127.0.0.1:8080`，`/ws` 做 upgrade 管道反代（node:http 原生，零依赖）。Java 后端地址可经 env 配置。
- 一键脚本：`tools/design-server/` 提供 `npm run build`（tsc 编译服务+工具箱）与 `npm start`（生产运行）；前端 `npm run build` 照旧。README 写明三步：build ui → build server → start。
- 启动日志（G6）：生产打印 `桌游设计助手工作台 → http://localhost:8090/design`（端口/host 可经 env 覆盖）；dev 打印 `http://localhost:5173/design`。
- Dockerfile/compose/nginx 改动不在 v1 范围（本机 Windows 不适用 host 网络）；迁 Linux 服务器时另行启用。

## 约束

- Node ≥ 20.19.5；工具箱依赖纪律不变（yaml/zod）；design-server 零新运行时依赖，dev 依赖仅 tsx/typescript/vitest/@types/node。
- 模拟数值逻辑单一来源：服务 import 工具箱源码，禁止复制实现。
- 种子可复现口径不变：报告含 seed/runs、无时间戳；网页下载的报告与 CLI 同输入逐位一致。
- 不 commit（用户约定）；不改 catalog.ts、象棋后端、数据库。
- 本需求**取代**旧计划「不改前端既有文件」约束，仅限下列 additive 改动：`views/lobby/index.vue`（加入口卡片）、`router/index.ts`（加路由）、`vite.config.ts`（加 proxy/日志插件）。
- TDD；中文文案；Windows + Git Bash 开发环境，UTF-8。

## 测试口径

- 工具箱增量：validateText/resolveParamsFromText/renderReport 各单测（含与文件版等价性断言）。
- 服务：vitest 起临时端口真实 HTTP 请求（fetch），覆盖六端点 happy path + 400/404/408/503 + 全部输入上限（含 overrides/sweep 推高值的场景）+ 报告与 CLI 逐位一致 + worker 超时 terminate + 并发闸。
- 生产托管：静态文件服务 + SPA fallback + /api /auth /system 反代（临时假上游断言透传）+ /ws upgrade 管道冒烟。
- 前端：沿用 vitest+jsdom+@vue/test-utils 惯例——工作台渲染/运行/下载、样本与机制浏览、大厅入口卡片出现且导航正确；既有 113 测试不回归。
- 启动日志：dev/prod 两种模式打印的工作台网址断言。

## 留给 builder 的决策

- 服务内部文件组织与路由写法、tsc 双包编译的具体 tsconfig 布局。
- 编辑器/结果区的具体样式细节（沿用大厅视觉变量）。
- 预置配置的具体内容（每样本配一个能跑通的 sim 配置示例）。
- 生产托管的 env 命名与静态目录解析细节。
- 服务端口 8090 若与既有冲突可顺延（8091…），并在文档/日志同步。