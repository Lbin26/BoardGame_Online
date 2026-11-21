import { describe, it, expect } from 'vitest'
import { projectEconomy, simulateEconomy, type EconomyParams } from '../src/sim/economy'

describe('economy', () => {
  it('投影：initial 5 +2/-1 三回合 → 6/7/8，netFlow 1，有沉没口', () => {
    const p: EconomyParams = { resources: ['gold'], initial: { gold: 5 }, perTurn: [{ resource: 'gold', produce: 2, consume: 1 }] }
    const r = projectEconomy(p, 3)
    expect(r.rows.map(x => x.stocks.gold)).toEqual([6, 7, 8])
    expect(r.netFlow.gold).toBe(1)
    expect(r.noSink).toEqual([])
  })
  it('consume 钳制到库存，不为负', () => {
    const p: EconomyParams = { resources: ['gold'], initial: { gold: 0 }, perTurn: [{ resource: 'gold', produce: 0, consume: 5 }] }
    const r = projectEconomy(p, 3)
    expect(r.rows.map(x => x.stocks.gold)).toEqual([0, 0, 0])
  })
  it('noSink：有产出无消耗口 → 标记', () => {
    const p: EconomyParams = { resources: ['gold'], perTurn: [{ resource: 'gold', produce: 2, consume: 0 }] }
    expect(projectEconomy(p, 1).noSink).toContain('gold')
  })
  it('greedy 策略手算：5 起 +2/回合，买 3 费 1vp，两回合 totalVp 3', () => {
    const p: EconomyParams = {
      resources: ['gold'], initial: { gold: 5 },
      perTurn: [{ resource: 'gold', produce: 2, consume: 0 }],
      actions: [{ key: 'buy', cost: { gold: 3 }, vp: 1 }],
    }
    const r = simulateEconomy(p, 2, { type: 'greedy' })
    expect(r.rows[0].stocks.gold).toBe(1)
    expect(r.rows[0].actionsTaken).toEqual(['buy', 'buy'])
    expect(r.rows[1].stocks.gold).toBe(0)
    expect(r.totalVp).toBe(3)
    expect(r.stalled).toBe(false)
  })
  it('stall：连续 stallK 回合无行动可负担 → stalled，跑完不死循环', () => {
    const p: EconomyParams = {
      resources: ['gold'], initial: { gold: 0 },
      actions: [{ key: 'buy', cost: { gold: 3 }, vp: 1 }], stallK: 2,
    }
    const r = simulateEconomy(p, 5, { type: 'greedy' })
    expect(r.stalled).toBe(true)
    expect(r.stallFromTurn).toBe(1)
    expect(r.rows.length).toBe(5)
  })
  it('C1：cost:{} 免费行动 → throw 点名该行动（防无限循环）', () => {
    const p: EconomyParams = { resources: ['gold'], actions: [{ key: 'free', cost: {}, vp: 1 }] }
    expect(() => simulateEconomy(p, 1, { type: 'greedy' })).toThrow(/free/)
  })
  it('C1：净零 cost/gain 行动 → throw（防无限循环）', () => {
    const p: EconomyParams = {
      resources: ['gold'], initial: { gold: 5 },
      actions: [{ key: 'loop', cost: { gold: 1 }, gain: { gold: 1 }, vp: 1 }],
    }
    expect(() => simulateEconomy(p, 1, { type: 'greedy' })).toThrow(/loop/)
  })
  it('C1：净产出 gain>cost 行动 → throw（防无限循环）', () => {
    const p: EconomyParams = {
      resources: ['gold'], initial: { gold: 5 },
      actions: [{ key: 'farm', cost: { gold: 1 }, gain: { gold: 2 }, vp: 1 }],
    }
    expect(() => simulateEconomy(p, 1, { type: 'greedy' })).toThrow(/farm/)
  })
  it('C1：资源循环（A↔B 互换）→ 单回合行动上限 throw', () => {
    const p: EconomyParams = {
      resources: ['gold', 'wood'], initial: { gold: 5, wood: 5 },
      actions: [
        { key: 'a', cost: { gold: 1 }, gain: { wood: 1 } },
        { key: 'b', cost: { wood: 1 }, gain: { gold: 1 } },
      ],
    }
    expect(() => simulateEconomy(p, 1, { type: 'greedy' })).toThrow(/1000/)
  })
  it('I4：priority.order 未知键 → throw 列出该键（防假 stall 证据）', () => {
    const p: EconomyParams = {
      resources: ['gold'], initial: { gold: 99 },
      actions: [{ key: 'buy', cost: { gold: 3 }, vp: 1 }],
    }
    expect(() => simulateEconomy(p, 2, { type: 'priority', order: ['typoKey'] })).toThrow(/typoKey/)
  })
  it('priority order 生效：先买贵的', () => {
    const p: EconomyParams = {
      resources: ['gold'], initial: { gold: 5 },
      actions: [{ key: 'cheap', cost: { gold: 1 }, vp: 1 }, { key: 'pricey', cost: { gold: 4 }, vp: 3 }],
    }
    const r = simulateEconomy(p, 1, { type: 'priority', order: ['pricey', 'cheap'] })
    expect(r.rows[0].actionsTaken).toEqual(['pricey', 'cheap'])
    expect(r.totalVp).toBe(4)
  })
})