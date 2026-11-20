# 大厅桌游分区三级导航 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 大厅单路由三级导航（分区→游戏→房间操作），7 分区+机制标签+7 游戏条目（1 可玩 6 占位），每层可返回大厅。

**Architecture:** `/lobby` 靠 `route.query.zone/game` 推导层级（`resolveLevel` 纯函数）；`ZoneGrid`/`GameList` 两个展示子组件；lobby 视图编排层级与既有房间操作逻辑，轮询生命周期绑定 level 2。零后端改动、零新路由。

**Tech Stack:** Vue3 setup + TS + Element Plus + vue-router + pinia；vitest 2.1.9 + @vue/test-utils + jsdom（EP inline 错误需 `vi.waitFor`）；Python Playwright 一次性实测脚本。

**Spec:** `docs/superpowers/specs/2026-11-19-lobby-zones-design.md`

## Global Constraints

- **不 commit**（用户未要求）；所有 task 无 commit 步骤，工作区保留。
- 不改后端、不加路由、不动房间 zone 字段；不动验证码开关 `sys.account.captchaEnabled`；保留未提交的 captcha data-URI 与 logo 修复。
- 分区名称/机制标签/游戏机制标签**原文照录 spec Catalog 表**，不编造。
- 不可玩游戏（playable=false）任何路径不得进入 level 2；非法 query 静默回落、不改写 URL。
- 登出按钮仅大厅（顶栏，所有层级保留）；房间页既有返回按钮不动。
- TDD：每 task 先写测试并**亲眼看到 RED** 再实现；完成门槛=全量 `npm test` + `npm run build` 绿。
- 实测仅 Python Playwright 一次性脚本（`logs/`），不新增 e2e 依赖/配置。
- 测试命令：`cd boardgame-ui && npm test`（vitest run）；单文件：`npx vitest run src/...spec.ts`。

## Review Focus

1. **离开 level 2 后轮询/监听泄漏**（interval 继续打 listRooms）→ Task 4 测试：query 切回 level 0 后 advance 10s 调用数不增；unmount 后不增。
2. **手输/分享 URL 指向不可玩或错配游戏**（`?zone=abstract&game=go`、`?zone=card&game=xiangqi`）不得呈现象棋房间三卡 → Task 1 resolveLevel 表驱动测试 + Task 4 渲染回落测试。
3. **返回大厅缺失或错层**（用户硬要求）→ Task 4：level 1/2 各有「← 返回大厅」且 click push `/lobby`（无 query）；Task 6 实走含房间页返回。
4. **level 0 房间号快捷加入回归**（不选分区也能加房）→ Task 4：level 0 输入房间号→getRoom 校验→push `/game/:id`。
5. **深链刷新时 profile 水合失败不得阻断 level 2 列表**（store 仅 token）→ Task 4：fetchProfile reject 仍 listRooms 一次+错误 toast。

## File Structure

- Create `boardgame-ui/src/constants/catalog.ts` — 分区/游戏静态目录 + `resolveLevel` 纯函数（唯一数据源）
- Create `boardgame-ui/src/constants/__tests__/catalog.spec.ts`
- Create `boardgame-ui/src/components/ZoneGrid.vue` — 分区卡网格（props zones，emit select）
- Create `boardgame-ui/src/components/__tests__/ZoneGrid.spec.ts`
- Create `boardgame-ui/src/components/GameList.vue` — 分区头部+游戏卡/空态（props zone/games，emit select）
- Create `boardgame-ui/src/components/__tests__/GameList.spec.ts`
- Modify `boardgame-ui/src/views/lobby/index.vue` — 三级编排+返回按钮+轮询绑定 level 2；房间操作逻辑原样保留
- Modify `boardgame-ui/src/views/__tests__/lobby.spec.ts` — query 驱动挂载，既有用例改挂对应层级
- Modify `boardgame-ui/src/views/home/index.vue` — 象棋卡 route 深链
- Modify `boardgame-ui/src/views/__tests__/home.spec.ts` — 深链测试
- Create `logs/lobby_zones_walk.py` — 一次性实测（Task 6）

---

