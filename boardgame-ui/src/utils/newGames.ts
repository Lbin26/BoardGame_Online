import type { GameDefinition } from '@/api/game'

/** 上线多久以内算「新」：14 天，覆盖近期连续上线的几款桌游 */
export const NEW_GAME_DAYS = 14

const DAY_MS = 86_400_000

/** game_code → 前端 catalog key 的例外映射（库里的编码与目录 key 不一致时使用） */
const KEY_ALIASES: Record<string, string> = {
  xiangqi: 'chess',
}

/** game_code（下划线）→ 前端 catalog key（驼峰），如 incan_gold → incanGold */
export function gameCodeToKey(gameCode: string): string {
  return KEY_ALIASES[gameCode] ?? gameCode.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase())
}

/** 兼容 "yyyy-MM-dd HH:mm:ss" 与 ISO 两种时间格式 */
function parseCreateTime(value: string | null): number {
  if (!value) return Number.NaN
  return new Date(value.includes(' ') ? value.replace(' ', 'T') : value).getTime()
}

/** 该游戏定义是否属于近期上线 */
export function isNewGame(
  definition: GameDefinition,
  now: number = Date.now(),
  days: number = NEW_GAME_DAYS,
): boolean {
  const created = parseCreateTime(definition.createTime)
  if (!Number.isFinite(created) || created > now) return false
  return now - created <= Math.max(1, days) * DAY_MS
}

/** 从后端目录算出需要打 NEW 标的前端游戏 key 集合 */
export function collectNewGameKeys(
  definitions: GameDefinition[] | undefined,
  now: number = Date.now(),
): Set<string> {
  if (!definitions?.length) return new Set()
  return new Set(
    definitions
      .filter((definition) => isNewGame(definition, now))
      .map((definition) => gameCodeToKey(definition.gameCode)),
  )
}
