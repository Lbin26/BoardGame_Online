import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useGameWebSocket, type UseGameWebSocketOptions } from '../useGameWebSocket'

const mocks = vi.hoisted(() => ({
  warning: vi.fn(),
  error: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning: mocks.warning, error: mocks.error },
}))

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
  emitOpen() {
    this.onopen?.()
  }
  emitClose(reason = '') {
    this.onclose?.({ code: 1000, reason })
  }
  emitMessage(msg: unknown) {
    this.onmessage?.({ data: JSON.stringify(msg) })
  }
  sentTypes(): string[] {
    return this.sent.map((s) => (JSON.parse(s) as { type: string }).type)
  }
}

function setup(overrides: Partial<UseGameWebSocketOptions> = {}) {
  const onMessage = vi.fn()
  const onDisconnected = vi.fn()
  const api = useGameWebSocket({
    path: '/ws/test',
    roomId: () => 'R1',
    playerName: () => 'alice',
    onMessage,
    onDisconnected,
    reconnectDelayMs: 2000,
    ...overrides,
  })
  return { api, onMessage, onDisconnected }
}

describe('useGameWebSocket', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    FakeWebSocket.instances = []
    vi.stubGlobal('WebSocket', FakeWebSocket)
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('拼装 URL 时对玩家名编码，onopen 后 connected 变 true', () => {
    const { api } = setup({ playerName: () => '张 三' })
    api.connect()
    const ws = FakeWebSocket.instances[0]
    expect(ws.url).toContain('/ws/test?roomId=R1&playerName=' + encodeURIComponent('张 三'))
    expect(api.connected.value).toBe(false)
    ws.emitOpen()
    expect(api.connected.value).toBe(true)
  })

  it('send 仅在连接可用时发出，并把消息分发给 onMessage', () => {
    const { api, onMessage } = setup()
    expect(api.send({ type: 'CHAT' })).toBe(false)

    api.connect()
    const ws = FakeWebSocket.instances[0]
    ws.emitOpen()
    expect(api.send({ type: 'CHAT', payload: { text: 'hi' } })).toBe(true)
    expect(ws.sentTypes()).toEqual(['CHAT'])

    ws.emitMessage({ type: 'CHAT', payload: { text: 'hello' } })
    expect(onMessage).toHaveBeenCalledWith({ type: 'CHAT', payload: { text: 'hello' } })
  })

  it('断线后清理临时态并退避重连', () => {
    const { api, onDisconnected } = setup()
    api.connect()
    const ws = FakeWebSocket.instances[0]
    ws.emitOpen()
    ws.emitClose('network')

    expect(api.connected.value).toBe(false)
    expect(onDisconnected).toHaveBeenCalledTimes(1)
    expect(mocks.warning).toHaveBeenCalledTimes(1)
    expect(FakeWebSocket.instances).toHaveLength(1)

    vi.advanceTimersByTime(2000)
    expect(FakeWebSocket.instances).toHaveLength(2)
  })

  it('被同名连接替换时不重连，只置互踢提示', () => {
    const { api } = setup()
    api.connect()
    const ws = FakeWebSocket.instances[0]
    ws.emitOpen()
    ws.emitClose('replaced by new connection')

    expect(api.replacedNotice.value).toBe(true)
    expect(mocks.warning).not.toHaveBeenCalled()
    vi.advanceTimersByTime(10_000)
    expect(FakeWebSocket.instances).toHaveLength(1)
  })

  it('resume 清除互踢提示并重新连接', () => {
    const { api } = setup()
    api.connect()
    FakeWebSocket.instances[0].emitClose('replaced by new connection')
    expect(api.replacedNotice.value).toBe(true)

    api.resume()
    expect(api.replacedNotice.value).toBe(false)
    expect(FakeWebSocket.instances).toHaveLength(2)
  })

  it('markServerClosed 后关房不触发重连', () => {
    const { api } = setup()
    api.connect()
    const ws = FakeWebSocket.instances[0]
    ws.emitOpen()
    api.markServerClosed()
    ws.emitClose('')

    vi.advanceTimersByTime(10_000)
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(mocks.warning).not.toHaveBeenCalled()
  })

  it('close 屏蔽 onclose 回调，主动离开不会重连', () => {
    const { api, onDisconnected } = setup()
    api.connect()
    const ws = FakeWebSocket.instances[0]
    ws.emitOpen()

    api.close()
    ws.emitClose('network')
    vi.advanceTimersByTime(10_000)

    expect(onDisconnected).not.toHaveBeenCalled()
    expect(FakeWebSocket.instances).toHaveLength(1)
  })

  it('心跳与 AFK 自身不刷新活跃态，真实操作会刷新', () => {
    const { api } = setup()
    api.connect()
    const ws = FakeWebSocket.instances[0]
    ws.emitOpen()

    // 30s 后发心跳（心跳 25s 间隔也会自动发），不应推迟 60s 的 AFK
    vi.advanceTimersByTime(30_000)
    api.send({ type: 'HEARTBEAT', payload: {} })
    vi.advanceTimersByTime(31_000)
    expect(ws.sentTypes()).toContain('AFK')

    // 另起一局：真实聊天在 30s 时刷新活跃态，则 61s 时不应进入 AFK
    vi.clearAllMocks()
    const { api: api2 } = setup()
    api2.connect()
    const ws2 = FakeWebSocket.instances[1]
    ws2.emitOpen()
    vi.advanceTimersByTime(30_000)
    api2.send({ type: 'CHAT', payload: { text: 'hi' } })
    vi.advanceTimersByTime(31_000)
    expect(ws2.sentTypes()).not.toContain('AFK')
  })
})
