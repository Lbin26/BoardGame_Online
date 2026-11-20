import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

import SeatPicker from '@/components/SeatPicker.vue'
import type { RoomInfo } from '@/api/xiangqi'

function room(over: Partial<RoomInfo> = {}): RoomInfo {
  return {
    roomId: 'ABC123',
    redPlayerName: null,
    blackPlayerName: null,
    status: 'WAITING',
    viewerColor: null,
    ...over,
  }
}

function mountPicker(r: RoomInfo, myName = 'alice') {
  return mount(SeatPicker, {
    props: { room: r, myName },
    global: { plugins: [ElementPlus] },
  })
}

describe('SeatPicker', () => {
  it('renders both empty seats as clickable', async () => {
    const wrapper = mountPicker(room())
    expect(wrapper.text()).toContain('空位')
    await wrapper.find('[data-testid="seat-red"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([['RED']])
    await wrapper.find('[data-testid="seat-black"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([['RED'], ['BLACK']])
  })

  it('shows occupant name and disables seat taken by others', async () => {
    const wrapper = mountPicker(room({ redPlayerName: 'bob' }))
    const red = wrapper.find('[data-testid="seat-red"]')
    expect(red.text()).toContain('bob')
    await red.trigger('click')
    expect(wrapper.emitted('select')).toBeUndefined()
    // 黑座仍可选
    await wrapper.find('[data-testid="seat-black"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([['BLACK']])
  })

  it('own seat stays clickable (idempotent re-seat)', async () => {
    const wrapper = mountPicker(room({ redPlayerName: 'alice' }))
    await wrapper.find('[data-testid="seat-red"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([['RED']])
  })

  it('spectate button emits spectate', async () => {
    const wrapper = mountPicker(room())
    await wrapper.find('[data-testid="spectate"]').trigger('click')
    expect(wrapper.emitted('spectate')).toHaveLength(1)
  })
})