### Task 1: catalog 目录模块 + resolveLevel

**Files:**
- Create: `boardgame-ui/src/constants/catalog.ts`
- Test: `boardgame-ui/src/constants/__tests__/catalog.spec.ts`

**Interfaces:**
- Produces:
  ```ts
  export interface Zone { key: string; name: string; icon: string; mechanisms: string[] }
  export interface Game { key: string; name: string; zone: string; playable: boolean; desc: string; players?: number; mechanisms?: string[] }
  export const ZONES: Zone[]            // 7 区，顺序=spec 表顺序
  export const GAMES: Game[]            // 7 条目
  export function findZone(key: unknown): Zone | undefined
  export function findGame(key: unknown): Game | undefined
  export function gamesInZone(zoneKey: string): Game[]
  export function resolveLevel(query: Record<string, unknown>): { level: 0 | 1 | 2; zone?: Zone; game?: Game }
  ```
- `resolveLevel` 规则：zone=findZone(query.zone)；game=findGame(query.game)；`zone && game && game.zone===zone.key && game.playable` → `{level:2,zone,game}`；否则 `zone` → `{level:1,zone}`；否则 `{level:0}`。
- icon（我的裁决）：party🎉 family🏠 strategy🧠 card🃏 narrative🎭 abstract♟️ heavy⚙️。

- [ ] **Step 1: 写失败测试**（目录原文+映射+回落表驱动）

```ts
import { describe, expect, it } from 'vitest'
import { ZONES, GAMES, gamesInZone, resolveLevel, findZone } from '@/constants/catalog'

describe('catalog zones', () => {
  it('has the 7 zones with verbatim names and mechanisms', () => {
    expect(ZONES.map((z) => z.key)).toEqual(['party', 'family', 'strategy', 'card', 'narrative', 'abstract', 'heavy'])
    expect(findZone('party')!.name).toBe('轻策/派对区')
    expect(findZone('party')!.mechanisms).toEqual(['同时行动', '反应', '猜词', '吹牛', '轮抽轻量版'])
    expect(findZone('family')!.mechanisms).toEqual(['成套收集', '板块放置', '骰子轻策', '交易谈判'])
    expect(findZone('strategy')!.mechanisms).toEqual(['工人放置', '引擎构建', '区域控制', '路线构建', '行动点分配'])
    expect(findZone('card')!.mechanisms).toEqual(['手牌管理', '牌库构建', '卡牌驱动', '轮抽', '多用途卡牌'])
    expect(findZone('narrative')!.mechanisms).toEqual(['合作', '传承', '叙事', '隐藏身份', '战斗结算'])
    expect(findZone('abstract')!.mechanisms).toEqual(['抽象棋', '模式构建', '逻辑推理'])
    expect(findZone('heavy')!.mechanisms).toEqual(['资源管理+引擎+路线+推轨的复合体'])
  })
})

describe('catalog games', () => {
  it('places the 4 placeholder games with verbatim mechanism tags', () => {
    const by = (k: string) => GAMES.find((g) => g.key === k)!
    expect(by('splendor')).toMatchObject({ zone: 'card', playable: false, mechanisms: ['引擎构建', '成套收集'] })
    expect(by('diceThrone')).toMatchObject({ zone: 'narrative', playable: false, mechanisms: ['骰子驱动', '角色能力（不对称）', '卡牌辅助'] })
    expect(by('avalon')).toMatchObject({ zone: 'narrative', playable: false, mechanisms: ['隐藏身份', '推理/演绎', '投票'] })
    expect(by('brass')).toMatchObject({ zone: 'heavy', playable: false, mechanisms: ['网络构建', '经济/资源管理', '手牌管理（轻卡驱）'] })
    expect(by('xiangqi')).toMatchObject({ zone: 'abstract', playable: true })
    expect(by('go').playable).toBe(false)
    expect(by('gomoku').playable).toBe(false)
    expect(gamesInZone('abstract').map((g) => g.key)).toEqual(['xiangqi', 'go', 'gomoku'])
    expect(gamesInZone('party')).toEqual([])
  })
})

describe('resolveLevel', () => {
  it.each([
    [{}, 0],
    [{ zone: 'abstract' }, 1],
    [{ zone: 'abstract', game: 'xiangqi' }, 2],
    [{ zone: 'abstract', game: 'go' }, 1],          // 已知但不可玩 → level 1
    [{ zone: 'card', game: 'xiangqi' }, 1],         // 错配 → level 1
    [{ zone: 'nope', game: 'xiangqi' }, 0],
    [{ game: 'xiangqi' }, 0],
    [{ zone: 'abstract', game: 'nope' }, 1],
  ] as [Record<string, unknown>, number][])('query %j → level %i', (query, level) => {
    expect(resolveLevel(query).level).toBe(level)
  })
  it('returns zone/game objects at level 2', () => {
    const r = resolveLevel({ zone: 'abstract', game: 'xiangqi' })
    expect(r.zone?.key).toBe('abstract')
    expect(r.game?.key).toBe('xiangqi')
  })
})
```

