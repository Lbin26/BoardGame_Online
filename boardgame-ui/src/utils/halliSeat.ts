export interface SeatLike {
  seatNo: number
  name: string | null
}

/**
 * 解析"我是几号位"。
 *
 * 房间视图有两种视角：REST 查询带本人名字，`mySeatNo` 有值；WS 的
 * GAME_START / SEAT_CHANGED 广播是中立视角（viewerName=null），`mySeatNo`
 * 为空。若直接采信空值，前端会丢失自己的座位，翻牌判定 `flipSeatNo === mySeat`
 * 永远不成立，表现为"翻牌点了没反应"。
 *
 * 因此按可靠性依次回退：本人视角 → 座位表按名字反查 → 上一轮的座位。
 */
export function resolveMySeat(
  seats: SeatLike[] | null | undefined,
  mySeatNo: number | null | undefined,
  playerName: string,
  fallback: number | null = null,
): number | null {
  if (mySeatNo != null) return mySeatNo
  const found = seats?.find((seat) => seat.name === playerName)?.seatNo
  if (found != null) return found
  return fallback ?? null
}
