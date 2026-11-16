# 桌游平台前端 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建一个 Vue3 + Vite + Element Plus 前端工程 `boardgame-ui/`，实现桌游平台的首页、大厅、登录/注册，并把现有象棋对局迁移为组件接入登录账号。

**Architecture:** 独立前端工程，通过 axios 调后端 REST（登录/注册复用 Sa-Token，象棋接口单独封装），WebSocket 做象棋实时对局。登录态用 Pinia 存 token，路由守卫控制访问。象棋棋盘用 Canvas（逻辑从现有 `static/chess/index.html` 迁移）。

**Tech Stack:** Vue3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + Axios + Vitest；后端 RuoYi-Vue-Plus 6.0.0（Sa-Token + JWT + Redis）。

**Spec:** `docs/superpowers/specs/2026-11-16-boardgame-frontend-design.md`

## Global Constraints

- 后端端口 `8080`、context-path `/`；vite dev 代理 `/api` 与 `/ws` 到 `http://localhost:8080`。
- 后端改动仅限一处：`application.yml` 的 `api-decrypt.enabled: true → false`。**不改任何后端 Java**。
- 登录响应字段为 snake_case：`access_token` / `refresh_token` / `expire_in` / `refresh_expire_in` / `client_id` / `scope` / `openid`。
- 用户信息取现成接口 `GET /system/user/profile` → `R<ProfileVo>`，用户字段为 `user.userName` / `user.nickName`。
- 统一返回 `R` 结构：`{code, msg, data}`，成功 `code === 200`。象棋 `/api/chess/*` 返回**裸 Map**（`{success, ...}` / `{success:false, error}`），不走 R 解包。
- 象棋玩家身份用方案 A：前端把登录用户名当 `playerName` 传给象棋接口与 WS 握手，后端不鉴权。
- 本期忽略 `refresh_token`，token 失效直接跳登录，不做静默刷新。
- 视觉延续现有 demo 深色渐变风（`#1a1a2e → #16213e`、红 `#e94560`）。

## Review Focus

1. **验证码关闭时登录不报错**：`GET /auth/code` 返回 `captchaEnabled=false` 时，登录 body 不带 `code/uuid`，且登录页不渲染验证码输入框。合理预期：后端关了验证码，登录仍能成功。
2. **象棋错误响应（`success:false`）不当作成功解包**：`createRoom/joinRoom` 失败时返回 `{success:false, error:"房间不存在"}`，前端要提示 `error` 而非静默崩溃。合理预期：加入不存在房间会弹"房间不存在"。
3. **token 缺失/失效时的请求**：未登录调 `/system/user/profile` 或过期 token 时，被拦截器统一跳登录，而非白屏。合理预期：任何受保护页面在无 token 时重定向 `/login`。
4. **棋盘点击越界坐标**：Canvas 缩放后点击，`pixelToCell` 对边界外/间距过大的点击返回 `null` 且不抛异常。合理预期：点到棋盘外空白处什么都不发生。
5. **WebSocket 断线后对局状态**：WS `onclose` 后不残留"选中/合法走法"高亮，界面提示连接断开。合理预期：刷新/断网后页面不卡死在选中态。

---

### Task 1: 后端关闭接口加密

**Files:**
- Modify: `ruoyi-admin/src/main/resources/application.yml:150-152`

**Interfaces:**
- Produces: 后端登录/注册接口改为明文 JSON，前端无需加密握手。

- [ ] **Step 1: 修改配置** 把 `api-decrypt.enabled` 由 `true` 改为 `false`（仅此一行，`headerFlag` 等其余项不动）。

- [ ] **Step 2: 验证** 重启后端后，用 curl 直接发明文 `POST /auth/login`（body 带 `clientId/grantType/username/password`，无 `encrypt-key` 头），确认不再返回加密相关错误（能返回 `R` 结构即算通过，具体登录成败取决于账号）。若后端无法在本机启动（缺 DB/Redis），此步记为"联调时验证"并继续。

---

### Task 2: 前端工程脚手架

**Files:**
- Create: `boardgame-ui/package.json`
- Create: `boardgame-ui/vite.config.ts`
- Create: `boardgame-ui/tsconfig.json`
- Create: `boardgame-ui/index.html`
- Create: `boardgame-ui/src/main.ts`
- Create: `boardgame-ui/src/App.vue`
- Create: `boardgame-ui/src/router/index.ts`（空路由占位，后续任务填充）
- Create: `boardgame-ui/src/store/index.ts`（Pinia 实例）
- Create: `boardgame-ui/src/styles/index.scss`（全局深色主题变量）

