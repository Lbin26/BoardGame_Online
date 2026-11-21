# 游戏模块拆分实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `ruoyi-boardgame`（象棋）拆为 `ruoyi-game-common`（房间/聊天/WS 公共层）+ `ruoyi-xiangqi`（象棋）+ `ruoyi-dicego`（骨架），并抽前端公共 composable。

**Architecture:** 后端三个 Maven 模块，chess 和 dicego 依赖 game-common。公共层包含房间生命周期、清理、WS 连接管理与聊天；象棋保留 engine/座位/走子。前端把 WS 连接与聊天逻辑抽成 `useRoom`/`useChat` composable。

**Tech Stack:** Spring Boot (RuoYi-Vue-Plus)、Spring WebSocket、Jackson(tools.jackson)、Vue 3 + TS + Vitest。

**Spec:** `docs/superpowers/specs/2026-11-20-game-module-split-design.md`

## Global Constraints

- 仓库根：`D:\码上学习\Projects\presonal_projects\BoardGame_Online\BoardGame_Online`（git 仓库）。
- 每个 Task 结束必须 `mvn -pl ruoyi-modules/ruoyi-xiangqi -am test -q`（或对应模块）通过后才 commit。
- 公共层包名 `org.dromara.game.common`；象棋包名 `org.dromara.xiangqi`（Task 5 统一改名，前序任务中 Chess* 类名暂不变）。
- 不引入新第三方依赖；不改 RuoYi 框架模块代码。
- 迁移是"移动 + 改包名"，不重写逻辑；行为不得变化，现有测试全部保留并通过。
- 构建命令统一用 `mvn`（Windows bash 环境，非 mvnw）。

## Review Focus

- WS 消息协议不变：前端 `chess.ts` 依赖的 type 字符串（CHAT/HEARTBEAT/MOVE/…）拆分后必须原样；测试：拆分后手动跑一次 `XiangqiWebSocketHandlerTest`。
- 同名玩家跨房间不互踢、刷新替换旧连接不误清在线状态（`replaced` 标记）——移入公共基类时不得丢失；测试：连接生命周期用例随类迁移。
- 房间清理宽限（WAITING 30s / PLAYING 120s / FINISHED 60s）不变；测试：`RoomManagerCleanupTest` 迁移后通过。
- UTF-8 查询参数解码（中文玩家名）保留在公共基类 `extractParam`。
- dicego 骨架必须能被 admin 聚合构建，否则 Task 6 算失败。

---

### Task 1: 创建 ruoyi-game-common 模块骨架

**Files:**
- Create: `ruoyi-modules/ruoyi-game-common/pom.xml`
- Modify: `ruoyi-modules/pom.xml`（modules 加 `ruoyi-game-common`）

**Interfaces:**
- Produces: artifactId `ruoyi-game-common`，groupId `org.dromara`，version `${revision}`；后续任务把类放进 `src/main/java/org/dromara/game/common/`。

- [ ] **Step 1:** 参照 `ruoyi-modules/ruoyi-boardgame/pom.xml` 写 pom：parent 为 `ruoyi-modules`，artifactId `ruoyi-game-common`，description "桌游公共模块 - 房间/聊天/WS"；依赖仅 `ruoyi-common-core` + `spring-boot-starter-websocket`（若 boardgame pom 中 websocket 依赖写法不同，照抄其写法）。
- [ ] **Step 2:** `ruoyi-modules/pom.xml` 的 `<modules>` 加入 `<module>ruoyi-game-common</module>`。
- [ ] **Step 3:** 验证：`mvn -pl ruoyi-modules/ruoyi-game-common compile -q` → BUILD SUCCESS。
- [ ] **Step 4:** Commit: `feat: 新增 ruoyi-game-common 公共模块骨架`

### Task 2: 迁移共享 domain/enums 到 game-common

**Files:**
- Move: `ruoyi-boardgame/src/main/java/org/dromara/boardgame/enums/RoomStatus.java` → `ruoyi-game-common/src/main/java/org/dromara/game/common/enums/RoomStatus.java`
- Move: `enums/GameMessageType.java` → 同上包（检查枚举值：象棋专属值如 MOVE/RESIGN/UNDO/REMATCH 暂留，dicego 用不到也无害；若文件内有象棋注释一并带走，不改语义）
- Move: `domain/vo/GameMessage.java`、`domain/vo/RoomVO.java`、`domain/bo/CreateRoomBO.java`、`domain/bo/SeatBO.java` → `org.dromara.game.common.domain.vo/bo`
- Modify: ruoyi-boardgame 内所有 import 这些类的文件（约 8 个主类 + 4 个测试类）

**Interfaces:**
- Produces: `org.dromara.game.common.enums.RoomStatus/GameMessageType`、`org.dromara.game.common.domain.vo.GameMessage/RoomVO`、`org.dromara.game.common.domain.bo.CreateRoomBO/SeatBO`。Task 3-6 全部依赖。

