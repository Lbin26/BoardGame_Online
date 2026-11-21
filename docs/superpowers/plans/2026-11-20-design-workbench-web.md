# 桌游设计助手·网页工作台（无状态 v1）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **不 commit（用户约定）**：本计划所有任务**无 commit 步骤**；任务完成 = 该任务测试全绿 + 既有套件不回归 + ledger 记录（沿用上一计划的执行惯例）。工作区 ledger 是唯一审计轨迹。

**Goal:** 把已交付的桌游设计工具箱包成 HTTP 服务 + 大厅入口 + `/design` 网页工作台（样本/机制浏览、配置驱动五类模拟、校验、报告下载），本机 Node 单进程一键生产运行。

**Architecture:** 工具箱只增三个纯文本入口 + 一个 sweep 文本版（数值逻辑单一来源）；新包 `tools/design-server/` 用 node:http 手写路由 + worker_threads 跑模拟（15s 预算、并发闸 1、解析后参数上限）；生产模式同进程托管 vite build 静态文件并把 `/api` `/auth` `/system` `/ws` 反代到 Java 8080。前端 Vue3 新增 `/design` 三页签视图与大厅入口卡片。

**Tech Stack:** TypeScript (ESM, NodeNext)、node:http / node:worker_threads（零新运行时依赖）、yaml + zod（经工具箱）、tsx / vitest / tsc；前端 Vue 3 + Element Plus + axios（rawRequest）+ vue-router 4，vitest + jsdom + @vue/test-utils。

**Spec:** `docs/superpowers/specs/2026-11-20-design-workbench-web-design.md`（已批准，绑定权威；冲突以 spec 为准）

## Global Constraints

- Node ≥ 20.19.5；Windows + Git Bash 开发，UTF-8；文案中文（机制名双语）。
- design-server **零新运行时依赖**（node:http / node:worker_threads）；dev 依赖仅 tsx/typescript/vitest/@types/node。yaml/zod 由工具箱文件位置解析（`tools/boardgame-design/node_modules`），不在 design-server 重复声明。
- 模拟数值逻辑单一来源：服务 import 工具箱源码，**禁止复制实现**。
- 种子可复现口径不变：报告含 seed/runs、无时间戳；网页报告与 CLI 同输入**逐位一致**。
- 只暴露配置驱动模拟（gameText + configText），不做裸参数接口；ad-hoc 留在 CLI。
- 护栏精确值：请求体 ≤ 512KB；runs ≤ 50000；economy turns ≤ 500；dice count ≤ 200 且 faces ≤ 64；conflict maxRounds ≤ 200、每方 diceCount ≤ 20、hp ≤ 1000；sweep values ≤ 20。**上限在 overrides 合并后的最终参数上校验，sweep 逐行校验**。worker 墙钟预算 15s → terminate + 408；并发闸 1 → 503 + Retry-After。
- 不 commit；不改 `catalog.ts`、象棋 Java 后端、数据库。前端既有文件仅允许 additive 改动：`views/lobby/index.vue`、`router/index.ts`、`vite.config.ts`（本需求取代旧「不改前端既有文件」约束，仅限这三处）。
- 样本 yaml 文件一律不改；xiangqi/go/gomoku/avalon 预置 = 样本原文 + 明确标注「演示数据，非样本数值」的演示配置；不复制规则书原文/美术；不造研究来源。
- TDD：每个实现步骤前先写失败测试并亲眼看它失败。既有基线：工具箱 78 测试、前端 113 测试，不得回归。
- 端口 8090（EADDRINUSE 顺延 +1，最多 10 次）；启动日志 `桌游设计助手工作台 → <url>`。

## Review Focus

规格未明说、但真实用户最可能撞上的五类输入/失效（每条已钉进所属任务的测试）：

1. **畸形请求体**（非法 JSON、缺 gameText/configText、text 非字符串、YAML 解析失败、空文本）→ 400 可读错误，绝不 500/进程崩溃。→ Task 4。
2. **静态托管路径穿越**（`/../`、`..%2f` 编码）不得读出 dist 目录之外的文件。→ Task 6。
3. **conflict faces 长度规格未设上限**（大 faces × 20 骰 × 200 轮 × 50000 runs 可算挂）→ 计划收紧 conflict 每方 faces ≤ 64（builder 加固，与 dice 对齐），15s 预算兜底其余未枚举维度。→ Task 4 上限测试 + Task 5 超时测试。
4. **`__proto__`/`constructor`/`prototype` 键**出现在 gameText/configText（YAML 解析可能直接改原型）→ 前置拒绝 400，`Object.prototype` 不被污染。→ Task 4。
5. **前端 axios 默认 10s 超时 < worker 15s 预算**：长模拟会被客户端先掐断 → run/sweep 每请求 timeout 30000。→ Task 7。

---

### Task 1: 工具箱纯文本入口（validateText / resolveParamsFromText）

**Files:**
- Modify: `tools/boardgame-design/src/schema/load.ts`（validateDoc 拆出 validateText）
- Modify: `tools/boardgame-design/src/schema/simConfig.ts`（resolveParams 拆出共享核 + resolveParamsFromText）
- Test: `tools/boardgame-design/tests/text-api.spec.ts`

**Interfaces:**
- Consumes: 既有 `ValidateResult`、`GameDocSchema`、`loadVocabulary`、`getAtPath`、`deepMerge`、`SimConfigInput`（签名见现文件，不改）。
- Produces:
  - `validateText(text: string, source?: string): ValidateResult`（source 缺省 `'(文本)'`；错误前缀 `${source}: ` 与文件版完全同格式）
  - `resolveParamsFromText(gameText: string, config: SimConfigInput): { params: Record<string, unknown>; game: GameDoc; seed: number; runs: number }`
  - `validateDoc(path)` / `resolveParams(config, configDir)` 签名与行为**不变**（内部改为调共享核）。

