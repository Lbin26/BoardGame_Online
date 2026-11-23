/** 记住玩家"上次所在房间"，供大厅右下角一键返回（意外离开/刷新/直接跳大厅时保留） */

export type LastRoomGame = 'splendor' | 'xiangqi' | 'halliGalli' | 'magicalAthlete' | 'incanGold'

export interface LastRoomEntry {
  roomId: string
  gameKey: LastRoomGame
  /** 归属用户（userName），切换账号后不串台 */
  owner: string
  savedAt: number
}

const STORAGE_KEY = 'dicego:lastRoom'

function isGameKey(v: unknown): v is LastRoomGame {
  return (
    v === 'splendor' || v === 'xiangqi' || v === 'halliGalli' || v === 'magicalAthlete' || v === 'incanGold'
  )
}

/** 读取记录；JSON 损坏 / 字段缺失 / 账号不匹配一律视为无记录 */
export function readLastRoom(owner?: string): LastRoomEntry | null {
  let raw: string | null
  try {
    raw = localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
  if (!raw) return null
  try {
    const parsed: unknown = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return null
    const e = parsed as Record<string, unknown>
    if (typeof e.roomId !== 'string' || !e.roomId) return null
    if (!isGameKey(e.gameKey)) return null
    if (owner && typeof e.owner === 'string' && e.owner !== owner) return null
    return {
      roomId: e.roomId,
      gameKey: e.gameKey,
      owner: typeof e.owner === 'string' ? e.owner : '',
      savedAt: typeof e.savedAt === 'number' ? e.savedAt : 0,
    }
  } catch {
    return null
  }
}

/** 记录房间（进入房间页且确认房间有效后调用） */
export function rememberLastRoom(gameKey: LastRoomGame, roomId: string, owner?: string): void {
  const entry: LastRoomEntry = {
    roomId,
    gameKey,
    owner: owner ?? '',
    savedAt: Date.now(),
  }
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(entry))
  } catch {
    // 隐私模式 / 存储配额耗尽：静默降级，不影响游戏
  }
}

/** 清除记录（房间被解散/关闭、玩家主动关闭悬浮卡、房间已不存在时调用） */
export function forgetLastRoom(): void {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // 同上，静默降级
  }
}