- [ ] **Step 1:** git mv 六个文件到新路径，改 package 声明为 `org.dromara.game.common.*`。
- [ ] **Step 2:** boardgame 的 pom 加依赖 `ruoyi-game-common`；全模块 `import org.dromara.boardgame.enums.RoomStatus` 等改为新包名（用 grep 找全）。
- [ ] **Step 3:** 验证：`mvn -pl ruoyi-modules/ruoyi-boardgame -am test -q` → 全部 PASS。
- [ ] **Step 4:** Commit: `refactor: 迁移共享 domain/enums 到 ruoyi-game-common`

### Task 3: 抽取 GameRoom 基类与通用 GameRoomManager

**Files:**
- Create: `ruoyi-game-common/src/main/java/org/dromara/game/common/room/GameRoom.java`
- Create: `ruoyi-game-common/src/main/java/org/dromara/game/common/room/GameRoomManager.java`
- Move: `ruoyi-boardgame/.../manager/RoomManagerCleanupTest.java` → `ruoyi-game-common/src/test/java/org/dromara/game/common/room/GameRoomManagerCleanupTest.java`
- Modify: `ruoyi-boardgame/.../manager/ChessRoom.java`（extends GameRoom）、`RoomManager.java`（删除，ChessController 改注入 GameRoomManager<ChessRoom>）

**Interfaces:**
- Produces:
  - `GameRoom`（公共层）：持有 `roomId`、`status`、`lastActivityAt`、`connectedPlayers: Set<String>`；方法 `markConnected(String, boolean)`（子类可通过 protected 钩子 `onConnectedChanged(String, boolean)` 扩展）、`hasConnectedPlayers()`、`touch()`（protected）、`getStatus()/getRoomId()/getLastActivityAt()`。
  - `GameRoomManager<T extends GameRoom>`：构造注入 `Function<String, T> roomFactory`；方法 `createRoom(String playerName)`（内部 `roomFactory.apply(roomId)`，roomId 生成逻辑=现有 UUID 6 位大写）、`getRoom`/`removeRoom`/`listRoomIds`/`countWaiting`/`countPlaying`、`@PostConstruct startCleanup` / `@PreDestroy stopCleanup`、包级 `cleanup(long now)`，宽限常量原值（30s/120s/60s/15s）。
  - ChessRoom：保留 engine、red/black 座位、SeatResult、makeMove/resign/undoMove/resetForRematch/toVO/isInCheck 等；连接状态（redConnected/blackConnected）改为在 `onConnectedChanged` 里按 `getPlayerColor` 更新。
  - ChessController 注入 bean：在 chess 模块新增 `@Configuration` 提供 `@Bean GameRoomManager<ChessRoom> chessRoomManager()`（factory = `ChessRoom::new`）。

- [ ] **Step 1:** 先写/迁移失败测试：`GameRoomManagerCleanupTest` 移到 common 测试目录，包名改 `org.dromara.game.common.room`，用 `new GameRoomManager<>(roomId -> new TestRoom(roomId))` 构造（TestRoom 为测试内最小 GameRoom 子类）。运行 `mvn -pl ruoyi-modules/ruoyi-game-common test -q` → 编译失败（GameRoom 不存在）。
- [ ] **Step 2:** 实现 `GameRoom`：从 `ChessRoom.java:20-77` 抽出 roomId/status/connectedPlayers/lastActivityAt/markConnected/hasConnectedPlayers/touch；ChessRoom 改为 extends GameRoom，删除重复字段，加 `onConnectedChanged` 钩子维护 redConnected/blackConnected。
- [ ] **Step 3:** 实现 `GameRoomManager<T>`，逻辑从现 `RoomManager.java` 原样搬移，泛型化 rooms 类型为 `ConcurrentHashMap<String, T>`。
- [ ] **Step 4:** ChessController 与 `ChessWebSocketHandler` 中 `RoomManager` → `GameRoomManager<ChessRoom>`（chess 配置类提供 bean）。`RoomManagerCleanupTest` 中的 RoomManager 引用改为新类。
- [ ] **Step 5:** 删除旧 `RoomManager.java`。验证：`mvn -pl ruoyi-modules/ruoyi-boardgame -am test -q` → 全部 PASS。
- [ ] **Step 6:** Commit: `refactor: 抽取 GameRoom/GameRoomManager 到 game-common`

### Task 4: 抽取 AbstractGameWebSocketHandler

