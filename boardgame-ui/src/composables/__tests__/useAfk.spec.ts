import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAfk } from '../useAfk'

describe('useAfk', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('超时无操作上报 AFK', () => {
    const send = vi.fn(() => true)
    const { start } = useAfk({ send, timeoutMs: 1000, events: [] })
    start()
    expect(send).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1000)
    expect(send).toHaveBeenCalledTimes(1)
    expect(send).toHaveBeenCalledWith(false) // active=false → AFK
  })

  it('noteActivity 重置计时，且恢复时上报 ACTIVE', () => {
    const send = vi.fn(() => true)
    const { start, noteActivity } = useAfk({ send, timeoutMs: 1000, events: [] })
    start()
    vi.advanceTimersByTime(600)
    noteActivity() // 有操作，重置
    vi.advanceTimersByTime(600)
    expect(send).not.toHaveBeenCalled() // 还没到 1000ms
    vi.advanceTimersByTime(400)
    expect(send).toHaveBeenCalledTimes(1)
  })

  it('待机后 noteActivity 上报 ACTIVE', () => {
    const send = vi.fn(() => true)
    const { start, noteActivity, afk } = useAfk({ send, timeoutMs: 1000, events: [] })
    start()
    vi.advanceTimersByTime(1000)
    expect(afk.value).toBe(true)
    expect(send).toHaveBeenLastCalledWith(false)
    noteActivity()
    expect(afk.value).toBe(false)
    expect(send).toHaveBeenLastCalledWith(true) // active=true → ACTIVE
  })

  it('DOM 事件视作操作并重置计时', () => {
    const send = vi.fn(() => true)
    const { start } = useAfk({ send, timeoutMs: 1000, events: ['pointerdown'] })
    start()
    vi.advanceTimersByTime(700)
    window.dispatchEvent(new Event('pointerdown'))
    vi.advanceTimersByTime(700)
    expect(send).not.toHaveBeenCalled()
    vi.advanceTimersByTime(300)
    expect(send).toHaveBeenCalledTimes(1)
  })

  it('stop 后不再上报且移除监听', () => {
    const send = vi.fn(() => true)
    const { start, stop } = useAfk({ send, timeoutMs: 1000, events: ['keydown'] })
    start()
    stop()
    vi.advanceTimersByTime(2000)
    window.dispatchEvent(new Event('keydown'))
    expect(send).not.toHaveBeenCalled()
  })
})
