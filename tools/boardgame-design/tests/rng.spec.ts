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