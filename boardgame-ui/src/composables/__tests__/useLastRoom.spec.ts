import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  forgetLastRoom,
  readLastRoom,
  rememberLastRoom,
} from '@/composables/useLastRoom'

const KEY = 'dicego:lastRoom'

describe('useLastRoom', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => localStorage.clear())

  it('写入后可读取，字段完整', () => {
    rememberLastRoom('splendor', 'ABC123', 'alice')
    const got = readLastRoom('alice')
    expect(got).toMatchObject({ roomId: 'ABC123', gameKey: 'splendor', owner: 'alice' })
    expect(got!.savedAt).toBeGreaterThan(0)
    expect(localStorage.getItem(KEY)).toContain('ABC123')
  })

  it('账号不匹配时视为无记录', () => {
    rememberLastRoom('xiangqi', 'XYZ789', 'alice')
    expect(readLastRoom('bob')).toBeNull()
    expect(readLastRoom('alice')?.roomId).toBe('XYZ789')
    // 不传 owner 时不校验账号（大厅未就绪时也能读出）
    expect(readLastRoom()?.roomId).toBe('XYZ789')
  })

  it('后续进入别的房间会覆盖旧记录', () => {
    rememberLastRoom('splendor', 'A', 'alice')
    rememberLastRoom('xiangqi', 'B', 'alice')
    expect(readLastRoom('alice')).toMatchObject({ roomId: 'B', gameKey: 'xiangqi' })
  })

  it('清除后读取为 null', () => {
    rememberLastRoom('splendor', 'A', 'alice')
    forgetLastRoom()
    expect(readLastRoom('alice')).toBeNull()
  })

  it('JSON 损坏 / 字段非法一律视为无记录而不抛错', () => {
    localStorage.setItem(KEY, '{not json')
    expect(readLastRoom()).toBeNull()
    localStorage.setItem(KEY, JSON.stringify({ roomId: 'A', gameKey: 'unknown' }))
    expect(readLastRoom()).toBeNull()
    localStorage.setItem(KEY, JSON.stringify({ gameKey: 'splendor' }))
    expect(readLastRoom()).toBeNull()
  })
})
