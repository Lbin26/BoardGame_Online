# 桌游设计助手工具链 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建成 `design-boardgame` 项目 skill + TS 数值模拟工具包 + 7 份种子样本档案，让 Claude Code 成为可从零设计新桌游（出创意/打磨点子、样本 remix、数据化平衡验证、双出口导出）的设计助手。

**Architecture:** agent = Claude Code 本身；工具 = markdown skill（工作流+知识库）+ 独立 npm 包 `tools/boardgame-design/`（zod schema 校验的结构化游戏格式 + 配置驱动的可复现模拟器）+ `design/samples/` 样本库与 `design/games/` 设计工作区。game.yaml 是数值单一事实来源，sim 配置引用它并做覆盖合并。

**Tech Stack:** TypeScript (strict) + Node 20 + vitest + tsx + yaml + zod；skill 为纯 markdown。

**Spec:** `docs/superpowers/specs/2026-11-20-boardgame-design-agent-tools-design.md`（含"实现语义裁定"三条，执行时必读）

## Global Constraints

- **不 commit（用户约定）**：本计划所有任务不含 commit 步骤，以验证步骤收尾；也不动 git 状态。
- 不引入 Node 之外的运行环境；工具包依赖仅限 `yaml`、`zod`（deps）+ `tsx`、`typescript`、`vitest`、`@types/node`（dev）。
- TDD：每个代码任务先写失败测试、跑红、再实现、跑绿。
- 不修改后端、数据库、`catalog.ts` 及前端任何既有文件；`catalog.ts` 只被测试只读 import。
- 环境 Windows + Git Bash；所有文件 UTF-8；路径用相对路径 + `path.join`，禁止硬编码盘符。
- 报告、规则书、skill 文案默认中文；机制标签中英对照（`name` + `nameEn`）。
- 样本档案只存提炼后的机制/结构/数值，禁止照搬规则书原文表达与美术。
- 机制词汇对齐 catalog.ts 7 区；分区判例沿用（Splendor→card、Dice Throne/Avalon→narrative、Brass→heavy）。
- 模拟必须可复现：带种子 PRNG；落盘报告必须来自配置驱动运行；ad-hoc 直跑输出须带"AD-HOC（非证据）"标注。
- 调研可离线降级：纯知识推理时置信度标 `medium`/`low`，禁止编造来源。

## Review Focus

1. **中文 YAML 在 Windows 下的解析**（UTF-8 中文、BOM、CRLF）→ loader 必须正确解析并校验含中文的文档，不得乱码或崩溃。（Task 3 测试）
2. **sim 配置引用悬空**：game 路径不存在、`from` 点路径在文档中不存在 → 必须抛出含路径信息的明确错误，不得静默得到 undefined 继续跑。（Task 4 测试）
3. **deepMerge 语义**：对象递归合并、**数组整体替换**、标量覆盖；sweep 逐值覆盖不得污染基础 params。（Task 4、Task 9 测试）
4. **经济策略循环安全性**：无行动可负担时进入 stall 计数而非死循环；consume 不得超过当前库存（钳到 0）。（Task 7 测试）
5. **蒙特卡洛可复现与容差**：同种子两次运行结果逐位相同；容差断言必须固定种子且留足边际，不得 flaky。（Task 5、Task 8 测试）

---

### Task 1: 工具包脚手架 + 带种子 PRNG

**Files:**
- Create: `tools/boardgame-design/package.json`
- Create: `tools/boardgame-design/tsconfig.json`
- Create: `tools/boardgame-design/vitest.config.ts`
- Create: `tools/boardgame-design/src/rng.ts`
- Test: `tools/boardgame-design/tests/rng.spec.ts`

**Interfaces:**
- Consumes: 无
- Produces:
  - `mulberry32(seed: number): () => number` — 返回 [0,1) 均匀分布，同种子序列逐位相同
  - `randInt(rng: () => number, min: number, max: number): number` — 含两端
  - npm scripts（cwd = `tools/boardgame-design/`）：`npm test` = `vitest run`；`npm run typecheck` = `tsc --noEmit`；`npm run sim` = `tsx src/cli.ts`（cli 在 Task 10 建，本任务先占位脚本名）

- [ ] **Step 1: 写 package.json / tsconfig / vitest 配置**

package.json 要点：`name: "boardgame-design-tools"`、`private: true`、`type: "module"`、scripts 如上；deps `yaml@^2`、`zod@^3`；devDeps `tsx@^4`、`typescript@^5.7`、`vitest@^2`、`@types/node@^22`（与 boardgame-ui 版本带对齐）。
tsconfig：`strict: true`、`target: ES2022`、`module: ESNext`、`moduleResolution: Bundler`、`noEmit: true`、`types: ["node"]`、include `src` 与 `tests`。
vitest.config.ts：`test: { environment: 'node', include: ['tests/**/*.spec.ts'] }`。
然后 `npm install`（在工具包目录内）。

- [ ] **Step 2: 写失败测试** `tests/rng.spec.ts`

```ts
import { describe, it, expect } from 'vitest'
import { mulberry32, randInt } from '../src/rng'

describe('mulberry32', () => {
  it('同种子序列逐位相同', () => {
    const a = mulberry32(42), b = mulberry32(42)
    for (let i = 0; i < 1000; i++) expect(a()).toBe(b())
  })
  it('值域 [0,1)，不同种子序列不同', () => {
    const r = mulberry32(1)
    for (let i = 0; i < 1000; i++) { const v = r(); expect(v).toBeGreaterThanOrEqual(0); expect(v).toBeLessThan(1) }
    expect(mulberry32(1)()).not.toBe(mulberry32(2)())
  })
  it('randInt 含两端且落在区间内', () => {
    const r = mulberry32(7)
    const seen = new Set<number>()
    for (let i = 0; i < 500; i++) { const v = randInt(r, 1, 6); expect(v).toBeGreaterThanOrEqual(1); expect(v).toBeLessThanOrEqual(6); seen.add(v) }
    expect(seen.size).toBe(6)
  })
})
```

- [ ] **Step 3: 跑测试确认红**

Run: `npm test`（cwd `tools/boardgame-design/`）
Expected: FAIL — `src/rng` 不存在。

- [ ] **Step 4: 实现 `src/rng.ts`**

```ts
export function mulberry32(seed: number): () => number {
  let a = seed >>> 0
  return () => {
    a |= 0; a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
export function randInt(rng: () => number, min: number, max: number): number {
  return min + Math.floor(rng() * (max - min + 1))
}
```

- [ ] **Step 5: 跑测试确认绿 + typecheck**

Run: `npm test && npm run typecheck`
Expected: 3 tests PASS，tsc 无错误。

---

### Task 2: 机制词汇数据 + 加载器 + catalog 一致性

**Files:**
- Create: `tools/boardgame-design/data/mechanisms.yaml`
- Create: `tools/boardgame-design/src/schema/vocabulary.ts`
- Test: `tools/boardgame-design/tests/vocabulary.spec.ts`

