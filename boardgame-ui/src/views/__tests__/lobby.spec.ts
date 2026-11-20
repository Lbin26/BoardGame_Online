import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'

import Lobby from '@/views/lobby/index.vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  route: { query: {} as Record<string, string> },
  store: {
    isLogin: true,
    userInfo: { userName: 'alice' } as { userName: string } | null,
    fetchProfile: vi.fn(),
    logout: vi.fn(),
  },
  api: {
    listRooms: vi.fn(),
    createRoom: vi.fn(),
    getRoom: vi.fn(),
  },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
  useRoute: () => mocks.route,
}))
vi.mock('@/store/user', () => ({ useUserStore: () => mocks.store }))
vi.mock('@/api/chess', () => mocks.api)

// vi.hoisted 工厂先于 import 执行，不能在其中调用 vue 的 reactive；导入后再包一层
mocks.route = reactive(mocks.route)

// 每个用例后自动卸载，避免 focus/interval 监听泄漏到后续用例
enableAutoUnmount(afterEach)

const emptyList = { rooms: [], waitingCount: 0, playingCount: 0 }
const oneWaiting = {
  rooms: [{ roomId: 'ABC123', redPlayerName: null, blackPlayerName: null, status: 'WAITING' }],
  waitingCount: 1,
  playingCount: 0,
}
/** level 2：象棋房间层 */
const L2 = { zone: 'abstract', game: 'xiangqi' }

function mountLobby(query: Record<string, string> = {}) {
  mocks.route.query = query
  return mount(Lobby, { global: { plugins: [ElementPlus] } })
}

function buttonByText(wrapper: ReturnType<typeof mountLobby>, text: string) {
  return wrapper.findAll('button').find((b) => b.text().trim() === text)
}

describe('lobby mount resilience', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.isLogin = true
    mocks.store.userInfo = { userName: 'alice' }
    mocks.store.fetchProfile.mockResolvedValue(undefined)
    mocks.api.listRooms.mockResolvedValue(emptyList)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads room list on mount when profile already present', async () => {
    mountLobby(L2)
    await flushPromises()
    expect(mocks.store.fetchProfile).not.toHaveBeenCalled()
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(1)
  })

  it('still loads room list when fetchProfile fails', async () => {
    mocks.store.userInfo = null
    mocks.store.fetchProfile.mockRejectedValue(new Error('登录已过期'))
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mountLobby(L2)
    await flushPromises()
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(1)
    expect(errorSpy).toHaveBeenCalled()
    errorSpy.mockRestore()
  })

  it('create guards against missing userName (never sends empty name)', async () => {
    mocks.store.userInfo = null
    mocks.store.fetchProfile.mockRejectedValue(new Error('x'))
    const warnSpy = vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({}) as any)
    const wrapper = mountLobby(L2)
    await flushPromises()
    await buttonByText(wrapper, '创建房间')!.trigger('click')
    await flushPromises()
    expect(mocks.api.createRoom).not.toHaveBeenCalled()
    expect(warnSpy).toHaveBeenCalled()
    warnSpy.mockRestore()
  })
})

describe('lobby brand', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.userInfo = { userName: 'alice' }
    mocks.api.listRooms.mockResolvedValue(emptyList)
  })

  it('shows the DiceGo wordmark in the top bar', async () => {
    const wrapper = mountLobby()
    await flushPromises()
    expect(wrapper.find('img[alt="DiceGo"]').exists()).toBe(true)
  })
})

