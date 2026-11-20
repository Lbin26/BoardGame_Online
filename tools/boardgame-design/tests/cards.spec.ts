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