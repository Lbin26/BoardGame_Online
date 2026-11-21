import { rawRequest } from '@/utils/request'

/** 座位视图（协议 §1 room.seats） */
export interface SplendorSeat {
  seatNo: number
  playerName: string | null
  connected: boolean
  afk: boolean
}

/** 房间信息（后端 SplendorRoomVO） */
export interface SplendorRoomInfo {
  roomId: string
  hostName: string
  maxPlayers: number
  status: 'WAITING' | 'PLAYING' | 'FINISHED'
  seats: SplendorSeat[]
  viewerSeatNo: number | null
  afkPlayers: string[]
}

/** 房间列表项（GET /api/splendor/rooms 的展开形态，与象棋行不同构） */
export interface SplendorRoomListItem {
  roomId: string
  hostName: string
  maxPlayers: number
  seatedCount: number
  status: 'WAITING' | 'PLAYING' | 'FINISHED'
}

export interface SplendorRoomListData {
  rooms: SplendorRoomListItem[]
  waitingCount: number
  playingCount: number
}

/** 创建/入座/起身/查询返回体 */
export interface SplendorRoomData {
  success: boolean
  room?: SplendorRoomInfo
  error?: string
}

/** 获取 Splendor 房间列表 */
export function listRooms(): Promise<SplendorRoomListData> {
  return rawRequest.get('/api/splendor/rooms')
}

/**
 * 创建房间：maxPlayers 可选 2-4，缺省 4。
 * 建房不自动入座（M1 设计），房主随后在房间内选座。
 */
export function createRoom(playerName: string, maxPlayers?: number): Promise<SplendorRoomData> {
  return rawRequest.post('/api/splendor/rooms', { playerName, maxPlayers })
}

/** 入座：seatNo 可选 1..maxPlayers，缺省自动补最小空位 */
export function takeSeat(
  roomId: string,
  playerName: string,
  seatNo?: number,
): Promise<SplendorRoomData> {
  return rawRequest.post(`/api/splendor/rooms/${roomId}/seat`, { playerName, seatNo })
}

/** 起身离座（仅等待中） */
export function standUp(roomId: string, playerName: string): Promise<SplendorRoomData> {
  return rawRequest.post(`/api/splendor/rooms/${roomId}/stand`, { playerName })
}

/** 查询单个房间，用于进入房间/刷新恢复 */
export function getRoom(roomId: string, playerName?: string): Promise<SplendorRoomData> {
  return rawRequest.get(`/api/splendor/rooms/${roomId}`, { params: { playerName } })
}