- [ ] **Step 2: 跑 RED**：`npx vitest run src/constants/__tests__/catalog.spec.ts` → FAIL（模块不存在）。
- [ ] **Step 3: 实现 catalog.ts**：按 Interfaces 签名；ZONES/GAMES 数据照录 spec Catalog 两张表（名称含英文副标题照 spec：璀璨宝石 Splendor 等）；helper 为 find/filter/上述规则，无副作用。
- [ ] **Step 4: 跑 GREEN**：同命令 → PASS（10 tests）。

---

### Task 2: ZoneGrid 分区卡网格

**Files:**
- Create: `boardgame-ui/src/components/ZoneGrid.vue`
- Test: `boardgame-ui/src/components/__tests__/ZoneGrid.spec.ts`

**Interfaces:**
- Consumes: `Zone`（Task 1）。
- Produces: props `{ zones: Zone[] }`；emits `select(zoneKey: string)`；卡面 class `zone-card`，标签 class `zone-tag`（每机制一个 chip）。

- [ ] **Step 1: 写失败测试**

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ZoneGrid from '@/components/ZoneGrid.vue'
import { ZONES } from '@/constants/catalog'

describe('ZoneGrid', () => {
  it('renders one card per zone with name and mechanism tags', () => {
    const w = mount(ZoneGrid, { props: { zones: ZONES } })
    expect(w.findAll('.zone-card')).toHaveLength(7)
    expect(w.text()).toContain('轻策/派对区')
    expect(w.findAll('.zone-card')[0].findAll('.zone-tag').map((t) => t.text())).toEqual(ZONES[0].mechanisms)
  })
  it('emits select with the zone key on card click', async () => {
    const w = mount(ZoneGrid, { props: { zones: ZONES } })
    await w.findAll('.zone-card')[5].trigger('click')
    expect(w.emitted('select')![0]).toEqual(['abstract'])
  })
})
```

- [ ] **Step 2: RED**：`npx vitest run src/components/__tests__/ZoneGrid.spec.ts` → FAIL（组件不存在）。
- [ ] **Step 3: 实现 ZoneGrid.vue**：`v-for` 卡（icon+name+tags chips），整卡 `@click="$emit('select', zone.key)"`；深色卡面样式沿用 lobby-card 色系（#16213e 底、圆角 8、hover 边框 var(--color-primary)）。
- [ ] **Step 4: GREEN**：同命令 → PASS（2 tests）。

---

### Task 3: GameList 游戏卡/空态

**Files:**
- Create: `boardgame-ui/src/components/GameList.vue`
- Test: `boardgame-ui/src/components/__tests__/GameList.spec.ts`

**Interfaces:**
- Consumes: `Zone`/`Game`/`gamesInZone` 不需要（games 由父传入）。
- Produces: props `{ zone: Zone; games: Game[] }`；emits `select(gameKey: string)`；头部渲染 zone.name+zone.mechanisms chips；游戏卡 class `game-entry`，不可玩卡加 `is-disabled` 且**不 emit**；机制标签 class `game-tag`（仅 games[].mechanisms 存在时）；空态 class `zone-empty` 文案含「筹备中」。

- [ ] **Step 1: 写失败测试**

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GameList from '@/components/GameList.vue'
import { findZone, gamesInZone } from '@/constants/catalog'

const abstract = findZone('abstract')!
describe('GameList', () => {
  it('emits select only for playable games', async () => {
    const w = mount(GameList, { props: { zone: abstract, games: gamesInZone('abstract') } })
    const cards = w.findAll('.game-entry')
    expect(cards).toHaveLength(3)
    await cards[1].trigger('click') // 围棋 敬请期待
    expect(w.emitted('select')).toBeUndefined()
    await cards[0].trigger('click') // 象棋
    expect(w.emitted('select')![0]).toEqual(['xiangqi'])
  })
  it('shows user-given mechanism tags on placeholder cards', () => {
    const w = mount(GameList, { props: { zone: findZone('card')!, games: gamesInZone('card') } })
    expect(w.findAll('.game-tag').map((t) => t.text())).toEqual(['引擎构建', '成套收集'])
    expect(w.text()).toContain('敬请期待')
  })
  it('renders preparation empty state for zones without games', () => {
    const w = mount(GameList, { props: { zone: findZone('party')!, games: [] } })
    expect(w.find('.zone-empty').exists()).toBe(true)
    expect(w.find('.zone-empty').text()).toContain('筹备中')
    expect(w.findAll('.game-entry')).toHaveLength(0)
  })
})
```