**Interfaces:**
- Produces: 可 `npm run dev` / `npm run build` / `npm run test` 的工程骨架。`router/index.ts` 导出 `router`；`store/index.ts` 导出 `pinia`。

- [ ] **Step 1: 初始化工程** 用 `npm create vite@latest boardgame-ui -- --template vue-ts` 生成，再装依赖：`element-plus`、`pinia`、`vue-router`、`axios`、`sass`；dev 依赖：`vitest`、`@vue/test-utils`、`jsdom`、`@vitejs/plugin-vue`（按需）。

- [ ] **Step 2: 写 vite.config.ts** 配置 `@vitejs/plugin-vue`，`resolve.alias` 把 `@` 指向 `src`，`server.proxy`：`/api` → `http://localhost:8080`（不重写）、`/ws` → `ws://localhost:8080`（`ws: true`）。`vitest` 用 `environment: 'jsdom'`。

- [ ] **Step 3: 写全局样式与主题** `styles/index.scss` 定义深色背景渐变与主色变量（`#e94560` 主红、`#1a1a2e` 底色），`main.ts` 引入 Element Plus 与全局样式。

- [ ] **Step 4: 验证构建通过** 运行 `npm run build`（含 `vue-tsc` 类型检查），期望无类型错误、产物生成成功。

- [ ] **Step 5: Commit**

```bash
git add boardgame-ui
git commit -m "feat(frontend): scaffold Vue3 + Vite + Element Plus project"
```

---

### Task 3: 请求层与 API 封装

**Files:**
- Create: `boardgame-ui/src/utils/request.ts`
- Create: `boardgame-ui/src/api/auth.ts`
- Create: `boardgame-ui/src/api/user.ts`
- Create: `boardgame-ui/src/api/chess.ts`
- Test: `boardgame-ui/src/utils/__tests__/request.spec.ts`

**Interfaces:**
- Consumes: Task 2 的工程骨架。
- Produces:
  - `unwrapR(body: any): any` — 纯函数：`body.code === 200` 返回 `body.data`，否则 `throw new Error(body.msg)`。
  - `request` — axios 实例：请求拦截器加 `Authorization: Bearer ${token}`（token 从 localStorage 读）；响应拦截器对非象棋路径调用 `unwrapR`；401 时清 token 并跳 `/login`。
  - `rawRequest` — axios 实例：同样加 Bearer 头，但响应直接返回 `res.data`（不 unwrap），供象棋接口用。
  - `api/auth.ts`：`login(body: {clientId, grantType, username, password, code?, uuid?}) → Promise<LoginData>`；`register(body: {username, password, code?, uuid?, userType}) → Promise<void>`；`logout() → Promise<void>`；`getCaptcha() → Promise<{captchaEnabled, uuid, img}>`。`LoginData` 字段为 snake_case：`access_token/refresh_token/expire_in/client_id` 等。
  - `api/user.ts`：`getProfile() → Promise<ProfileVo>`（`ProfileVo.user.userName`、`user.nickName`）。
  - `api/chess.ts`：`listRooms() → {rooms, waitingCount, playingCount}`；`createRoom(playerName) → {success, room, board}`；`joinRoom(roomId, playerName) → {success, room, board, error?}`；`getValidMoves(roomId, row, col) → {success, validMoves}`。均用 `rawRequest`。

- [ ] **Step 1: 写失败测试** 在 `request.spec.ts` 里对 `unwrapR` 写测试：`{code:200, msg:'ok', data:{x:1}}` → 返回 `{x:1}`；`{code:500, msg:'失败', data:null}` → 抛 `Error('失败')`。

- [ ] **Step 2: 运行测试确认失败**

Run: `npm run test -- request.spec.ts`
Expected: FAIL（`unwrapR` 未定义）。

- [ ] **Step 3: 实现 `utils/request.ts`** 实现 `unwrapR`、`request`、`rawRequest` 三个导出。token 存取约定：`localStorage` key 为 `access_token`。

- [ ] **Step 4: 实现 `api/auth.ts` / `api/user.ts` / `api/chess.ts`** 按 Interfaces 定义的签名与路径封装：auth 用 `request`，chess 用 `rawRequest`。

- [ ] **Step 5: 运行测试确认通过**

