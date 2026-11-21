import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useHeartbeat } from '../useHeartbeat'

describe('useHeartbeat', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('start 后按间隔发送心跳', () => {
    const send = vi.fn(() => true)
    const { start } = useHeartbeat(send, { intervalMs: 1000 })
    start()
    expect(send).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1000)
    expect(send).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(2000)
    expect(send).toHaveBeenCalledTimes(3)
  })

  it('immediate 时 start 立即发一次', () => {
    const send = vi.fn(() => true)
    const { start } = useHeartbeat(send, { intervalMs: 1000, immediate: true })
    start()
    expect(send).toHaveBeenCalledTimes(1)
  })

  it('stop 后不再发送', () => {
    const send = vi.fn(() => true)
    const { start, stop } = useHeartbeat(send, { intervalMs: 1000 })
    start()
    vi.advanceTimersByTime(1000)
    stop()
    vi.advanceTimersByTime(3000)
    expect(send).toHaveBeenCalledTimes(1)
  })

  it('send 返回 false 时自动停表，running 变 false', () => {
    const send = vi.fn(() => false)
    const { start, running } = useHeartbeat(send, { intervalMs: 1000 })
    start()
    vi.advanceTimersByTime(1000)
    expect(send).toHaveBeenCalledTimes(1)
    expect(running.value).toBe(false)
    // 停表后不再触发
    vi.advanceTimersByTime(3000)
    expect(send).toHaveBeenCalledTimes(1)
  })

  it('重复 start 不叠加定时器', () => {
    const send = vi.fn(() => true)
    const { start } = useHeartbeat(send, { intervalMs: 1000 })
    start()
    start()
    start()
    vi.advanceTimersByTime(1000)
    expect(send).toHaveBeenCalledTimes(1)
  })
})
