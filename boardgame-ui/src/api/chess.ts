import { rawRequest } from '@/utils/request'

/** 房间列表项 */
export interface RoomListItem {
  roomId: string
  redPlayerName: string | null
  blackPlayerName: string | null
  status: string
}

/** 房间列表返回体 */
export interface RoomListData {
  rooms: RoomListItem[]
  waitingCount: number
  playingCount: number
}

/** 创建/加入/查询房间返回体 */
export interface RoomData {
  success: boolean
  room?: unknown
  board?: unknown
  error?: string
}

/** 可落子位置 */
export interface ValidMove {
  row: number
  col: number
}

/** 可落子返回体 */
export interface ValidMovesData {
  success: boolean
  validMoves: ValidMove[]
  error?: string
}

/** 获取房间列表 */
export function listRooms(): Promise<RoomListData> {
  return rawRequest.get('/api/chess/rooms')
}

/** 创建房间 */
export function createRoom(playerName: string): Promise<RoomData> {
  return rawRequest.post('/api/chess/rooms', { playerName })
}

/** 加入房间 */
export function joinRoom(roomId: string, playerName: string): Promise<RoomData> {
  return rawRequest.post('/api/chess/rooms/join', { roomId, playerName })
}

/** 查询单个房间（含棋盘快照），用于进入房间/刷新页面 */
export function getRoom(roomId: string, playerName?: string): Promise<RoomData> {
  return rawRequest.get(`/api/chess/rooms/${roomId}`, {
    params: { playerName },
  })
}

/** 查询某位置的可落子点 */
export function getValidMoves(
  roomId: string,
  row: number,
  col: number,
): Promise<ValidMovesData> {
  return rawRequest.get(`/api/chess/rooms/${roomId}/valid-moves`, {
    params: { row, col },
  })
}
