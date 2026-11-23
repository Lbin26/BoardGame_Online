import { rawRequest } from '@/utils/request'

/** 座位视图（后端 MagicalAthleteRoomVO.seats） */
export interface MagicalAthleteSeat {
  seatNo: number
  name: string | null
  online: boolean
  afk: boolean
}

export type MagicalAthleteStatus = 'WAITING' | 'PLAYING' | 'FINISHED'

export interface MagicalAthleteRoomInfo {
  roomId: string
  hostName: string
  maxPlayers: number
  status: MagicalAthleteStatus
  seats: MagicalAthleteSeat[]
  mySeatNo: number | null
  afkPlayers: string[]
}

export interface MagicalAthleteRoomListItem {
  roomId: string
  hostName: string
  maxPlayers: number
  seatedCount: number
  status: MagicalAthleteStatus
}

export interface MagicalAthleteRoomListData {
  rooms: MagicalAthleteRoomListItem[]
  waitingCount: number
  playingCount: number
}

export interface MagicalAthleteRoomData {
  success: boolean
  room?: MagicalAthleteRoomInfo
  error?: string
}

/** 赛道棋子状态；M5 双角色局 seatNo 为参赛号 lane，ownerSeatNo 为归属真实座位 */
export interface MagicalAthleteRacerState {
  seatNo: number
  name: string
  racerKey: string
  position: number
  tripped: boolean
  finishRank: number
  bronze: number
  ownerSeatNo?: number
}

/** 终局排名（同分同名次） */
export interface MagicalAthleteScore {
  seatNo: number
  name: string
  gold: number
  silver: number
  bronze: number
  total: number
  rank: number
}

/** 阶段快照；MUSTER 的 activeRacerKeys 恒空、hands 只含自己的手牌（个性化下发） */
export interface MagicalAthleteSnapshot {
  phase: 'WAITING' | 'DRAFT' | 'MUSTER' | 'RACE' | 'RACE_END' | 'FINISHED'
  raceNo: number
  batch: number
  pickOrder: number[]
  pickIndex: number
  pool: string[]
  hands: Record<string, string[]>
  currentSeatNo: number | null
  deadlineAt: number
  finished: boolean
  players: { seatNo: number; name: string; online: boolean; afk: boolean }[]
  readySeats: number[]
  activeRacerKeys: Record<string, string>
  myActiveRacerKey: string | null
  racers: MagicalAthleteRacerState[]
  lastDice: number | null
  diceSeatNo: number | null
  lastEffects: string[]
  goldSeat: number | null
  silverSeat: number | null
  scores: Record<string, number>
  rankings: MagicalAthleteScore[]
  /** M4：轮到我时可用的可选能力项，空数组表示本回合无可选能力 */
  myOptionalOptions: string[]
  /** M5 3 人双角色变体字段（普通局下发默认值；可选以兼容旧快照） */
  variant?: string
  racersPerSeat?: number
  currentLane?: number | null
  laneToSeat?: Record<string, number>
  activeRacerKeysByLane?: Record<string, string>
  myActiveRacerKeys?: string[]
  musterProgress?: Record<string, number>
  /** 过往场次已出场过的选手 key（公开字段，用于手牌灰显） */
  usedRacerKeys?: string[]
}

/** 选手展示信息（由 racerKey 映射，M1 前端内置 catalog） */
export interface RacerInfo {
  key: string
  name: string
  abilityType: string
  abilitySummary: string
}

export function listRooms(): Promise<MagicalAthleteRoomListData> {
  return rawRequest.get('/api/magical-athlete/rooms')
}

/** 创建房间：maxPlayers 2-6，缺省 4；建房不自动入座 */
export function createRoom(playerName: string, maxPlayers?: number): Promise<MagicalAthleteRoomData> {
  return rawRequest.post('/api/magical-athlete/rooms', { playerName, maxPlayers })
}

export function takeSeat(
  roomId: string,
  playerName: string,
  seatNo?: number,
): Promise<MagicalAthleteRoomData> {
  return rawRequest.post(`/api/magical-athlete/rooms/${roomId}/seat`, { playerName, seatNo })
}

export function standUp(roomId: string, playerName: string): Promise<MagicalAthleteRoomData> {
  return rawRequest.post(`/api/magical-athlete/rooms/${roomId}/stand`, { playerName })
}

export function getRoom(roomId: string, playerName?: string): Promise<MagicalAthleteRoomData> {
  return rawRequest.get(`/api/magical-athlete/rooms/${roomId}`, { params: { playerName } })
}
