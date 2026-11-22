import { describe, expect, it } from 'vitest'
import { ZONES, GAMES, gamesInZone, resolveLevel, findZone } from '@/constants/catalog'

describe('catalog zones', () => {
  it('has the 7 zones with verbatim names and mechanisms', () => {
    expect(ZONES.map((z) => z.key)).toEqual(['party', 'family', 'strategy', 'card', 'narrative', 'abstract', 'heavy'])
    expect(findZone('party')!.name).toBe('轻策/派对区')
    expect(findZone('party')!.mechanisms).toEqual(['同时行动', '反应', '猜词', '吹牛', '轮抽轻量版'])
    expect(findZone('family')!.mechanisms).toEqual(['成套收集', '板块放置', '骰子轻策', '交易谈判'])
    expect(findZone('strategy')!.mechanisms).toEqual(['工人放置', '引擎构建', '区域控制', '路线构建', '行动点分配'])
    expect(findZone('card')!.mechanisms).toEqual(['手牌管理', '牌库构建', '卡牌驱动', '轮抽', '多用途卡牌'])
    expect(findZone('narrative')!.mechanisms).toEqual(['合作', '传承', '叙事', '隐藏身份', '战斗结算'])
    expect(findZone('abstract')!.mechanisms).toEqual(['抽象棋', '模式构建', '逻辑推理'])
    expect(findZone('heavy')!.mechanisms).toEqual(['资源管理+引擎+路线+推轨的复合体'])
  })
})

describe('catalog games', () => {
  it('places the 3 placeholder games with verbatim mechanism tags and playable splendor', () => {
    const by = (k: string) => GAMES.find((g) => g.key === k)!
    expect(by('splendor')).toMatchObject({ zone: 'card', playable: true, mechanisms: ['引擎构建', '成套收集'] })
    expect(by('diceThrone')).toMatchObject({ zone: 'narrative', playable: false, mechanisms: ['骰子驱动', '角色能力（不对称）', '卡牌辅助'] })
    expect(by('avalon')).toMatchObject({ zone: 'narrative', playable: false, mechanisms: ['隐藏身份', '推理/演绎', '投票'] })
    expect(by('brass')).toMatchObject({ zone: 'heavy', playable: false, mechanisms: ['网络构建', '经济/资源管理', '手牌管理（轻卡驱）'] })
    expect(by('xiangqi')).toMatchObject({ zone: 'abstract', playable: true })
    expect(by('go').playable).toBe(false)
    expect(by('gomoku').playable).toBe(false)
    expect(gamesInZone('abstract').map((g) => g.key)).toEqual(['xiangqi', 'go', 'gomoku'])
    expect(gamesInZone('card').map((g) => g.key)).toEqual(['splendor'])
    expect(gamesInZone('party').map((g) => g.key)).toEqual(['halliGalli', 'magicalAthlete'])
    expect(by('halliGalli')).toMatchObject({ zone: 'party', playable: true, players: 6 })
    expect(by('magicalAthlete')).toMatchObject({ zone: 'party', playable: true, players: 6 })
  })
})

describe('resolveLevel', () => {
  it.each([
    [{}, 0],
    [{ zone: 'abstract' }, 1],
    [{ zone: 'abstract', game: 'xiangqi' }, 2],
    [{ zone: 'card', game: 'splendor' }, 2],  // Splendor 已可玩 → level 2
    [{ zone: 'abstract', game: 'go' }, 1],          // 已知但不可玩 → level 1
    [{ zone: 'card', game: 'xiangqi' }, 1],         // 错配 → level 1
    [{ zone: 'nope', game: 'xiangqi' }, 0],
    [{ game: 'xiangqi' }, 0],
    [{ zone: 'abstract', game: 'nope' }, 1],
  ] as [Record<string, unknown>, number][])('query %j → level %i', (query, level) => {
    expect(resolveLevel(query).level).toBe(level)
  })
  it('returns zone/game objects at level 2', () => {
    const r = resolveLevel({ zone: 'abstract', game: 'xiangqi' })
    expect(r.zone?.key).toBe('abstract')
    expect(r.game?.key).toBe('xiangqi')
  })
})