- [ ] **Step 1: 写失败测试** `tests/text-api.spec.ts`，用例与断言：
  1. `validateText(mini-game 文本) ok:true`，且与 `validateDoc(fixture 路径)` 结果 **deep-equal**（读 `tests/fixtures/mini-game.yaml` 原文传文本，source 传同一路径）。
  2. `validateText(mini-sample 文本, path)` 与 `validateDoc(mini-sample 路径)` deep-equal（ok:true）。
  3. 坏文本（把 `- worker-placement` 换成 `- not-a-mechanism`）→ `ok:false` 且 errors 含 `not-a-mechanism`；与同内容临时文件的 `validateDoc` deep-equal。
  4. `validateText('')` 与 `validateText('纯字符串')` → `ok:false`（不 throw）。
  5. `resolveParamsFromText(mini-game 文本, { game:'mini-game.yaml', sim:'economy', from:'subsystems.economy', overrides:{ turns: 5 } })` 与 `resolveParams(同 config, fixtures 目录)` **deep-equal**（params/game/seed/runs 四键；seed 42、runs 10000 默认值生效）。
  6. `resolveParamsFromText('不是: [合法', …)` → throw；`from: 'subsystems.nope'` → throw 含 `路径不存在`。
- [ ] **Step 2: 跑测试确认失败**：`cd tools/boardgame-design && npx vitest run tests/text-api.spec.ts` → FAIL（validateText/resolveParamsFromText 不存在）。
- [ ] **Step 3: 实现**：
  - `load.ts`：把 `validateDoc` 中「raw 解析之后」的全部逻辑移入 `validateText(text, source='(文本)')`（开头 `parse(text.replace(/^﻿/, ''))`，解析错误 → `{ ok:false, errors:[`${source}: ${msg}`] }`）；`validateDoc(path)` 改为 `readFileSync(path,'utf-8')`（catch → 原格式错误）后 `return validateText(text, path)`。`summarizeIssues` 的 path 参数即 source，格式不动。
  - `simConfig.ts`：抽出 `resolveFromGame(game: GameDoc, config: SimConfigInput)`（getAtPath + deepMerge + seed/runs 默认）；`resolveParams` = loadGameDoc → resolveFromGame；`resolveParamsFromText` = BOM 剥离 + `parse` + `GameDocSchema.parse`（ZodError → `Error('(文本): ' + issues…)`，格式仿 loadGameDoc）→ resolveFromGame。
- [ ] **Step 4: 跑测试确认通过**：同 Step 2 命令 → PASS。
- [ ] **Step 5: 全套件 + 类型**：`npm test`（78 + 新增全绿，既有 schema.spec 的 validateDoc 错误格式断言必须仍过）；`npm run typecheck` exit 0。

### Task 2: renderReport 抽出 + runSweepFromText

**Files:**
- Modify: `tools/boardgame-design/src/report.ts`
- Modify: `tools/boardgame-design/src/sim/sweep.ts`
- Test: `tools/boardgame-design/tests/report-text.spec.ts`

**Interfaces:**
- Consumes: Task 1 `resolveParamsFromText`；既有 `runSim`、`writeReport`、`runSweep`、`setAtPath`。
- Produces:
  - `export interface ReportData { title: string; config: SimConfig | SimConfigInput | SweepConfig; params: unknown; result: SimResult }`
  - `renderReport(data: ReportData): { md: string; json: string }`（纯函数，不落盘；md/json 构造逻辑从 writeReport 原样搬移，含 seed 42 / runs 10000 默认回显）
  - `writeReport(outPath, data: ReportData)` 签名行为不变（调 renderReport 后写文件）。
  - `runSweepFromText(gameText: string, config: SweepConfigInput): { rows: { value: number | string; result: SimResult }[]; baseParams: Record<string, unknown> }`
  - `runSweep(config, configDir)` 签名行为不变。

- [ ] **Step 1: 写失败测试** `tests/report-text.spec.ts`：
  1. **逐位等价**：用 mini-game fixture 跑 `runSim('economy', …)` 得 result，`writeReport(tmp 路径, data)` 后读回 md/json 文件内容，与 `renderReport(同 data)` 的 md/json **字符串全等**（data 含 title/config/params/result；tmp 用 `mkdtempSync`，afterEach 清理）。
  2. renderReport 纯函数：调用前后 tmp 目录无新文件。
  3. **sweep 等价**：fixture 目录写临时 sweep 配置（`sim: economy, from: subsystems.economy, sweep: { param: turns, values: [3, 5] }`，game 指向 mini-game.yaml），`runSweep(config, fixturesDir)` 与 `runSweepFromText(mini-game 文本, 同 config)` deep-equal（rows 两行、baseParams 不被污染）。
- [ ] **Step 2: 跑测试确认失败**：`npx vitest run tests/report-text.spec.ts` → FAIL（renderReport/runSweepFromText 不存在）。
- [ ] **Step 3: 实现**：report.ts 把 md/json 构造搬入 `renderReport`，writeReport 调它再 `writeFileSync`（jsonPath 规则不变）；sweep.ts 抽出共享核 `sweepRows(base: { params; seed; runs }, config)`（逐值 setAtPath + runSim），`runSweep` = resolveParams → sweepRows，`runSweepFromText` = resolveParamsFromText → sweepRows。
- [ ] **Step 4: 跑测试确认通过**：同 Step 2 → PASS。
- [ ] **Step 5: 全套件 + 类型**：`npm test` 全绿（既有 report/cli 相关测试不回归）；`npm run typecheck` exit 0。

### Task 3: design-server 骨架 + 只读端点（health/samples/mechanisms）+ 启动横幅

**Files:**
- Create: `tools/design-server/package.json`、`tsconfig.json`、`src/paths.ts`、`src/http.ts`、`src/mechanisms.ts`、`src/samples.ts`、`src/banner.ts`、`src/app.ts`、`src/main.ts`
- Test: `tools/design-server/tests/paths.spec.ts`、`tests/mechanisms.spec.ts`、`tests/read-api.spec.ts`

