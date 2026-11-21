import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import BackToRoom from '@/components/BackToRoom.vue'
import { rememberLastRoom } from '@/composables/useLastRoom'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  store: { userInfo: { userName: 'alice' } as { userName: string } | null },
  splendor: { getRoom: vi.fn() },
  chess: { getRoom: vi.fn() },
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/store/user', () => ({ useUserStore: () => mocks.store }))
vi.mock('@/api/splendor', () => mocks.splendor)
vi.mock('@/api/xiangqi', () => mocks.chess)

function mountCard() {
  return mount(BackToRoom)
}

describe('BackToRoom', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    mocks.store.userInfo = { userName: 'alice' }
  })

  it('无记录时不渲染', async () => {
    const w = mountCard()
    await flushPromises()
    expect(w.find('.back-to-room').exists()).toBe(false)
  })

  it('Splendor：显示房号+游戏名+人数，点击跳回原房间', async () => {
    rememberLastRoom('splendor', 'ABC123', 'alice')
    mocks.splendor.getRoom.mockResolvedValue({
      success: true,
      room: {
        roomId: 'ABC123',
        hostName: 'alice',
        maxPlayers: 4,
        seats: [
          { playerName: 'alice' },
          { playerName: 'bob' },
          { playerName: null },
          { playerName: null },
        ],
      },
    })
    const w = mountCard()
    await flushPromises()

    const text = w.find('.back-to-room').text()
    expect(text).toContain('回到房间 ABC123')
    expect(text).toContain('璀璨宝石')
    expect(text).toContain('2/4')
    expect(text).toContain('房主 alice')
    await w.find('.btr-main').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/splendor/ABC123')
  })

  it('象棋：人数按红黑非空座位计算', async () => {
    rememberLastRoom('xiangqi', 'XYZ1', 'alice')
    mocks.chess.getRoom.mockResolvedValue({
      success: true,
      room: { roomId: 'XYZ1', redPlayerName: 'alice', blackPlayerName: null },
    })
    const w = mountCard()
    await flushPromises()

    expect(w.find('.btr-sub').text()).toContain('1/2')
    await w.find('.btr-main').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/game/XYZ1')
  })

  it('房间已不存在时不渲染并清除记录', async () => {
    rememberLastRoom('splendor', 'GONE1', 'alice')
    mocks.splendor.getRoom.mockResolvedValue({ success: false, error: '房间不存在' })
    const w = mountCard()
    await flushPromises()

    expect(w.find('.back-to-room').exists()).toBe(false)
    expect(localStorage.getItem('dicego:lastRoom')).toBeNull()
  })

  it('点关闭按钮清除记录', async () => {
    rememberLastRoom('splendor', 'ABC123', 'alice')
    mocks.splendor.getRoom.mockResolvedValue({
      success: true,
      room: { roomId: 'ABC123', maxPlayers: 4, seats: [{ playerName: 'alice' }] },
    })
    const w = mountCard()
    await flushPromises()
    await w.find('.btr-close').trigger('click')

    expect(localStorage.getItem('dicego:lastRoom')).toBeNull()
    expect(w.find('.back-to-room').exists()).toBe(false)
  })
})
