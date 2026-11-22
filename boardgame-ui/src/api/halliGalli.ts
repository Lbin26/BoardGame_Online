import { rawRequest } from '@/utils/request'

/** 座位视图（后端 HalliGalliRoomVO.seats） */
export interface HalliGalliSeat {
  seatNo: number
  name: string | null
  online: boolean
  afk: boolean
}

export type HalliGalliStatus = 'WAITING' | 'PLAYING' | 'FINISHED'

/** 房间信息 */
export interface HalliGalliRoomInfo {
  roomId: string
  hostName: string
  maxPlayers: number
  status: HalliGalliStatus
  seats: HalliGalliSeat[]
  mySeatNo: number | null
  afkPlayers: string[]
}

export interface HalliGalliRoomListItem {
  roomId: string
  hostName: string
  maxPlayers: number
  seatedCount: number
  status: HalliGalliStatus
}

export interface HalliGalliRoomListData {
  rooms: HalliGalliRoomListItem[]
  waitingCount: number
  playingCount: number
}

export interface HalliGalliRoomData {
  success: boolean
  room?: HalliGalliRoomInfo
  error?: string
}

/** 牌面：fruit 为 apple/lemon/cherry/grape，count 为 1-5 */
export interface HalliGalliCardFace {
  fruit: string
  count: number
}

/** 玩家公开状态：牌堆内容永远不下行 */
export interface HalliGalliPlayerState {
  seatNo: number
  name: string
  online: boolean
  afk: boolean
  out: boolean
  deckCount: number
  faceUpCount: number
  totalCards: number
  topCard: HalliGalliCardFace | null
}

export interface HalliGalliRingPenalty {
  seatNo: number
  cards: number
}

/** 抢铃结果：CORRECT / WRONG / LATE / DEBOUNCED */
export interface HalliGalliRing {
  status: string
  seatNo: number
  cardsTaken: number
  beneficiarySeat: number | null
  penalties: HalliGalliRingPenalty[]
  reason: string | null
}

export interface HalliGalliSnapshot {
  phase: HalliGalliStatus
  flipSeatNo: number | null
  flipDeadlineAt: number
  totals: Record<string, number>
  players: HalliGalliPlayerState[]
  lastRing: HalliGalliRing | null
}

export interface HalliGalliRank {
  seatNo: number
  name: string
  cardCount: number
  rank: number
}

export function listRooms(): Promise<HalliGalliRoomListData> {
  return rawRequest.get('/api/halli-galli/rooms')
}

/** 创建房间：maxPlayers 2-6，缺省 4；建房不自动入座 */
export function createRoom(playerName: string, maxPlayers?: number): Promise<HalliGalliRoomData> {
  return rawRequest.post('/api/halli-galli/rooms', { playerName, maxPlayers })
}

export function takeSeat(
  roomId: string,
  playerName: string,
  seatNo?: number,
): Promise<HalliGalliRoomData> {
  return rawRequest.post(`/api/halli-galli/rooms/${roomId}/seat`, { playerName, seatNo })
}

export function standUp(roomId: string, playerName: string): Promise<HalliGalliRoomData> {
  return rawRequest.post(`/api/halli-galli/rooms/${roomId}/stand`, { playerName })
}

export function getRoom(roomId: string, playerName?: string): Promise<HalliGalliRoomData> {
  return rawRequest.get(`/api/halli-galli/rooms/${roomId}`, { params: { playerName } })
}
