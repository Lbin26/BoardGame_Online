# DiceGo 设计日志

## 确认记录

- 2026-11-20 · Checkpoint 1:设计系统(夜局 token / ZCOOL QingKe HuangYou + 系统中文正文栈 / 8pt / 6px 圆角 / 2 级阴影)——用户回复「确认」
- 2026-11-20 · 落地页 v0 推送后用户纠偏:平台是多桌游平台,大厅页只做清晰引导,不 hero 单一棋种;交付物改为大厅页
- 2026-11-20 · 大厅 v0 Checkpoint 2——用户回复「通过了」→ 进入 v1 完整构建(`DiceGo Lobby v2.html`)

## 模式分类

大厅 = **Redesign · Overhaul**:视觉语言重建;保护契约 = 三级导航(URL query 唯一事实来源)、resolveLevel 回落、房间字段(roomId/红黑座/空位/三态)、状态→动作映射(waiting→加入,其余→观战)、房间号快速入场在 level 0/2 可见、创建后跳 /game/:roomId 选座、5s 轮询(设计稿标占位)。

## 关键决策

- 分区印章字(派家策牌叙棋重)替代 catalog 原 emoji;机制 chips 保留原文
- 诚实徽章:1 款可玩 / 敬请期待 / 规划中(无棋种分区)
- 状态色不新造色相:waiting=accent、playing=中性、finished=虚线;--danger 固定由品牌红色相派生
- 首屏分流文案:「有房间号?右侧直接入场。想逛逛?下面七个分区…」
- 演示数据全标「演示数据」;跳转/登录/退出以 toast 说明真实应用行为,不伪造结果

## 版本

- `DiceGo Landing.html` — 落地页 v0(已搁置,保留)
- `DiceGo Lobby.html` — 大厅 v0(已通过,保留)
- `DiceGo Lobby v2.html` — 大厅 v1 完整构建(当前交付候选)

## 自查修复

- v1 自查发现 `.crumb/.rooms/.empty/.list-error` 的 display:flex 覆盖 hidden 属性 → 加 `[hidden]{display:none!important}`

## 实装进 boardgame-ui(2026-11-20,用户指令「实装进项目里」,计划经 ExitPlanMode 批准)

视觉事实来源 = `DiceGo Lobby v2.html`;模式 Redesign · Overhaul,行为/IA/测试契约全保留。

- **改动文件**:`styles/index.scss`(:root 追加 token,纯追加)、`main.ts`(字体 css 一行 import)、`assets/fonts/zcool-qingke-huangyou/`(自托管,92 切片按需加载)、`constants/zoneSeals.ts`(新,印章字映射;catalog.ts 不动)、`ZoneGrid.vue` / `GameList.vue`(模板+样式重写,props/emits 不变)、`views/lobby/index.vue`(script 逐字保留,仅新增 listError/showSkeleton;模板换原生元素)、`lobby.spec.ts`(1 行授权变更 + 2 新用例)
- **授权契约变更(唯一)**:测试 `buttonByText(wrapper, '🔄 刷新')` → `'刷新'`(设计去 emoji);计划批准即授权。其余 24 个用例断言零改动
- **Element Plus**:lobby 内 el-card/el-button/el-input 换原生 button/input + 设计样式;ElMessage 保留(全 app 约定);EP 全局注册不动,其他页面不受影响
- **oklch 相对色**:生产端全部预计算为静态 rgba/hex 等值,保旧浏览器兼容(设计稿里的 oklch(from …) 不进生产)
- **状态逻辑**:骨架屏仅 `refreshing && rooms.length===0 && !listError`(避免 5s 轮询闪骨架);`listError` 仅手动刷新/首载失败置位,轮询失败仍静默(契约);空态 CTA「创建房间」复用 handleCreate
- **a11y 提升**:分区卡/可玩游戏入口由 div 改 button(键盘可达 + focus-visible);ZoneGrid 内 h3 改 span(button 内 phrasing content 合法性);prefers-reduced-motion 全动效降级
- **Tweaks 面板不进生产**(设计稿专用)
- **验证口径**:npm test + npm run build(vue-tsc);浏览器真机验收需后端(:8080)+登录态(requiresAuth),按 skill 规定须用户显式请求后另行执行