**Interfaces:**
- Consumes: Task 1 的 npm/test 环境；只读 import `../../../boardgame-ui/src/constants/catalog`（相对 tests 目录，纯 TS 无 Vue 依赖）
- Produces:
  - `interface MechanismEntry { key: string; name: string; nameEn: string; zones: string[]; aliases?: string[] }`
  - `loadVocabulary(): MechanismEntry[]` — 读包内 `data/mechanisms.yaml`（用 `new URL('../../data/mechanisms.yaml', import.meta.url)` 定位，cwd 无关）
  - `coveredTags(entries: MechanismEntry[]): Set<string>` — 全部 `name` + `aliases` 的并集
  - `assertCatalogCoverage(entries: MechanismEntry[], tags: string[]): void` — 有未覆盖标签则 throw，消息列出缺失项
  - 词汇 key 用 kebab-case 英文（如 `worker-placement`）；`name` 为中文标签

- [ ] **Step 1: 建 `data/mechanisms.yaml`**

结构（条目措辞是内容工作，但**覆盖范围是硬约束**）：

```yaml
zones: [party, family, strategy, card, narrative, abstract, heavy]
mechanisms:
  - key: worker-placement
    name: 工人放置
    nameEn: Worker Placement
    zones: [strategy]
  # ...
```

必须收录：catalog.ts `ZONES[*].mechanisms` 与 `GAMES[*].mechanisms` 的**每一个**中文标签（含复合串 `资源管理+引擎+路线+推轨的复合体`，作为一个条目、zones: [heavy]）。另加 ≥10 个设计扩展机制，key 固定为：`auction`（拍卖竞价）、`secret-objectives`（秘密目标）、`progress-track`（进度轨）、`dice-placement`（骰子放置）、`pick-up-and-deliver`（拾取运送）、`stock-holding`（股份持有）、`time-track`（时间轨）、`variable-player-powers`（可变玩家能力）、`grid-movement`（网格移动）、`push-your-luck`（押注博弈）。每个扩展机制声明 zones（可多区）。总条目 30~45。

**样本任务（Task 13/14/15）会引用的 key 映射钉死如下**（其余 catalog 标签的 key 由实现者按同风格自定，但下列不得更改）：

| catalog 标签 | key | 备注 |
|---|---|---|
| 抽象棋 | `abstract-chess` | |
| 模式构建 | `pattern-building` | |
| 逻辑推理 | `logical-deduction` | `推理/演绎` 作其 aliases |
| 引擎构建 | `engine-building` | |
| 成套收集 | `set-collection` | |
| 骰子驱动 | `dice-driven` | |
| 隐藏身份 | `hidden-roles` | |
| 投票 | `voting` | |
| 网络构建 | `network-building` | |
| 经济/资源管理 | `economic-management` | |
| 手牌管理 | `hand-management` | `手牌管理（轻卡驱）` 作其 aliases |
| 工人放置 | `worker-placement` | |
| 角色能力（不对称） | — | 作 `variable-player-powers` 的 aliases |
| 卡牌辅助 | `card-assist` | |

- [ ] **Step 2: 写失败测试** `tests/vocabulary.spec.ts`

```ts
import { describe, it, expect } from 'vitest'
import { ZONES, GAMES } from '../../../boardgame-ui/src/constants/catalog'
import { loadVocabulary, coveredTags, assertCatalogCoverage } from '../src/schema/vocabulary'

const catalogTags = [...new Set([...ZONES.flatMap(z => z.mechanisms), ...GAMES.flatMap(g => g.mechanisms ?? [])])]

describe('mechanism vocabulary', () => {
  const entries = loadVocabulary()
  it('覆盖 catalog 全部标签', () => { expect(() => assertCatalogCoverage(entries, catalogTags)).not.toThrow() })
  it('key 唯一、kebab-case，zones 均在 7 区内且非空', () => {
    const keys = entries.map(e => e.key)
    expect(new Set(keys).size).toBe(keys.length)
    for (const e of entries) {
      expect(e.key).toMatch(/^[a-z][a-z0-9]*(-[a-z0-9]+)*$/)
      expect(e.zones.length).toBeGreaterThan(0)
      for (const z of e.zones) expect(ZONES.map(zz => zz.key)).toContain(z)
    }
  })
  it('含 10 个指定扩展机制与钉死的样本 key，条目总数 30~45', () => {
    for (const k of ['auction','secret-objectives','progress-track','dice-placement','pick-up-and-deliver','stock-holding','time-track','variable-player-powers','grid-movement','push-your-luck',
                     'abstract-chess','pattern-building','logical-deduction','engine-building','set-collection','dice-driven','hidden-roles','voting','network-building','economic-management','hand-management','worker-placement','card-assist'])
      expect(entries.map(e => e.key)).toContain(k)
    expect(entries.length).toBeGreaterThanOrEqual(30)
    expect(entries.length).toBeLessThanOrEqual(45)
  })
  it('aliases 覆盖推理/演绎、手牌管理（轻卡驱）、角色能力（不对称）', () => {
    const tags = coveredTags(entries)
    for (const t of ['推理/演绎','手牌管理（轻卡驱）','角色能力（不对称）']) expect(tags.has(t)).toBe(true)
  })
  it('assertCatalogCoverage 对缺失标签 throw 且列出缺失项', () => {
    expect(() => assertCatalogCoverage(entries, ['不存在的机制'])).toThrow(/不存在的机制/)
  })
})
```

- [ ] **Step 3: 跑测试确认红**

Run: `npm test -- tests/vocabulary.spec.ts`
Expected: FAIL — `src/schema/vocabulary` 不存在。

- [ ] **Step 4: 实现 `src/schema/vocabulary.ts`**

签名见 Interfaces。YAML 用 `yaml` 包 `parse`；文件读取 `fs.readFileSync(fileURLToPath(...), 'utf-8')`。`coveredTags` 收集 name+aliases；`assertCatalogCoverage` 求差集、非空则 `throw new Error('词汇未覆盖 catalog 标签: ' + missing.join(', '))`。

- [ ] **Step 5: 跑测试确认绿**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS（含 Task 1）。

---

### Task 3: 游戏/样本 zod schema + YAML loader + validate

**Files:**
- Create: `tools/boardgame-design/src/schema/game.ts`
- Create: `tools/boardgame-design/src/schema/load.ts`
- Test: `tools/boardgame-design/tests/schema.spec.ts`
- Test: `tools/boardgame-design/tests/fixtures/mini-game.yaml`（合法最小设计文档，含中文）
- Test: `tools/boardgame-design/tests/fixtures/mini-sample.yaml`（合法样本 = 设计文档 + meta.zone + analysis）

**Interfaces:**
- Consumes: Task 2 `loadVocabulary()`、`coveredTags` 无关——机制校验按 **key** 属于词汇 key 集合
- Produces:
  - `GameDocSchema`（zod）→ `type GameDoc = z.infer<typeof GameDocSchema>`，形状：