describe('lobby join navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.userInfo = { userName: 'alice' }
    mocks.api.listRooms.mockResolvedValue(oneWaiting)
  })

  it('join validates room via getRoom then navigates (seating happens in-room)', async () => {
    mocks.api.getRoom.mockResolvedValue({ success: true, room: { roomId: 'ABC123' } })
    const wrapper = mountLobby(L2)
    await flushPromises()
    const joinBtn = wrapper.findAll('button').find((b) => b.text().trim() === '加入' && b.attributes('disabled') === undefined)
    await joinBtn!.trigger('click')
    await flushPromises()
    expect(mocks.api.getRoom).toHaveBeenCalledWith('ABC123')
    expect(mocks.push).toHaveBeenCalledWith('/game/ABC123')
  })

  it('join shows error and does not navigate when room missing', async () => {
    mocks.api.getRoom.mockResolvedValue({ success: false, error: '房间不存在' })
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    const wrapper = mountLobby(L2)
    await flushPromises()
    const joinBtn = wrapper.findAll('button').find((b) => b.text().trim() === '加入' && b.attributes('disabled') === undefined)
    await joinBtn!.trigger('click')
    await flushPromises()
    expect(mocks.push).not.toHaveBeenCalled()
    expect(errorSpy).toHaveBeenCalledWith('房间不存在')
    errorSpy.mockRestore()
  })

  it('level 0 join-by-id validates and navigates without zone selection', async () => {
    mocks.api.getRoom.mockResolvedValue({ success: true, room: { roomId: 'ABC123' } })
    const w = mountLobby()
    await flushPromises()
    await w.find('input[placeholder="房间号(如 ABC123)"]').setValue('ABC123')
    await buttonByText(w, '加入')!.trigger('click')
    await flushPromises()
    expect(mocks.api.getRoom).toHaveBeenCalledWith('ABC123')
    expect(mocks.push).toHaveBeenCalledWith('/game/ABC123')
  })
})

describe('lobby logout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.isLogin = true
    mocks.store.userInfo = { userName: 'alice' }
    mocks.store.fetchProfile.mockResolvedValue(undefined)
    mocks.api.listRooms.mockResolvedValue(emptyList)
  })

  it('shows current user and logout button delegates to store.logout', async () => {
    const wrapper = mountLobby()
    await flushPromises()
    expect(wrapper.text()).toContain('alice')
    await buttonByText(wrapper, '退出登录')!.trigger('click')
    expect(mocks.store.logout).toHaveBeenCalledTimes(1)
  })
})

describe('lobby levels', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.userInfo = { userName: 'alice' }
    mocks.store.fetchProfile.mockResolvedValue(undefined)
    mocks.api.listRooms.mockResolvedValue(emptyList)
  })

  it('level 0 shows zone grid and join-by-id, no create button', async () => {
    const w = mountLobby()
    await flushPromises()
    expect(w.findAll('.zone-card')).toHaveLength(7)
    expect(buttonByText(w, '创建房间')).toBeUndefined()
    expect(w.find('input[placeholder="房间号(如 ABC123)"]').exists()).toBe(true)
  })

  it('zone query shows game list without room cards', async () => {
    const w = mountLobby({ zone: 'abstract' })
    await flushPromises()
    expect(w.findAll('.game-entry')).toHaveLength(3)
    expect(buttonByText(w, '创建房间')).toBeUndefined()
  })

  it('zone+game query shows the three room cards', async () => {
    const w = mountLobby(L2)
    await flushPromises()
    expect(buttonByText(w, '创建房间')).toBeDefined()
    expect(w.text()).toContain('房间列表')
  })

  it('unplayable game query falls back to level 1 (no chess room ops)', async () => {
    const w = mountLobby({ zone: 'abstract', game: 'go' })
    await flushPromises()
    expect(w.findAll('.game-entry')).toHaveLength(3)
    expect(buttonByText(w, '创建房间')).toBeUndefined()
  })

  it('invalid query falls back to level 0', async () => {
    const w = mountLobby({ zone: 'nope', game: 'xiangqi' })
    await flushPromises()
    expect(w.findAll('.zone-card')).toHaveLength(7)
  })

  it('back button at level 1 and 2 pushes bare /lobby', async () => {
    for (const q of [{ zone: 'abstract' }, L2]) {
      const w = mountLobby(q)
      await flushPromises()
      await buttonByText(w, '← 返回大厅')!.trigger('click')
      expect(mocks.push).toHaveBeenCalledWith('/lobby')
      w.unmount()
    }
  })

  it('zone card click pushes zone query; game card click pushes zone+game', async () => {
    const w = mountLobby()
    await flushPromises()
    await w.findAll('.zone-card')[5].trigger('click')
    expect(mocks.push).toHaveBeenCalledWith({ path: '/lobby', query: { zone: 'abstract' } })
    const w2 = mountLobby({ zone: 'abstract' })
    await flushPromises()
    await w2.findAll('.game-entry')[0].trigger('click')
    expect(mocks.push).toHaveBeenCalledWith({ path: '/lobby', query: { zone: 'abstract', game: 'xiangqi' } })
  })

  it('keeps logout in top bar at every level', async () => {
    const queries: Record<string, string>[] = [{}, { zone: 'abstract' }, L2]
    for (const q of queries) {
      const w = mountLobby(q)
      await flushPromises()
      expect(buttonByText(w, '退出登录')).toBeDefined()
      w.unmount()
    }
  })
})

