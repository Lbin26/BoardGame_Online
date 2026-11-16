# 桌游平台前端设计文档

日期：2026-11-16
状态：已定稿（待最终确认后进入计划）

## 一、意图与背景（why）

这是一个基于 RuoYi-Vue-Plus 6.0.0 后端的个人项目，已有一个 `ruoyi-boardgame` 模块实现中国象棋的完整后端（REST `/api/chess/*` + WebSocket `/ws/chess` + 象棋引擎），以及一个单文件 demo 前端 `ruoyi-admin/.../static/chess/index.html`（Vue3 CDN + Canvas，无登录）。

目标是把"象棋 demo"升级为"桌游平台"：有一个正式的首页 + 大厅，接入真实的登录/注册（复用后端已有的 Sa-Token 账号体系），象棋作为第一款游戏接入，未来可扩展更多游戏。

## 二、目标 / 非目标 / 反目标

**目标**
- 一个独立、规范、可扩展的 Vue3 前端工程。
- 首页（平台名/Logo、游戏入口卡片、登录态入口）+ 大厅（房间列表、创建/加入房间）。
- 登录/注册/退出，复用后端 `/auth/login`、`/auth/register`、`/auth/code`、`/auth/logout`。
- 把现有象棋对局（Canvas 棋盘 + WebSocket）迁移为工程内组件，接入登录后的真实账号。

**非目标（本期不做）**
- 新增第二款游戏（只预留入口位）。
- 战绩/排行榜/个人中心详情页。
- 匹配系统、观战、AI 对战。
- 修改后端 Java 代码（象棋 controller/引擎/鉴权）。

**反目标（做出来就算失败）**
- 引入一套和后端 Sa-Token 不匹配的鉴权，导致登录态无法持久。
- 前端依赖后端无中生有的接口。
- 本期为"统一象棋返回结构/接入象棋鉴权"而去改后端 Java，超出既定范围。

## 三、约束（含评审后已核实的事实）

- 后端登录鉴权是 **Sa-Token + JWT + Redis**（不是 Spring Security），token 通过 header `Authorization: Bearer {token}` 传递。
- **接口加密已决定关闭**：后端 `application.yml` 的 `api-decrypt.enabled` 由 `true` 改为 `false`（这是唯一一处后端改动，属配置非 Java 代码）。关闭后登录/注册走明文 JSON。
- 登录必须带 `clientId`（存于 `sys_client` 表），`grantType` 用 `password`。
- **登录响应字段为 snake_case**：`access_token` / `refresh_token` / `expire_in` / `refresh_expire_in` / `client_id` / `scope` / `openid`。前端按这些字段名读取。
- 验证码接口 `GET /auth/code` 返回 `{captchaEnabled, uuid, img(base64)}`；验证码可能被后端关闭（`captchaEnabled=false`）。
- **获取当前登录用户信息用现成接口** `GET /system/user/profile` → `R<ProfileVo>`（`user.username`、`user.nickname`），不新增后端接口。
- **象棋接口不返回标准 `R` 结构**（见 5.4），前端需对 `/api/chess/*` 做特判。
- 象棋 WebSocket 握手为 `/ws/chess?roomId=&playerName=`，用"昵称"标识玩家，与登录态无关；本期不改后端，采用方案 A（见 5.5）。

## 四、技术方案（Approaches）

### 方案一：正式 Vue3 前端工程（已选定 ✅）
Vue3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + Axios，独立目录 `boardgame-ui/`，npm 构建。
- 优点：组件化、路由/状态管理规范、Element Plus 组件生态、最接近真实工程、可扩展。
- 缺点：工程量最大，需 npm 构建与独立部署（或打包进后端静态目录）。

### 方案二：静态多页面（弃用）
延续现有 `index.html` 的 Vue3 CDN 写法，拆多个 HTML。零构建但难维护、不组件化。

### 方案三：单页 + hash 路由 CDN（弃用）
折中，仍是 CDN 裸写，无 Element Plus 生态。

**选型理由**：用户目标是"完整平台"且这是 RuoYi-Vue-Plus（官方前端即 Vue3+Element Plus 的 plus-ui），方案一最贴近真实工程、可扩展、能真正学到前端工程化。

## 五、设计细节

