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
  it('同种子 MC 逐位相同', () => {
    expect(diceMC(sword, 42, 1000)).toEqual(diceMC(sword, 42, 1000))
  })
})