describe('lobby polling lifecycle', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.userInfo = { userName: 'alice' }
    mocks.store.fetchProfile.mockResolvedValue(undefined)
    mocks.api.listRooms.mockResolvedValue(emptyList)
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('stops polling when leaving level 2', async () => {
    mountLobby(L2)
    await vi.advanceTimersByTimeAsync(0)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(1)
    mocks.route.query = {}
    await vi.advanceTimersByTimeAsync(10000)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(1)
  })

  it('focus/visibility after leaving level 2 do not refresh (listeners removed)', async () => {
    mountLobby(L2)
    await vi.advanceTimersByTimeAsync(0)
    mocks.route.query = {}
    await vi.advanceTimersByTimeAsync(0)
    window.dispatchEvent(new Event('focus'))
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(5000)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(1)
  })

  it('entering level 2 via query change starts polling (re-entry)', async () => {
    mountLobby()
    await vi.advanceTimersByTimeAsync(0)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(0)
    mocks.route.query = { ...L2 }
    await vi.advanceTimersByTimeAsync(0)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(5000)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(2)
  })
})

describe('lobby auto refresh', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.userInfo = { userName: 'alice' }
    mocks.api.listRooms.mockResolvedValue(emptyList)
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('polls every 5s while visible', async () => {
    mountLobby(L2)
    await vi.advanceTimersByTimeAsync(0)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(5000)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(5000)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(3)
  })

  it('refreshes on window focus', async () => {
    mountLobby(L2)
    await vi.advanceTimersByTimeAsync(0)
    window.dispatchEvent(new Event('focus'))
    await vi.advanceTimersByTimeAsync(0)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(2)
  })

  it('stops polling after unmount', async () => {
    const wrapper = mountLobby(L2)
    await vi.advanceTimersByTimeAsync(0)
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(10000)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(1)
  })

  it('polling failures are silent (no toast spam)', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mocks.api.listRooms.mockResolvedValueOnce(emptyList).mockRejectedValue(new Error('network'))
    mountLobby(L2)
    await vi.advanceTimersByTimeAsync(5000)
    expect(mocks.api.listRooms).toHaveBeenCalledTimes(2)
    expect(errorSpy).not.toHaveBeenCalled()
    errorSpy.mockRestore()
  })

  it('manual refresh surfaces errors', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mocks.api.listRooms.mockResolvedValueOnce(emptyList).mockRejectedValue(new Error('network'))
    const wrapper = mountLobby(L2)
    await vi.advanceTimersByTimeAsync(0)
    await buttonByText(wrapper, '🔄 刷新')!.trigger('click')
    await vi.advanceTimersByTimeAsync(0)
    expect(errorSpy).toHaveBeenCalled()
    errorSpy.mockRestore()
  })
})
