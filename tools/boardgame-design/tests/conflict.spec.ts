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
  it('同种子逐位相同', () => {
    expect(conflictMC({ attacker: side(3), defender: side(3) }, 7, 500)).toEqual(conflictMC({ attacker: side(3), defender: side(3) }, 7, 500))
  })
  it('maxRounds=1 且双方 hp 高 → 全 draw', () => {
    const r = conflictMC({ attacker: side(100), defender: side(100), maxRounds: 1 }, 1, 100)
    expect(r.drawRate).toBe(1)
  })
})