**Interfaces:**
- Consumes: 工具箱 `loadSampleDoc`/`SampleDoc`、`loadVocabulary`；仓库 `design/samples/*.yaml`（7 份）、`.claude/skills/design-boardgame/references/mechanisms.md`（42 条，`### key · 中文名 (English)` + `## 分区` + 五要素 bullet）。
- Produces:
  - `package.json`：name `design-server`，type module，**无 dependencies**；devDependencies `@types/node ^22.10.2 / tsx ^4.19.2 / typescript ^5.7.2 / vitest ^2.1.8`（与工具箱同版本）；scripts：`dev: tsx src/main.ts`、`build: tsc -p tsconfig.json && node scripts/copy-assets.mjs`、`start: node dist/design-server/src/main.js`、`test: vitest run`、`typecheck: tsc --noEmit`。（scripts/copy-assets.mjs 在 Task 6 创建；本任务 build 暂不可用属预期。）
  - `tsconfig.json`：target ES2022 / module NodeNext / moduleResolution NodeNext / strict / skipLibCheck / types:["node"] / **rootDir `".."`**（= tools/）/ outDir `"dist"` / include `["src/**/*", "../boardgame-design/src/**/*"]`、exclude tests。产物布局 `dist/design-server/src/*.js` + `dist/boardgame-design/src/*.js`。
  - `paths.ts`：`findRepoRoot(startDir: string): string`（逐级向上找同时含 `design/samples` 与 `tools/boardgame-design` 的目录，最多 10 级，找不到 throw）；`resolvePaths(env: NodeJS.ProcessEnv = process.env): { repoRoot; samplesDir; mechanismsMdPath; uiDistDir; upstream }`——env 名钉死：`DESIGN_REPO_ROOT`（缺省 findRepoRoot(模块目录)）、`DESIGN_UI_DIST`（缺省 `<repoRoot>/boardgame-ui/dist`）、`DESIGN_UPSTREAM`（缺省 `http://127.0.0.1:8080`）；samplesDir/mechanismsMdPath 由 repoRoot 派生。
  - `http.ts`：`class HttpError extends Error { constructor(status: number, message: string, headers?: Record<string,string>) }`；`sendJson(res, status, body)`；`readBody(req, maxBytes): Promise<string>`（超限 throw HttpError 400 含上限值）。
  - `mechanisms.ts`：`interface MechanismDetail { key; name; nameEn; zone; elements: { 说明; 代表作; 设计旋钮; 常见坑; 常见组合 } }`（elements 五键均 string）；`parseMechanismsMd(text: string): MechanismDetail[]`。
  - `samples.ts`：`interface SampleEntry { key: string; text: string; doc: SampleDoc }`；`loadSamples(dir: string): SampleEntry[]`（readdirSync 过滤 `.yaml` 排序，text=原文，doc=loadSampleDoc，key=doc.meta.key）。
  - `banner.ts`：`startupBanner(opts: { serveUi: boolean; port: number; env?: NodeJS.ProcessEnv }): string` → `桌游设计助手工作台 → ${env.PUBLIC_URL ?? (serveUi ? \`http://localhost:${port}/design\` : 'http://localhost:5173/design')}`。
  - `app.ts`：`interface AppOptions { paths?: ReturnType<typeof resolvePaths>; serveUi?: boolean; simBudgetMs?: number }`；`createApp(opts?: AppOptions): http.Server`。路由表（method+pathname 精确匹配）：`GET /design-api/health → {ok:true}`、`GET /design-api/samples → SampleEntry[]`、`GET /design-api/mechanisms → MechanismDetail[]`；其余 `/design-api/*` → 404 `{error:'未知路径: …'}`；HttpError → 其 status + `{error: message}`（附 headers）；未预期错误 → 500 `{error}`。serveUi 分支 Task 6 接入，本任务非 api 路径一律 404。
  - `main.ts`：`serveUi = env.NODE_ENV==='production' || argv 含 --serve-ui`；端口 `DESIGN_PORT ?? 8090`，EADDRINUSE 顺延 +1 最多 10 次；listen 成功后 `console.log(startupBanner({serveUi, port: 实际端口}))`。

- [ ] **Step 1: 脚手架 + 失败测试**：创建 package.json/tsconfig（如上），`npm install`（design-server 目录）。写三个测试文件：
  - `paths.spec.ts`：`findRepoRoot(工具箱某深层目录)` === 仓库根（用 `fileURLToPath(new URL('../../..', import.meta.url))` 类推算的已知目录断言）；`resolvePaths({ DESIGN_REPO_ROOT: 'X', … })` env 覆盖生效；缺省 upstream `http://127.0.0.1:8080`。
  - `mechanisms.spec.ts`：① 读真实 md 解析 → **42 条**，key 集合与 `loadVocabulary()` key 集合**完全相等**；② 首条 `simultaneous-action`：name `同时行动`、nameEn `Simultaneous Action Selection`、zone `轻策/派对`、elements.说明 以 `所有玩家同时暗选` 开头、五要素均非空；③ 合成 md 缺 `常见坑` bullet → throw 含该 key；④ 空文本 → throw。
  - `read-api.spec.ts`：`createApp()` listen(0) 后真实 fetch——health `{ok:true}`；samples 长度 7、含 key `splendor`、entry.text 含 `schemaVersion`、doc.analysis.confidence `medium`；mechanisms 长度 42；`GET /design-api/nope` → 404；`POST /design-api/health` → 404。banner：dev 模式串 `桌游设计助手工作台 → http://localhost:5173/design`；serveUi+port 8090 → `…→ http://localhost:8090/design`；`PUBLIC_URL` 覆盖两者。afterEach close server。
- [ ] **Step 2: 跑测试确认失败**：`cd tools/design-server && npx vitest run` → FAIL（模块不存在）。
- [ ] **Step 3: 实现各模块**。mechanisms 解析算法（钉死）：逐行扫——`/^## (.+)$/` 更新当前 zone；`/^### ([a-z0-9-]+) · (.+?) \((.+)\)\s*$/` 开新条目；`/^- (说明|代表作|设计旋钮|常见坑|常见组合)：(.*)$/` 填 elements；结束后逐条校验五要素齐全非空，缺 → throw `机制 ${key} 缺少要素: ${…}`；零条目 → throw。
- [ ] **Step 4: 跑测试确认通过**：`npx vitest run` → PASS。
- [ ] **Step 5: 类型 + dev 冒烟**：`npm run typecheck` exit 0；`DESIGN_PORT=8099 npx tsx src/main.ts` 后台起，`curl http://localhost:8099/design-api/health` → `{"ok":true}`，控制台出现 `桌游设计助手工作台 → http://localhost:5173/design`；杀进程。

