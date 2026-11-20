import { describe, expect, it, vi } from 'vitest'
import { useRoom } from '../useRoom'

describe('useRoom', () => {
  it('拉取成功填充列表与计数', async () => {
    const listRooms = vi.fn().mockResolvedValue({
      rooms: [{ roomId: 'A1', status: 'WAITING' }],
      waitingCount: 1,
      playingCount: 0,
    })
    const { rooms, waitingCount, refresh } = useRoom({ listRooms })
    await refresh()
    expect(rooms.value).toHaveLength(1)
    expect(waitingCount.value).toBe(1)
  })

  it('非静默失败把原始错误交给 onError，由视图决定文案', async () => {
    const error = new Error('network down')
    const listRooms = vi.fn().mockRejectedValue(error)
    const onError = vi.fn()
    const { listError, refresh } = useRoom({ listRooms, onError })
    await refresh()
    expect(onError).toHaveBeenCalledWith(error)
    expect(listError.value).toBe(true)
  })

  it('静默失败不提示也不置位 listError', async () => {
    const listRooms = vi.fn().mockRejectedValue(new Error('x'))
    const onError = vi.fn()
    const { listError, refresh } = useRoom({ listRooms, onError })
    await refresh(true)
    expect(onError).not.toHaveBeenCalled()
    expect(listError.value).toBe(false)
  })
})