- [ ] **Step 2: RED** → FAIL（组件不存在）。
- [ ] **Step 3: 实现 GameList.vue**：头部 zone.name+tags；`v-for` 游戏卡（name、players 如有、desc、mechanisms chips、playable?「可玩」徽标:「敬请期待」徽标）；`@click="game.playable && $emit('select', game.key)"`；`v-if="games.length===0"` 空态（文案「该分区游戏筹备中，敬请期待」+zone tags）。
- [ ] **Step 4: GREEN** → PASS（3 tests）。

---

### Task 4: lobby 三级编排 + 返回大厅 + 轮询生命周期

**Files:**
- Modify: `boardgame-ui/src/views/lobby/index.vue`
- Test: `boardgame-ui/src/views/__tests__/lobby.spec.ts`（重写挂载为 query 驱动）

**Interfaces:**
- Consumes: Task 1 `resolveLevel/findZone/gamesInZone`、Task 2 ZoneGrid、Task 3 GameList。
- Produces（供 Task 6 实测定位）：level 0 含 `.zone-card` 与房间号加入卡、**无**「创建房间」按钮；level 1/2 顶栏下 `.lobby-nav` 内按钮文案「← 返回大厅」；level 2 含「创建房间」按钮与房间列表卡。

- [ ] **Step 1: 重写/新增失败测试**。spec 顶部 mocks 增加 `route: reactive({ query: {} as Record<string, string> })`（`import { reactive } from 'vue'`），`vi.mock('vue-router')` 增 `useRoute: () => mocks.route`；`mountLobby(query: Record<string, string> = {})` 先赋值 `mocks.route.query = query` 再 mount。既有用例改挂：mount resilience/auto refresh/join navigation(列表加入) 挂 `{zone:'abstract',game:'xiangqi'}`；logout/brand 挂 `{}`；join navigation 增 level 0 房间号用例。新增：

