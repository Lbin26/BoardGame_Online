import { rawRequest } from '@/utils/request'

/** 平台级在线人数：后端汇总所有游戏 WS 连接并按玩家名去重 */
export interface OnlineCountData {
  success: boolean
  onlineCount: number
}

/** GET /api/online —— 只读，失败由调用方静默兜底（装饰性信息，不打断大厅） */
export function getOnlineCount(): Promise<OnlineCountData> {
  return rawRequest.get('/api/online')
}