```ts
{
  schemaVersion: 1,
  meta: { key: string /* ^[a-z][a-zA-Z0-9]*$，camelCase 对齐 catalog */, name: string,
          players: { min: number /* int ≥1 */, max: number /* int ≥min */ },
          durationMin: number /* int >0 */, targetWeight?: number /* 1~5 */, zone?: string /* 7 区 key */ },
  mechanisms: string[] /* min 1，值为词汇 key */,
  components: {
    resources?: { key: string, name: string, supply?: number }[],
    dice?: { key: string, faces: (string|number)[] /* min 1 */, count: number /* int ≥1 */ }[],
    decks?: { key: string, name?: string, cards: { key: string, name?: string, count: number /* int ≥1 */,
              cost?: Record<string, number>, gain?: Record<string, number>, vp?: number }[] }[]
  },
  turnStructure: { phases: { key: string, name: string, summary: string }[] /* min 1 */ },
  subsystems: {
    economy?: { resources: string[] /* min 1 */, initial?: Record<string, number>,
                perTurn?: { resource: string, produce: number, consume: number }[],
                actions?: { key: string, name?: string, cost: Record<string, number>,
                            gain?: Record<string, number>, vp?: number }[],
                stallK?: number /* int ≥1，默认 3 */ },
    conflict?: { attacker: Side, defender: Side, maxRounds?: number /* int ≥1，默认 100 */ },
    scoring?: { endTrigger: string, vpSources: { key: string, name?: string, description: string }[] }
  },
  analysis?: { whyFun: string, remixable: string[] /* min 1 */, pitfalls?: string,
               sources: { title: string, url?: string }[] /* min 1 */, confidence: 'high'|'medium'|'low' }
}
// Side = { diceCount: number /* int ≥0 */, faces: (string|number)[] /* min 1 */,
//          hitSymbols: (string|number)[] /* min 1 */, hp: number /* int ≥1 */, bonus?: number /* int，默认 0 */ }
```

  - `SampleDocSchema` = GameDocSchema 强化：`meta.zone` 必填（7 区 enum）、`analysis` 必填 → `type SampleDoc`
  - `loadYamlFile(path: string): unknown` — UTF-8 读取 + 去 BOM + `yaml.parse`
  - `loadGameDoc(path: string): GameDoc` / `loadSampleDoc(path: string): SampleDoc` — zod parse，失败 throw 且消息含文件路径与 zod issue 摘要
  - `validateDoc(path: string): { ok: true, doc: GameDoc | SampleDoc } | { ok: false, errors: string[] }` — 先按 SampleDoc 试（有 analysis 或 zone 时），否则 GameDoc；再校验 mechanisms 每个 key ∈ 词汇 key 集合、`subsystems.economy.resources` 与 actions 的 cost/gain 资源名 ∈ `components.resources` key（若声明了 resources）；错误收集为字符串数组不 throw
  - zod 默认值（stallK=3、maxRounds=100、bonus=0）用 `.default()`，parse 后字段必有值

- [ ] **Step 1: 写 fixtures**

`mini-game.yaml`：schemaVersion 1；meta `{ key: miniGame, name: 迷你测试游戏, players: {min: 2, max: 4}, durationMin: 30 }`；mechanisms `[worker-placement]`；components.resources `[{key: gold, name: 金币}]`、dice `[{key: d6, faces: [1,2,3,4,5,6], count: 2}]`；turnStructure.phases `[{key: income, name: 收入, summary: 每回合获得金币}]`；subsystems.economy `{ resources: [gold], initial: {gold: 5}, perTurn: [{resource: gold, produce: 2, consume: 1}], actions: [{key: buy, cost: {gold: 3}, gain: {gold: 0}, vp: 1}] }`。文件故意带 BOM 与 CRLF（验证 Review Focus 1）。
`mini-sample.yaml`：同上 + `meta.zone: strategy` + analysis `{ whyFun: 测试, remixable: [测试特征], sources: [{title: 测试来源}], confidence: medium }`。

- [ ] **Step 2: 写失败测试** `tests/schema.spec.ts`

```ts
import { describe, it, expect } from 'vitest'
import { fileURLToPath } from 'node:url'
import { loadGameDoc, loadSampleDoc, validateDoc } from '../src/schema/load'

const fx = (n: string) => fileURLToPath(new URL(`./fixtures/${n}`, import.meta.url))

describe('schema & loader', () => {
  it('解析含中文/BOM/CRLF 的设计文档，默认值生效', () => {
    const doc = loadGameDoc(fx('mini-game.yaml'))
    expect(doc.meta.name).toBe('迷你测试游戏')
    expect(doc.subsystems.economy!.stallK).toBe(3)
  })
  it('样本文档 analysis/zone 必填校验通过', () => {
    const s = loadSampleDoc(fx('mini-sample.yaml'))
    expect(s.analysis!.confidence).toBe('medium')
    expect(s.meta.zone).toBe('strategy')
  })
  it('validateDoc 对合法文档 ok:true', () => {
    expect(validateDoc(fx('mini-game.yaml')).ok).toBe(true)
    expect(validateDoc(fx('mini-sample.yaml')).ok).toBe(true)
  })
  it('缺必填字段 → ok:false 且错误含路径', () => {
    const r = validateDoc(fx('mini-game.yaml') + '')  // 用临时改写文件：复制 fixture 去掉 meta.name 写临时文件再 validate
    // 实现本断言时：fs 复制 mini-game.yaml 内容、删除 name 行、写入 tests/fixtures/.tmp-invalid.yaml，validate 后删除
    expect(r.ok).toBe(false)
  })
  it('未知机制 key → ok:false 且错误列出该 key', () => {
    // 同上法造临时文件，mechanisms 改为 [not-a-mechanism]
    // expect(errors.join()).toContain('not-a-mechanism')
  })
  it('economy 引用未声明资源 → ok:false', () => {
    // 临时文件 perTurn.resource 改为 silver（components 只有 gold）
  })
  it('文件不存在 → loadGameDoc throw 含路径', () => {
    expect(() => loadGameDoc('no/such/file.yaml')).toThrow(/no\/such\/file.yaml|no.such.file.yaml/)
  })
})
```

（注释处实现为完整断言：用 `fs.writeFileSync` 造临时变体 fixture，`afterEach` 清理；每个变体断言 `ok:false` 且 errors 提及相关字段/key。）

- [ ] **Step 3: 跑测试确认红**

Run: `npm test -- tests/schema.spec.ts`
Expected: FAIL — `src/schema/load` 不存在。

- [ ] **Step 4: 实现 `game.ts` 与 `load.ts`**

签名与形状见 Interfaces。BOM 处理：读入后 `content.replace(/^﻿/, '')`。zod issue 摘要：`issues.map(i => i.path.join('.') + ': ' + i.message)`。validateDoc 的资源交叉校验只在 `components.resources` 存在时执行。

- [ ] **Step 5: 跑测试确认绿**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS。

---

### Task 4: sim 配置 schema + 参数解析（单一事实来源）

