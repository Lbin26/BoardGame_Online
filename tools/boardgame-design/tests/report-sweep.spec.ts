import { describe, it, expect, afterEach } from 'vitest'
import { readFileSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { runSim, writeReport } from '../src/report'
import { runSweep } from '../src/sim/sweep'
import type { SimConfigInput, SweepConfigInput } from '../src/schema/simConfig'

const miniGame = fileURLToPath(new URL('./fixtures/mini-game.yaml', import.meta.url))
const tmpDirs: string[] = []
const tmp = () => { const d = mkdtempSync(join(tmpdir(), 'bgd-')); tmpDirs.push(d); return d }
afterEach(() => { for (const d of tmpDirs.splice(0)) rmSync(d, { recursive: true, force: true }) })

const diceParams = { count: 2, faces: [1,2,3,4,5,6], symbol: 6, op: '>=', value: 1 }

describe('runSim / writeReport / runSweep', () => {
  it('runSim dice → exact + mc', () => {
    const r = runSim('dice', diceParams, 42, 1000)
    expect((r.exact as { probability: number }).probability).toBeCloseTo(11/36, 10)
    expect(typeof (r.mc as { probability: number }).probability).toBe('number')
  })
  it('runSim economy → rows/stalled/totalVp（mini-game 手算 totalVp 2）', () => {
    const params = {
      resources: ['gold'], initial: { gold: 5 },
      perTurn: [{ resource: 'gold', produce: 2, consume: 1 }],
      actions: [{ key: 'buy', cost: { gold: 3 }, vp: 1 }],
      turns: 2, policy: { type: 'greedy' },
    }
    const r = runSim('economy', params, 42, 1000)
    expect((r.rows as unknown[]).length).toBe(2)
    expect(r.totalVp).toBe(2)
    expect(r.stalled).toBe(false)
  })
  it('writeReport：md 含种子与指标，json 可解析，同输入逐位相同', () => {
    const d = tmp()
    const out = join(d, 'report.md')
    const config: SimConfigInput = { game: miniGame, sim: 'dice', from: 'components.dice', seed: 42, runs: 1000 }
    const result = runSim('dice', diceParams, 42, 1000)
    const data = { title: '测试报告', config, params: diceParams, result }
    const a = writeReport(out, data)
    const b = writeReport(out, data)
    const md = readFileSync(a.mdPath, 'utf-8')
    expect(md).toContain('42')
    expect(md).toContain('probability')
    const json = JSON.parse(readFileSync(a.jsonPath, 'utf-8'))
    expect(json.result).toEqual(result)
    expect(readFileSync(a.mdPath, 'utf-8')).toBe(readFileSync(b.mdPath, 'utf-8'))
    expect(readFileSync(a.jsonPath, 'utf-8')).toBe(readFileSync(b.jsonPath, 'utf-8'))
  })
  it('runSweep：逐值覆盖不污染基础 params，totalVp 单调不减', () => {
    const config: SweepConfigInput = {
      game: miniGame, sim: 'economy', from: 'subsystems.economy',
      overrides: { turns: 2, policy: { type: 'greedy' } },
      sweep: { param: 'initial.gold', values: [0, 5, 10] },
    }
    const r = runSweep(config, '.')
    expect(r.rows.map(x => x.value)).toEqual([0, 5, 10])
    const vps = r.rows.map(x => x.result.totalVp as number)
    expect(vps[1]).toBeGreaterThanOrEqual(vps[0])
    expect(vps[2]).toBeGreaterThanOrEqual(vps[1])
    expect((r.baseParams.initial as Record<string, number>).gold).toBe(5)
  })
  it('未知 sim 名 → throw', () => {
    expect(() => runSim('bogus' as never, {}, 1, 1)).toThrow(/bogus/)
  })
  it('I1：dice 缺 count → throw 点名 sim 与字段（而非 NaN 垃圾）', () => {
    expect(() => runSim('dice', { faces: [1, 2], symbol: 1, op: '>=', value: 1 }, 42, 10)).toThrow(/dice.*count|count.*dice/)
  })
  it('I1：dice count 负数 → throw', () => {
    expect(() => runSim('dice', { ...diceParams, count: -2 }, 42, 10)).toThrow(/count/)
  })
  it('I1：cards 传入数组（from components.decks）→ throw 点名 cards（而非 TypeError）', () => {
    expect(() => runSim('cards', [{ key: 'a', count: 1 }] as never, 42, 10)).toThrow(/cards/)
  })
  it('I1：economy turns:0 → throw 点名 turns', () => {
    expect(() => runSim('economy', { resources: ['gold'], turns: 0 }, 42, 10)).toThrow(/turns/)
  })
  it('I1：economy policy 非法 type → throw 点名 policy', () => {
    expect(() => runSim('economy', { resources: ['gold'], policy: { type: 'bogus' } }, 42, 10)).toThrow(/policy/)
  })
  it('I1：conflict 缺 attacker 字段 → throw 点名 conflict', () => {
    expect(() => runSim('conflict', { attacker: {}, defender: {} }, 42, 10)).toThrow(/conflict/)
  })
  it('I3：economy policy static → 静态投影（netFlow/noSink，无 actionsTaken）', () => {
    const params = {
      resources: ['gold'], initial: { gold: 5 },
      perTurn: [{ resource: 'gold', produce: 2, consume: 1 }],
      turns: 3, policy: { type: 'static' },
    }
    const r = runSim('economy', params, 42, 10)
    expect((r.netFlow as Record<string, number>).gold).toBe(1)
    expect(r.noSink).toEqual([])
    expect((r.rows as { stocks: Record<string, number> }[]).map(x => x.stocks.gold)).toEqual([6, 7, 8])
    expect(r.totalVp).toBeUndefined()
  })
  it('I5：cards 从牌库条目投影（cards→deck，单一事实来源）', () => {
    const params = { cards: [{ key: 'silk', count: 3 }, { key: 'wood', count: 7 }], draw: 2, target: 'silk', op: '>=', value: 1 }
    const r = runSim('cards', params, 42, 10)
    expect(r.probability as number).toBeCloseTo(1 - 21 / 45, 10)
    expect(r.expectedCount as number).toBeCloseTo(0.6, 10)
  })
})