import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'

import Lobby from '@/views/lobby/index.vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  back: vi.fn(),
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
  splendor: {
    listRooms: vi.fn(),
    createRoom: vi.fn(),
    takeSeat: vi.fn(),
    standUp: vi.fn(),
    getRoom: vi.fn(),
  },
  hg: {
    listRooms: vi.fn(),
    createRoom: vi.fn(),
    takeSeat: vi.fn(),
    standUp: vi.fn(),
    getRoom: vi.fn(),
  },
  ma: {
    listRooms: vi.fn(),
    createRoom: vi.fn(),
    takeSeat: vi.fn(),
    standUp: vi.fn(),
    getRoom: vi.fn(),
  },
  incanGold: {
    listRooms: vi.fn(async () => ({ rooms: [], waitingCount: 0, playingCount: 0 })),
    createRoom: vi.fn(),
    takeSeat: vi.fn(),
    standUp: vi.fn(),
    getRoom: vi.fn(async () => ({ success: false, error: '房间不存在' })),
  },
  // 在线人数是装饰性信息：给默认成功实现，避免各 describe 挂载时产生未处理 rejection
  online: {
    getOnlineCount: vi.fn(async () => ({ success: true, onlineCount: 7 })),
  },
  // 战绩栏同样只做装饰展示，默认空列表
  match: {
    getRecentMatches: vi.fn(async () => ({ success: true, matches: [] as any[] })),
  },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push, back: mocks.back }),
  useRoute: () => mocks.route,
}))
vi.mock('@/store/user', () => ({ useUserStore: () => mocks.store }))
vi.mock('@/api/xiangqi', () => mocks.api)
vi.mock('@/api/splendor', () => mocks.splendor)
vi.mock('@/api/halliGalli', () => mocks.hg)
vi.mock('@/api/magicalAthlete', () => mocks.ma)
vi.mock('@/api/incanGold', () => mocks.incanGold)
vi.mock('@/api/online', () => mocks.online)
vi.mock('@/api/match', () => mocks.match)

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
    // 并行探测的另一路：缺省按"房间不存在"落定，保证 allSettled 同步收敛
    mocks.splendor.getRoom.mockResolvedValue({ success: false, error: '房间不存在' })
    mocks.hg.getRoom.mockResolvedValue({ success: false, error: '房间不存在' })
    mocks.ma.getRoom.mockResolvedValue({ success: false, error: '房间不存在' })
  })

  it('join validates room via getRoom then navigates (seating happens in-room)', async () => {
    mocks.api.getRoom.mockResolvedValue({ success: true, room: { roomId: 'ABC123' } })
    const wrapper = mountLobby(L2)
    await flushPromises()
    const joinBtn = wrapper.findAll('button').find((b) => b.text().trim() === '加入' && b.attributes('disabled') === undefined)
    await joinBtn!.trigger('click')
    await flushPromises()
    expect(mocks.api.getRoom).toHaveBeenCalledWith('ABC123', undefined)
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
    expect(mocks.api.getRoom).toHaveBeenCalledWith('ABC123', undefined)
    expect(mocks.push).toHaveBeenCalledWith('/game/ABC123')
  })
})

/** level 2：Splendor 房间层（卡牌区 → 璀璨宝石） */
const L2S = { zone: 'card', game: 'splendor' }

