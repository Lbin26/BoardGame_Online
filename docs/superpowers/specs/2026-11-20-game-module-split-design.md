# 游戏模块拆分设计（game-common / chess / dicego）

日期：2026-11-20

## 意图

为后续多个桌游（dicego 等）复用公共能力，把象棋专属代码与游戏无关的房间/聊天代码分开。

## 现状

- 后端仅 `ruoyi-modules/ruoyi-boardgame`（包 `org.dromara.boardgame`），只含象棋：engine/、ChessController、ChessWebSocketHandler、RoomManager/ChessRoom、bo/vo/enums。
- 房间与聊天逻辑与象棋耦合但本身游戏无关。
- 前端 `boardgame-ui/src`：api/chess.ts、views/lobby、views/game 等，未抽象。
- dicego 后端代码尚不存在，仅前端 design 稿。

## 目标结构

```
ruoyi-modules/
├── ruoyi-game-common   org.dromara.game.common：GameRoomManager、GameRoom、CreateRoomBO/SeatBO/RoomVO、GameMessage/GameMessageType、WS handler 公共基类（连接管理、房间路由、聊天）
├── ruoyi-chess         org.dromara.chess：ChessEngine/ChessBoard/ChessPiece、ChessController、ChessWebSocketHandler（继承基类，仅走子消息）、PieceType 等象棋专属
└── ruoyi-dicego        骨架：controller/ws/engine 包结构，可启动无业务
```

依赖：chess、dicego → game-common；ruoyi-admin 聚合更新。

## 前端

- `api/`：chess.ts 保留，新增 dicego.ts 占位。
- lobby/房间/聊天逻辑抽公共 composable（useRoom、useChat）与组件，象棋复用。

## 取舍

- 聊天不独立成模块：与房间生命周期强耦合（进房才能聊），合入 game-common 最简单。
- WS 用统一端点 + 基类按消息类型分发，而非每游戏一个端点。

## 非目标

- 不实现任何 dicego 业务逻辑。
- 不改数据库/RuoYi 框架模块。

## 风险

- 迁移期间保持可编译可运行；原 ruoyi-boardgame 先改名迁移，验证后再删除。
- chess-server-pom.xml（独立运行备用 pom）需同步更新模块名。