Run: `npm run test -- request.spec.ts`
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add boardgame-ui/src/utils boardgame-ui/src/api
git commit -m "feat(frontend): request layer and api modules"
```

---

### Task 4: 棋盘坐标纯函数

**Files:**
- Create: `boardgame-ui/src/utils/board.ts`
- Test: `boardgame-ui/src/utils/__tests__/board.spec.ts`

**Interfaces:**
- Consumes: Task 2。
- Produces:
  - 常量 `ROWS=10, COLS=9, CELL=56, PADDING=28, BOARD_W, BOARD_H`。
  - `cellToPixel(row, col): {x, y}`。
  - `pixelToCell(x, y): {row, col} | null`（超出棋盘或距交叉点 > CELL/2 返回 null）。
  - `getPieceAt(pieces, row, col): Piece | undefined`。

- [ ] **Step 1: 写失败测试** 覆盖：`cellToPixel(0,0)` 返回 `{x:28,y:28}`；`pixelToCell(28,28)` 返回 `{row:0,col:0}`；`pixelToCell(0,0)` 返回 `null`；`getPieceAt([{row:0,col:0}],0,0)` 命中、`getPieceAt([],1,1)` 为 `undefined`。

- [ ] **Step 2: 运行测试确认失败**

Run: `npm run test -- board.spec.ts`
Expected: FAIL。

- [ ] **Step 3: 实现 `utils/board.ts`** 从现有 `static/chess/index.html` 的 `cellToPixel/pixelToCell/getPieceAt` 迁移，抽出为纯函数并导出常量。

- [ ] **Step 4: 运行测试确认通过**

Run: `npm run test -- board.spec.ts`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add boardgame-ui/src/utils/board.ts boardgame-ui/src/utils/__tests__/board.spec.ts
git commit -m "feat(frontend): board coordinate math"
```

---

### Task 5: 用户状态 store + 登录/注册页 + 路由守卫

**Files:**
- Create: `boardgame-ui/src/store/user.ts`
- Create: `boardgame-ui/src/views/login/index.vue`
- Create: `boardgame-ui/src/views/register/index.vue`
- Modify: `boardgame-ui/src/router/index.ts`（补 `/`、`/login`、`/register`、`/lobby`、`/game/:roomId` 路由与守卫）

**Interfaces:**
- Consumes: Task 3（`api.auth`、`api.user`）。
- Produces:
  - `useUserStore()`：state `{ token, userInfo }`；getter `isLogin`（token 非空）；actions `login(form)`（调 `api.auth.login`，存 `access_token` 到 localStorage + state，再 `fetchProfile()`）、`fetchProfile()`（调 `api.user.getProfile` 存 `userInfo`）、`logout()`（调 `api.auth.logout`，清 token/userInfo 并跳 `/login`）。
  - `router` 守卫：`/lobby`、`/game/*` 需登录（无 token 重定向 `/login?redirect=...`）；已登录访问 `/login`/`/register` 重定向 `/`。

- [ ] **Step 1: 实现 `store/user.ts`** 按 Interfaces 定义 state/getter/actions。

- [ ] **Step 2: 实现登录页 `views/login/index.vue`** 用户名 + 密码 + 图形验证码（`onMounted` 调 `getCaptcha`，`captchaEnabled=false` 时隐藏验证码；显示 `img` base64；点击刷新重取 `uuid`）。提交调 `userStore.login`，成功后跳 `redirect` 或 `/`。

- [ ] **Step 3: 实现注册页 `views/register/index.vue`** 用户名 + 密码 + 确认密码 + 验证码，提交调 `api.auth.register`，成功提示后跳 `/login`。

- [ ] **Step 4: 补路由与守卫** 在 `router/index.ts` 加上述路由（组件先占位或懒加载）与 `beforeEach` 守卫。

- [ ] **Step 5: 验证构建通过**

Run: `npm run build`
Expected: 类型检查 + 构建通过。

- [ ] **Step 6: Commit**

```bash
git add boardgame-ui/src/store/user.ts boardgame-ui/src/views/login boardgame-ui/src/views/register boardgame-ui/src/router
git commit -m "feat(frontend): auth store, login/register pages, route guard"
```

---

### Task 6: 首页

**Files:**
- Create: `boardgame-ui/src/views/home/index.vue`

**Interfaces:**
- Consumes: Task 5（`useUserStore`）。
- Produces: `/` 页面，游戏卡片（象棋可玩、其余"敬请期待"占位），右上角未登录显示"登录/注册"，已登录显示 `userInfo.nickName` + 退出按钮。

- [ ] **Step 1: 实现首页** 顶部栏 + 游戏入口卡片网格；"象棋"卡片跳 `/lobby`，占位卡片禁用。登录态按 `useUserStore` 渲染。

