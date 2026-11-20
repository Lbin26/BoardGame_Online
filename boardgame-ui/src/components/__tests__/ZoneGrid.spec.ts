import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ZoneGrid from '@/components/ZoneGrid.vue'
import { ZONES } from '@/constants/catalog'

describe('ZoneGrid', () => {
  it('renders one card per zone with name and mechanism tags', () => {
    const w = mount(ZoneGrid, { props: { zones: ZONES } })
    expect(w.findAll('.zone-card')).toHaveLength(7)
    expect(w.text()).toContain('轻策/派对区')
    expect(w.findAll('.zone-card')[0].findAll('.zone-tag').map((t) => t.text())).toEqual(ZONES[0].mechanisms)
  })
  it('emits select with the zone key on card click', async () => {
    const w = mount(ZoneGrid, { props: { zones: ZONES } })
    await w.findAll('.zone-card')[5].trigger('click')
    expect(w.emitted('select')![0]).toEqual(['abstract'])
  })
})