### Task 4: 写端点（validate/run/sweep）+ 护栏上限（主线程直跑版）

**Files:**
- Create: `tools/design-server/src/guard.ts`、`src/simJobs.ts`
- Modify: `tools/design-server/src/app.ts`（加三条 POST 路由）
- Test: `tools/design-server/tests/guard.spec.ts`、`tests/write-api.spec.ts`、`tests/report-parity.spec.ts`

**Interfaces:**
- Consumes: Task 1/2 `validateText`/`resolveParamsFromText`/`renderReport`/`runSweepFromText` 的共享核、`runSim`、`SimConfigSchema`/`SweepConfigSchema`/`setAtPath`；Task 3 `HttpError`/`readBody`/`sendJson`/`createApp`。
- Produces:
  - `guard.ts`：`CAPS = { bodyBytes: 512*1024, runs: 50000, economyTurns: 500, diceCount: 200, diceFaces: 64, conflictMaxRounds: 200, conflictDiceCount: 20, conflictHp: 1000, conflictFaces: 64, sweepValues: 20 } as const`（conflictFaces 为 Review Focus #3 的 builder 加固）；`assertCaps(sim: string, params: Record<string, unknown>, runs: number): void`（throw HttpError 400，消息含字段名/实际值/上限值；economy turns 取 `params.turns ?? 20`，conflict maxRounds 取 `?? 100`）；`assertNoDangerousKeys(text: string): void`（正则 `/(^|\n)\s*(__proto__|constructor|prototype)\s*:/` 命中 → HttpError 400，Review Focus #4）。
  - `simJobs.ts`：`type SimKind = 'dice'|'cards'|'economy'|'conflict'`；`interface RunJob { kind:'run'; sim: SimKind; params: Record<string,unknown>; seed: number; runs: number }`；`interface SweepJob { kind:'sweep'; sim: SimKind; rows: { value: number|string; params: Record<string,unknown> }[]; seed: number; runs: number }`；`type JobOutcome = { kind:'run'; result: SimResult } | { kind:'sweep'; rows: { value: number|string; result: SimResult }[] }`；
    `prepareRun(gameText, configText): { job: RunJob; config: SimConfig }`；`prepareSweep(gameText, configText): { job: SweepJob; config: SweepConfig; baseParams: Record<string,unknown> }`；`executeJob(job: RunJob | SweepJob, budgetMs: number): Promise<JobOutcome>`（**本任务主线程直跑**：runSim / 逐行 runSim；模拟 throw → HttpError 400 原消息；budgetMs 本任务忽略，Task 5 启用）。
  - 路由：`POST /design-api/validate` `{text:string}` → `{ok, errors}`（ok:true 时 errors:[]）；`POST /design-api/run` `{gameText,configText}` → `{result, md, json, seed, runs}`；`POST /design-api/sweep` 同体 → `{rows, md, json}`。
  - prepare 流程（钉死）：assertNoDangerousKeys(两文本) → readBody 已限 512KB → JSON.parse（失败 400）→ 字段须 string（缺/非 string 400）→ yaml parse + `SimConfigSchema/SweepConfigSchema.parse`（ZodError → 400 `sim 配置非法: …`）→ sweep values ≤ 20 → `resolveParamsFromText`（throw → 400 原消息）→ `assertCaps(sim, 合并后 params, runs)`；sweep 另对 base 与**每行 setAtPath 后**的 params assertCaps（400 消息点名推超限的 value）。
  - 报告口径（与 CLI 逐位一致）：run → `renderReport({ title: \`${sim} 模拟报告\`, config: 解析后 SimConfig, params: job.params, result })`；sweep → `renderReport({ title: '参数扫描报告', config: 解析后 SweepConfig, params: baseParams, result: { rows } })`。

- [ ] **Step 1: 写失败测试**：
  - `guard.spec.ts`：assertCaps 逐上限——runs 50001、dice count 201/faces 65、economy turns 501（含缺省 20 放行）、conflict maxRounds 201/diceCount 21/hp 1001/faces 65 → 均 throw 400 且消息含上限值；边界值（50000/200/64/500/200/20/1000）放行。assertNoDangerousKeys：`overrides:\n  __proto__: {}` 文本 → 400；正常文本放行。
  - `write-api.spec.ts`（真实 fetch，mini-game 文本为 gameText）：
    1. run economy（from subsystems.economy, overrides turns 3, policy static）→ 200，result.rows 长 3，seed 42/runs 10000 回显，md 含 `# economy 模拟报告`、json 可 parse。
    2. run dice（from subsystems.scoring 不存在于 mini-game → 用 `from: components.dice.0`? **钉死**：mini-game 无 dice 子系统参数形状，dice 用 `from: subsystems.economy` + overrides `{count:2, faces:[1,2,3,4,5,6], symbol:6, op:'>=', value:10}`——zod 剥离 economy 键后合法）→ result.exact.probability 为 number。
    3. run conflict / cards：同法 overrides 注入完整参数（conflict 双方 diceCount 2/faces [1,2,3,4,5,6]/hitSymbols [6]/hp 5；cards deck [{key:'a',count:3}]/draw 2/target 'a'/op '>='/value 1）→ 200。
    4. sweep economy param turns values [3,5] → rows 长 2，md 含 `# 参数扫描报告`。
    5. validate：mini-game 文本 → `{ok:true, errors:[]}`；坏机制文本 → ok:false errors 含 key；`{text: 123}` → 400。
    6. **400 矩阵**：非法 JSON 体；缺 gameText；configText 非法 YAML；sim 未知枚举；runs 50001；overrides 推高 dice count 201；economy turns 501；sweep values 21 个；**sweep param=count values [2,201] → 400 点名 201**；body > 512KB（超长 text）→ 400；`__proto__` 键文本 → 400 且 `(Object.prototype as any).polluted` 仍 undefined。
    7. 未知路径 POST /design-api/nope → 404。
  - `report-parity.spec.ts`：mkdtemp 目录写 mini-game.yaml + config.yaml（dice 与 sweep 各一），`execFileSync(process.execPath, ['--import','tsx', <工具箱 cli.ts 绝对路径>, 'dice'|'sweep', '--config', …, '--out', …], { cwd: 工具箱目录 })` 跑 CLI → 读 md/json 文件；POST /run、/sweep 传**同文本** → `res.md === 文件内容`、`res.json === 文件内容`（字符串全等）。afterEach 清理 tmp。
