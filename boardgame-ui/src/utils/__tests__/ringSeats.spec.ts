import { describe, expect, it } from 'vitest'

import { RING_RX, RING_RY, ringSlots } from '@/utils/ringSeats'

/** 椭圆半径一致性：点必须落在外接椭圆上 */
function onEllipse(dx: number, dy: number): number {
  return (dx / RING_RX) ** 2 + (dy / RING_RY) ** 2
}

describe('ringSlots', () => {
  it('空列表返回空数组', () => {
    expect(ringSlots([], 1)).toEqual([])
  })

  it('两人：自己在下、对手在上，水平居中', () => {
    const slots = ringSlots([1, 2], 2)
    const self = slots.find((s) => s.seatNo === 2)!
    const other = slots.find((s) => s.seatNo === 1)!

    expect(self.dx).toBe(0)
    expect(self.dy).toBe(RING_RY)
    expect(other.dx).toBe(0)
    expect(other.dy).toBe(-RING_RY)
  })

  it('三人：倒正三角，自己占顶点，另两人在上方左右对称', () => {
    const slots = ringSlots([1, 2, 3], 2)
    const self = slots.find((s) => s.seatNo === 2)!
    const rest = slots.filter((s) => s.seatNo !== 2)

    expect(self.dy).toBe(RING_RY)
    expect(self.dx).toBe(0)
    // 上方两人同高、一左一右
    expect(rest[0].dy).toBeCloseTo(rest[1].dy, 5)
    expect(rest[0].dy).toBeLessThan(0)
    expect(rest[0].dx * rest[1].dx).toBeLessThan(0)
    // 倒三角：上方两人水平间距应大于自己到任一人的水平距离
    expect(Math.abs(rest[0].dx - rest[1].dx)).toBeGreaterThan(Math.abs(rest[0].dx))
  })

  it('四人：菱形，上下左右各一个', () => {
    const slots = ringSlots([1, 2, 3, 4], 1)
    const self = slots.find((s) => s.seatNo === 1)!
    const opposite = slots.find((s) => s.seatNo === 3)!
    const sides = slots.filter((s) => s.seatNo === 2 || s.seatNo === 4)

    expect(self.dy).toBe(RING_RY)
    expect(opposite.dy).toBe(-RING_RY)
    // 左右两侧垂直居中
    expect(sides.every((s) => Math.abs(s.dy) < 0.01)).toBe(true)
    expect(sides.map((s) => Math.sign(s.dx)).sort()).toEqual([-1, 1])
    expect(sides.every((s) => Math.abs(Math.abs(s.dx) - RING_RX) < 0.01)).toBe(true)
  })

  it('五人：正五边形顶点朝下，只有自己在下半圈', () => {
    const slots = ringSlots([1, 2, 3, 4, 5], 3)
    const self = slots.find((s) => s.seatNo === 3)!
    expect(self.dy).toBe(RING_RY)
    expect(Math.max(...slots.map((s) => s.dy))).toBe(self.dy)

    // 顶点朝下：上半圈恰好两人，其余三个都在中线以下
    expect(slots.filter((s) => s.dy < 0)).toHaveLength(2)
    expect(slots.filter((s) => s.dy > 0)).toHaveLength(3)
    // 五边形相邻两点角度差 72°
    const angles = slots
      .map((s) => (Math.atan2(s.dy / RING_RY, s.dx / RING_RX) * 180) / Math.PI)
      .sort((a, b) => a - b)
    for (let i = 1; i < angles.length; i++) {
      // 五位坐标已四舍五入到两位小数，角度会有 0.0x° 的量化误差
      expect(angles[i] - angles[i - 1]).toBeCloseTo(72, 1)
    }
  })

  it('六人：正六边形，自己在下、正上方有一人', () => {
    const slots = ringSlots([1, 2, 3, 4, 5, 6], 4)
    const self = slots.find((s) => s.seatNo === 4)!
    const top = slots.find((s) => s.dy < 0 && Math.abs(s.dx) < 0.01)

    expect(self.dy).toBe(RING_RY)
    expect(top).toBeDefined()
    expect(top!.dy).toBeCloseTo(-RING_RY, 5)
  })

  it('自己不在列表时按传入顺序排，第一个落在下方', () => {
    const slots = ringSlots([7, 8], 99)
    expect(slots[0].dy).toBe(RING_RY)
    expect(slots[0].dx).toBe(0)
    expect(slots[1].dy).toBe(-RING_RY)
  })

  it('座位顺序是顺时针：三人时第二个排在自己左侧', () => {
    const slots = ringSlots([1, 2, 3], 1)
    expect(slots[1].dx).toBeLessThan(0)
    expect(slots[2].dx).toBeGreaterThan(0)
  })

  it.each([2, 3, 4, 5, 6])('%i 人时所有面板都落在外接椭圆上且互不重合', (n) => {
    const seats = Array.from({ length: n }, (_, i) => i + 1)
    const slots = ringSlots(seats, 1)

    expect(slots).toHaveLength(n)
    for (const s of slots) {
      expect(onEllipse(s.dx, s.dy)).toBeCloseTo(1, 3)
    }
    for (let i = 0; i < slots.length; i++) {
      for (let j = i + 1; j < slots.length; j++) {
        const gap = Math.hypot(slots[i].dx - slots[j].dx, slots[i].dy - slots[j].dy)
        expect(gap).toBeGreaterThan(1)
      }
    }
  })
})