describe('lobby splendor level 2', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.userInfo = { userName: 'alice' }
    mocks.splendor.getRoom.mockResolvedValue({ success: false, error: '房间不存在' })
    mocks.api.getRoom.mockResolvedValue({ success: false, error: '房间不存在' })
    mocks.hg.getRoom.mockResolvedValue({ success: false, error: '房间不存在' })
    mocks.ma.getRoom.mockResolvedValue({ success: false, error: '房间不存在' })
  })

  it('lists splendor rooms via splendor api and renders host/seated info', async () => {
    mocks.splendor.listRooms.mockResolvedValue({
      rooms: [{ roomId: 'S1', hostName: 'alice', maxPlayers: 4, seatedCount: 2, status: 'WAITING' }],
      waitingCount: 1,
      playingCount: 0,
    })
    const w = mountLobby(L2S)
    await flushPromises()
    expect(mocks.splendor.listRooms).toHaveBeenCalled()
    expect(mocks.api.listRooms).not.toHaveBeenCalled()
    expect(w.text()).toContain('房主 alice')
    expect(w.text()).toContain('2/4 人')
  })

  it('create splendor room posts capacity and routes to /splendor/:roomId', async () => {
    mocks.splendor.createRoom.mockResolvedValue({ success: true, room: { roomId: 'S2' } })
    const w = mountLobby(L2S)
    await flushPromises()
    await buttonByText(w, '创建房间')!.trigger('click')
    await flushPromises()
    expect(mocks.splendor.createRoom).toHaveBeenCalledWith('alice', 4)
    expect(mocks.push).toHaveBeenCalledWith('/splendor/S2')
  })

  it('join splendor room by id probes splendor api first at splendor lobby', async () => {
    mocks.splendor.getRoom.mockResolvedValue({ success: true, room: { roomId: 'S3' } })
    const w = mountLobby(L2S)
    await flushPromises()
    await w.find('input[placeholder="房间号(如 ABC123)"]').setValue('S3')
    await buttonByText(w, '加入')!.trigger('click')
    await flushPromises()
    expect(mocks.splendor.getRoom).toHaveBeenCalledWith('S3', undefined)
    expect(mocks.push).toHaveBeenCalledWith('/splendor/S3')
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
    expect(w.findAll('.zone-card')).toHaveLength(6)
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
    expect(w.findAll('.zone-card')).toHaveLength(6)
  })

  it('back button at level 1 and 2 pushes bare /lobby', async () => {
    for (const q of [{ zone: 'abstract' }, L2]) {
      const w = mountLobby(q)
      await flushPromises()
      await buttonByText(w, '⬅ 回到大厅')!.trigger('click')
      expect(mocks.push).toHaveBeenCalledWith('/lobby')
      w.unmount()
    }
  })

  it('shows back-one-page button next to back-to-lobby in breadcrumb', async () => {
    const w = mountLobby(L2)
    await flushPromises()
    expect(buttonByText(w, '⬅ 返回')).toBeDefined()
    expect(buttonByText(w, '⬅ 回到大厅')).toBeDefined()
  })

  it('zone card click pushes zone query; game card click pushes zone+game', async () => {
    const w = mountLobby()
    await flushPromises()
    await w.findAll('.zone-card')[4].trigger('click')
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

describe('lobby design entry', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.userInfo = { userName: 'alice' }
    mocks.store.fetchProfile.mockResolvedValue(undefined)
    mocks.api.listRooms.mockResolvedValue(emptyList)
  })

  it('level 0 shows the design assistant card and click pushes /design', async () => {
    const w = mountLobby()
    await flushPromises()
    const card = w.find('.design-entry')
    expect(card.exists()).toBe(true)
    expect(card.text()).toContain('桌游设计助手')
    expect(card.text()).toContain('样本 · 机制 · 数值模拟工作台')
    await card.trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/design')
  })

  it('design entry is not rendered at level 1 or 2', async () => {
    for (const q of [{ zone: 'abstract' }, L2]) {
      const w = mountLobby(q)
      await flushPromises()
      expect(w.find('.design-entry').exists()).toBe(false)
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
    await buttonByText(wrapper, '刷新')!.trigger('click')
    await vi.advanceTimersByTimeAsync(0)
    expect(errorSpy).toHaveBeenCalled()
    errorSpy.mockRestore()
  })
})

describe('lobby list states', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.userInfo = { userName: 'alice' }
    mocks.store.fetchProfile.mockResolvedValue(undefined)
    mocks.api.listRooms.mockResolvedValue(emptyList)
  })

  it('manual refresh failure shows inline error panel; retry recovers the list', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mocks.api.listRooms
      .mockResolvedValueOnce(emptyList)
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(oneWaiting)
    const wrapper = mountLobby(L2)
    await flushPromises()
    await buttonByText(wrapper, '刷新')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.list-error').exists()).toBe(true)
    expect(errorSpy).toHaveBeenCalled()
    await buttonByText(wrapper, '重试')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.list-error').exists()).toBe(false)
    expect(wrapper.text()).toContain('ABC123')
    errorSpy.mockRestore()
  })

  it('empty state CTA creates a room and navigates', async () => {
    mocks.api.createRoom.mockResolvedValue({ success: true, room: { roomId: 'NEW001' } })
    const wrapper = mountLobby(L2)
    await flushPromises()
    expect(wrapper.find('.empty').exists()).toBe(true)
    await wrapper.find('.empty button').trigger('click')
    await flushPromises()
    expect(mocks.api.createRoom).toHaveBeenCalledWith('alice')
    expect(mocks.push).toHaveBeenCalledWith('/game/NEW001')
  })
})