- [ ] **Step 2: 跑测试确认失败**：`npx vitest run` → 新测试 FAIL（路由 404/模块不存在），Task 3 测试仍绿。
- [ ] **Step 3: 实现 guard.ts / simJobs.ts / 路由**（按上面钉死的流程与口径；executeJob 本任务为 async 包主线程直跑）。
- [ ] **Step 4: 跑测试确认通过**：`npx vitest run` → 全绿（含 parity 逐位一致）。
- [ ] **Step 5: 类型 + 工具箱不回归**：`npm run typecheck` exit 0；工具箱 `npm test` 仍全绿（本任务没改工具箱，跑一遍作证据）。

### Task 5: worker_threads + 15s 预算 + 并发闸 1

**Files:**
- Create: `tools/design-server/src/worker.ts`
- Modify: `tools/design-server/src/simJobs.ts`（executeJob 改 worker 派发；prepare/路由不动）
- Test: `tools/design-server/tests/runner.spec.ts`

**Interfaces:**
- Consumes: Task 4 `RunJob`/`SweepJob`/`JobOutcome`/`HttpError`；工具箱 `runSim`（worker 内 import，数值单一来源）。
- Produces: `executeJob(job, budgetMs)` 签名不变，语义变为——模块级 `busy` 闸：占用中 throw `HttpError(503, '模拟进行中，请稍后重试', { 'Retry-After': '1' })`；worker 超预算 terminate → `HttpError(408, '模拟超时（…秒预算）——请降低 runs/回合数后重试')`；worker 业务错误 → 400 原消息；worker 意外 exit/error → 500；finally 释放闸。budgetMs 由 createApp 传入：`opts.simBudgetMs ?? Number(env.DESIGN_SIM_BUDGET_MS) || 15000`。
  - worker 入口解析（钉死，dev/test 与 prod 双态）：
    ```ts
    const isTs = fileURLToPath(import.meta.url).endsWith('.ts')
    const worker = new Worker(new URL(isTs ? './worker.ts' : './worker.js', import.meta.url), {
      workerData: job,
      execArgv: isTs ? ['--import', 'tsx'] : [],
    })
    ```
    （vitest 下 simRunner 模块 URL 以 .ts 结尾 → 显式 tsx loader，worker 独立于 vitest 变换，与 CLI 同编译路径，parity 不破。）
  - `worker.ts`：`workerData` 即 job；run → `runSim`；sweep → 逐行 `runSim`；`parentPort.postMessage({ ok:true, …outcome })`；catch → `{ ok:false, error: (e as Error).message }`。

- [ ] **Step 1: 写失败测试** `tests/runner.spec.ts`。**慢任务夹具（钉死）**：gameText 为最小合法 game yaml，`subsystems.conflict` 双方 `diceCount: 20`、`faces: [x×63, hit]`（测试里模板字符串生成 64 面）、`hitSymbols: [hit]`、`hp: 1000`、`maxRounds: 200`；config `sim: conflict, from: subsystems.conflict, runs: 50000, seed: 42`——每 run 必满 200 轮 ×40 骰 ≈ 4 亿次 RNG，稳超 1.5s（全部在上限内）。
  1. **超时 terminate**：`createApp({ simBudgetMs: 300 })` → POST 慢任务 → 408 且消息含 `降低`；随后 POST 小 dice → 200（闸已释放、worker 已终止）。
  2. **并发闸**：budget 1500；发慢任务（不 await）→ 等 100ms → 第二发小 dice → **503 + 头 `Retry-After: 1`**；期间 `GET /design-api/health` 与 `/samples` → 200（主线程不被阻塞）；await 慢任务 → 408。
  3. **worker 错误传播**：economy 自维持行动（cost {}）gameText → 400 且消息点名该行动（证明错误从 worker 传回）。
  4. 小任务正常：dice → 200（worker 路径 happy）。
- [ ] **Step 2: 跑测试确认失败**：`npx vitest run tests/runner.spec.ts` → FAIL（主线程直跑版：慢任务阻塞事件循环，health/503 断言失败；无 408）。
- [ ] **Step 3: 实现 worker.ts + executeJob 改造**（按钉死的 spawn/协议/闸/计时器；message/error/exit 三事件都 settle 一次，finally clearTimeout + busy=false + 存活则 terminate）。
- [ ] **Step 4: 跑测试确认通过**：`npx vitest run` → **全绿**（Task 4 全部路由与 parity 测试经 worker 重跑仍逐位一致——这是数值单一来源的直接证据）。
- [ ] **Step 5: 类型**：`npm run typecheck` exit 0。

### Task 6: 生产托管（静态 + SPA + 反代 + ws）+ 构建脚本 + README

**Files:**
- Create: `tools/design-server/src/proxy.ts`、`scripts/copy-assets.mjs`、`README.md`
- Modify: `tools/design-server/src/app.ts`（serveUi 分支 + upgrade 事件）、`src/main.ts`（prod 横幅已支持，确认 --serve-ui 路径）
- Test: `tools/design-server/tests/proxy.spec.ts`

