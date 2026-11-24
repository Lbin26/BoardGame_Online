import { rawRequest } from '@/utils/request'
import type { Game } from '@/constants/catalog'
import { gameCodeToKey } from '@/utils/newGames'

/** 游戏定义（后端 game_definition 表，只读） */
export interface GameDefinition {
  gameCode: string
  gameName: string
  zoneCode?: string
  playable?: boolean
  minPlayers?: number | null
  maxPlayers?: number | null
  description?: string | null
  mechanisms?: string | null
  story?: string | null
  icon?: string | null
  /** 后端可能返回 "yyyy-MM-dd HH:mm:ss" 或 ISO 字符串 */
  createTime: string | null
}

export interface GameDefinitionResp {
  success: boolean
  error?: string
  games?: GameDefinition[]
}

/** 游戏目录：大厅游戏列表与 NEW 标都来自这里 */
export function getGameDefinitions(): Promise<GameDefinitionResp> {
  return rawRequest.get('/api/dicego/games')
}

/** 把后端 game_definition 行转成大厅 Game 卡片；空列表返回空数组，由调用方决定是否兜底 */
export function toCatalogGames(definitions: GameDefinition[] | undefined): Game[] {
  if (!definitions?.length) return []
  return definitions
    .filter((d) => !!d.gameCode && !!d.zoneCode)
    .map((d) => ({
      key: gameCodeToKey(d.gameCode),
      name: d.gameName,
      zone: d.zoneCode ?? '',
      playable: d.playable === true,
      desc: d.description ?? (d.playable ? '' : '敬请期待'),
      story: d.story || undefined,
      players: d.maxPlayers ?? undefined,
      mechanisms: d.mechanisms
        ? d.mechanisms.split(',').map((m) => m.trim()).filter(Boolean)
        : undefined,
    }))
}