```ts
describe('lobby levels', () => {
  beforeEach(() => { /* clearAllMocks; userInfo alice; listRooms emptyList */ })
  it('level 0 shows zone grid and join-by-id, no create button', async () => {
    const w = mountLobby()
    await flushPromises()
    expect(w.findAll('.zone-card')).toHaveLength(7)
    expect(buttonByText(w, '创建房间')).toBeUndefined()
    expect(w.find('input[placeholder="房间号(如 ABC123)"]').exists()).toBe(true)
  })
  it('zone query shows game list without room cards', async () => {
    const w = mountLobby({ zone: 'abstract' })
    await flushPromises()
    expect(w.findAll('.game-entry')).toHaveLength(3)
    expect(buttonByText(w, '创建房间')).toBeUndefined()
  })
  it('zone+game query shows the three room cards', async () => {
    const w = mountLobby({ zone: 'abstract', game: 'xiangqi' })
    await flushPromises()
    expect(buttonByText(w, '创建房间')).toBeDefined()
    expect(w.text()).toContain('房间列表')
  })
  it('unplayable game query falls back to level 1 (no chess room ops)', async () => {
    const w = mountLobby({ zone: 'abstract', game: 'go' })
    await flushPromises()
    expect(w.findAll('.game-entry')).toHaveLength(3)
    expect(buttonByText(w, '创建房间')).toBeUndefined()
  })
  it('invalid query falls back to level 0', async () => {
    const w = mountLobby({ zone: 'nope', game: 'xiangqi' })
    await flushPromises()
    expect(w.findAll('.zone-card')).toHaveLength(7)
  })
  it('back button at level 1 and 2 pushes bare /lobby', async () => {
    for (const q of [{ zone: 'abstract' }, { zone: 'abstract', game: 'xiangqi' }]) {
      const w = mountLobby(q)
      await flushPromises()
      await buttonByText(w, '← 返回大厅')!.trigger('click')
      expect(mocks.push).toHaveBeenCalledWith('/lobby')
      w.unmount()
    }
  })
  it('zone card click pushes zone query; game card click pushes zone+game', async () => {
    const w = mountLobby()
    await flushPromises()
    await w.findAll('.zone-card')[5].trigger('click')
    expect(mocks.push).toHaveBeenCalledWith({ path: '/lobby', query: { zone: 'abstract' } })
    const w2 = mountLobby({ zone: 'abstract' })
    await flushPromises()
    await w2.findAll('.game-entry')[0].trigger('click')
    expect(mocks.push).toHaveBeenCalledWith({ path: '/lobby', query: { zone: 'abstract', game: 'xiangqi' } })
  })
  it('keeps logout in top bar at every level', async () => {
    for (const q of [{}, { zone: 'abstract' }, { zone: 'abstract', game: 'xiangqi' }]) {
      const w = mountLobby(q)
      await flushPromises()
      expect(buttonByText(w, '退出登录')).toBeDefined()
      w.unmount()
    }
  })
})
describe('lobby polling lifecycle', () => {
  beforeEach(() => { /* clearAllMocks; userInfo alice; listRooms emptyList; vi.useFakeTimers() */ })
  afterEach(() => vi.useRealTimers())
  it('stops polling when leaving level 2', async () => {
    mountLobby({ zone: 'abstract', game: 'xiangqi' })
    await vi.advanceTimersByTimeAsync(0)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(1)
    mocks.route.query = {}
    await vi.advanceTimersByTimeAsync(10000)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(1)
  })
})
```

  既有 auto refresh describe 的挂载全部改挂 level 2 query（断言不变）；mount resilience 的 fetchProfile-fail 用例挂 level 2（Review Focus 5）。join navigation 增 level 0 快捷加入用例（Review Focus 4）：