**Files:**
- Create: `ruoyi-game-common/src/main/java/org/dromara/game/common/ws/AbstractGameWebSocketHandler.java`
- Move: `ruoyi-boardgame/.../handler/XiangqiWebSocketHandlerTest.java` 拆分：连接/聊天/心跳用例迁到 `ruoyi-game-common/src/test/java/org/dromara/game/common/ws/AbstractGameWebSocketHandlerTest.java`；走子/悔棋/再来一局用例留在 chess 模块测试
- Modify: `ruoyi-boardgame/.../handler/ChessWebSocketHandler.java`

**Interfaces:**
- Produces:
  - `AbstractGameWebSocketHandler<T extends GameRoom>`：持有 `sessions` map（key `roomId:playerName`）、`GameMessage` 收发、`extractParam`（UTF-8）、`send/broadcast/broadcastExcept`。`afterConnectionEstablished`/`afterConnectionClosed`/`handleTextMessage` 完整实现：连接校验、replaced 替换、PLAYER_JOINED 自身同步+中立广播、PLAYER_LEAVE 广播、CHAT/HEARTBEAT 处理。抽象方法：`protected abstract GameRoom getRoom(GameRoomManager<T> mgr, String roomId)` 由子类返回自己的房间类型；`protected abstract void handleGameMessage(WebSocketSession session, T room, String roomId, String playerName, GameMessageType type, JsonNode node)` 处理游戏专属消息（MOVE/RESIGN/UNDO/REMATCH）。若泛型解析复杂，允许抽象类持有 `GameRoomManager<? extends GameRoom>` 并由子类构造传入——实现者按编译结果二选一，保持行为不变。
  - `ChessWebSocketHandler extends AbstractGameWebSocketHandler<ChessRoom>`：构造注入 chess 的 GameRoomManager；`handleGameMessage` switch 只留 MOVE/RESIGN/UNDO/REMATCH；`broadcastSeatChange` 保留（Controller 调用）。

- [ ] **Step 1:** 迁移失败测试到 common：用 TestRoom + 最小子类 Handler 覆盖 连接建立(缺参拒绝/房间不存在拒绝)、同名替换、CHAT 广播、HEARTBEAT 回应、断开 PLAYER_LEAVE。运行 → 编译失败。
- [ ] **Step 2:** 实现 `AbstractGameWebSocketHandler`，逻辑从 `ChessWebSocketHandler.java:41-96,157-162,220-278` 原样搬移。
- [ ] **Step 3:** `ChessWebSocketHandler` 改继承，删除已上移代码，游戏消息进 `handleGameMessage`。
- [ ] **Step 4:** 验证：`mvn -pl ruoyi-modules/ruoyi-boardgame -am test -q` → 全部 PASS（含迁移后的公共测试）。
- [ ] **Step 5:** Commit: `refactor: 抽取 AbstractGameWebSocketHandler 公共 WS 基类`

### Task 5: ruoyi-boardgame 重命名为 ruoyi-xiangqi（中国象棋）

**Files:**
- Rename: 目录 `ruoyi-modules/ruoyi-boardgame` → `ruoyi-modules/ruoyi-xiangqi`
- Modify: `ruoyi-xiangqi/pom.xml`（artifactId `ruoyi-xiangqi`，description "桌游模块 - 中国象棋联机对战"）、`ruoyi-modules/pom.xml`（module 名）、`ruoyi-admin/pom.xml`（依赖 artifactId）、根目录 `chess-server-pom.xml`（若引用旧 artifactId）
- Move: 所有 `org/dromara/boardgame` → `org/dromara/xiangqi`（含 test；target/ 不动）
- Rename 类：`ChessApplication→XiangqiApplication`、`ChessWebSocketConfig→XiangqiWebSocketConfig`、`ChessController→XiangqiController`、`ChessWebSocketHandler→XiangqiWebSocketHandler`、`ChessRoom→XiangqiRoom`、`ChessEngine→XiangqiEngine`、`ChessBoard→XiangqiBoard`、`ChessPiece→XiangqiPiece`、`BoardSnapshotVO→BoardSnapshotVO`（保留）、`ChessPieceVO→XiangqiPieceVO`；对应测试类同名前缀替换
- Rename 前端: `boardgame-ui/src/api/chess.ts` → `xiangqi.ts`，引用处（views/lobby、views/game、__tests__）同步

**Interfaces:**
- Produces: artifactId `ruoyi-xiangqi`，包 `org.dromara.xiangqi`，类前缀 `Xiangqi`。WS 端点路径与消息协议字符串不变（前端只改 import 文件名）。若 yml/配置中有 `boardgame`/`chess` 字样一并同步（端点路径除外）。

