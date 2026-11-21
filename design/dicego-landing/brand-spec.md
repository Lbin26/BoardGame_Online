# DiceGo Brand Spec

来源:全部提取自本仓库代码与真实素材,无虚构。

## 品牌资产

| 资产 | 路径 | 说明 |
|---|---|---|
| Logo wordmark(真实 PNG,浅色字版本) | `assets/dicego-brand/logo-wordmark.png` | 复制自 `boardgame-ui/src/assets/logo-wordmark.png`。斜体运动感字标:"Dice" 银白(D 内含骰子点 + 蓝色箭头)、"G" 蓝、"o" 橙 + 橙色速度弧线。**深色背景专用**(字标主体为白色) |

- 平台名:**DiceGo**(中文语境可称 "DiceGo 桌游在线")
- 无官方 slogan → 落地页文案为设计提案,需用户确认
- 无官方产品截图 → hero 用**代码构建的真实 UI 风格棋盘 mock**(象棋界面元素来自 `boardgame-ui/src/views/game/index.vue`),标注为界面演示

## 色彩 token(现有应用,`boardgame-ui/src/styles/index.scss`)

| token | 值 | 用途 |
|---|---|---|
| `--color-primary` | `#e94560` | 品牌红( Element Plus 主色映射) |
| `--bg-base` | `#1a1a2e` | 深夜蓝底 |
| `--bg-gradient-end` | `#16213e` | 渐变终点蓝 |
| logo 蓝 | ≈ `#2f9fe0`(取色自 wordmark) | 仅 logo 资产内部 |
| logo 橙 | ≈ `#f5821f`(取色自 wordmark) | 仅 logo 资产内部 |

## 产品事实(代码核实,2026-11-20)

- 可玩:**象棋**(红方/黑方、轮走提示、将军、悔棋、认输、观战、对局聊天)
- 敬请期待:**围棋、五子棋**(首页卡片 `playable: false`,不得宣传为可玩)
- 大厅:创建房间、房间号加入、房间列表(等待中/对局中)、观战入口
- 实时:WebSocket(`ChessWebSocketHandler`)
- 技术栈:Vue3 + TS + Element Plus + Vite / Spring Boot(RuoYi-Vue-Plus 6.X,MIT)
- 无真实运营数据(用户数/对局数)→ 页面不放任何统计数字

## 字体

- 现有应用:Helvetica Neue / PingFang SC / Microsoft YaHei(正文栈,可沿用)
- 落地页 display 提案:ZCOOL QingKe HuangYou(本地自托管,见设计声明)