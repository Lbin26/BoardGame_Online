import { describe, expect, it } from 'vitest'
import { ZONES, GAMES, gamesInZone, resolveLevel, findZone } from '@/constants/catalog'

describe('catalog zones', () => {
  it('has the 6 zones with verbatim names and mechanisms', () => {
    expect(ZONES.map((z) => z.key)).toEqual(['party', 'family', 'card', 'narrative', 'abstract', 'strategy'])
    expect(findZone('party')!.name).toBe('派对/聚会')
    expect(findZone('party')!.mechanisms).toEqual(['秒反应', '同时出招', '笑到拍桌'])
    expect(findZone('family')!.mechanisms).toEqual(['合家欢', '轻松上手', '攒套收集'])
    expect(findZone('card')!.mechanisms).toEqual(['牌库构筑', '手牌博弈', '抽换轮转'])
    expect(findZone('narrative')!.mechanisms).toEqual(['角色代入', '隐藏身份', '剧情抉择'])
    expect(findZone('abstract')!.mechanisms).toEqual(['完全信息', '纯逻辑', '低随机'])
    expect(findZone('strategy')!.mechanisms).toEqual(['长线布局', '资源运营', '多系统叠加'])
    expect(findZone('heavy')).toBeUndefined()
  })
})

describe('catalog games', () => {
  it('places games per the refreshed 6-zone table', () => {
    const by = (k: string) => GAMES.find((g) => g.key === k)!
    expect(by('splendor')).toMatchObject({ zone: 'card', playable: true, mechanisms: ['引擎构建', '成套收集'] })
    expect(by('diceThrone')).toMatchObject({ zone: 'narrative', playable: false, mechanisms: ['骰子驱动', '角色能力（不对称）', '卡牌辅助'] })
    expect(by('avalon')).toMatchObject({ zone: 'narrative', playable: false, mechanisms: ['隐藏身份', '推理/演绎', '投票'] })
    expect(by('brass')).toMatchObject({ zone: 'strategy', playable: false, mechanisms: ['网络构建', '经济/资源管理', '手牌管理（轻卡驱）'] })
    expect(by('xiangqi')).toMatchObject({ zone: 'abstract', playable: true })
    expect(by('go').playable).toBe(false)
    expect(by('gomoku').playable).toBe(false)
    expect(gamesInZone('abstract').map((g) => g.key)).toEqual(['xiangqi', 'go', 'gomoku'])
    expect(gamesInZone('card').map((g) => g.key)).toEqual(['splendor'])
    expect(gamesInZone('party').map((g) => g.key)).toEqual(['halliGalli', 'magicalAthlete', 'incanGold'])
    expect(gamesInZone('strategy').map((g) => g.key)).toEqual(['brass'])
    expect(gamesInZone('family')).toEqual([])
    expect(by('halliGalli')).toMatchObject({ zone: 'party', playable: true, players: 6 })
    expect(by('magicalAthlete')).toMatchObject({ zone: 'party', playable: true, players: 6 })
    expect(by('incanGold')).toMatchObject({ zone: 'party', playable: true, players: 8 })
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
