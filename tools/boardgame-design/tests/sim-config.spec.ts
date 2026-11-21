import { describe, it, expect, afterEach } from 'vitest'
import { writeFileSync, unlinkSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { getAtPath, setAtPath, deepMerge, resolveParams, loadSimConfig, type SimConfigInput } from '../src/schema/simConfig'

const miniGame = fileURLToPath(new URL('./fixtures/mini-game.yaml', import.meta.url))
const tmpFiles: string[] = []
afterEach(() => { for (const p of tmpFiles.splice(0)) if (existsSync(p)) unlinkSync(p) })

const baseConfig: SimConfigInput = { game: miniGame, sim: 'economy', from: 'subsystems.economy' }

describe('sim config & params resolution', () => {
  it('getAtPath 取值；缺失路径 throw 含路径', () => {
    expect(getAtPath({ a: { b: 1 } }, 'a.b')).toBe(1)
    expect(() => getAtPath({ a: {} }, 'a.x')).toThrow(/a\.x/)
  })
  it('点路径支持数组下标（裁定：dice 模拟须引用具体骰子条目）', () => {
    expect(getAtPath({ arr: [{ x: 1 }, { x: 2 }] }, 'arr.1.x')).toBe(2)
    expect(() => getAtPath({ arr: [] }, 'arr.0')).toThrow(/arr\.0/)
    const orig = { arr: [{ x: 1 }] }
    const next = setAtPath(orig, 'arr.0.x', 9)
    expect((next.arr as { x: number }[])[0].x).toBe(9)
    expect(orig.arr[0].x).toBe(1)
    expect(Array.isArray(next.arr)).toBe(true)  // 数组须保持数组（下游 for...of 依赖）
  })
  it('I2：原型链段（toString/constructor）→ throw 路径不存在', () => {
    expect(() => getAtPath({ a: {} }, 'a.toString')).toThrow(/路径不存在/)
    expect(() => getAtPath({ a: {} }, 'a.constructor')).toThrow(/路径不存在/)
  })
  it('I2：数组非规范下标（length/-1/01）→ throw 路径不存在', () => {
    expect(() => getAtPath({ arr: [{ x: 1 }] }, 'arr.length')).toThrow(/路径不存在/)
    expect(() => getAtPath({ arr: [1] }, 'arr.-1')).toThrow(/路径不存在/)
    expect(() => getAtPath({ arr: [1] }, 'arr.01')).toThrow(/路径不存在/)
  })
  it('deepMerge：对象递归、数组整体替换、标量覆盖', () => {
    expect(deepMerge({ a: { x: 1, y: 2 }, arr: [1, 2] }, { a: { y: 3 }, arr: [9] }))
      .toEqual({ a: { x: 1, y: 3 }, arr: [9] })
  })
  it('setAtPath 不可变且可建中间段', () => {
    const orig = { a: { b: 1 } }
    const next = setAtPath(orig, 'a.b', 5)
    expect(next).toEqual({ a: { b: 5 } })
    expect(orig.a.b).toBe(1)
    expect(setAtPath({}, 'a.b', 5)).toEqual({ a: { b: 5 } })
  })
  it('resolveParams：game.yaml 为单一事实来源，overrides 合并，默认 seed/runs', () => {
    const r = resolveParams({ ...baseConfig, overrides: { initial: { gold: 99 } } }, '.')
    expect((r.params.initial as Record<string, number>).gold).toBe(99)
    expect(r.params.perTurn).toEqual([{ resource: 'gold', produce: 2, consume: 1 }])
    expect(r.seed).toBe(42)
    expect(r.runs).toBe(10000)
  })
  it('from 路径不存在 → throw 含路径', () => {
    expect(() => resolveParams({ ...baseConfig, from: 'subsystems.nonexistent' }, '.')).toThrow(/subsystems\.nonexistent/)
  })
  it('game 文件不存在 → throw 含文件名', () => {
    expect(() => resolveParams({ ...baseConfig, game: 'no-such.yaml' }, '.')).toThrow(/no-such\.yaml/)
  })
  it('loadSimConfig 读 YAML 并应用默认值', () => {
    const p = fileURLToPath(new URL('./fixtures/.tmp-simcfg.yaml', import.meta.url))
    writeFileSync(p, `game: ${miniGame}\nsim: economy\nfrom: subsystems.economy\n`, 'utf-8')
    tmpFiles.push(p)
    const cfg = loadSimConfig(p)
    expect(cfg.sim).toBe('economy')
    expect(cfg.seed).toBe(42)
    expect(cfg.runs).toBe(10000)
  })
})