**Interfaces:**
- Consumes: Task 3 `AppOptions.paths`（uiDistDir/upstream）、`createApp`。
- Produces:
  - `proxy.ts`：`serveStatic(req, res, distDir): void`（GET/HEAD；decodeURIComponent pathname → path.resolve 后**必须以 distDir 前缀开头**，否则 404——Review Focus #2；命中文件 → 按扩展名 content-type 流式返回；未命中 → SPA fallback index.html；dist 不存在 → 500 `{error:'前端未构建：先运行 boardgame-ui npm run build'}`）；`proxyHttp(req, res, upstreamUrl): void`（node:http.request 透传 method/url/headers（host 改写为上游）/body 双向 pipe，回写 status/headers；上游不可达 → 502）；`proxyUpgrade(req, socket, head, upstreamUrl): void`（向上游发 upgrade 请求，'upgrade' 事件后向客户端写 101 + 原始头，双向 pipe socket，先写 head）。
  - content-type 表：html/js/mjs/css/json/svg/png/jpg/ico/woff/woff2/txt/map，缺省 application/octet-stream。
  - app serveUi 路由序：`/design-api` → 本地；pathname 以 `/api` `/auth` `/system` 开头 → proxyHttp；server 'upgrade' 事件 url 以 `/ws` 开头 → proxyUpgrade，否则 socket.destroy()；其余 → serveStatic。非 serveUi 模式行为不变。
  - `scripts/copy-assets.mjs`：`cpSync(<tools/boardgame-design/data>, <dist/boardgame-design/data>, { recursive: true })`（vocabulary.ts 的 `new URL('../../data/…', import.meta.url)` 在编译产物布局下指向 dist/boardgame-design/data，必须拷贝；机制 md 与样本经 repoRoot 向上查找在 prod 布局下仍可定位，无需拷贝）。
  - `README.md`：三步生产运行（① `cd boardgame-ui && npm run build` ② `cd tools/design-server && npm run build` ③ `npm start`）；前置 `tools/boardgame-design npm install`；env 表（DESIGN_PORT/DESIGN_REPO_ROOT/DESIGN_UI_DIST/DESIGN_UPSTREAM/DESIGN_SIM_BUDGET_MS/PUBLIC_URL）；dev 双进程说明（server npm run dev + ui npm run dev）；启动横幅示例。

- [ ] **Step 1: 写失败测试** `tests/proxy.spec.ts`（tmp dist：index.html 内容 `design-spa`、assets/app.js；假上游 http server 记录 method/path/body 并回 `{echo:true}`；`createApp({ serveUi:true, paths: {…resolvePaths(), uiDistDir: tmp, upstream: 假上游地址} })`）：
  1. `GET /` 与 `GET /design` → 200 内容 `design-spa`（SPA fallback）。
  2. `GET /assets/app.js` → 200 content-type 含 javascript。
  3. **穿越**：`GET /../secret.txt`（tmp 外放 secret 文件）与 `GET /%2e%2e/secret.txt` → 不得返回 secret 内容（404 或 index）。
  4. `POST /api/chess/rooms` 带 body → 假上游收到同 path/method/body，响应透传；`/auth/login`、`/system/x` 同断言。
  5. 假上游关闭 → `GET /api/x` → 502。
  6. **ws 冒烟**：假上游处理 upgrade 并 echo；客户端 http.request（headers connection: Upgrade / upgrade: websocket）→ 收到 101，写字节得 echo。
  7. dist 不存在 → `GET /` → 500 含 `前端未构建`。
- [ ] **Step 2: 跑测试确认失败**：`npx vitest run tests/proxy.spec.ts` → FAIL（serveUi 未接线，全 404）。
- [ ] **Step 3: 实现 proxy.ts + app 接线 + copy-assets.mjs + README**。
- [ ] **Step 4: 跑测试确认通过**：`npx vitest run` → 全绿。
- [ ] **Step 5: 构建冒烟**：`npm run build` exit 0（tsc 双包 + data 拷贝，确认 `dist/boardgame-design/data/mechanisms.yaml` 存在）；`DESIGN_PORT=8099 node dist/design-server/src/main.js` 后台起 → 控制台 `桌游设计助手工作台 → http://localhost:8099/design`；`curl /design-api/health` → ok、`curl /design-api/mechanisms` → 42 条（**编译产物里 vocabulary 路径正确**的直接证据）；杀进程。`npm run typecheck` exit 0。

### Task 7: 前端 API 层 + /design 路由 + vite 接线

**Files:**
- Create: `boardgame-ui/src/api/design.ts`、`boardgame-ui/vite-plugin-design-url.ts`
- Modify: `boardgame-ui/src/router/index.ts`（routes 数组 /game 之后加一条）、`boardgame-ui/vite.config.ts`（plugins 加 designUrlPlugin()；proxy 加 '/design-api'）
- Test: `boardgame-ui/src/api/__tests__/design.spec.ts`、`boardgame-ui/src/__tests__/vite-plugin-design-url.spec.ts`

**Interfaces:**
- Consumes: `rawRequest`（`@/utils/request`，拦截器直接返回 res.data）；Task 3-6 端点形状。
- Produces:
  - `api/design.ts` 类型与函数（返回即数据，`as Promise<T>` 标注）：
    ```ts
    export interface SampleEntry { key: string; text: string; doc: Record<string, any> }
    export interface MechanismDetail { key: string; name: string; nameEn: string; zone: string; elements: Record<string, string> }
    export interface RunResponse { result: Record<string, unknown>; md: string; json: string; seed: number; runs: number }
    export interface SweepResponse { rows: { value: number | string; result: Record<string, unknown> }[]; md: string; json: string }
    export const getSamples: () => Promise<SampleEntry[]>            // GET /design-api/samples
    export const getMechanisms: () => Promise<MechanismDetail[]>     // GET /design-api/mechanisms
    export const validateGame: (text: string) => Promise<{ ok: boolean; errors: string[] }>  // POST /design-api/validate {text}
    export const runSim: (body: { gameText: string; configText: string }) => Promise<RunResponse>    // POST /design-api/run, { timeout: 30000 }
    export const runSweep: (body: { gameText: string; configText: string }) => Promise<SweepResponse> // POST /design-api/sweep, { timeout: 30000 }
    ```
    **run/sweep 必须每请求 `{ timeout: 30000 }`**（rawRequest 实例默认 10000 < worker 预算，Review Focus #5）。
  - `vite-plugin-design-url.ts`：`export function designUrlPlugin(): Plugin`（vite 类型），`configureServer() { console.log('桌游设计助手工作台 → http://localhost:5173/design') }`。
  - 路由：`{ path: '/design', name: 'design', component: () => import('@/views/design/index.vue'), meta: { requiresAuth: true } }`（视图 Task 8 创建；本任务测试只 resolve 路由记录，不挂组件）。
  - vite.config：`plugins: [vue(), designUrlPlugin()]`；proxy 增 `'/design-api': { target: 'http://localhost:8090', changeOrigin: true }`。

