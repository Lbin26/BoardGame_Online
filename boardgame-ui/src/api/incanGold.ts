import { rawRequest } from '@/utils/request'

/** 座位视图（后端 IncanGoldRoomVO.seats） */
export interface IncanGoldSeat {
  seatNo: number
  name: string | null
  online: boolean
  afk: boolean
}

export type IncanGoldStatus = 'WAITING' | 'PLAYING' | 'FINISHED'

export type IncanGoldPhase = 'WAITING' | 'DECIDE' | 'ROUND_END' | 'FINISHED'

export interface IncanGoldFieldCard {
  kind: 'TREASURE' | 'HAZARD' | 'ARTIFACT'
  value: number
  hazard: string | null
  remainder: number
}

export interface IncanGoldSnapshotPlayer {
  seatNo: number
  name: string
  online: boolean
  afk: boolean
  inExpedition: boolean
  submitted: boolean
  /** 只有本人视角有值；他人为 null（帐篷遮挡） */
  campGems: number | null
  roundGems: number | null
  artifacts: number
}

export interface IncanGoldEvent {
  seq: number
  code: string
  message: string
  data: Record<string, any>
}

export interface IncanGoldRanking {
  seatNo: number
  name: string
  gems: number
  artifacts: number
  score: number
}

/** 按收件人定制的全量快照（STATE_SYNC） */
export interface IncanGoldSnapshot {
  phase: IncanGoldPhase
  round: number
  endCause: 'ALL_RETREAT' | 'COLLAPSE' | 'DECK_EMPTY' | null
  phaseDeadlineAt: number
  field: IncanGoldFieldCard[]
  fieldArtifacts: number
  hazardRemaining: Record<string, number>
  players: IncanGoldSnapshotPlayer[]
  events: IncanGoldEvent[]
  rankings: IncanGoldRanking[]
}

/** 房间信息 */
export interface IncanGoldRoomInfo {
  roomId: string
  hostName: string
  maxPlayers: number
  status: IncanGoldStatus
  seats: IncanGoldSeat[]
  mySeatNo: number | null
  afkPlayers: string[]
}

export interface IncanGoldRoomListItem {
  roomId: string
  hostName: string
  maxPlayers: number
  seatedCount: number
  status: IncanGoldStatus
}

export interface IncanGoldRoomListData {
  rooms: IncanGoldRoomListItem[]
  waitingCount: number
  playingCount: number
}

export interface IncanGoldRoomData {
  success: boolean
  room?: IncanGoldRoomInfo
  error?: string
}

export function listRooms(): Promise<IncanGoldRoomListData> {
  return rawRequest.get('/api/incan-gold/rooms')
}

/** 创建房间：maxPlayers 3-8，缺省 4；建房不自动入座 */
export function createRoom(playerName: string, maxPlayers?: number): Promise<IncanGoldRoomData> {
  return rawRequest.post('/api/incan-gold/rooms', { playerName, maxPlayers })
}

export function takeSeat(
  roomId: string,
  playerName: string,
  seatNo?: number,
): Promise<IncanGoldRoomData> {
  return rawRequest.post(`/api/incan-gold/rooms/${roomId}/seat`, { playerName, seatNo })
}

export function standUp(roomId: string, playerName: string): Promise<IncanGoldRoomData> {
  return rawRequest.post(`/api/incan-gold/rooms/${roomId}/stand`, { playerName })
}

export function getRoom(roomId: string, playerName?: string): Promise<IncanGoldRoomData> {
  return rawRequest.get(`/api/incan-gold/rooms/${roomId}`, { params: { playerName } })
}
