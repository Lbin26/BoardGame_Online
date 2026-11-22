import { describe, expect, it } from 'vitest'
import { resolveMySeat } from '../halliSeat'

const seats = [
  { seatNo: 1, name: '阿谭' },
  { seatNo: 2, name: '小红' },
  { seatNo: 3, name: null },
]

describe('resolveMySeat', () => {
  it('个人视角有 mySeatNo 时直接采用', () => {
    expect(resolveMySeat(seats, 2, '阿谭')).toBe(2)
  })

  it('中立广播 mySeatNo 为空时按名字反查座位', () => {
    // GAME_START / SEAT_CHANGED 用 toVO(null) 广播，mySeatNo 恒为 null
    expect(resolveMySeat(seats, null, '小红')).toBe(2)
  })

  it('空座位表也按名字兜底到上一轮座位', () => {
    expect(resolveMySeat(null, null, '小红', 3)).toBe(3)
  })

  it('名字不在座位表且无旧值时视为未入座', () => {
    expect(resolveMySeat(seats, null, '路人')).toBe(null)
    expect(resolveMySeat(seats, undefined, '路人', null)).toBe(null)
  })
})