- [ ] **Step 1: 写失败测试**：
  - `design.spec.ts`：`vi.mock('@/utils/request', …rawRequest get/post vi.fn())`——五函数路径/体正确；**runSim/runSweep 第三参 `{ timeout: 30000 }`**，getSamples 无 timeout 覆盖；`import { router } from '@/router'` → `router.resolve('/design').meta.requiresAuth === true`、name 'design'。
  - `vite-plugin-design-url.spec.ts`：spy console.log → 调 `designUrlPlugin().configureServer!(…)` → 打印恰为 `桌游设计助手工作台 → http://localhost:5173/design`。
- [ ] **Step 2: 跑测试确认失败**：`cd boardgame-ui && npx vitest run src/api/__tests__/design.spec.ts src/__tests__/vite-plugin-design-url.spec.ts` → FAIL。
- [ ] **Step 3: 实现 api/design.ts、插件、路由、vite.config 三处 additive 改动**。
- [ ] **Step 4: 跑测试确认通过**：同 Step 2 → PASS。
- [ ] **Step 5: 前端不回归**：`npm test` → 113 + 新增全绿（路由/vite 改动不破坏既有用例）。

### Task 8: /design 工作台视图（三页签）+ 预置

**Files:**
- Create: `boardgame-ui/src/views/design/index.vue`、`SamplesPanel.vue`、`MechanismsPanel.vue`、`WorkbenchPanel.vue`、`presets.ts`（同目录）
- Test: `boardgame-ui/src/views/__tests__/design.spec.ts`

**Interfaces:**
- Consumes: Task 7 `api/design.ts` 全部函数与类型。
- Produces:
  - `presets.ts`：`interface Preset { key: string; label: string; sampleKey: string; configText: string; demo: boolean }`；`PRESETS: Preset[]` 7 条（sampleKey = 样本 meta.key）。configText 钉死：
    - splendor / brass（demo:false，样本自身数值）：
      ```yaml
      game: splendor.yaml   # brass 预置为 brass.yaml
      sim: economy
      from: subsystems.economy
      seed: 42
      runs: 1000
      overrides:
        turns: 20
      ```
    - diceThrone（demo:false）：`game: diceThrone.yaml / sim: conflict / from: subsystems.conflict / seed: 42 / runs: 10000`。
    - xiangqi / go / gomoku / avalon（**demo:true**，label 含「演示」字样）：
      ```yaml
      # 演示数据，非样本数值（本样本无可模拟子系统，overrides 注入演示骰子参数）
      game: xiangqi.yaml   # 各自样本文件名
      sim: dice
      from: subsystems.scoring
      seed: 42
      runs: 10000
      overrides:
        count: 2
        faces: [1, 2, 3, 4, 5, 6]
        symbol: 6
        op: '>='
        value: 10
      ```
      （zod 剥离 scoring 键后为合法 dice 参数；样本 yaml 不改，演示来源在 UI 与报告配置回显中可见。）
  - `index.vue`：el-tabs 三页签（样本库/机制库/模拟工作台）；onMounted `Promise.all([getSamples(), getMechanisms()])`，失败 ElMessage.error；页首提示文案恰含「对话式设计请在 Claude Code 使用 /design-boardgame」。samples/mechanisms 以 props 下发。
  - `SamplesPanel.vue`：7 卡片（name/zone/机制标签/置信度）→ 点击展开详情（whyFun/remixable 列表/pitfalls/sources）。
  - `MechanismsPanel.vue`：搜索框按 key/name/nameEn 过滤；条目展开五要素。
  - `WorkbenchPanel.vue`：预置 el-select（选中 → gameText = 对应 sample.text、configText = preset.configText；demo 预置显示警示「演示数据，非样本数值——不构成样本平衡证据」）；两个 textarea（game.yaml / sim 配置）；三按钮 **校验文档 / 运行模拟 / 参数扫描**（显式选端点，前端不解析 YAML、不加新依赖）；结果区：result 标量（number/string/boolean）渲染 `| 指标 | 值 |` 表格 + 完整 md 于 `<pre>`（含 yaml 块，镜像 writeReport 版式）；sweep 结果直接 md `<pre>` + 下载；**下载 md/json**：`new Blob([md|json])` + `URL.createObjectURL` + 临时 `<a download>` click + revoke（服务端不落盘）；请求失败 ElMessage.error（展示后端 error 消息）。
  - 样式沿用大厅视觉变量（--ink/--line/--color-primary/--muted/--radius/--font-display）与 .btn/.op/.rise 语言；具体细节 builder 自定。

- [ ] **Step 1: 写失败测试** `design.spec.ts`（vi.mock @/api/design；mount index + ElementPlus，仿 lobby.spec 惯例；jsdom 缺 `URL.createObjectURL` → beforeEach vi.stubGlobal 补 spy）：
  1. 渲染三页签与提示文案；samples mock 7 条 → 样本卡片名可见。
  2. 机制搜索：输入过滤词 → 列表只剩匹配项。
  3. 选 diceThrone 预置 → 两 textarea 值分别含 `diceThrone` 与 `conflict`；选 xiangqi 预置 → 演示警示可见且 configText 含 `演示数据，非样本数值`。
  4. 运行模拟：填文本 → 点按钮 → `runSim` 收到 `{gameText, configText}`；mock 返回 `{result:{probability:0.5}, md:'# dice 模拟报告…', json:'{}', seed:42, runs:100}` → 表格出现 probability/0.5、pre 含 md；点下载 md → createObjectURL 收到含 md 的 Blob。
  5. 校验文档：mock `{ok:false, errors:['未知机制 key: x']}` → 错误文本可见；ok:true → OK 提示。
  6. 参数扫描：mock rows 两条 → runSweep 被调、md 渲染、json 下载可用。
  7. runSim reject → ElMessage.error 被调（spy）。
