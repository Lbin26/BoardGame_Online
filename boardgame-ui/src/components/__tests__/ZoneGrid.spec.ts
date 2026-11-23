import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ZoneGrid from '@/components/ZoneGrid.vue'
import { ZONES } from '@/constants/catalog'

describe('ZoneGrid', () => {
  it('renders one table row per zone with name and mechanism tags', () => {
    const w = mount(ZoneGrid, { props: { zones: ZONES } })
    expect(w.findAll('.zone-card')).toHaveLength(6)
    expect(w.text()).toContain('派对/聚会')
    expect(w.findAll('.zone-card')[0].findAll('.zone-tag').map((t) => t.text())).toEqual(ZONES[0].mechanisms)
  })
  it('shows unified status: playable / upcoming / planning', () => {
    const w = mount(ZoneGrid, { props: { zones: ZONES } })
    const statuses = w.findAll('.zone-card .zone-status').map((s) => s.text())
    expect(statuses).toEqual([
      '3款', // 派对/聚会：德国心脏病 + 胡闹运动会 + 印加宝藏
      '规划中', // 家庭/亲子：暂无
      '1款', // 卡牌构筑：璀璨宝石
      '2款', // 叙事/身份：王权骰铸 + 阿瓦隆
      '3款', // 抽象/双人：象棋 + 围棋 + 五子棋
      '1款', // 策略/重策：伯明翰
    ])
  })
  it('emits select with the zone key on row click', async () => {
    const w = mount(ZoneGrid, { props: { zones: ZONES } })
    await w.findAll('.zone-card')[4].trigger('click')
    expect(w.emitted('select')![0]).toEqual(['abstract'])
  })
})