**Files:**
- Create: `tools/boardgame-design/src/schema/simConfig.ts`
- Test: `tools/boardgame-design/tests/sim-config.spec.ts`

**Interfaces:**
- Consumes: Task 3 `loadGameDoc`、`loadYamlFile`、`GameDoc`
- Produces:
  - `SimConfigSchema` → `type SimConfig = { game: string, sim: 'dice'|'cards'|'economy'|'conflict', from: string, seed?: number /* 默认 42 */, runs?: number /* int ≥1，默认 10000 */, overrides?: Record<string, unknown>, out?: string }`
  - `SweepConfigSchema` → `type SweepConfig = SimConfig & { sweep: { param: string, values: (number|string)[] /* min 2 */ } }`
  - `loadSimConfig(path: string): SimConfig` / `loadSweepConfig(path: string): SweepConfig`
  - `getAtPath(obj: unknown, path: string): unknown` — 点路径（仅对象段，不支持数组下标）；任一段缺失 throw `Error('路径不存在: ' + path)`
  - `setAtPath(obj: Record<string, unknown>, path: string, value: unknown): Record<string, unknown>` — **返回新对象**（不可变），中间段缺失则建空对象
  - `deepMerge(base: unknown, override: unknown): unknown` — 双方均纯对象→递归合并；**数组整体替换**；其余 override 覆盖
  - `resolveParams(config: SimConfig, configDir: string): { params: Record<string, unknown>, game: GameDoc, seed: number, runs: number }` — `path.resolve(configDir, config.game)` 加载游戏文档 → `getAtPath(game, config.from)` → `deepMerge(params, config.overrides ?? {})`；game 文件不存在时 throw 含解析后路径

- [ ] **Step 1: 写失败测试** `tests/sim-config.spec.ts`

断言组（用 Task 3 的 `mini-game.yaml` fixture 作 game 引用，配置写在临时目录）：
1. `getAtPath({a:{b:1}}, 'a.b') === 1`；`getAtPath({a:{}}, 'a.x')` throw `/a.x/`
2. `deepMerge({a:{x:1,y:2}, arr:[1,2]}, {a:{y:3}, arr:[9]})` → `{a:{x:1,y:3}, arr:[9]}`（数组替换，Review Focus 3）
3. `setAtPath` 不可变：原对象不被修改；`setAtPath({}, 'a.b', 5)` → `{a:{b:5}}`
4. `resolveParams`：config `{game: '<fixture相对路径>', sim: 'economy', from: 'subsystems.economy', overrides: {initial: {gold: 99}}}` → params.initial.gold === 99 且 perTurn 保留（来自 game.yaml，证明单一事实来源）；seed 默认 42、runs 默认 10000
5. `from: 'subsystems.nonexistent'` → throw `/subsystems.nonexistent/`（Review Focus 2）
6. `game: 'no-such.yaml'` → throw 含 `no-such.yaml`（Review Focus 2）

- [ ] **Step 2: 跑测试确认红**

Run: `npm test -- tests/sim-config.spec.ts`
Expected: FAIL — 模块不存在。

- [ ] **Step 3: 实现 `simConfig.ts`**

签名见 Interfaces。纯对象判定：`typeof v === 'object' && v !== null && !Array.isArray(v)`。

- [ ] **Step 4: 跑测试确认绿**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS。

---

### Task 5: 骰子池模拟器（精确分布 + MC）

**Files:**
- Create: `tools/boardgame-design/src/sim/dice.ts`
- Test: `tools/boardgame-design/tests/dice.spec.ts`

**Interfaces:**
- Consumes: Task 1 `mulberry32`
- Produces:
  - `type DiceParams = { count: number, faces: (string|number)[], symbol: string|number, op: '>='|'>'|'<='|'<'|'=', value: number }`
  - `diceExact(p: DiceParams): { pmf: number[] /* pmf[k]=P(恰好k个匹配), k=0..count */, probability: number /* 满足 op 的概率 */, mean: number }`
  - `diceMC(p: DiceParams, seed: number, runs: number): { probability: number, mean: number }`
  - 语义：每颗骰子独立掷 faces 均匀分布，统计等于 symbol 的个数 X，probability = P(X op value)

- [ ] **Step 1: 写失败测试** `tests/dice.spec.ts`

```ts
import { describe, it, expect } from 'vitest'
import { diceExact, diceMC } from '../src/sim/dice'

const sword = { count: 5, faces: ['sword','sword','shield','blank','blank','blank'], symbol: 'sword', op: '>=' as const, value: 3 }

describe('dice', () => {
  it('精确分布对二项解析值：5 骰 p=1/3，P(X>=3)', () => {
    const r = diceExact(sword)
    const p = 1/3, n = 5
    const binom = (k: number) => { let c = 1; for (let i = 0; i < k; i++) c = c * (n - i) / (i + 1); return c }
    const expectP = [3,4,5].reduce((s,k) => s + binom(k) * p**k * (1-p)**(n-k), 0)
    expect(r.probability).toBeCloseTo(expectP, 10)
    expect(r.mean).toBeCloseTo(n * p, 10)
    expect(r.pmf.reduce((a,b)=>a+b,0)).toBeCloseTo(1, 10)
  })
  it('数值面：2d6 掷出至少一个 6 的概率 = 11/36', () => {
    const r = diceExact({ count: 2, faces: [1,2,3,4,5,6], symbol: 6, op: '>=', value: 1 })
    expect(r.probability).toBeCloseTo(11/36, 10)
  })
  it('MC 与精确值接近（固定种子，容差 0.02）', () => {
    const mc = diceMC(sword, 42, 20000)
    expect(Math.abs(mc.probability - diceExact(sword).probability)).toBeLessThan(0.02)
  })
  it('同种子 MC 逐位相同（Review Focus 5）', () => {
    expect(diceMC(sword, 42, 1000)).toEqual(diceMC(sword, 42, 1000))
  })
})
```

- [ ] **Step 2: 跑测试确认红**

Run: `npm test -- tests/dice.spec.ts`
Expected: FAIL — 模块不存在。

- [ ] **Step 3: 实现 `src/sim/dice.ts`**

精确：单骰匹配概率 p = faces 中 symbol 出现次数 / faces.length；pmf 用逐骰卷积（`next[k] = cur[k]*(1-p) + cur[k-1]*p`）；probability 按 op 对 pmf 求和；mean = count*p。MC：mulberry32(seed)，每 run 掷 count 次（`faces[Math.floor(rng()*faces.length)]`），统计满足 op 的频率。

- [ ] **Step 4: 跑测试确认绿**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS。

---

### Task 6: 抽卡概率模拟器（超几何）

**Files:**
- Create: `tools/boardgame-design/src/sim/cards.ts`
- Test: `tools/boardgame-design/tests/cards.spec.ts`

