import { describe, it, expect } from 'vitest'
import { fileURLToPath } from 'node:url'
import { loadGameDoc, loadYamlFile, validateDoc } from '../src/schema/load'

const GAME_YAML = fileURLToPath(
  new URL('../../../design/games/magicalAthlete/game.yaml', import.meta.url),
)

const RACER_KEYS = [
  'alchemist', 'baba_yaga', 'banana', 'blimp', 'centaur', 'cheerleader',
  'coach', 'copycat', 'dicemonger', 'duelist', 'egg', 'flip_flop',
  'genius', 'gunk', 'hare', 'heckler', 'huge_baby', 'hypnotist',
  'inchworm', 'lackey', 'leaptoad', 'legs', 'lovable_loser', 'magician',
  'mastermind', 'mouth', 'party_animal', 'rocket_scientist', 'romantic',
  'scoocher', 'sisyphus', 'skipper', 'stickler', 'suckerfish',
  'third_wheel', 'twin',
] as const

function raw(): Record<string, any> {
  return loadYamlFile(GAME_YAML) as Record<string, any>
}

describe('magical-athlete 数据集（P1 校验）', () => {
  it('game.yaml 通过 schema 与机制词汇校验', () => {
    const r = validateDoc(GAME_YAML)
    expect(r.ok, r.ok ? '' : r.errors.join('\n')).toBe(true)
  })

  it('人数 2-6，归区 party，四项机制均为词汇表成员', () => {
    const doc = loadGameDoc(GAME_YAML)
    expect(doc.meta.players).toEqual({ min: 2, max: 6 })
    expect(doc.meta.zone).toBe('party')
    expect(doc.mechanisms).toEqual([
      'drafting', 'dice-driven', 'variable-player-powers', 'light-dice-strategy',
    ])
  })

  it('36 名选手卡唯一、各 1 张', () => {
    const doc = loadGameDoc(GAME_YAML)
    const deck = doc.components.decks?.find((d) => d.key === 'racer')
    expect(deck, 'racer deck 不存在').toBeTruthy()
    const keys = deck!.cards.map((c) => c.key)
    expect(keys).toHaveLength(36)
    expect(new Set(keys).size).toBe(36)
    for (const c of deck!.cards) expect(c.count).toBe(1)
  })

  it('36 名选手 ID 与核对记录一致', () => {
    const doc = loadGameDoc(GAME_YAML)
    const deck = doc.components.decks?.find((d) => d.key === 'racer')
    expect(deck!.cards.map((c) => c.key).sort()).toEqual([...RACER_KEYS].sort())
  })

  it('赛道为 0-30，起点 0、终点 30、第二拐角 15', () => {
    const board = raw().board
    expect(board.start).toBe(0)
    expect(board.finishAt).toBe(30)
    expect(board.lastCell).toBe(29)
    expect(board.secondCorner).toBe(15)
    expect(board.mildSide.specials).toHaveLength(0)
  })

  it('狂野面 10 个特殊格均落在有效格内', () => {
    const specials = raw().board.wildSide.specials as Array<Record<string, any>>
    expect(specials).toHaveLength(10)
    const spaces = specials.map((s) => s.space)
    expect(spaces).toEqual([...spaces].sort((a, b) => a - b))
    for (const s of specials) {
      expect(s.space).toBeGreaterThan(0)
      expect(s.space).toBeLessThan(30)
    }
  })

  it('星星格 1/13、绊倒格 5/17/26、箭头格 7/11/16/23/24', () => {
    const specials = raw().board.wildSide.specials as Array<Record<string, any>>
    const bySpace = new Map(specials.map((s) => [s.space, s]))
    for (const n of [1, 13]) expect(bySpace.get(n)!.effect).toBe('bronze1')
    for (const n of [5, 17, 26]) expect(bySpace.get(n)!.effect).toBe('trip')
    for (const n of [7, 11, 16, 23, 24]) expect(bySpace.get(n)!.effect).toBe('move')
    expect(bySpace.get(16)!.delta).toBe(-4)
    expect(bySpace.get(24)!.delta).toBe(-2)
  })

  it('四场奖牌递增：金 3/4/4/5、银 1/2/2/3，赛道平缓/狂野交替', () => {
    const trophies = raw().trophies as Array<Record<string, any>>
    expect(trophies.map((t) => t.gold)).toEqual([3, 4, 4, 5])
    expect(trophies.map((t) => t.silver)).toEqual([1, 2, 2, 3])
    expect(trophies.map((t) => t.track)).toEqual(['mild', 'wild', 'mild', 'wild'])
    expect(raw().tiePolicy).toBe('shared-victory')
  })

  it('36 条能力数据与选手卡一一对应，覆盖六类触发器', () => {
    const abilities = raw().racerAbilities as Record<string, { type: string }>
    expect(Object.keys(abilities).sort()).toEqual([...RACER_KEYS].sort())
    const types = new Set(Object.values(abilities).map((a) => a.type))
    for (const t of ['fixed-move', 'mod-move', 'interrupt', 'reactive', 'scoring', 'warp', 'copy']) {
      expect(types.has(t), `缺少能力类型 ${t}`).toBe(true)
    }
  })

  it('普通/2 人/3 人变体的选手数与出场数自洽', () => {
    const variants = raw().variants
    expect(variants.normal.racersPerPlayer).toBe(4)
    expect(variants.normal.activePerRace).toBe(1)
    expect(variants.twoPlayer.racersPerPlayer).toBe(8)
    expect(variants.twoPlayer.activePerRace).toBe(2)
    expect(variants.threePlayerDouble.racersPerPlayer).toBe(8)
    expect(variants.threePlayerDouble.activePerRace).toBe(2)
  })
})
