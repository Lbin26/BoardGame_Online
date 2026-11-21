# design-server — 桌游设计助手工作台服务

无状态 v1：样本浏览、机制速查、文档校验、配置驱动的数值模拟（dice/cards/economy/conflict）与参数扫描。
数值逻辑单一来源：直接编译引用 `tools/boardgame-design` 源码，报告与 CLI 逐位一致。
对话式设计请在 Claude Code 使用 `/design-boardgame`。

## 生产运行（三步）

前置：`tools/boardgame-design` 已 `npm install`（本服务编译引用其源码与依赖）。

```bash
# ① 构建前端
cd boardgame-ui && npm install && npm run build

# ② 构建服务（tsc 双包编译 + 数据资产拷贝）
cd tools/design-server && npm install && npm run build

# ③ 启动（单进程：静态托管 + SPA + /design-api + 反代 Java 后端）
npm start
```

启动成功后控制台输出工作台网址（G6），例如：

```
桌游设计助手工作台 → http://localhost:8090/design
```

生产模式（`NODE_ENV=production` 或 `--serve-ui`）下：
- `/design-api/*` 本地处理；
- `/api` `/auth` `/system` 反向代理到 Java 后端（默认 `http://127.0.0.1:8080`）；
- `/ws` WebSocket upgrade 透传；
- 其余路径托管 `boardgame-ui/dist`，未命中回退 index.html（SPA）。

## 开发模式（双进程）

```bash
cd tools/design-server && npm run dev   # 默认 8090，仅 /design-api
cd boardgame-ui && npm run dev          # 5173，vite 代理 /design-api → 8090
```

vite 启动时同样在控制台打印 `桌游设计助手工作台 → http://localhost:5173/design`。

## 环境变量

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `DESIGN_PORT` | `8090` | 监听端口；被占用自动 +1 顺延（最多 10 次） |
| `DESIGN_REPO_ROOT` | 自动向上查找 | 仓库根（须含 `design/samples` 与 `tools/boardgame-design`） |
| `DESIGN_UI_DIST` | `<repoRoot>/boardgame-ui/dist` | 前端产物目录 |
| `DESIGN_UPSTREAM` | `http://127.0.0.1:8080` | Java 后端反代目标 |
| `DESIGN_SIM_BUDGET_MS` | `15000` | 单次模拟墙钟预算，超时 terminate → 408 |
| `DESIGN_SIM_HEAP_MB` | `1024` | 模拟 worker 堆上限（resourceLimits）；超限 worker 死亡 → 500，不波及主进程 |
| `PUBLIC_URL` | 按模式推导 | 覆盖启动横幅里的访问网址 |

## 护栏（超限 → 400）

请求体 ≤512KB；runs ≤50000；economy turns ≤500；dice count ≤200、faces ≤64；
conflict maxRounds ≤200、单方 diceCount ≤20、hp ≤1000、faces ≤64；sweep values ≤20（逐行校验）；
cards deck 总数 ≤100000、draw ≤100000（logFact 缓存数组内存护栏）。
`__proto__` / `constructor` / `prototype` 键一律拒绝（文本前置 + 解析后自有键深度遍历，含 flow/引号/数组嵌套形态）。
模拟并发闸 1：占用中 → 503 + `Retry-After: 1`。墙钟预算 15s → 408；worker 堆上限兜底内存维度（OOM 降级为 500，进程存活）。
畸形百分号编码路径 → 400，进程存活。

## 测试

```bash
npm test        # vitest（含与 CLI 报告逐位一致的 parity 测试）
npm run typecheck
```