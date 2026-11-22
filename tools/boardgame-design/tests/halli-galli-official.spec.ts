import { describe, it, expect } from 'vitest'
import { fileURLToPath } from 'node:url'
import { loadGameDoc, validateDoc } from '../src/schema/load'

const GAME_YAML = fileURLToPath(new URL('../../../design/games/halli-galli/game.yaml', import.meta.url))

const FRUITS = ['apple', 'lemon', 'cherry', 'grape'] as const
/** 每种水果的牌面数量 → 该牌型的张数 */
const DIST: Record<number, number> = { 1: 5, 2: 3, 3: 3, 4: 2, 5: 1 }

function flatCards() {
  const doc = loadGameDoc(GAME_YAML)
  const deck = doc.components.decks?.find((d) => d.key === 'fruit')
  if (!deck) throw new Error('fruit deck 不存在')
  return deck.cards
}

describe('halli-galli 数据集（P1 校验）', () => {
  it('game.yaml 通过 schema 与机制词汇校验', () => {
    const r = validateDoc(GAME_YAML)
    expect(r.ok, r.ok ? '' : r.errors.join('\n')).toBe(true)
  })

  it('人数 2-6，机制为 reaction + simultaneous-action', () => {
    const doc = loadGameDoc(GAME_YAML)
    expect(doc.meta.players).toEqual({ min: 2, max: 6 })
    expect(doc.mechanisms).toEqual(['reaction', 'simultaneous-action'])
  })

  it('56 张牌、4 种水果、每种 14 张', () => {
    const cards = flatCards()
    const total = cards.reduce((s, c) => s + c.count, 0)
    expect(total).toBe(56)
    for (const f of FRUITS) {
      const n = cards.filter((c) => c.gain?.[f] !== undefined).reduce((s, c) => s + c.count, 0)
      expect(n, `${f} 应为 14 张`).toBe(14)
    }
  })

  it('每种水果分布 5/3/3/2/1，牌面 1~5 个', () => {
    const cards = flatCards()
    for (const f of FRUITS) {
      for (const [face, count] of Object.entries(DIST)) {
        const card = cards.find((c) => c.gain?.[f] === Number(face))
        expect(card, `${f} 缺 ${face} 个的牌型`).toBeTruthy()
        expect(card!.count, `${f}-${face} 张数`).toBe(count)
      }
    }
  })

  it('每张牌只画一种水果，且数量为 1~5', () => {
    for (const c of flatCards()) {
      const keys = Object.keys(c.gain ?? {})
      expect(keys, `${c.key} 应只有一种水果`).toHaveLength(1)
      const n = c.gain?.[keys[0]] ?? 0
      expect(n).toBeGreaterThanOrEqual(1)
      expect(n).toBeLessThanOrEqual(5)
    }
  })

  it('水果面合计 132（每种 33）', () => {
    const total = flatCards().reduce(
      (s, c) => s + Object.values(c.gain ?? {}).reduce((a, v) => a + (v ?? 0), 0) * c.count,
      0,
    )
    expect(total).toBe(132)
  })

  it('牌型 key 唯一且共 20 种', () => {
    const keys = flatCards().map((c) => c.key)
    expect(new Set(keys).size).toBe(keys.length)
    expect(keys).toHaveLength(20)
  })
})
