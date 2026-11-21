import { describe, it, expect } from 'vitest'
import { fileURLToPath } from 'node:url'
import { loadGameDoc, validateDoc } from '../src/schema/load'

const GAME_YAML = fileURLToPath(new URL('../../../design/games/splendor/game.yaml', import.meta.url))

const GEM_COLORS = ['white', 'blue', 'green', 'red', 'black'] as const
type Gem = (typeof GEM_COLORS)[number]

interface FlatCard {
  key: string
  cost: Partial<Record<Gem, number>>
  gain: Partial<Record<Gem, number>>
  vp?: number
}

function flatCards(deckKey: string): FlatCard[] {
  const doc = loadGameDoc(GAME_YAML)
  const deck = doc.components.decks?.find((d) => d.key === deckKey)
  if (!deck) throw new Error(`deck 不存在: ${deckKey}`)
  return deck.cards.map((c) => ({
    key: c.key,
    cost: (c.cost ?? {}) as FlatCard['cost'],
    gain: (c.gain ?? {}) as FlatCard['gain'],
    vp: c.vp,
  }))
}

describe('splendor 官方数据集（P1 校验）', () => {
  it('game.yaml 通过 schema 与机制词汇校验', () => {
    const r = validateDoc(GAME_YAML)
    expect(r.ok, r.ok ? '' : r.errors.join('\n')).toBe(true)
  })

  it('张数：一级 40 / 二级 30 / 三级 20 / 贵族 10', () => {
    expect(flatCards('level1')).toHaveLength(40)
    expect(flatCards('level2')).toHaveLength(30)
    expect(flatCards('level3')).toHaveLength(20)
    expect(flatCards('nobles')).toHaveLength(10)
  })

  it('key 全局唯一且 count 均为 1', () => {
    const all = [...flatCards('level1'), ...flatCards('level2'), ...flatCards('level3'), ...flatCards('nobles')]
    expect(all).toHaveLength(100)
    expect(new Set(all.map((c) => c.key)).size).toBe(100)
    const doc = loadGameDoc(GAME_YAML)
    for (const deck of doc.components.decks ?? []) {
      for (const card of deck.cards) expect(card.count).toBe(1)
    }
  })

  it('发展卡：费用为正、单色 ≤7、加成为唯一合法色且恒为 1', () => {
    for (const deckKey of ['level1', 'level2', 'level3'] as const) {
      for (const card of flatCards(deckKey)) {
        const colors = Object.keys(card.cost) as Gem[]
        const total = Object.values(card.cost).reduce((s, n) => s + (n ?? 0), 0)
        expect(total, `${card.key} 费用合计须为正`).toBeGreaterThan(0)
        for (const c of colors) {
          expect(GEM_COLORS, `${card.key} 费用含非法色 ${c}`).toContain(c)
          expect(card.cost[c], `${card.key} 单色费用 ${c} 超 7`).toBeLessThanOrEqual(7)
          expect(Number.isInteger(card.cost[c]), `${card.key} 费用非整数`).toBe(true)
        }
        const gainKeys = Object.keys(card.gain)
        expect(gainKeys, `${card.key} 加成须为唯一色`).toHaveLength(1)
        expect(GEM_COLORS, `${card.key} 加成非法色`).toContain(gainKeys[0] as Gem)
        expect(card.gain[gainKeys[0] as Gem]).toBe(1)
      }
    }
  })

  it('每级每色加成卡数：一级 8 / 二级 6 / 三级 4', () => {
    const expected: Record<string, number> = { level1: 8, level2: 6, level3: 4 }
    for (const [deckKey, n] of Object.entries(expected)) {
      for (const color of GEM_COLORS) {
        const count = flatCards(deckKey).filter((c) => Object.keys(c.gain)[0] === color).length
        expect(count, `${deckKey} ${color} 加成卡数`).toBe(n)
      }
    }
  })

  it('VP 合计：一级 5 / 二级 55 / 三级 80 / 贵族 30（卡牌总计 140）', () => {
    const vpSum = (k: string) => flatCards(k).reduce((s, c) => s + (c.vp ?? 0), 0)
    expect(vpSum('level1')).toBe(5)
    expect(vpSum('level2')).toBe(55)
    expect(vpSum('level3')).toBe(80)
    expect(vpSum('nobles')).toBe(30)
    expect(vpSum('level1') + vpSum('level2') + vpSum('level3')).toBe(140)
  })

  it('贵族：各 3VP，需求为两色 4+4 或三色 3+3+3（合计 8）', () => {
    for (const noble of flatCards('nobles')) {
      expect(noble.vp).toBe(3)
      const colors = Object.keys(noble.cost) as Gem[]
      const values = Object.values(noble.cost)
      expect(colors.length, `${noble.key} 贵族须为两色或三色`).toBeGreaterThanOrEqual(2)
      expect(colors.length).toBeLessThanOrEqual(3)
      const total = values.reduce((s, n) => s + (n ?? 0), 0)
      expect(total, `${noble.key} 需求合计须为 8（两色）或 9（三色）`).toBe(colors.length === 2 ? 8 : 9)
      if (colors.length === 2) values.forEach((v) => expect(v).toBe(4))
      else values.forEach((v) => expect(v).toBe(3))
    }
  })

  it('贵族结构：5 个相邻色对 + 5 个补集三色组（五边形对称）', () => {
    const nobles = flatCards('nobles')
    const norm = (c: FlatCard['cost']) => GEM_COLORS.filter((g) => (c[g] ?? 0) > 0).sort().join('+')
    const pairs = nobles.filter((n) => Object.keys(n.cost).length === 2).map((n) => norm(n.cost))
    const triples = nobles.filter((n) => Object.keys(n.cost).length === 3).map((n) => norm(n.cost)).sort()
    expect([...pairs].sort()).toEqual(['black+red', 'black+white', 'blue+green', 'blue+white', 'green+red'])
    // 三色组 = 全五色减去对应双色对
    const allColors = ['black', 'blue', 'green', 'red', 'white']
    const complements = pairs
      .map((p) => allColors.filter((c) => !p.split('+').includes(c)).sort().join('+'))
      .sort()
    expect(triples).toEqual(complements)
  })

  it('二级结构：每色含 5 同色 2VP + 6 同色 3VP，且 5 费用构成五色完美排列', () => {
    const l2 = flatCards('level2')
    // 6 同色 3VP：每色一张
    const sixSame = l2.filter((c) => {
      const colors = Object.keys(c.cost)
      return colors.length === 1 && c.cost[colors[0] as Gem] === 6 && c.vp === 3
    })
    expect(sixSame).toHaveLength(5)
    // 5 同色 2VP：每色一张，费用色五色各现一次
    const fiveSame = l2.filter((c) => {
      const colors = Object.keys(c.cost)
      return colors.length === 1 && c.cost[colors[0] as Gem] === 5 && c.vp === 2
    })
    expect(fiveSame).toHaveLength(5)
    const feeColors = fiveSame.map((c) => Object.keys(c.cost)[0]).sort()
    expect(feeColors).toEqual([...GEM_COLORS].sort())
  })

  it('仲裁回归：两张曾经分歧的卡（黑 5白2VP / 绿 3白2绿3红1VP）钉死为共识值', () => {
    const black5 = flatCards('level2').find((c) => c.key === 'l2-029')
    expect(black5).toMatchObject({ vp: 2, gain: { black: 1 }, cost: { white: 5 } })
    const green1 = flatCards('level2').find((c) => c.key === 'l2-014')
    expect(green1).toMatchObject({ vp: 1, gain: { green: 1 }, cost: { white: 3, green: 2, red: 3 } })
  })

  it('三级结构：每色 7 同色 4VP、3+7 五分、3+6+3 四分、3+3+3+5 三分', () => {
    const l3 = flatCards('level3')
    expect(l3.filter((c) => Object.keys(c.cost).length === 1)).toHaveLength(5)
    for (const color of GEM_COLORS) {
      const own = l3.filter((c) => Object.keys(c.gain)[0] === color)
      expect(own).toHaveLength(4)
      const vpList = own.map((c) => c.vp).sort((a, b) => (a ?? 0) - (b ?? 0))
      expect(vpList).toEqual([3, 4, 4, 5])
    }
  })
})