- [ ] **Step 2: 验证构建 + 手动** `npm run dev` 起服务，浏览器访问 `/`，确认未登录/已登录两种状态、象棋卡片跳转、退出按钮可用。

- [ ] **Step 3: Commit**

```bash
git add boardgame-ui/src/views/home
git commit -m "feat(frontend): homepage with game cards"
```

---

### Task 7: 大厅

**Files:**
- Create: `boardgame-ui/src/views/lobby/index.vue`

**Interfaces:**
- Consumes: Task 3（`api.chess`）、Task 5（`useUserStore`）。
- Produces: `/lobby` 页面，房间列表（`listRooms`）、创建房间（`createRoom(当前用户名)`）、输入房间号加入（`joinRoom`）、状态标签（等待中/对局中）。加入/创建成功后跳 `/game/:roomId`。`success:false` 时用 `ElMessage` 提示 `error`。

- [ ] **Step 1: 实现大厅** 布局参考现有 `index.html` 的 lobby 区（深色卡片）。`playerName` 一律取 `userStore.userInfo.userName`。

- [ ] **Step 2: 验证构建 + 手动** `npm run dev` 起服务，登录后访问 `/lobby`，确认列表加载、创建/加入跳转、错误提示（如加入不存在房间号弹"房间不存在"）。

- [ ] **Step 3: Commit**

```bash
git add boardgame-ui/src/views/lobby
git commit -m "feat(frontend): lobby room list/create/join"
```

---

### Task 8: 象棋对局（棋盘组件 + 对局页 + WebSocket）

**Files:**
- Create: `boardgame-ui/src/components/ChessBoard.vue`
- Create: `boardgame-ui/src/views/game/index.vue`

**Interfaces:**
- Consumes: Task 3（`api.chess`）、Task 4（`utils/board`）、Task 5（`useUserStore`）。
- Produces:
  - `ChessBoard.vue`：props `{ pieces, currentTurn, viewerColor, selectedCell, validMoves }`；emit `cell-click({row,col})`；用 Canvas 绘制棋盘与棋子（绘制逻辑从现有 `index.html` 的 `drawBoard/drawPiece` 迁移），`pixelToCell` 用 Task 4 的纯函数。
  - `views/game/index.vue`：进入后 `getRoom`/`joinRoom` 拿到 `room` + `board`，建立 WS `/ws/chess?roomId=&playerName=`（`playerName=userInfo.userName`）；处理 `MOVE/GAME_START/GAME_OVER/RESIGN/UNDO/REMATCH/CHAT/PLAYER_JOINED/PLAYER_LEAVE` 消息（逻辑从现有 `index.html` 迁移）；落子前用 `getValidMoves` 高亮合法走法；聊天/悔棋/认输/再来一局按钮。

- [ ] **Step 1: 实现 `ChessBoard.vue`** 迁移 Canvas 绘制；`onBoardClick` 用 `board.pixelToCell` 换算，命中自己棋子时 emit 选中，命中合法走法时 emit 落子。

- [ ] **Step 2: 实现 `views/game/index.vue`** 迁移现有 WS 逻辑到 `onMounted/onUnmounted`（`onUnmounted` 关 WS 并清理选中态，对应 Review Focus #5）。

- [ ] **Step 3: 验证构建 + 手动双开** `npm run build` 通过；两个浏览器（或普通+隐身）各登录不同账号，创建/加入同一房间，确认能实时对弈、聊天、悔棋、认输、再来一局。

- [ ] **Step 4: Commit**

```bash
git add boardgame-ui/src/components/ChessBoard.vue boardgame-ui/src/views/game
git commit -m "feat(frontend): chess game board component and game view"
```

---

## 自检记录

- **Spec 覆盖**：首页(Task6)、大厅(Task7)、登录/注册(Task5)、对局(Task8)、请求层(Task3)、棋盘迁移(Task4/8)、后端关加密(Task1)、脚手架(Task2) 全部有对应任务。
- **Review Focus 落点**：#1→Task5 登录页；#2→Task7 大厅错误提示；#3→Task3 拦截器 + Task5 守卫；#4→Task4 坐标测试；#5→Task8 对局页 `onUnmounted`。
- **类型一致**：`unwrapR`/`request`/`rawRequest`/`api.*`/`useUserStore`/`cellToPixel`/`pixelToCell`/`getPieceAt` 在各任务签名保持一致。
- **比例**：纯函数（request 解包、棋盘坐标）配 Vitest 单测；UI 页以 `npm run build` + 手动验证，不伪造组件测试。