- [ ] **Step 1:** `git mv ruoyi-modules/ruoyi-boardgame ruoyi-modules/ruoyi-xiangqi`；`git mv` 包目录到 `org/dromara/xiangqi`（test 同理）。
- [ ] **Step 2:** 全模块 `package org.dromara.boardgame` → `org.dromara.xiangqi`，import 同步；类名按上面清单逐个 rename（含文件名、构造器、测试引用）。
- [ ] **Step 3:** 改 `ruoyi-xiangqi/pom.xml` artifactId；`ruoyi-modules/pom.xml` module 名；`ruoyi-admin/pom.xml` 依赖；检查 `chess-server-pom.xml` 与 yml 配置同步。
- [ ] **Step 4:** 前端 `git mv boardgame-ui/src/api/chess.ts boardgame-ui/src/api/xiangqi.ts`，更新 import；`grep -r "api/chess" boardgame-ui/src` → 无残留。
- [ ] **Step 5:** 验证：`mvn -pl ruoyi-modules/ruoyi-xiangqi -am test -q` → PASS；`grep -r "boardgame" ruoyi-modules ruoyi-admin --include="*.java" --include="pom.xml"` → 无残留（boardgame-ui 目录名不算）。
- [ ] **Step 6:** Commit: `refactor: ruoyi-boardgame 重命名为 ruoyi-xiangqi（中国象棋）`

### Task 6: ruoyi-dicego 骨架

**Files:**
- Create: `ruoyi-modules/ruoyi-dicego/pom.xml`（artifactId `ruoyi-dicego`，依赖 `ruoyi-game-common` + `ruoyi-common-core`）
- Create: `ruoyi-modules/ruoyi-dicego/src/main/java/org/dromara/dicego/package-info.java`（包声明占位）
- Modify: `ruoyi-modules/pom.xml`（加 module）

**Interfaces:**
- Consumes: `ruoyi-game-common`。Produces: dicego 后续开发入口；不含任何业务类。

- [ ] **Step 1:** 写 pom 与 package-info，注册 module。
- [ ] **Step 2:** 验证：`mvn -pl ruoyi-modules/ruoyi-dicego compile -q` → PASS。
- [ ] **Step 3:** Commit: `feat: 新增 ruoyi-dicego 骨架模块`

### Task 7: 前端抽 useRoom/useChat composable + dicego api 占位

**Files:**
- Create: `boardgame-ui/src/composables/useRoom.ts`、`boardgame-ui/src/composables/useChat.ts`
- Create: `boardgame-ui/src/api/dicego.ts`（导出空对象 + 注释占位）
- Modify: `boardgame-ui/src/views/lobby/index.vue`、`boardgame-ui/src/views/game/index.vue`（改用 composable；先读两个文件，把重复的 WS 连接/重连/房间列表逻辑与聊天收发逻辑分别抽入）

**Interfaces:**
- Produces:
  - `useRoom()`：返回 `{ rooms, loading, createRoom, joinRoom, refresh }`（具体字段以两视图中实际逻辑为准，实现者以去重为目标，签名允许按现实微调并在代码注释说明）。
  - `useChat(sendFn)`：返回 `{ messages, sendChat }`；`sendChat(text)` 空白文本不发。
- 约束：纯重构，不改变任何 UI 行为；`boardgame-ui/src/api/chess.ts` 的导出签名不变。

- [ ] **Step 1:** 先运行现有前端测试 `cd boardgame-ui && npm run test -- --run`（或 package.json 中的实际 test 脚本）确认基线 PASS。
- [ ] **Step 2:** 读 lobby 与 game 两个 view，识别 WS/房间/聊天重复逻辑，抽入 composables 并改两个 view 引用。
- [ ] **Step 3:** 新建 `api/dicego.ts` 占位。
- [ ] **Step 4:** 为 useChat 写单测（空文本不发送、收到 CHAT 追加 messages），放 `boardgame-ui/src/composables/__tests__/useChat.spec.ts`；运行全部前端测试 → PASS。
- [ ] **Step 5:** Commit: `refactor: 前端抽 useRoom/useChat composable 并加 dicego api 占位`

### Task 8: 全量验证

- [ ] **Step 1:** `mvn test -q`（根目录全量）→ PASS。
- [ ] **Step 2:** 前端全量测试 → PASS。
- [ ] **Step 3:** `mvn -pl ruoyi-admin spring-boot:run -q` 或按 README 启动方式启动，确认 admin 能聚合启动、`/chess` REST 与 WS 正常（能开房即视为通过）。
- [ ] **Step 4:** Commit（如有遗漏文件）: `chore: 模块拆分收尾`

---

## Self-Review 记录

- 规格覆盖：spec 的 5 点（common/chess/dicego/前端/依赖聚合）分别对应 Task 1-4、5、6、7、1&5&6。
- 类型一致性：`GameRoomManager<T extends GameRoom>` 在 Task 3 定义、Task 4/5 使用一致；bean 由 chess 配置类提供。
- 已知开放点：Task 4 中抽象类持有 manager 的泛型方式允许实现者二选一（已注明），是因为 Java 泛型自引用在此处有两种等价写法，行为不变。