```ts
it('level 0 join-by-id validates and navigates without zone selection', async () => {
  mocks.api.getRoom.mockResolvedValue({ success: true, room: { roomId: 'ABC123' } })
  const w = mountLobby()
  await flushPromises()
  await w.find('input[placeholder="房间号(如 ABC123)"]').setValue('ABC123')
  await buttonByText(w, '加入')!.trigger('click')
  await flushPromises()
  expect(mocks.api.getRoom).toHaveBeenCalledWith('ABC123')
  expect(mocks.push).toHaveBeenCalledWith('/game/ABC123')
})
```
- [ ] **Step 2: RED**：`npx vitest run src/views/__tests__/lobby.spec.ts` → 新用例 FAIL（level 0 仍有创建卡、无 zone-card、无返回按钮）；记录失败清单。
- [ ] **Step 3: 重写 lobby/index.vue 编排**（房间操作函数 refreshRooms/handleCreate/handleJoin/handleJoinInput/statusText/errMsg 原样保留）：
  - `const route = useRoute()`；`const nav = computed(() => resolveLevel(route.query))`。
  - 模板：`lobby-top` 不变（logo+昵称+登出）；`<div v-if="nav.level > 0" class="lobby-nav"><el-button size="small" plain @click="backToLobby">← 返回大厅</el-button><span class="lobby-crumb">{{ nav.zone?.name }}<template v-if="nav.game"> / {{ nav.game.name }}</template></span></div>`；
    level 0：`<ZoneGrid :zones="ZONES" @select="openZone" />` + 房间号加入卡（原卡搬入）；
    level 1：`<GameList :zone="nav.zone!" :games="gamesInZone(nav.zone!.key)" @select="openGame" />`；
    level 2：创建卡+房间号卡+房间列表卡（原三卡）。
  - `openZone(key)` → `router.push({ path: '/lobby', query: { zone: key } })`；`openGame(key)` → push `{ path: '/lobby', query: { zone: nav.value.zone!.key, game: key } }`；`backToLobby()` → `router.push('/lobby')`。
  - 轮询：`stopPolling()` 清 interval+两监听；`startPolling()` = refreshRooms()+interval 5s(仅 visible)+监听；`watch(() => nav.value.level, (lv) => lv === 2 ? startPolling() : stopPolling(), { immediate: true })`；onMounted 仅 profile 水合+userName 缺失 toast（不再直接 refreshRooms）；onUnmounted stopPolling。
  - 样式：.lobby-nav flex gap、.lobby-crumb 色 #888；zone/game 卡样式在子组件内。
- [ ] **Step 4: GREEN**：lobby spec 全绿 → 再跑全量 `npm test` 确认无跨文件回归（home/game 等不受影响）。

---

### Task 5: 首页象棋卡深链

**Files:**
- Modify: `boardgame-ui/src/views/home/index.vue`（games 数组 xiangqi.route）
- Test: `boardgame-ui/src/views/__tests__/home.spec.ts`

**Interfaces:**
- Consumes: 无新接口；深链字符串 `/lobby?zone=abstract&game=xiangqi`（与 Task 1 key 一致）。

- [ ] **Step 1: 写失败测试**（沿用该 spec 既有 mocks/mount helper）：

```ts
it('xiangqi card deep-links into the chess room level', async () => {
  const w = mountHome()
  await flushPromises()
  await w.findAll('.game-card')[0].trigger('click')
  expect(mocks.push).toHaveBeenCalledWith('/lobby?zone=abstract&game=xiangqi')
})
```

- [ ] **Step 2: RED** → FAIL（现 push '/lobby'）。
- [ ] **Step 3: 改 route 字符串**：`route: '/lobby?zone=abstract&game=xiangqi'`（仅此一行的值）。
- [ ] **Step 4: GREEN** → home spec 全绿。

---

### Task 6: 全量验证 + 实走 + 台账

**Files:**
- Create: `logs/lobby_zones_walk.py`（一次性）
- Create: `.superpowers/sdd/2026-11-19-lobby-zones/progress.md`（台账）

- [ ] **Step 1: 全量门槛**：`npm test`（记录实际总数，预期 ≥105）+ `npm run build` 退出码 0；任何红→修到绿并记录。
- [ ] **Step 2: 实走脚本**（token 注入沿用 `logs/token_admin.txt`，过期则识图 curl 重取）：level 0 截图(7 卡)→点抽象/双人→level 1 截图(3 游戏卡+标签)→点象棋→level 2 截图(三卡)→「← 返回大厅」→断言 url=/lobby 且无 query→直链 `?zone=abstract&game=go` 断言 level 1→直链 level 2 创建房间→game 页「← 返回大厅」→断言 /lobby→level 2 停留 6s 断言 listRooms 网络请求≥2（轮询活）→回 level 0 停 6s 断言无新请求（泄漏检查）。截图存 `logs/zones_*.png` 并目检。
- [ ] **Step 3: 台账**：progress.md 记 RED/GREEN 证据文件名、全量计数、build 退出码、实走断言结果、我的裁决清单（icon/映射/空态文案）、workspace-keep（不 commit）。
- [ ] **Step 4: 汇报**：向用户报完成+截图路径+返回大厅实测结论；提醒后端重启仍 pending（数据源+乱码修复，与本功能无关）。
