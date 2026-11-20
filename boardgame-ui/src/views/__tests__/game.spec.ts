import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'

import Game from '@/views/game/index.vue'
import type { RoomInfo } from '@/api/chess'

class FakeWebSocket {
  static OPEN = 1
  static instances: FakeWebSocket[] = []
  url: string
  readyState = 1
  sent: string[] = []
  onopen: (() => void) | null = null
  onmessage: ((evt: { data: string }) => void) | null = null
  onclose: ((evt: { code: number; reason: string }) => void) | null = null
  onerror: (() => void) | null = null
  constructor(url: string) {
    this.url = url
    FakeWebSocket.instances.push(this)
  }
  send(data: string) {
    this.sent.push(data)
  }
  close() {
    this.readyState = 3
  }
  emitMessage(msg: unknown) {
    this.onmessage?.({ data: JSON.stringify(msg) })
  }
}

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  copyText: vi.fn(),
  store: {
    isLogin: true,
    userInfo: { userName: 'alice' } as { userName: string } | null,
    fetchProfile: vi.fn(),
    logout: vi.fn(),
  },
  api: {
    getRoom: vi.fn(),
    getValidMoves: vi.fn(),
    takeSeat: vi.fn(),
    standUp: vi.fn(),
  },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { roomId: 'ABC123' } }),
  useRouter: () => ({ push: mocks.push, replace: mocks.replace }),
}))
vi.mock('@/store/user', () => ({ useUserStore: () => mocks.store }))
vi.mock('@/api/chess', () => mocks.api)
vi.mock('@/utils/clipboard', () => ({ copyText: mocks.copyText }))

enableAutoUnmount(afterEach)

const board = { pieces: [], currentTurn: 'RED', gameOver: false, winner: null }

function roomVO(over: Partial<RoomInfo> = {}): RoomInfo {
  return {
    roomId: 'ABC123',
    redPlayerName: null,
    blackPlayerName: null,
    status: 'WAITING',
    viewerColor: null,
    ...over,
  }
}

function mountGame() {
  return mount(Game, {
    global: { plugins: [ElementPlus], stubs: { ChessBoard: true } },
  })
}

function buttonByText(wrapper: ReturnType<typeof mountGame>, text: string) {
  return wrapper.findAll('button').find((b) => b.text().includes(text))
}

