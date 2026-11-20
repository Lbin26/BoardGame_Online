import { describe, it, expect } from 'vitest'
import { assertCaps, assertNoDangerousKeys, assertTextSafe, CAPS } from '../src/guard.js'
import { HttpError } from '../src/http.js'

const conflictParams = (over: Record<string, unknown> = {}) => ({
  attacker: { diceCount: 2, faces: [1, 2, 3, 4, 5, 6], hitSymbols: [6], hp: 5 },
  defender: { diceCount: 2, faces: [1, 2, 3, 4, 5, 6], hitSymbols: [6], hp: 5 },
  ...over,
})
const diceParams = (over: Record<string, unknown> = {}) => ({
  count: 2, faces: [1, 2, 3, 4, 5, 6], symbol: 6, op: '>=', value: 10, ...over,
})

function expect400(fn: () => void, capValue: number): void {
  try {
    fn()
    throw new Error('应当 throw')
  } catch (e) {
    expect(e).toBeInstanceOf(HttpError)
    expect((e as HttpError).status).toBe(400)
    expect((e as HttpError).message).toContain(String(capValue))
  }
}

describe('assertCaps（解析后最终参数）', () => {
  it('runs 上限 50000', () => {
    expect400(() => assertCaps('dice', diceParams(), 50001), CAPS.runs)
    expect(() => assertCaps('dice', diceParams(), 50000)).not.toThrow()
  })
  it('dice count ≤ 200、faces ≤ 64', () => {
    expect400(() => assertCaps('dice', diceParams({ count: 201 }), 10), CAPS.diceCount)
    expect400(() => assertCaps('dice', diceParams({ faces: Array(65).fill(1) }), 10), CAPS.diceFaces)
    expect(() => assertCaps('dice', diceParams({ count: 200, faces: Array(64).fill(1) }), 10)).not.toThrow()
  })
  it('economy turns ≤ 500（缺省 20 放行）', () => {
    expect400(() => assertCaps('economy', { resources: ['gold'], turns: 501 }, 10), CAPS.economyTurns)
    expect(() => assertCaps('economy', { resources: ['gold'] }, 10)).not.toThrow()
    expect(() => assertCaps('economy', { resources: ['gold'], turns: 500 }, 10)).not.toThrow()
  })
  it('cards deck 总数 ≤ 100000、draw ≤ 100000（logFact 巨数组进程级 OOM 护栏，Final #5）', () => {
    const cardsParams = (over: Record<string, unknown> = {}) => ({
      deck: [{ key: 'a', count: 3 }], draw: 2, target: 'a', op: '>=', value: 1, ...over,
    })
    expect400(() => assertCaps('cards', cardsParams({ deck: [{ key: 'a', count: 100000001 }] }), 10), CAPS.cardsDeckTotal)
    expect400(() => assertCaps('cards', cardsParams({ deck: [{ key: 'a', count: 60000 }, { key: 'b', count: 60000 }] }), 10), CAPS.cardsDeckTotal)
    expect400(() => assertCaps('cards', cardsParams({ draw: 100001 }), 10), CAPS.cardsDraw)
    expect(() => assertCaps('cards', cardsParams({ deck: [{ key: 'a', count: 100000 }], draw: 100000 }), 10)).not.toThrow()
  })
  it('conflict maxRounds ≤ 200、diceCount ≤ 20、hp ≤ 1000、faces ≤ 64', () => {
    expect400(() => assertCaps('conflict', conflictParams({ maxRounds: 201 }), 10), CAPS.conflictMaxRounds)
    expect400(() => assertCaps('conflict', conflictParams({ attacker: { diceCount: 21, faces: [1], hitSymbols: [1], hp: 5 } }), 10), CAPS.conflictDiceCount)
    expect400(() => assertCaps('conflict', conflictParams({ defender: { diceCount: 2, faces: [1], hitSymbols: [1], hp: 1001 } }), 10), CAPS.conflictHp)
    expect400(() => assertCaps('conflict', conflictParams({ attacker: { diceCount: 2, faces: Array(65).fill(1), hitSymbols: [1], hp: 5 } }), 10), CAPS.conflictFaces)
    expect(() => assertCaps('conflict', conflictParams({ maxRounds: 200 }), 10)).not.toThrow()
  })
})

describe('assertNoDangerousKeys（原型污染前置拒绝）', () => {
  it('__proto__ 键 → 400', () => {
    expect(() => assertNoDangerousKeys('overrides:\n  __proto__:\n    polluted: 1\n')).toThrow(HttpError)
  })
  it('constructor/prototype 键 → 400', () => {
    expect(() => assertNoDangerousKeys('constructor:\n  x: 1\n')).toThrow(HttpError)
    expect(() => assertNoDangerousKeys('a:\n  prototype: 1\n')).toThrow(HttpError)
  })
  it('正常文本放行', () => {
    expect(() => assertNoDangerousKeys('sim: dice\noverrides:\n  count: 2\n')).not.toThrow()
  })
})

describe('assertTextSafe（解析后自有键遍历，Final #4）', () => {
  it('flow 风格 __proto__ → 400（文本正则漏网形态）', () => {
    expect(() => assertTextSafe('overrides: {__proto__: {polluted: 1}}')).toThrow(HttpError)
  })
  it('引号键 "__proto__" → 400', () => {
    expect(() => assertTextSafe('overrides:\n  "__proto__":\n    polluted: 1\n')).toThrow(HttpError)
  })
  it('数组内嵌套危险键 → 400', () => {
    expect(() => assertTextSafe('a:\n  - constructor: 1\n')).toThrow(HttpError)
  })
  it('循环别名不得栈溢出（YAML anchor/alias 可成环）', () => {
    expect(() => assertTextSafe('a: &a\n  b: *a\n')).not.toThrow()
  })
  it('正常文本放行', () => {
    expect(() => assertTextSafe('sim: dice\noverrides:\n  count: 2\n')).not.toThrow()
  })
})