**Interfaces:**
- Consumes: 无（纯精确计算，不需要 RNG）
- Produces:
  - `hypergeometric(N: number, K: number, n: number, k: number): number` — P(抽 n 张恰含 k 张目标)；非法参数（k>n、k>K、n>N、负数）throw
  - `type CardsParams = { deck: { key: string, count: number }[], draw: number, target: string, op: '>='|'>'|'<='|'<'|'=', value: number }`
  - `cardsExact(p: CardsParams): { probability: number, expectedCount: number, expectedDrawsToFirst: number }` — target 不在 deck 中 throw；`expectedDrawsToFirst = (N+1)/(K+1)`（K=0 时为 Infinity）

- [ ] **Step 1: 写失败测试** `tests/cards.spec.ts`

```ts
import { describe, it, expect } from 'vitest'
import { hypergeometric, cardsExact } from '../src/sim/cards'

describe('cards', () => {
  it('40 张牌 8 张目标抽 5：P(>=1) ≈ 0.6940', () => {
    const r = cardsExact({ deck: [{key:'silk',count:8},{key:'other',count:32}], draw: 5, target: 'silk', op: '>=', value: 1 })
    expect(r.probability).toBeCloseTo(0.6940, 3)
    expect(r.expectedCount).toBeCloseTo(1, 10)          // n*K/N = 5*8/40
    expect(r.expectedDrawsToFirst).toBeCloseTo(41/9, 10)
  })
  it('hypergeometric 边界：k=0 与全中', () => {
    expect(hypergeometric(10, 3, 4, 0)).toBeCloseTo(0.1667, 3)  // C(7,4)/C(10,4)=35/210=1/6
    expect(hypergeometric(10, 3, 3, 3)).toBeCloseTo(1/120, 10)  // 1/C(10,3)
  })
  it('非法参数与未知 target throw', () => {
    expect(() => hypergeometric(10, 3, 4, 5)).toThrow()
    expect(() => cardsExact({ deck: [{key:'a',count:1}], draw: 1, target: 'b', op: '>=', value: 1 })).toThrow(/b/)
  })
})
```

- [ ] **Step 2: 跑测试确认红**

Run: `npm test -- tests/cards.spec.ts`
Expected: FAIL — 模块不存在。

- [ ] **Step 3: 实现 `src/sim/cards.ts`**

组合数用对数阶乘（Lanczos lgamma 或逐项乘除）防溢出；`cardsExact` 对 k 满足 op 的 hypergeometric 求和；expectedCount = draw*K/N。

- [ ] **Step 4: 跑测试确认绿**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS。

---

### Task 7: 经济模拟器（投影 + 结构检查 + 声明式策略）

**Files:**
- Create: `tools/boardgame-design/src/sim/economy.ts`
- Test: `tools/boardgame-design/tests/economy.spec.ts`

**Interfaces:**
- Consumes: Task 3 `GameDoc['subsystems']['economy']` 形状（params 即该子系统对象，经 resolveParams 而来）
- Produces:
  - `type EconomyParams = { resources: string[], initial?: Record<string, number>, perTurn?: { resource: string, produce: number, consume: number }[], actions?: { key: string, cost: Record<string, number>, gain?: Record<string, number>, vp?: number }[], stallK?: number }`
  - `type Policy = { type: 'greedy'|'priority', order?: string[] }` — order 为 action key 优先级列表；greedy 缺省 order = actions 声明顺序；priority 必须给 order
  - `projectEconomy(p: EconomyParams, turns: number): { rows: { turn: number, stocks: Record<string, number> }[], netFlow: Record<string, number>, noSink: string[] }` — 每回合 stock += produce − min(stock, consume)；netFlow = produce − consume（费率差）；noSink = 有产出（perTurn.produce>0 或某 action.gain>0）但无任何消耗口（perTurn.consume=0 且不出现在任何 action.cost）的资源
  - `simulateEconomy(p: EconomyParams, turns: number, policy: Policy): { rows: { turn: number, stocks: Record<string, number>, actionsTaken: string[], vp: number }[], stalled: boolean, stallFromTurn?: number, totalVp: number, noSink: string[] }`
  - 回合语义（固定，测试依此）：① 应用 perTurn（produce 加、consume 钳到库存）② 按 order 循环扫描 actions：可负担（每种 cost 资源库存 ≥ 需求量）则扣 cost、加 gain、累 vp、记 key，**从头重扫**直到整轮无可负担 ③ 本回合 actionsTaken 为空则 stall 计数 +1，否则清零；连续 stallK 回合 → stalled=true、stallFromTurn=首次计数回合，**继续跑完剩余回合**（不死循环，Review Focus 4）
  - 未声明 initial 的资源从 0 起；gain 缺省为空；vp 缺省 0

- [ ] **Step 1: 写失败测试** `tests/economy.spec.ts`

断言组（手算值）：
1. 投影：resources [gold]，initial {gold:5}，perTurn [{gold, produce:2, consume:1}]，turns 3 → rows stocks 依次 6、7、8；netFlow.gold = 1；noSink 空（consume>0 是沉没口）
2. consume 钳制：initial {gold:0}，produce 0、consume 5 → 库存恒 0 不为负
3. noSink：perTurn produce 2、consume 0 且无 actions → noSink 含 gold
4. greedy 策略：initial {gold:5}，perTurn produce 2，actions [{key:buy, cost:{gold:3}, vp:1}]，turns 2 → 回合1：5+2=7，买2次剩1，vp2；回合2：1+2=3，买1次剩0，vp1；totalVp 3
5. stall：initial {gold:0}，produce 0，actions cost {gold:3}，stallK 2，turns 5 → stalled true、stallFromTurn 1、rows.length 5（跑完不死循环）
6. priority order 生效：两个 action，order 指定先买贵的，断言 actionsTaken 顺序

- [ ] **Step 2: 跑测试确认红**

Run: `npm test -- tests/economy.spec.ts`
Expected: FAIL — 模块不存在。

- [ ] **Step 3: 实现 `src/sim/economy.ts`**

签名与回合语义见 Interfaces。纯函数、无 RNG（策略是确定性的）。

- [ ] **Step 4: 跑测试确认绿**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS。

---

### Task 8: 冲突结算蒙特卡洛

**Files:**
- Create: `tools/boardgame-design/src/sim/conflict.ts`
- Test: `tools/boardgame-design/tests/conflict.spec.ts`

**Interfaces:**
- Consumes: Task 1 `mulberry32`；Task 3 `Side` 形状
- Produces:
  - `type ConflictParams = { attacker: Side, defender: Side, maxRounds?: number }`（Side 见 Task 3；parse 后 bonus/maxRounds 有默认值，但本模块入参允许缺省：bonus 默认 0、maxRounds 默认 100）
  - `conflictMC(p: ConflictParams, seed: number, runs: number): { attackerWinRate: number, defenderWinRate: number, drawRate: number, meanRounds: number }`
  - 回合语义：双方各掷 diceCount 颗 faces 均匀骰，hits = 命中 hitSymbols 的个数 + bonus；伤害同时结算；hp≤0 判定：双亡=draw，单亡=对方胜；达 maxRounds 未分胜负=draw；meanRounds 含 draw 局

- [ ] **Step 1: 写失败测试** `tests/conflict.spec.ts`

