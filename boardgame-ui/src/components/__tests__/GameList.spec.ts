import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GameList from '@/components/GameList.vue'
import { findZone, gamesInZone, GAMES } from '@/constants/catalog'

const abstract = findZone('abstract')!
describe('GameList', () => {
  it('emits select only for playable games', async () => {
    const w = mount(GameList, { props: { zone: abstract, games: gamesInZone('abstract') } })
    const cards = w.findAll('.game-entry')
    expect(cards).toHaveLength(3)
    await cards[1].trigger('click') // 围棋 敬请期待
    expect(w.emitted('select')).toBeUndefined()
    await cards[0].trigger('click') // 象棋
    expect(w.emitted('select')![0]).toEqual(['xiangqi'])
  })
  it('shows user-given mechanism tags on placeholder cards', () => {
    const w = mount(GameList, { props: { zone: findZone('narrative')!, games: gamesInZone('narrative') } })
    expect(w.findAll('.game-tag').map((t) => t.text())).toEqual([
      '骰子驱动', '角色能力（不对称）', '卡牌辅助',
      '隐藏身份', '推理/演绎', '投票',
    ])
    expect(w.text()).toContain('敬请期待')
  })
  it('marks the playable splendor card with badge and entry hint', () => {
    const w = mount(GameList, { props: { zone: findZone('card')!, games: gamesInZone('card') } })
    expect(w.findAll('.game-tag').map((t) => t.text())).toEqual(['引擎构建', '成套收集'])
    expect(w.find('.badge-playable').text()).toBe('可玩')
    expect(w.text()).toContain('进入房间大厅 ➜')
    expect(w.text()).not.toContain('敬请期待')
  })
  it('shows rules link only for games with a rule book and emits rules event', async () => {
    const w = mount(GameList, { props: { zone: findZone('abstract')!, games: gamesInZone('abstract') } })
    const links = w.findAll('.rules-link')
    expect(links).toHaveLength(1) // 象棋有规则书，围棋/五子棋没有
    await links[0].trigger('click')
    expect(w.emitted('rules')![0]).toEqual(['xiangqi'])
    expect(w.emitted('select')).toBeUndefined() // .stop 阻止触发整卡进入房间
  })
  it('applies renamed poster silhouettes only to games with a poster', () => {
    const w = mount(GameList, { props: { zone: findZone('party')!, games: GAMES } })
    const cards = w.findAll('.game-entry')
    expect(cards).toHaveLength(GAMES.length)

    const posters = cards.filter((c) => c.classes().includes('has-poster'))
    expect(posters).toHaveLength(7)
    const urls = posters.map((c) => c.attributes('style') ?? '')
    for (const key of ['magicalAthlete', 'incanGold', 'splendor', 'diceThrone', 'halliGalli', 'avalon', 'brass']) {
      expect(urls.some((u) => u.includes(`/posters/${key}.webp`))).toBe(true)
    }
    // 象棋等没有海报的游戏不铺背景
    expect(cards[0].classes()).not.toContain('has-poster')
    expect(cards[0].attributes('style')).not.toContain('--poster')
  })
  it('shows NEW badge only for games in newKeys', () => {
    const w = mount(GameList, {
      props: {
        zone: findZone('card')!,
        games: gamesInZone('card'),
        newKeys: new Set(['splendor']),
      },
    })
    expect(w.findAll('.new-badge').map((b) => b.text())).toEqual(['NEW'])
  })
  it('renders preparation empty state for zones without games', () => {
    const w = mount(GameList, { props: { zone: findZone('party')!, games: [] } })
    expect(w.find('.zone-empty').exists()).toBe(true)
    expect(w.find('.zone-empty').text()).toContain('筹备中')
    expect(w.findAll('.game-entry')).toHaveLength(0)
  })
})
