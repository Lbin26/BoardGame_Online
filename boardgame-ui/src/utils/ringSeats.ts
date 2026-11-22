export interface RingSlot {
  seatNo: number
  /** 相对容器宽度的百分比偏移（-30 ~ 30） */
  dx: number
  /** 相对容器高度的百分比偏移（-31 ~ 31） */
  dy: number
}

/** 椭圆半径：面板是横向矩形，水平留得多一点才不会贴边 */
export const RING_RX = 30
export const RING_RY = 31

/**
 * 环形牌桌坐标：**自己恒在正下方**，其余玩家按座位号顺时针均分一圈。
 *
 * 屏幕坐标 y 向下，所以 90° 就是正下方：
 * - n=2 → 上下相对
 * - n=3 → 倒正三角（顶点在自己）
 * - n=4 → 菱形
 * - n=5 → 正五边形，顶点朝下
 * - n=6 → 正六边形
 *
 * `selfSeatNo` 不在列表里（例如还没入座、或观战）时按传入顺序排，第一个落在下方。
 */
export function ringSlots(
  seatNos: number[],
  selfSeatNo: number | null,
  rx: number = RING_RX,
  ry: number = RING_RY,
): RingSlot[] {
  const n = seatNos.length
  if (!n) return []

  const selfIdx = selfSeatNo === null ? -1 : seatNos.indexOf(selfSeatNo)

  return seatNos.map((seatNo, i) => {
    const offset = selfIdx < 0 ? i : (i - selfIdx + n) % n
    const rad = ((90 + (360 / n) * offset) * Math.PI) / 180
    return {
      seatNo,
      dx: round2(Math.cos(rad) * rx),
      dy: round2(Math.sin(rad) * ry),
    }
  })
}

function round2(v: number): number {
  // 消除 -0 与浮点尾数，模板里直接拼进 calc()
  return Math.round(v * 100) / 100 + 0
}