- [ ] **Step 2: 跑测试确认失败**：`npx vitest run src/views/__tests__/design.spec.ts` → FAIL（视图不存在）。
- [ ] **Step 3: 实现 presets.ts + 四个 vue 文件**（按上面钉死的行为；样式自定）。
- [ ] **Step 4: 跑测试确认通过**：同 Step 2 → PASS。
- [ ] **Step 5: 全套件 + 类型**：`npm test` 全绿；`npx vue-tsc --noEmit` exit 0。

### Task 9: 大厅入口卡片

**Files:**
- Modify: `boardgame-ui/src/views/lobby/index.vue`（level-0 模板 `<ZoneGrid … />` 之后插入；script 加 goDesign；style 加 .design-entry）
- Test: `boardgame-ui/src/views/__tests__/lobby.spec.ts`（**追加**用例，不改既有）

**Interfaces:**
- Consumes: 既有 lobby 的 `router`（useRouter）、.op/.rise 样式类；Task 7 路由。
- Produces: level-0 分区网格下方一张入口卡片，文案「桌游设计助手」+ 副题「样本 · 机制 · 数值模拟工作台」，点击 `router.push('/design')`。**不进七分区、不动 catalog.ts**。

- [ ] **Step 1: 写失败测试**（lobby.spec.ts 追加 describe）：`mountLobby({})`（level 0）→ 找到文本含「桌游设计助手」的可点击元素 → trigger click → `mocks.push` 收到 `'/design'`；level 1/2（query zone/game）下不渲染该卡片。
- [ ] **Step 2: 跑测试确认失败**：`npx vitest run src/views/__tests__/lobby.spec.ts` → 新用例 FAIL（卡片不存在），既有用例仍绿。
- [ ] **Step 3: 实现**：模板插入（goDesign 函数 + 卡片 section，class 含 op rise design-entry）；样式用既有 CSS 变量，卡片与分区网格视觉同级但独立成行。
- [ ] **Step 4: 跑测试确认通过**：同 Step 2 → 全绿。
- [ ] **Step 5: 前端全套件**：`npm test` → 113 + 全部新增绿。

### Task 10: 端到端验证 + 收尾

**Files:**
- Modify: 无新代码（仅验证；发现缺陷按 systematic-debugging 修复并补测试）
- Test: 三套既有套件 + 构建 + 生产冒烟

**Interfaces:**
- Consumes: 全部前序任务产物。
- Produces: 交付证据（ledger 记录各命令与结果）。

- [ ] **Step 1: 工具箱**：`cd tools/boardgame-design && npm test`（78 + Task 1/2 新增全绿）→ `npm run typecheck` exit 0。
- [ ] **Step 2: 服务**：`cd tools/design-server && npm test` 全绿 → `npm run typecheck` exit 0 → `npm run build` exit 0。
- [ ] **Step 3: 前端**：`cd boardgame-ui && npm test`（113 + Task 7/8/9 新增全绿）→ `npm run build` exit 0（vue-tsc + vite，dist 产出）。
- [ ] **Step 4: 生产冒烟（真实 dist）**：`DESIGN_PORT=8099 node tools/design-server/dist/design-server/src/main.js` 后台起（cwd 仓库根）→ 横幅 `桌游设计助手工作台 → http://localhost:8099/design`；`curl /design-api/health` → ok；`curl /design` → index.html 内容；`curl /design-api/samples` → 7 条；`curl /design-api/mechanisms` → 42 条；`curl -X POST /design-api/run`（mini-game + dice 配置体）→ 200 含 md/json；杀进程。
- [ ] **Step 5: dev 冒烟**：server `npm run dev`（DESIGN_PORT=8099）横幅 5173 行；`curl /design-api/health` ok；杀进程。（vite 插件横幅已由 Task 7 测试钉死。）
- [ ] **Step 6: 收尾**：ledger 汇总全部任务/裁定；向用户交付最终报告（用法：dev 双进程 / 生产三步；Rulings 与 Deferred minors  exhaustive）。

---

## Self-Review（已执行）

1. **Spec 覆盖**：G1→Task 9；G2→Task 3+8；G3→Task 4/5+8；G4→Task 4+8；G5→Task 8 presets（演示标注）；G6→Task 3 banner+Task 6 prod+Task 7 vite 插件；G7→Task 6+10。工具箱三增量+文本 sweep→Task 1/2；护栏全值→Task 4（resolved+逐行）；worker/预算/并发→Task 5；静态/SPA/反代/ws→Task 6；测试口径（parity/上限矩阵/408/503/穿越/假上游/启动日志）→各任务；Docker 移出 v1→无任务（符合）。无缺口。
2. **Step 扫描**：每步单一动作；算法仅钉死非显然处（md 解析正则、worker spawn 双态、慢任务夹具、preset yaml、guard 正则、prepare 流程）；其余给签名+断言。
3. **类型一致**：RunJob/SweepJob/JobOutcome 在 Task 4 定义、Task 5 消费同名；executeJob(job, budgetMs) 两任务同签名；SampleEntry/MechanismDetail 服务端（Task 3）与前端（Task 7）字段一致；ReportData 与 writeReport 既有调用兼容。
4. **Review Focus**：五条各有归属任务测试（#1 Task 4 400 矩阵；#2 Task 6 穿越；#3 Task 4 conflictFaces + Task 5 超时；#4 Task 4 __proto__；#5 Task 7 timeout 断言）。
5. **比例**：计划以签名/测试名/断言为主，代码块仅限钉死值；与 spec 篇幅相称。