```ts
import { describe, it, expect } from 'vitest'
import { conflictMC } from '../src/sim/conflict'

const side = (hp: number) => ({ diceCount: 2, faces: ['hit','hit','miss','miss'], hitSymbols: ['hit'], hp })

describe('conflict', () => {
  it('对称战斗双方胜率相等（种子42，20000局，容差0.03；同时结算含平局）', () => {
    const r = conflictMC({ attacker: side(5), defender: side(5) }, 42, 20000)
    expect(Math.abs(r.attackerWinRate - r.defenderWinRate)).toBeLessThan(0.03)
    expect(r.attackerWinRate + r.defenderWinRate + r.drawRate).toBeCloseTo(1, 10)
  })
  it('确定性碾压：0 骰 + bonus 10 vs 零伤害 hp 1 → 攻击方全胜率 1', () => {
    const r = conflictMC({ attacker: { diceCount: 0, faces: ['x'], hitSymbols: ['x'], hp: 1, bonus: 10 }, defender: { diceCount: 0, faces: ['x'], hitSymbols: ['x'], hp: 1 } }, 1, 100)
    expect(r.attackerWinRate).toBe(1)
    expect(r.meanRounds).toBe(1)
  })
  it('同种子逐位相同（Review Focus 5）', () => {
    expect(conflictMC({ attacker: side(3), defender: side(3) }, 7, 500)).toEqual(conflictMC({ attacker: side(3), defender: side(3) }, 7, 500))
  })
  it('maxRounds=1 且双方 hp 高 → 全 draw', () => {
    const r = conflictMC({ attacker: side(100), defender: side(100), maxRounds: 1 }, 1, 100)
    expect(r.drawRate).toBe(1)
  })
})
```

- [ ] **Step 2: 跑测试确认红**

Run: `npm test -- tests/conflict.spec.ts`
Expected: FAIL — 模块不存在。

- [ ] **Step 3: 实现 `src/sim/conflict.ts`**

单个 mulberry32(seed) 流贯穿全部 runs（保证可复现）；每局独立 hp 副本。

- [ ] **Step 4: 跑测试确认绿**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS。

---

### Task 9: 报告输出 + 参数扫描

**Files:**
- Create: `tools/boardgame-design/src/report.ts`
- Create: `tools/boardgame-design/src/sim/sweep.ts`
- Test: `tools/boardgame-design/tests/report-sweep.spec.ts`

**Interfaces:**
- Consumes: Task 4 `SweepConfig`、`resolveParams`、`setAtPath`、`deepMerge`；Task 5~8 四个模拟器；Task 3 `loadSweepConfig`
- Produces:
  - `type SimResult = Record<string, unknown>`（各模拟器返回值的统一别名）
  - `runSim(sim: SimConfig['sim'], params: Record<string, unknown>, seed: number, runs: number): SimResult` — 分发：dice→diceMC+ diceExact 合并 `{exact, mc}`；cards→cardsExact；economy→simulateEconomy(params, turns, policy)（params 须含 `turns: number` 与 `policy: Policy`，缺省 turns=20、policy={type:'greedy'}）；conflict→conflictMC
  - `writeReport(outPath: string, data: { title: string, config: SimConfig | SweepConfig, params: unknown, result: SimResult }): { mdPath: string, jsonPath: string }` — md 含：标题、配置回显（yaml stringify）、种子/局数、结果表格（result 顶层标量成表；数组/对象以 yaml 块附后）；jsonPath = 同基名 `.json`，内容 `{ config, params, result }`（**不含时间戳**，保证逐位可复现）；目录不存在则递归创建
  - `runSweep(config: SweepConfig, configDir: string): { rows: { value: number|string, result: SimResult }[], baseParams: Record<string, unknown> }` — 对 sweep.values 逐值：`setAtPath(resolveParams(...).params, sweep.param, value)` 后 runSim；**每值用同一 seed**；基础 params 不被污染（Review Focus 3）
  - sweep 的 economy 特例：sweep.param 可指向 `turns`/`policy.*` 等运行参数（setAtPath 作用于合并后的 params，天然支持）

- [ ] **Step 1: 写失败测试** `tests/report-sweep.spec.ts`

断言组（临时目录，fixture game 用 Task 3 mini-game.yaml）：
1. `runSim('dice', {count:2, faces:[1,2,3,4,5,6], symbol:6, op:'>=', value:1}, 42, 1000)` → 返回含 `exact.probability ≈ 11/36` 与 `mc.probability`
2. `runSim('economy', {...mini-game 经济子系统, turns: 2, policy: {type:'greedy'}}, ...)` → 含 rows/stalled/totalVp
3. `writeReport`：md 文件存在且含种子数字与 `probability` 字样；json 可 parse 且 `result` 深等于传入；同输入写两次文件内容逐位相同
4. `runSweep`：config 引用 mini-game.yaml、from `subsystems.economy`、sim economy、sweep `{param: 'initial.gold', values: [0, 5, 10]}`、overrides `{turns: 2, policy: {type:'greedy'}}` → rows.length 3、value 依次 0/5/10、totalVp 单调不减；baseParams.initial.gold 仍为 5（未被污染）
5. 未知 sim 名 → runSim throw

- [ ] **Step 2: 跑测试确认红**

Run: `npm test -- tests/report-sweep.spec.ts`
Expected: FAIL — 模块不存在。

- [ ] **Step 3: 实现 `report.ts` 与 `sim/sweep.ts`**

签名见 Interfaces。md 表格只处理顶层标量字段，其余 `yaml.stringify` 附块——避免为每种结果定制排版（YAGNI）。

- [ ] **Step 4: 跑测试确认绿**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS。

---

### Task 10: CLI 接线（validate / 五模拟 / ad-hoc）

**Files:**
- Create: `tools/boardgame-design/src/cli.ts`
- Test: `tools/boardgame-design/tests/cli.spec.ts`

**Interfaces:**
- Consumes: Task 3 `validateDoc`；Task 4 `loadSimConfig`/`loadSweepConfig`/`resolveParams`；Task 9 `runSim`/`runSweep`/`writeReport`；Task 5/6 精确模拟器
- Produces（命令行契约，cwd = `tools/boardgame-design/`，经 `npm run sim --` 调用）：
  - `validate <file>` — ok → stdout `OK <file>`、exit 0；失败 → stderr 逐行错误、exit 1
  - `dice|cards|economy|conflict --config <path> [--out <path>]` — resolveParams → runSim → writeReport（out 缺省 = `path.join(configDir, config.sim + '-report.md')`）→ stdout 打印 mdPath 与关键标量；exit 0
  - `sweep --config <path> [--out <path>]` — loadSweepConfig → runSweep → writeReport（缺省 `sweep-report.md`，result = `{rows}`）
  - ad-hoc：`dice --faces <逗号分隔> --count N --symbol S --op OP --value K [--seed N --runs N]` 与 `cards --deck <key:count,...> --draw N --target K --op OP --value K` — 仅精确计算，stdout 首行固定 `AD-HOC（非证据）`，结果打印 stdout，**不写报告文件**
  - 错误 exit code：参数/校验错误 1，文件不存在 2；错误消息含出错路径
  - 解析用 Node 内置 `util.parseArgs`（不引新依赖）；faces/deck 的纯数字面自动转 number

