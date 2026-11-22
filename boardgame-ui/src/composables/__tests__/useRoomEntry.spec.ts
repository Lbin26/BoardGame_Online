import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { readLastRoom } from '../useLastRoom'
import { useRoomEntry, type RoomEntryResponse, type UseRoomEntryOptions } from '../useRoomEntry'

interface TestData extends RoomEntryResponse {
  room?: { id: string }
}

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  error: vi.fn(),
  fetchProfile: vi.fn(),
  unmountHook: null as (() => void) | null,
  isLogin: true,
  userInfo: null as { userName: string } | null,
  roomId: 'R1',
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { roomId: mocks.roomId } }),
  useRouter: () => ({ replace: mocks.replace }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: mocks.error },
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    get isLogin() {
      return mocks.isLogin
    },
    get userInfo() {
      return mocks.userInfo
    },
    fetchProfile: mocks.fetchProfile,
  }),
}))

// 捕获 onUnmounted 回调以便测试卸载守卫；onMounted 不自动触发，enter 由用例手动调用
vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return {
    ...actual,
    getCurrentInstance: () => ({}),
    onMounted: () => {},
    onUnmounted: (cb: () => void) => {
      mocks.unmountHook = cb
    },
  }
})

function setup(overrides: Partial<UseRoomEntryOptions<TestData>> = {}) {
  const fetchRoom = vi.fn(
    (_roomId: string, _name: string): Promise<TestData> =>
      Promise.resolve({ success: true, room: { id: 'R1' } }),
  )
  const applyRoom = vi.fn()
  const afterRoom = vi.fn()
  const connect = vi.fn()
  const playerName = ref('')
  mocks.unmountHook = null
  const api = useRoomEntry<TestData>({
    gameKey: 'splendor',
    loginRedirect: (id) => `/splendor/${id}`,
    fetchRoom,
    applyRoom,
    afterRoom,
    playerName,
    connect,
    ...overrides,
  })
  return { api, fetchRoom, applyRoom, afterRoom, connect, playerName }
}

describe('useRoomEntry', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.isLogin = true
    mocks.userInfo = { userName: 'alice' }
    mocks.fetchProfile.mockResolvedValue(undefined)
    mocks.roomId = 'R1'
    mocks.unmountHook = null
    localStorage.clear()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('未登录时跳登录页并携带 redirect，不拉房间', async () => {
    mocks.isLogin = false
    const { api, fetchRoom, connect } = setup()

    await api.enter()

    expect(mocks.replace).toHaveBeenCalledWith(
      '/login?redirect=' + encodeURIComponent('/splendor/R1'),
    )
    expect(fetchRoom).not.toHaveBeenCalled()
    expect(connect).not.toHaveBeenCalled()
  })

  it('userInfo 缺失时先补 profile 再取 userName', async () => {
    mocks.userInfo = null
    mocks.fetchProfile.mockImplementation(async () => {
      mocks.userInfo = { userName: 'alice' }
    })
    const { api, fetchRoom, playerName } = setup()

    await api.enter()

    expect(mocks.fetchProfile).toHaveBeenCalledTimes(1)
    expect(fetchRoom).toHaveBeenCalledWith('R1', 'alice')
    expect(playerName.value).toBe('alice')
  })

  it('profile 失败且玩家名为空时提示并回大厅', async () => {
    mocks.userInfo = null
    mocks.fetchProfile.mockRejectedValue(new Error('network'))
    const { api, fetchRoom } = setup()

    await api.enter()

    expect(mocks.error).toHaveBeenCalledWith('用户信息未就绪，请重新登录')
    expect(mocks.replace).toHaveBeenCalledWith('/lobby')
    expect(fetchRoom).not.toHaveBeenCalled()
  })

  it('getRoom 抛异常时提示进入失败并回大厅', async () => {
    const { api, fetchRoom, applyRoom } = setup()
    fetchRoom.mockRejectedValue(new Error('500'))

    await api.enter()

    expect(mocks.error).toHaveBeenCalledWith('进入房间失败')
    expect(mocks.replace).toHaveBeenCalledWith('/lobby')
    expect(applyRoom).not.toHaveBeenCalled()
  })

  it('success 为 false 时优先展示后端 error 文案', async () => {
    const { api, fetchRoom, applyRoom, connect } = setup()
    fetchRoom.mockResolvedValue({ success: false, error: '房间已解散' })

    await api.enter()

    expect(mocks.error).toHaveBeenCalledWith('房间已解散')
    expect(mocks.replace).toHaveBeenCalledWith('/lobby')
    expect(applyRoom).not.toHaveBeenCalled()
    expect(connect).not.toHaveBeenCalled()
  })

  it('isValid 拒绝时回退并给出兜底文案', async () => {
    const { api, fetchRoom } = setup({ isValid: (data) => !!data.room })
    fetchRoom.mockResolvedValue({ success: true })

    await api.enter()

    expect(mocks.error).toHaveBeenCalledWith('进入房间失败')
    expect(mocks.replace).toHaveBeenCalledWith('/lobby')
  })

  it('正常路径按顺序写名、应用房间、记住房间并连 WS', async () => {
    const { api, fetchRoom, applyRoom, afterRoom, connect, playerName } = setup()

    await api.enter()

    expect(playerName.value).toBe('alice')
    expect(fetchRoom).toHaveBeenCalledWith('R1', 'alice')
    expect(applyRoom).toHaveBeenCalledWith({ success: true, room: { id: 'R1' } })
    expect(afterRoom).toHaveBeenCalledTimes(1)
    expect(readLastRoom('alice')).toMatchObject({
      roomId: 'R1',
      gameKey: 'splendor',
      owner: 'alice',
    })
    expect(connect).toHaveBeenCalledWith('R1', 'alice')
    expect(applyRoom.mock.invocationCallOrder[0]).toBeLessThan(afterRoom.mock.invocationCallOrder[0])
    expect(connect.mock.invocationCallOrder[0]).toBeGreaterThan(afterRoom.mock.invocationCallOrder[0])
  })

  it('补 profile 期间卸载后不再拉房间或建连', async () => {
    let resolveProfile!: () => void
    mocks.userInfo = null
    mocks.fetchProfile.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveProfile = resolve
        }).then(() => {
          mocks.userInfo = { userName: 'alice' }
        }),
    )
    const { api, fetchRoom, connect } = setup()

    const p = api.enter()
    expect(mocks.unmountHook).toBeTypeOf('function')
    mocks.unmountHook?.()
    resolveProfile()
    await p

    expect(fetchRoom).not.toHaveBeenCalled()
    expect(connect).not.toHaveBeenCalled()
  })

  it('拉房间期间卸载后不再建连', async () => {
    let resolveRoom!: (v: TestData) => void
    const { api, fetchRoom, applyRoom, connect } = setup()
    fetchRoom.mockImplementation(
      () =>
        new Promise<TestData>((resolve) => {
          resolveRoom = resolve
        }),
    )

    const p = api.enter()
    mocks.unmountHook?.()
    resolveRoom({ success: true, room: { id: 'R1' } })
    await p

    expect(applyRoom).not.toHaveBeenCalled()
    expect(connect).not.toHaveBeenCalled()
  })
})
