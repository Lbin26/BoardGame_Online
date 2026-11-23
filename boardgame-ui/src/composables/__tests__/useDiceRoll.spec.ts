import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useDiceRoll } from '@/composables/useDiceRoll'

describe('useDiceRoll', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('翻滚中 rolling=true，定格后 rolling=false 且点数等于服务端值', () => {
    const d = useDiceRoll({ rollMs: 700, tickMs: 80 })
    d.rollTo(5)

    expect(d.rolling.value).toBe(true)
    vi.advanceTimersByTime(700)
    expect(d.rolling.value).toBe(false)
    expect(d.face.value).toBe(5)
    expect(d.settleVersion.value).toBe(1)
  })

  it('翻滚过程中点数会变化（模拟换面）', () => {
    const d = useDiceRoll({ rollMs: 700, tickMs: 80 })
    d.rollTo(3)
    const seen = new Set<number>([d.face.value])
    for (let i = 0; i < 8; i++) {
      vi.advanceTimersByTime(80)
      seen.add(d.face.value)
    }
    expect(seen.size).toBeGreaterThan(1)
    vi.advanceTimersByTime(700)
    expect(d.face.value).toBe(3)
  })

  it('非法点数回退为 1，不抛异常', () => {
    const d = useDiceRoll({ rollMs: 100 })
    d.rollTo(99)
    vi.advanceTimersByTime(100)
    expect(d.face.value).toBe(1)
  })

  it('settle 可立即定格', () => {
    const d = useDiceRoll()
    d.rollTo(6)
    d.settle(2)
    expect(d.rolling.value).toBe(false)
    expect(d.face.value).toBe(2)
  })
})