- [ ] **Step 1: 写失败测试** `tests/cli.spec.ts`

用 `execFileSync('npx', ['tsx', 'src/cli.ts', ...args], { cwd: 包根, encoding: 'utf-8' })` 驱动（包根由 `new URL('..', import.meta.url)` 定位）；临时目录放 sim 配置（game 指向 Task 3 fixture 的绝对路径）：
1. `validate <mini-game.yaml>` → stdout 含 `OK`，exit 0
2. `validate <坏文件>` → exit 1，stderr 非空
3. `dice --config <临时配置>` → exit 0，缺省报告文件生成于配置旁，stdout 含报告路径；md 含 `seed`
4. `sweep --config <临时sweep配置>` → 生成 sweep-report.md，json 的 result.rows.length === values 数
5. ad-hoc dice（2d6 至少一个 6）→ stdout 首行 `AD-HOC（非证据）`，含 ≈0.3056 的概率值，且**不产生任何报告文件**
6. `dice --config 不存在.yaml` → exit 2

- [ ] **Step 2: 跑测试确认红**

Run: `npm test -- tests/cli.spec.ts`
Expected: FAIL — cli.ts 不存在。

- [ ] **Step 3: 实现 `src/cli.ts`**

契约见 Interfaces。main 流程：parseArgs 按子命令分流；try/catch 映射 exit code（zod/参数错误→1，ENOENT→2）。

- [ ] **Step 4: 跑测试确认绿 + 全量回归**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS（Task 1~10 全绿）。

---

### Task 11: SKILL.md + toolbox.md + 工作区骨架

**Files:**
- Create: `.claude/skills/design-boardgame/SKILL.md`
- Create: `.claude/skills/design-boardgame/references/toolbox.md`
- Create: `design/games/README.md`
- Test: `tools/boardgame-design/tests/skill-structure.spec.ts`

**Interfaces:**
- Consumes: Task 10 CLI 契约（toolbox.md 的命令示例必须与之一字不差）；Task 2 词汇 key
- Produces:
  - SKILL.md frontmatter：`name: design-boardgame`；description 含触发词：设计桌游、新桌游、桌游创意、design boardgame、analyze（建样本档案）
  - SKILL.md 正文必备段落（标题固定，供结构测试断言）：`## 触发`、`## 设计主流程`、`## analyze 工作流`、`## 纪律`、`## 导出`
  - 设计主流程 7 阶段（①意图 ②起草 game.yaml+rules.md ③样本对照 ④数值验证 ⑤评审 ⑥迭代 ⑦导出），②③⑤⑦ 前用户确认点；双入口（带点子/求创意出 2~3 案）写明
  - analyze 工作流：收集（WebSearch 佐证/用户喂规则书）→ schema 拆解 → analysis 段 → 来源+置信度 → 用户确认 → 写 `design/samples/<key>.yaml`；离线降级规则
  - 纪律段：平衡论断必须引用 sims/ 报告路径或样本证据；借鉴注明样本来源；决策记 journal.md；ad-hoc 输出不作证据；用户改判以用户为准；版权边界（只提炼结构/数值）
  - 导出段：实体→按 rulebook-template.md 写 `exports/rulebook.md`；平台→game.yaml + `exports/implementation-notes.md`（不含引擎代码）；登记走 import-boardgame skill（本 skill 不碰 catalog）
  - toolbox.md：五个模拟 + validate + sweep + ad-hoc 的完整命令示例与配置模板（yaml 块）、报告解读要点、经济两层语义与"卡死"定义
  - design/games/README.md：工作区布局（game.yaml/rules.md/journal.md/sims//exports/）与各文件职责

- [ ] **Step 1: 写失败结构测试** `tests/skill-structure.spec.ts`

```ts
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { loadVocabulary } from '../src/schema/vocabulary'

const root = fileURLToPath(new URL('../../..', import.meta.url))  // 仓库根
const skill = readFileSync(root + '/.claude/skills/design-boardgame/SKILL.md', 'utf-8')
const toolbox = readFileSync(root + '/.claude/skills/design-boardgame/references/toolbox.md', 'utf-8')

describe('skill structure', () => {
  it('SKILL.md 含必备段落与 frontmatter name', () => {
    expect(skill).toMatch(/name: design-boardgame/)
    for (const h of ['## 触发','## 设计主流程','## analyze 工作流','## 纪律','## 导出']) expect(skill).toContain(h)
  })
  it('toolbox.md 覆盖全部子命令', () => {
    for (const c of ['validate','dice','cards','economy','conflict','sweep','AD-HOC']) expect(toolbox).toContain(c)
  })
  it('SKILL.md 引用真实存在的路径', () => {
    for (const p of ['design/samples/','design/games/','references/toolbox.md']) expect(skill).toContain(p)
  })
})
```

- [ ] **Step 2: 跑测试确认红**

Run: `npm test -- tests/skill-structure.spec.ts`
Expected: FAIL — SKILL.md 不存在（readFileSync ENOENT）。

- [ ] **Step 3: 撰写 SKILL.md / toolbox.md / README.md**

内容要求见 Interfaces（措辞自由，段落标题与命令示例固定）。toolbox.md 每个命令示例须可实际执行——撰写后逐条在 Git Bash 跑一遍验证（用 Task 3 fixture 或临时配置）。

- [ ] **Step 4: 跑测试确认绿**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS。

---

### Task 12: 知识库 references（机制库 / 评审清单 / 规则书模板）

**Files:**
- Create: `.claude/skills/design-boardgame/references/mechanisms.md`
- Create: `.claude/skills/design-boardgame/references/design-rubric.md`
- Create: `.claude/skills/design-boardgame/references/rulebook-template.md`
- Test: `tools/boardgame-design/tests/skill-refs.spec.ts`

**Interfaces:**
- Consumes: Task 2 `loadVocabulary()`（机制库条目与词汇一一对应）
- Produces:
  - mechanisms.md：每个词汇 key 一节，节标题含 `key` 与中文 name；每节五要素：说明 / 代表作 / 设计旋钮（可调参数）/ 常见坑 / 常见组合。内容须与样本判例一致（Splendor=引擎构建+成套收集 等）
  - design-rubric.md：红旗清单，必含条目（标题关键词固定）：滚雪球、国王制造、先手优势、 downtime、分析瘫痪、人数缩放、资源卡死、随机性淹没决策、主题与机制脱节；每条含"检查问题 + 缓解手段"
  - rulebook-template.md：章节骨架固定：概述 / 配件清单 / 设置 / 回合流程 / 结算细节 / 结束与计分 / 变体与FAQ / 快速参考

- [ ] **Step 1: 写失败测试** `tests/skill-refs.spec.ts`

