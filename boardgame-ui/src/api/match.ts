import { rawRequest } from '@/utils/request'

/** 个人最近对局（后端 GameMatchRecentVO） */
export interface RecentMatch {
  matchNo: string
  gameCode: string
  playerCount: number
  seatNo: number
  rankNo: number
  score: number
  win: boolean
  status: string
  startTime: string | null
  endTime: string | null
}

export interface RecentMatchResp {
  success: boolean
  error?: string
  matches?: RecentMatch[]
}

/**
 * 我的最近对局（全游戏共用）：后端按 token 取当前用户，不接受 userId 参数。
 * 未登录返回 success=false，前端按空列表处理。
 */
export function getRecentMatches(limit = 5): Promise<RecentMatchResp> {
  return rawRequest.get('/api/game/matches/recent', { params: { limit } })
}