describe('大厅在线人数', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.isLogin = true
    mocks.store.userInfo = { userName: 'alice' }
    mocks.api.listRooms.mockResolvedValue(emptyList)
    mocks.online.getOnlineCount.mockResolvedValue({ success: true, onlineCount: 7 })
  })

  it('顶栏展示平台在线人数并请求 /api/online', async () => {
    const wrapper = mountLobby()
    await flushPromises()

    expect(mocks.online.getOnlineCount).toHaveBeenCalled()
    const badge = wrapper.find('.online-badge')
    expect(badge.exists(), '顶栏应有在线人数徽标').toBe(true)
    expect(badge.text()).toContain('7')
  })

  it('接口失败时保持占位符且不弹错误', async () => {
    mocks.online.getOnlineCount.mockRejectedValueOnce(new Error('network'))
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)

    const wrapper = mountLobby()
    await flushPromises()

    expect(wrapper.find('.online-badge').text()).toContain('—')
    expect(errorSpy, '装饰性信息失败不应打断大厅').not.toHaveBeenCalled()
    errorSpy.mockRestore()
  })
})

describe('大厅消息栏与玩家战绩栏', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.isLogin = true
    mocks.store.userInfo = { userName: 'alice' }
    mocks.api.listRooms.mockResolvedValue(emptyList)
    mocks.online.getOnlineCount.mockResolvedValue({ success: true, onlineCount: 7 })
    mocks.match.getRecentMatches.mockResolvedValue({ success: true, matches: [] })
  })

  it('首屏左侧是房间号表单，右侧展示上新/更新/热门消息', async () => {
    const wrapper = mountLobby()
    await flushPromises()

    const guide = wrapper.find('.lobby-guide')
    expect(guide.element.children[1].classList.contains('fastjoin')).toBe(true)
    expect(guide.find('.guide-title').text()).toBe('选一款桌游开玩')
    expect(guide.findAll('.guide-lede span').map((line) => line.text())).toEqual([
      '有人等你？右侧输入房间号赴约',
      '还没想好？点击分区参考看看',
    ])
    expect(guide.findAll('.hint span')).toHaveLength(2)

    const news = wrapper.find('.news-panel')
    expect(news.exists()).toBe(true)
    const text = news.text()
    expect(text).toContain('上新')
    expect(text).toContain('更新')
    expect(text).toContain('热门')
    expect(news.findAll('.news-item')).toHaveLength(4)
  })

  it('战绩栏取最近 5 场，展示游戏名、人数、时长与胜负', async () => {
    const base = {
      playerCount: 4,
      seatNo: 1,
      rankNo: 1,
      score: 10,
      win: true,
      status: '2',
      startTime: '2026-11-23T10:00:00',
      endTime: '2026-11-23T10:12:00',
    }
    mocks.match.getRecentMatches.mockResolvedValueOnce({
      success: true,
      matches: Array.from({ length: 5 }, (_, i) => ({
        ...base,
        matchNo: `M${i + 1}`,
        gameCode: i === 0 ? 'magical_athlete' : 'xiangqi',
      })),
    })

    const wrapper = mountLobby()
    await flushPromises()

    expect(mocks.match.getRecentMatches).toHaveBeenCalledWith(5)
    const rows = wrapper.findAll('.record-panel .record-row')
    expect(rows).toHaveLength(5)
    expect(rows[0].text()).toContain('胡闹运动会 Magical Athlete')
    expect(rows[0].text()).toContain('4 人')
    expect(rows[0].text()).toContain('12 分')
    expect(rows[0].text()).toContain('胜')
    expect(wrapper.find('.record-head').text()).toContain('人数')
    expect(wrapper.find('.record-head').text()).toContain('时长')
    expect(wrapper.find('.record-head').text()).toContain('胜负')
  })
})

describe('大厅房间层桌游介绍区', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.isLogin = true
    mocks.store.userInfo = { userName: 'alice' }
    mocks.api.listRooms.mockResolvedValue(emptyList)
    mocks.online.getOnlineCount.mockResolvedValue({ success: true, onlineCount: 7 })
    mocks.match.getRecentMatches.mockResolvedValue({ success: true, matches: [] })
  })

  it('房间层顶部展示海报、背景故事与规则入口', async () => {
    const wrapper = mountLobby({ zone: 'card', game: 'splendor' })
    await flushPromises()

    const hero = wrapper.find('.game-hero')
    expect(hero.exists()).toBe(true)
    expect(hero.classes()).toContain('has-poster')
    expect(hero.attributes('style')).toContain('/posters/splendor.webp')
    expect(hero.find('.hero-name').text()).toBe('璀璨宝石 Splendor')
    expect(hero.find('.hero-story').text()).toContain('宝石商人')

    const rules = hero.findAll('button').find((b) => b.text().includes('查看规则'))
    expect(rules).toBeDefined()
    await rules!.trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/rules/splendor')
  })

  it('没有海报的游戏仍展示故事但不铺海报背景', async () => {
    const wrapper = mountLobby(L2)
    await flushPromises()

    const hero = wrapper.find('.game-hero')
    expect(hero.exists()).toBe(true)
    expect(hero.classes()).not.toContain('has-poster')
    expect(hero.find('.hero-story').text()).toContain('楚河汉界')
  })
})