```ts
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { loadVocabulary } from '../src/schema/vocabulary'

const dir = fileURLToPath(new URL('../../../.claude/skills/design-boardgame/references/', import.meta.url))

describe('skill references', () => {
  it('mechanisms.md 覆盖每个词汇 key', () => {
    const md = readFileSync(dir + 'mechanisms.md', 'utf-8')
    for (const e of loadVocabulary()) expect(md).toContain(e.key)
  })
  it('design-rubric.md 含全部红旗条目', () => {
    const md = readFileSync(dir + 'design-rubric.md', 'utf-8')
    for (const k of ['滚雪球','国王制造','先手优势','downtime','分析瘫痪','人数缩放','资源卡死','随机性淹没决策','主题与机制脱节']) expect(md).toContain(k)
  })
  it('rulebook-template.md 含全部章节', () => {
    const md = readFileSync(dir + 'rulebook-template.md', 'utf-8')
    for (const k of ['概述','配件清单','设置','回合流程','结算细节','结束与计分','变体与FAQ','快速参考']) expect(md).toContain(k)
  })
})
```

- [ ] **Step 2: 跑测试确认红**

Run: `npm test -- tests/skill-refs.spec.ts`
Expected: FAIL — 文件不存在。

- [ ] **Step 3: 撰写三份 references**

内容要求见 Interfaces。机制库条目措辞是设计知识工作：代表作准确、坑要具体（如"工人放置：先手占位优势随人数放大→用回合轨补偿"），不写空话。

- [ ] **Step 4: 跑测试确认绿**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS。

---

### Task 13: 种子样本档案 ×3（抽象棋类）

**Files:**
- Create: `design/samples/xiangqi.yaml`
- Create: `design/samples/go.yaml`
- Create: `design/samples/gomoku.yaml`

**Interfaces:**
- Consumes: Task 3 `SampleDoc` schema；Task 10 `validate` 子命令；Task 11 analyze 工作流；Task 2 词汇 key
- Produces: 三份合法样本档案。key 与 catalog 一致（xiangqi/go/gomoku）；zone 均 `abstract`；mechanisms 用 Task 2 钉死的 key（`abstract-chess`、`pattern-building`、`logical-deduction`）；抽象棋类**无经济/冲突子系统**（subsystems 可空对象），components/turnStructure 如实描述（棋盘非组件卡牌，resources/dice/decks 可省）；analysis 必填：whyFun、remixable（如围棋"厚势/先手效率"、象棋"子力价值不对称"）、sources（WebSearch 佐证，查不到则自身知识 + confidence: medium）、置信度如实

- [ ] **Step 1: 按 analyze 工作流调研并撰写三份档案**

每份：收集信息（WebSearch 查机制/结构佐证；离线则降级标注）→ 按 schema 拆解 → analysis 段 → 只提炼结构不抄原文。

- [ ] **Step 2: 逐份校验**

Run: `npm run sim -- validate ../../design/samples/xiangqi.yaml`（go/gomoku 同；cwd 工具包目录）
Expected: 三份均 `OK`，exit 0。

- [ ] **Step 3: 全量回归**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS。

---

### Task 14: 种子样本档案 ×3（现代桌游）

**Files:**
- Create: `design/samples/splendor.yaml`
- Create: `design/samples/diceThrone.yaml`
- Create: `design/samples/avalon.yaml`

**Interfaces:**
- Consumes: 同 Task 13
- Produces: 三份合法样本档案，判例分区：splendor→card、diceThrone→narrative、avalon→narrative。mechanisms 用 Task 2 钉死 key：splendor `[engine-building, set-collection]`；diceThrone `[dice-driven, variable-player-powers, card-assist]`；avalon `[hidden-roles, logical-deduction, voting]`。数值要求：
  - splendor：components.decks 按三级发展卡如实建（数量/费用/gain/vp 参数化）；subsystems.economy 建宝石产消模型（可模拟"引擎成型回合数"）
  - diceThrone：components.dice 建 5 面骰符号面（按调研所得面分布）；subsystems.conflict 建参数化攻防示例；注明角色不对称不在单份档案内展开
  - avalon：无经济/冲突子系统；turnStructure 建组队→投票→任务→刺杀阶段；analysis 侧重隐藏身份信息结构
  - 关键数值查不到精确值时给约值并在 analysis.sources 注明"约值/推理"，confidence 相应降级

- [ ] **Step 1: 按 analyze 工作流调研并撰写三份档案**

同 Task 13 流程；数值以 WebSearch 佐证优先。

- [ ] **Step 2: 逐份校验**

Run: `npm run sim -- validate ../../design/samples/splendor.yaml`（diceThrone/avalon 同）
Expected: 三份均 `OK`。

- [ ] **Step 3: 抽验可模拟性**

为 splendor 写一个临时 sim 配置（economy，from `subsystems.economy`，overrides `{turns: 10, policy: {type: 'greedy'}}`），跑 `npm run sim -- economy --config <临时配置>`，确认报告生成且 rows 有 10 回合数据；验后删除临时配置与报告（样本库只留档案）。

- [ ] **Step 4: 全量回归**

Run: `npm test && npm run typecheck`
Expected: 全部 PASS。

---

### Task 15: 种子样本 brass + 端到端集成验证

**Files:**
- Create: `design/samples/brass.yaml`
- Test: 无新增测试文件；本任务做全链路验证

**Interfaces:**
- Consumes: 全部前序任务
- Produces: brass→heavy 合法档案（两时代网络/经济模型参数化：subsystems.economy 建煤铁市场产消、components.decks 建地点/产业卡约值）；全链路绿的证据

- [ ] **Step 1: 撰写 brass.yaml 并校验**

Run: `npm run sim -- validate ../../design/samples/brass.yaml`
Expected: `OK`。

- [ ] **Step 2: 七份样本全量校验**

Run: 对 design/samples/ 下 7 个 yaml 逐个 `npm run sim -- validate <path>`
Expected: 7/7 OK。

- [ ] **Step 3: 端到端冒烟（模拟一次真实设计调用链）**

在 `design/games/` 建临时工作区 `smoke-test/`：写一个最小 game.yaml（可复制 mini-game fixture 改 key）+ 一个 economy sim 配置 → `npm run sim -- economy --config ...` 生成报告 → 确认 md/json 落盘、报告含种子与回合表 → **删除整个 smoke-test/ 目录**（工作区骨架保留 README）。

- [ ] **Step 4: 全量测试 + typecheck + 前端无扰动确认**

Run: `npm test && npm run typecheck`（工具包）；`cd ../../boardgame-ui && npm test && npm run build`
Expected: 工具包全绿；前端既有测试与 build 不受影响（本计划未改前端文件，此为回归保险）。

- [ ] **Step 5: 交付清单核对**

对照 spec Goals 1~8 逐项确认产物存在：skill 四件 references、工具包五模拟+validate+sweep、7 份样本、工作区 README、双出口导出流程写在 SKILL.md。向用户报告完成状态与使用方式（`/design-boardgame`），不 commit。