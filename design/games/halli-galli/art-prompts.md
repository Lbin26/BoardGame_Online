# 德国心脏病 · 水果素材提示词包 v2

> **v2 变更（2026-11-21）**：水果组合定为 **苹果 / 柠檬 / 樱桃 / 葡萄**，素材改由用户提供成图、走抠图流程，不再用本包生成。v1 的「香蕉 / 草莓 / 柠檬 / 李子」组合**已废弃**，仅作历史保留（见文末）。改判理由见 `journal.md` 的 A6。

风格段落与 Splendor 宝石 token 保持同一套视觉语言（粗描边 + 左上方高光 + 平涂两档明暗）。改动风格必须同步两边，否则大厅里两款游戏的素材摆在一起会立刻露馅。

## 1. 风险前置

| 风险 | 解法 |
|---|---|
| 模型把 4 种水果画成一组静物（同框叠放） | 明确「单主体，画面内只有这一种水果」，一张图只出一种 |
| 苹果与樱桃同为红色系，缩小后混淆 | 苹果给"圆果 + 单梗单叶"，樱桃给"双果 + 长柄分叉"；剪影差异优先于色相 |
| 带投影导致抠图留脏边 | 从边缘泛洪抠底，并逐字排除 shadow / 地面 / 环境光遮蔽 |
| 画进光泽特效、水珠、装饰叶 | 负面词排除，只保留属于识别特征的梗与蒂 |
| 成图角落带生成工具水印 | 抠图前先把右下角低饱和区域刷白，否则水印会被算进主体 bbox、裁切框顶到画布边缘 |

## 2. 变量槽位表

| fruit_code | 中文 | 颜色线索 | 剪影线索 | emoji 兜底 |
|---|---|---|---|---|
| `apple` | 苹果 | 朱红 `#D42A2A` | 圆果，单梗 + 一片绿叶 | 🍎 |
| `lemon` | 柠檬 | 明黄 `#F5D33A` | 椭圆两端带尖，两片叶 | 🍋 |
| `cherry` | 樱桃 | 深红 `#C41E2A` | 双果并排，长柄分叉 | 🍒 |
| `grape` | 葡萄 | 紫 `#6B3F8F` | 圆珠成串堆叠，短梗 | 🍇 |

## 3. 风格锚点（逐字复用）

中文：

```
2D 卡牌游戏图标，粗深色描边（视觉约 6px），主色平涂加两档明暗，单一光源自左上方，高光处一枚柔和椭圆亮斑，无网格渐变，无噪点纹理，主体居中约占画面 72%，正视角平放，纯白背景，无阴影、无地面、无投影，无文字、无边框、无水印。
```

English：

```
2D card-game icon, thick dark outline (~6px visual weight), flat base color with two shading tiers, single light source from upper-left, one soft oval highlight, no mesh gradient, no texture noise, subject centered occupying 72% of the canvas, straight-on flat view, plain white background, no shadow, no ground, no cast shadow, no text, no border, no watermark
```

## 4. 提示词模板

```
【水果英文名】, single fruit only, [颜色线索], [剪影线索], 2D card-game icon, thick dark outline (~6px visual weight), flat base color with two shading tiers, single light source from upper-left, one soft oval highlight, no mesh gradient, no texture noise, subject centered occupying 72% of the canvas, straight-on flat view, plain white background, no shadow, no ground, no cast shadow, no text, no border, no watermark
```

## 5. 负面提示词

```
影子, 投影, 落地阴影, 环境光遮蔽, 地面, 桌面, 水珠, 光泽特效, 发光, 多主体, 静物组合, 篮子, 叶子堆, 文字, 边框, 水印, 渐变网格, 噪点
shadow, cast shadow, contact shadow, ambient occlusion, ground, table surface, water drops, glossy effect, glow, multiple subjects, still-life arrangement, basket, text, border, watermark, mesh gradient, noise
```

## 6. 抠图与归档

- 源图：`design/games/halli-galli/art-src/{code}.jpg`（1920×1920 白底）
- 处理脚本：`tools/cut-fruits.py`，三步——右下角低饱和区刷白去水印 → 四边泛洪抠背景（阈值 亮度≥222、饱和度≤26，主体内部高光不连通边缘故不会被误抠）→ 按 alpha bbox 裁正方形并留 6% 边距
- 成品：`boardgame-ui/public/cards/halli-galli/fruits/{code}.webp`，256×256 透明底，quality 92
- 前端引用：`fruitImage(code)` → `/cards/halli-galli/fruits/${code}.webp`；加载失败自动回退 emoji

## 7. 验收自检

- [x] 4 张背景透明、四角无灰字水印残留
- [x] 描边粗细与高光方向一致
- [x] 苹果与樱桃在 40px 下仍可区分
- [x] 4 张体积均在 10–16 KB

## 8. 降级方案

- **L1**：图片加载失败 → 回退 emoji（`🍎🍋🍒🍇`），已实现（`imgFailed` 集合）
- **L2**：素材风格不统一 → 全部走 emoji
- **L3**：纯 CSS 色块 + emoji

---

## 附：v1（已废弃）

v1 采用「香蕉 / 草莓 / 柠檬 / 李子」，槽位为 `banana`（弯月三根）、`strawberry`（心形 + 绿蒂）、`lemon`、`plum`（正圆带凹沟）。废弃原因：用户已提供苹果/柠檬/樱桃/葡萄四张成图；且香蕉与柠檬同为黄色系，小尺寸有混淆风险。旧槽位表如需查阅请走 git 历史。
