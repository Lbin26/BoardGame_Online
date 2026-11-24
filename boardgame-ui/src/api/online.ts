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

/** 大厅心跳：告诉后端"我还在线"，与房间 WS 连接合并计人数 */
export async function pingOnline(playerName: string): Promise<void> {
  await rawRequest.post('/api/online/ping', { playerName })
}

/** 主动下线：退出登录/关页/进入待机时立即清除心跳，不必等后端超时 */
export async function leaveOnline(playerName: string): Promise<void> {
  await rawRequest.post('/api/online/leave', { playerName })
}