### 5.1 工程位置与结构
- 目录：`boardgame-ui/`（项目根，与 `ruoyi-admin` 等平级）。
- 技术栈：Vite + Vue3 + TS + Element Plus + Pinia + Vue Router + Axios。
- 关键目录：
  ```
  boardgame-ui/
    src/
      api/            # 与后端接口一一对应的 api 封装（auth、user、chess）
      store/          # Pinia：user(登录态/token)、room
      router/         # 路由 + 登录守卫
      utils/request.js# axios 实例 + 拦截器（注入 Bearer token、统一处理 R 结构）
      views/
        home/         # 首页
        login/        # 登录
        register/     # 注册
        lobby/        # 大厅
        game/         # 对局（含象棋棋盘组件）
      components/
        ChessBoard.vue# 从现有 index.html 迁移的 Canvas 棋盘 + 走法
    vite.config.js    # dev 代理 /api、/ws 到后端
  ```

### 5.2 鉴权与请求
- 登录：`POST /auth/login`，明文 body `{clientId, grantType:"password", username, password, code, uuid}`。
  - `clientId` 做成前端常量（联调时与后端 `sys_client` 的 web 客户端对齐）。
  - 进入登录页先 `GET /auth/code` 拿验证码；若 `captchaEnabled=false` 则登录 body 不带 code/uuid。
- 注册：`POST /auth/register`，明文 body `{username, password, code, uuid, userType}`（`userType` 默认 `sys_user`）。
- 登录成功存 `access_token`（snake_case 字段名）；**不做静默刷新**，token 过期/401 直接跳登录页（`refresh_token` 本期忽略）。
- 获取用户信息：登录后调 `GET /system/user/profile` 拿 `username/nickname` 存 Pinia。
- axios 请求拦截器统一加 `Authorization: Bearer {token}`。
- 响应处理分两类（见 5.4）。

### 5.3 后端改动
- 仅一处配置：`api-decrypt.enabled: false`（关闭接口加密）。
- 不新增后端接口、不改后端 Java。

### 5.4 页面清单与接口响应处理
- `/` 首页：平台名/Logo、游戏卡片（象棋可玩 + 其余"敬请期待"占位）、右上角登录/注册 或 已登录用户 + 退出。
- `/login` 登录页：用户名 + 密码 + 图形验证码。
- `/register` 注册页：用户名 + 密码 + 确认密码 + 验证码。
- `/lobby` 大厅：房间列表、创建房间、输入房间号加入、显示等待状态。
- `/game/:roomId` 对局：象棋棋盘（Canvas）+ 玩家信息 + 聊天 + 悔棋/认输/再来一局，WebSocket 实时（逻辑从现有 `index.html` 迁移）。

**响应处理（两类，分开）**
- 标准 `R` 结构（`code/msg/data`）：auth、user 等接口，axios 拦截器统一解包。
- 象棋 `/api/chess/*` 返回裸 `Map`：`{success, room, board}`、`{success:false, error}`、`{rooms, waitingCount, playingCount}`、`{validMoves}`。前端对 chess API 单独封装、单独判断 `success/error`，不走统一 `R` 解包。

### 5.5 象棋玩家身份（已定：方案 A）
- 前端把登录用户名（`LoginHelper` 对应的 username）作为 `playerName` 传给象棋接口与 WS 握手。
- 后端不做鉴权（现状即信任前端传的 `playerName`）。
- 方案 B（后端 `@SaCheckLogin` + 用登录态识别玩家）留待后续增强，本期不做。

## 六、留给实现者的决定

- 视觉主题：延续现有 demo 的深色渐变风（`#1a1a2e → #16213e`、红 `#e94560`），具体配色/布局由实现者按 Element Plus 主题定制。
- 组件拆分粒度、Pinia store 结构细节。
- `clientId` 常量的具体取值（联调时与后端 `sys_client` 对齐）。
- 开发代理端口与后端地址。

## 七、验收口径

- 未登录 → 首页可见、点游戏/大厅跳登录。
- 登录成功 → 显示用户名（来自 `/system/user/profile`）；token 失效后请求被拦截跳登录。
- 大厅能列出房间、创建/加入房间。
- 进入对局后，两个账号能通过 WebSocket 实时对弈（复用现有引擎），聊天/悔棋/认输/再来一局可用。
- 后端仅改 `api-decrypt.enabled` 一处配置，无 Java 改动。
