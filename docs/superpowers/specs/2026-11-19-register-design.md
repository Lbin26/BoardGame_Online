# 注册功能启用与补全 — 设计（2026-11-19）

## 意图与为什么

用户要求"实现注册功能"。侦察发现前后端注册链路**已存在**：前端 `/register` 页（用户名/密码/确认密码/图形验证码、登录页"立即注册"链接、路由守卫）、后端 `POST /auth/register`（`SysRegisterService`）。注册不可用的根因是运行库参数 `sys.account.registerUser=false`（`AuthController.register` 直接拒绝）。另有三个真实缺口：验证码一次性但失败后不刷新（重试必报"验证码已失效"）、前端校验与后端长度规则不齐（用户名 2–30、密码 5–30）、注册页零测试。

目标：自助注册端到端可用（注册→回登录页→登录→进大厅），失败可重试，有单测与双浏览器 E2E 覆盖。

##  approaches（考虑过的方案）

- **A（选定）：启用框架开关 + 原位补全前端缺口 + 测试。** 复用 RuoYi 既有注册链路，改动最小；用户已确认开关改法。
- B：在 boardgame 模块自建注册端点绕过框架开关。否决：重复造用户体系，脱离后台用户管理，且违背"复用既有工作"。
- C：保持开关关闭、只修前端。否决：注册仍不可用，与需求矛盾；用户已选开启。

开关改法（用户选定）：admin 调 `PUT /system/config/updateByKey`（`system:config:edit` 权限，`updateConfig` 带 `@CachePut`，**立即生效、无需重启后端**）+ 种子 `script/sql/ry_vue.sql` 的 false→true（新装库默认开启）。

##  goals / non-goals / anti-goals

- goals：注册可用且失败可重试；校验前后端一致；vitest + Playwright 覆盖；种子与运行库一致。
- non-goals：注册后自动登录（保持"注册成功，请登录"→/login）；给注册用户分配角色（无角色即可登录、拉 profile、玩棋——象棋接口匿名）；改验证码开关；改 oracle/postgres/sqlserver 种子（本项目运行库为 MySQL，我的取舍：只改 mysql 种子，最小 diff）；不做 commit。
- anti-goals：为通过测试而关掉验证码或放宽后端校验；破坏既有未提交的 login/register captcha data-URI 修复。

## 约束

- 后端零代码改动 → 不需要 IDEA 重启；配置经管理接口改（带缓存刷新），不改 DB 直连 SQL（直改不刷缓存）。
- admin token 用 `logs/token_admin.txt`（有效）；clientid `e5cd7e4891bf95d1d19206ce24a7b32e`。
- 验证码 math 类型、一次性（`validateCaptcha` 先删 key 再比对）；E2E 识图求解。
- TDD：先写失败测试再修；全量回归（前端 vitest 全套 + build，后端无改动不重跑）。

## 改动清单（how）

1. **配置数据**：`PUT /system/config/updateByKey` body `{configKey:"sys.account.registerUser", configValue:"true"}`；读回 `GET /system/config/configKey/...` 确认 "true"。种子 `script/sql/ry_vue.sql:660` false→true。
2. **`boardgame-ui/src/views/register/index.vue`**：
   - rules 补长度：username 2–30、password 5–30（与 `RegisterBody` 的 `@Length` 一致）；confirmPassword 加"与密码一致"validator（替换提交时手工比对）。
   - `handleSubmit` catch 内 `await refreshCaptcha()`（任何注册失败后换新验证码，重试不再"已失效"）；成功路径不变。
3. **测试**：
   - 新 `views/__tests__/register.spec.ts`：验证码 data-URI 渲染与 uuid 注入；确认密码不一致/长度不足阻断提交（不调 register）；成功 body 含 clientId/grantType/userType/code/uuid + toast + push('/login')；失败 toast + getCaptcha 二次调用（刷新）；点击验证码图刷新。
   - 新 `logs/e2e_register.py`（Playwright）：UI 注册新用户（识图）→ 成功回 /login → 用新账号登录 → 大厅显示昵称；错验证码一次 → 报错且验证码已刷新 → 重试成功；重复用户名 → 报错文案。截图留 logs/。
4. 回归：`npm test` 全套 + `npm run build`。

## 留给实施者

- E2E 识图复用既有 math 验证码读图做法；新用户命名避开 admin/bob/carol。
- 若 admin token 过期：识图重新登录取 token（单用 uuid）。
- 注册页样式不动（既有 auth-page 风格）。