beforeEach(() => {
  vi.clearAllMocks()
  FakeWebSocket.instances = []
  vi.stubGlobal('WebSocket', FakeWebSocket)
  mocks.store.isLogin = true
  mocks.store.userInfo = { userName: 'alice' }
  mocks.store.fetchProfile.mockResolvedValue(undefined)
  mocks.api.getRoom.mockResolvedValue({ success: true, room: roomVO(), board })
  mocks.api.getValidMoves.mockResolvedValue({ success: true, validMoves: [] })
  mocks.copyText.mockResolvedValue(true)
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('room bar navigation', () => {
  it('has no logout button (logout lives only in lobby)', async () => {
    const wrapper = mountGame()
    await flushPromises()
    expect(wrapper.find('.room-bar').text()).not.toContain('退出登录')
    expect(buttonByText(wrapper, '退出登录')).toBeUndefined()
  })

  it('room bar has a back-to-lobby button that navigates', async () => {
    const wrapper = mountGame()
    await flushPromises()
    const back = wrapper
      .find('.room-bar')
      .findAll('button')
      .find((b) => b.text().includes('返回大厅'))
    expect(back).toBeDefined()
    await back!.trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/lobby')
  })
})

describe('game mount', () => {
  it('loads room, connects WS, does not auto-seat', async () => {
    const wrapper = mountGame()
    await flushPromises()
    expect(mocks.api.getRoom).toHaveBeenCalledWith('ABC123', 'alice')
    expect(mocks.api.takeSeat).not.toHaveBeenCalled()
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(FakeWebSocket.instances[0].url).toContain('roomId=ABC123')
    expect(FakeWebSocket.instances[0].url).toContain('playerName=alice')
    expect(wrapper.find('[data-testid="seat-red"]').exists()).toBe(true)
  })

  it('does not open a WebSocket when unmounted while mount awaits are in flight', async () => {
    let resolveRoom!: (v: unknown) => void
    mocks.api.getRoom.mockImplementation(
      () =>
        new Promise((r) => {
          resolveRoom = r
        }),
    )
    const wrapper = mountGame()
    await flushPromises() // profile 已就绪，getRoom 仍在途
    wrapper.unmount()
    resolveRoom({ success: true, room: roomVO(), board })
    await flushPromises()
    expect(FakeWebSocket.instances).toHaveLength(0)
  })

  it('aborts mount when profile cannot be fetched', async () => {
    mocks.store.userInfo = null
    mocks.store.fetchProfile.mockRejectedValue(new Error('登录已过期'))
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mountGame()
    await flushPromises()
    expect(mocks.api.getRoom).not.toHaveBeenCalled()
    expect(FakeWebSocket.instances).toHaveLength(0)
    expect(errorSpy).toHaveBeenCalled()
    expect(mocks.replace).toHaveBeenCalledWith('/lobby')
    errorSpy.mockRestore()
  })

  it('redirects to login when not logged in', async () => {
    mocks.store.isLogin = false
    mountGame()
    await flushPromises()
    expect(mocks.api.getRoom).not.toHaveBeenCalled()
    expect(mocks.replace).toHaveBeenCalledWith(
      '/login?redirect=' + encodeURIComponent('/game/ABC123'),
    )
  })

  it('returns to lobby when room missing', async () => {
    mocks.api.getRoom.mockResolvedValue({ success: false, error: '房间不存在' })
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mountGame()
    await flushPromises()
    expect(errorSpy).toHaveBeenCalledWith('房间不存在')
    expect(mocks.replace).toHaveBeenCalledWith('/lobby')
    expect(FakeWebSocket.instances).toHaveLength(0)
    errorSpy.mockRestore()
  })
})

describe('room id display and copy', () => {
  it('shows room id and copies it', async () => {
    const successSpy = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as any)
    const wrapper = mountGame()
    await flushPromises()
    expect(wrapper.text()).toContain('ABC123')
    await buttonByText(wrapper, '📋 复制')!.trigger('click')
    await flushPromises()
    expect(mocks.copyText).toHaveBeenCalledWith('ABC123')
    expect(successSpy).toHaveBeenCalled()
    successSpy.mockRestore()
  })

  it('shows error toast when copy fails', async () => {
    mocks.copyText.mockResolvedValue(false)
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    const wrapper = mountGame()
    await flushPromises()
    await buttonByText(wrapper, '📋 复制')!.trigger('click')
    await flushPromises()
    expect(errorSpy).toHaveBeenCalled()
    errorSpy.mockRestore()
  })
})

describe('manual seating', () => {
  it('chooseSeat success applies authoritative response', async () => {
    mocks.api.takeSeat.mockResolvedValue({
      success: true,
      room: roomVO({ redPlayerName: 'alice', viewerColor: 'RED' }),
      board,
    })
    const wrapper = mountGame()
    await flushPromises()
    await wrapper.find('[data-testid="seat-red"]').trigger('click')
    await flushPromises()
    expect(mocks.api.takeSeat).toHaveBeenCalledWith('ABC123', 'alice', 'RED')
    expect(wrapper.find('[data-testid="seat-red"]').exists()).toBe(false)
    expect(buttonByText(wrapper, '起身')).toBeTruthy()
  })

  it('chooseSeat failure resyncs room from response (race loser)', async () => {
    mocks.api.takeSeat.mockResolvedValue({
      success: false,
      error: '该座位已被占用',
      room: roomVO({ redPlayerName: 'bob' }),
      board,
    })
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    const wrapper = mountGame()
    await flushPromises()
    await wrapper.find('[data-testid="seat-red"]').trigger('click')
    await flushPromises()
    expect(errorSpy).toHaveBeenCalledWith('该座位已被占用')
    expect(wrapper.text()).toContain('bob')
    // 仍是旁观者：picker 保留、无起身按钮
    expect(wrapper.find('[data-testid="seat-red"]').exists()).toBe(true)
    expect(buttonByText(wrapper, '起身')).toBeUndefined()
    errorSpy.mockRestore()
  })

  it('standUp clears seat and reopens picker', async () => {
    mocks.api.getRoom.mockResolvedValue({
      success: true,
      room: roomVO({ redPlayerName: 'alice', viewerColor: 'RED' }),
      board,
    })
    mocks.api.standUp.mockResolvedValue({ success: true, room: roomVO(), board })
    const wrapper = mountGame()
    await flushPromises()
    await buttonByText(wrapper, '起身')!.trigger('click')
    await flushPromises()
    expect(mocks.api.standUp).toHaveBeenCalledWith('ABC123', 'alice')
    expect(wrapper.find('[data-testid="seat-red"]').exists()).toBe(true)
  })

  it('spectator cannot undo/resign/rematch', async () => {
    mocks.api.getRoom.mockResolvedValue({
      success: true,
      room: roomVO({ status: 'PLAYING', redPlayerName: 'bob', blackPlayerName: 'carol' }),
      board,
    })
    const wrapper = mountGame()
    await flushPromises()
    expect(wrapper.text()).toContain('你正在观战')
    expect(buttonByText(wrapper, '悔棋')!.attributes('disabled')).toBeDefined()
    expect(buttonByText(wrapper, '认输')!.attributes('disabled')).toBeDefined()
    expect(buttonByText(wrapper, '再来一局')).toBeUndefined()
  })
})

describe('WS message ownership', () => {
  it('broadcast viewerColor of another player never overwrites mine', async () => {
    mocks.api.getRoom.mockResolvedValue({
      success: true,
      room: roomVO({ redPlayerName: 'alice', viewerColor: 'RED' }),
      board,
    })
    const wrapper = mountGame()
    await flushPromises()
    FakeWebSocket.instances[0].emitMessage({
      type: 'PLAYER_JOINED',
      playerName: 'bob',
      payload: { room: roomVO({ redPlayerName: 'alice', blackPlayerName: 'bob', viewerColor: 'BLACK' }) },
    })
    await flushPromises()
    // 我仍是红方（起身按钮在），黑方面板显示 bob
    expect(buttonByText(wrapper, '起身')).toBeTruthy()
    expect(wrapper.text()).toContain('bob')
  })

  it('self-sync PLAYER_JOINED restores my seat after refresh', async () => {
    const wrapper = mountGame()
    await flushPromises()
    expect(wrapper.find('[data-testid="seat-red"]').exists()).toBe(true)
    FakeWebSocket.instances[0].emitMessage({
      type: 'PLAYER_JOINED',
      playerName: 'alice',
      payload: { room: roomVO({ redPlayerName: 'alice', viewerColor: 'RED' }), board },
    })
    await flushPromises()
    expect(wrapper.find('[data-testid="seat-red"]').exists()).toBe(false)
    expect(buttonByText(wrapper, '起身')).toBeTruthy()
  })

  it('neutral PLAYER_JOINED broadcast about me (no board) never wipes my color', async () => {
    mocks.api.getRoom.mockResolvedValue({
      success: true,
      room: roomVO({ redPlayerName: 'alice', viewerColor: 'RED' }),
      board,
    })
    const wrapper = mountGame()
    await flushPromises()
    // 握手慢于选座 REST 时，自己加入的中立广播会晚到：无 board、viewerColor=null
    FakeWebSocket.instances[0].emitMessage({
      type: 'PLAYER_JOINED',
      playerName: 'alice',
      payload: { room: roomVO({ redPlayerName: 'alice', viewerColor: null }) },
    })
    await flushPromises()
    expect(buttonByText(wrapper, '起身')).toBeTruthy()
    expect(wrapper.find('[data-testid="seat-red"]').exists()).toBe(false)
  })

  it('own SEAT_CHANGED broadcast takes payload.color, not neutral room viewerColor', async () => {
    const wrapper = mountGame()
    await flushPromises()
    // 后端对所有人广播 toVO(null)：关于我自己的选座广播 viewerColor 也是 null
    FakeWebSocket.instances[0].emitMessage({
      type: 'SEAT_CHANGED',
      playerName: 'alice',
      payload: { room: roomVO({ redPlayerName: 'alice', viewerColor: null }), color: 'RED' },
    })
    await flushPromises()
    expect(wrapper.find('[data-testid="seat-red"]').exists()).toBe(false)
    expect(buttonByText(wrapper, '起身')).toBeTruthy()
  })

  it('own stand-up SEAT_CHANGED (color null) clears my seat', async () => {
    mocks.api.getRoom.mockResolvedValue({
      success: true,
      room: roomVO({ redPlayerName: 'alice', viewerColor: 'RED' }),
      board,
    })
    const wrapper = mountGame()
    await flushPromises()
    FakeWebSocket.instances[0].emitMessage({
      type: 'SEAT_CHANGED',
      playerName: 'alice',
      payload: { room: roomVO({ viewerColor: null }), color: null },
    })
    await flushPromises()
    expect(wrapper.find('[data-testid="seat-red"]').exists()).toBe(true)
    expect(buttonByText(wrapper, '起身')).toBeUndefined()
  })

  it('REMATCH broadcast about me keeps my color (seats preserved)', async () => {
    mocks.api.getRoom.mockResolvedValue({
      success: true,
      room: roomVO({ status: 'FINISHED', redPlayerName: 'alice', blackPlayerName: 'bob', viewerColor: 'RED' }),
      board: { ...board, gameOver: true, winner: 'BLACK' },
    })
    const wrapper = mountGame()
    await flushPromises()
    FakeWebSocket.instances[0].emitMessage({
      type: 'REMATCH',
      playerName: 'alice',
      payload: {
        room: roomVO({ status: 'PLAYING', redPlayerName: 'alice', blackPlayerName: 'bob', viewerColor: null }),
        board,
      },
    })
    await flushPromises()
    expect(wrapper.find('[data-testid="seat-red"]').exists()).toBe(false)
    expect(buttonByText(wrapper, '悔棋')!.attributes('disabled')).toBeUndefined()
  })

  it('SEAT_CHANGED merges room and announces in chat', async () => {
    const wrapper = mountGame()
    await flushPromises()
    FakeWebSocket.instances[0].emitMessage({
      type: 'SEAT_CHANGED',
      playerName: 'bob',
      payload: { room: roomVO({ blackPlayerName: 'bob' }), color: 'BLACK' },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('bob 坐到了黑方')
  })
})

describe('WS auto reconnect', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  it('retries after 2s, at most 5 times', async () => {
    mountGame()
    await vi.advanceTimersByTimeAsync(0)
    expect(FakeWebSocket.instances).toHaveLength(1)
    for (let i = 0; i < 5; i++) {
      FakeWebSocket.instances[i].onclose?.({ code: 1006, reason: '' })
      await vi.advanceTimersByTimeAsync(2000)
      expect(FakeWebSocket.instances).toHaveLength(i + 2)
    }
    // 第 6 次断开不再重连
    FakeWebSocket.instances[5].onclose?.({ code: 1006, reason: '' })
    await vi.advanceTimersByTimeAsync(10000)
    expect(FakeWebSocket.instances).toHaveLength(6)
  })

  it('unmount stops reconnection', async () => {
    const wrapper = mountGame()
    await vi.advanceTimersByTimeAsync(0)
    FakeWebSocket.instances[0].onclose?.({ code: 1006, reason: '' })
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(10000)
    expect(FakeWebSocket.instances).toHaveLength(1)
  })

  it('replaced-by-new-connection close stops reconnect and shows notice', async () => {
    const wrapper = mountGame()
    await vi.advanceTimersByTimeAsync(0)
    FakeWebSocket.instances[0].onclose?.({ code: 1000, reason: 'replaced by new connection' })
    await vi.advanceTimersByTimeAsync(10000)
    // 不自动重连，避免双标签页互踢循环
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(wrapper.text()).toContain('其他标签页')
  })

  it('manual resume takes over the connection', async () => {
    const wrapper = mountGame()
    await vi.advanceTimersByTimeAsync(0)
    FakeWebSocket.instances[0].onclose?.({ code: 1000, reason: 'replaced by new connection' })
    await vi.advanceTimersByTimeAsync(0)
    await buttonByText(wrapper, '在此页继续')!.trigger('click')
    await vi.advanceTimersByTimeAsync(0)
    expect(FakeWebSocket.instances).toHaveLength(2)
    expect(wrapper.text()).not.toContain('其他标签页')
  })
})
