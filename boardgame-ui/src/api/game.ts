import { rawRequest } from '@/utils/request'

/** 游戏定义摘要（后端 game_definition 表，只读） */
export interface GameDefinition {
  gameCode: string
  gameName: string
  /** 后端可能返回 "yyyy-MM-dd HH:mm:ss" 或 ISO 字符串 */
  createTime: string | null
}

export interface GameDefinitionResp {
  success: boolean
  error?: string
  games?: GameDefinition[]
}

/** 游戏目录：用于按创建时间判定最近上线的游戏（NEW 图标） */
export function getGameDefinitions(): Promise<GameDefinitionResp> {
  return rawRequest.get('/api/dicego/games')
}
