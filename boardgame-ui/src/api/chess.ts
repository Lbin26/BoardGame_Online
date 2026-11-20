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

/** 房间信息（后端 RoomVO） */
export interface RoomInfo {
  roomId: string
  redPlayerName: string | null
  blackPlayerName: string | null
  status: string
  viewerColor: 'RED' | 'BLACK' | null
}

/** 创建/选座/起身/查询房间返回体 */
export interface RoomData {
  success: boolean
  room?: RoomInfo
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

/** 手动选座（成功失败都返回最新 room+board，竞争败者据此重同步） */
export function takeSeat(
  roomId: string,
  playerName: string,
  color: 'RED' | 'BLACK',
): Promise<RoomData> {
  return rawRequest.post(`/api/chess/rooms/${roomId}/seat`, { playerName, color })
}

/** 起身离座（仅等待中） */
export function standUp(roomId: string, playerName: string): Promise<RoomData> {
  return rawRequest.post(`/api/chess/rooms/${roomId}/stand`, { playerName })
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
