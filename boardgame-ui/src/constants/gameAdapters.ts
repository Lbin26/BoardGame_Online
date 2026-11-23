/**
 * 游戏适配器注册表（D9）：把「列表 / 探测 / 建房 / 路由」的差异收进一张表，
 * 大厅与「回到房间」悬浮卡只查表，不再按游戏写 if/else 嵌套。
 * 新增游戏只需在此注册一行，并补 api 与路由。
 */

import * as chessApi from '@/api/xiangqi'
import * as splendorApi from '@/api/splendor'
import * as halliGalliApi from '@/api/halliGalli'
import * as magicalAthleteApi from '@/api/magicalAthlete'
import * as incanGoldApi from '@/api/incanGold'
import type { LastRoomGame } from '@/composables/useLastRoom'

/** 归一化房间摘要：不同游戏的房间 VO 形状不同，统一到这里 */
export interface RoomSummary {
  roomId: string
  hostName: string
  maxPlayers: number
  seatedCount: number
  status: string
}

export interface RoomListResult {
  rooms: RoomSummary[]
  waitingCount: number
  playingCount: number
}

export interface RoomProbe {
  success: boolean
  status?: string
  hostName?: string
  maxPlayers?: number
  seatedCount?: number
  error?: string
}

export interface CreateRoomResult {
  success: boolean
  roomId?: string
  error?: string
}

export interface GameAdapter {
  key: LastRoomGame
  /** 数据库 game_code（对局落库、战绩查询回传的编码，与前端 key 命名不同） */
  gameCode: string
  /** 大厅展示名（规则页/回房卡文案复用） */
  label: string
  /** 对局页路由 */
  route: (roomId: string) => string
  minPlayers: number
  maxPlayers: number
  /** 建房可选容量（下拉） */
  capacities: number[]
  defaultCapacity: number
  listRooms: () => Promise<RoomListResult>
  probeRoom: (roomId: string, playerName?: string) => Promise<RoomProbe>
  createRoom: (playerName: string, capacity: number) => Promise<CreateRoomResult>
}

const xiangqiAdapter: GameAdapter = {
  key: 'xiangqi',
  gameCode: 'xiangqi',
  label: '中国象棋',
  route: (roomId) => `/game/${roomId}`,
  minPlayers: 2,
  maxPlayers: 2,
  capacities: [2],
  defaultCapacity: 2,
  async listRooms() {
    const data = await chessApi.listRooms()
    return {
      rooms: data.rooms.map((room) => ({
        roomId: room.roomId,
        hostName: room.redPlayerName ?? '',
        maxPlayers: 2,
        seatedCount: [room.redPlayerName, room.blackPlayerName].filter(Boolean).length,
        status: room.status,
      })),
      waitingCount: data.waitingCount,
      playingCount: data.playingCount,
    }
  },
  async probeRoom(roomId, playerName) {
    const data = await chessApi.getRoom(roomId, playerName)
    if (!data.success || !data.room) return { success: false, error: data.error }
    const room = data.room
    return {
      success: true,
      status: room.status,
      hostName: room.hostName ?? room.redPlayerName ?? '',
      maxPlayers: 2,
      seatedCount: [room.redPlayerName, room.blackPlayerName].filter(Boolean).length,
    }
  },
  async createRoom(playerName) {
    const data = await chessApi.createRoom(playerName)
    return {
      success: data.success && !!data.room,
      roomId: data.room?.roomId,
      error: data.error,
    }
  },
}

const splendorAdapter: GameAdapter = {
  key: 'splendor',
  gameCode: 'splendor',
  label: '璀璨宝石 Splendor',
  route: (roomId) => `/splendor/${roomId}`,
  minPlayers: 2,
  maxPlayers: 4,
  capacities: [2, 3, 4],
  defaultCapacity: 4,
  async listRooms() {
    return splendorApi.listRooms()
  },
  async probeRoom(roomId, playerName) {
    const data = await splendorApi.getRoom(roomId, playerName)
    if (!data.success || !data.room) return { success: false, error: data.error }
    const room = data.room
    return {
      success: true,
      status: room.status,
      hostName: room.hostName,
      maxPlayers: room.maxPlayers,
      seatedCount: room.seats?.filter((seat) => !!seat.playerName).length ?? 0,
    }
  },
  async createRoom(playerName, capacity) {
    const data = await splendorApi.createRoom(playerName, capacity)
    return {
      success: data.success && !!data.room,
      roomId: data.room?.roomId,
      error: data.error,
    }
  },
}

const halliGalliAdapter: GameAdapter = {
  key: 'halliGalli',
  gameCode: 'halli_galli',
  label: '德国心脏病 Halli Galli',
  route: (roomId) => `/halli-galli/${roomId}`,
  minPlayers: 2,
  maxPlayers: 6,
  capacities: [2, 3, 4, 5, 6],
  defaultCapacity: 4,
  async listRooms() {
    return halliGalliApi.listRooms()
  },
  async probeRoom(roomId, playerName) {
    const data = await halliGalliApi.getRoom(roomId, playerName)
    if (!data.success || !data.room) return { success: false, error: data.error }
    const room = data.room
    return {
      success: true,
      status: room.status,
      hostName: room.hostName,
      maxPlayers: room.maxPlayers,
      seatedCount: room.seats?.filter((seat) => !!seat.name).length ?? 0,
    }
  },
  async createRoom(playerName, capacity) {
    const data = await halliGalliApi.createRoom(playerName, capacity)
    return {
      success: data.success && !!data.room,
      roomId: data.room?.roomId,
      error: data.error,
    }
  },
}

const magicalAthleteAdapter: GameAdapter = {
  key: 'magicalAthlete',
  gameCode: 'magical_athlete',
  label: '胡闹运动会 Magical Athlete',
  route: (roomId) => `/magical-athlete/${roomId}`,
  minPlayers: 2,
  maxPlayers: 6,
  capacities: [2, 3, 4, 5, 6],
  defaultCapacity: 4,
  async listRooms() {
    return magicalAthleteApi.listRooms()
  },
  async probeRoom(roomId, playerName) {
    const data = await magicalAthleteApi.getRoom(roomId, playerName)
    if (!data.success || !data.room) return { success: false, error: data.error }
    const room = data.room
    return {
      success: true,
      status: room.status,
      hostName: room.hostName,
      maxPlayers: room.maxPlayers,
      seatedCount: room.seats?.filter((seat) => !!seat.name).length ?? 0,
    }
  },
  async createRoom(playerName, capacity) {
    const data = await magicalAthleteApi.createRoom(playerName, capacity)
    return {
      success: data.success && !!data.room,
      roomId: data.room?.roomId,
      error: data.error,
    }
  },
}

const incanGoldAdapter: GameAdapter = {
  key: 'incanGold',
  gameCode: 'incan_gold',
  label: '印加宝藏 Diamant',
  route: (roomId) => `/incan-gold/${roomId}`,
  minPlayers: 3,
  maxPlayers: 8,
  capacities: [3, 4, 5, 6, 7, 8],
  defaultCapacity: 4,
  async listRooms() {
    return incanGoldApi.listRooms()
  },
  async probeRoom(roomId, playerName) {
    const data = await incanGoldApi.getRoom(roomId, playerName)
    if (!data.success || !data.room) return { success: false, error: data.error }
    const room = data.room
    return {
      success: true,
      status: room.status,
      hostName: room.hostName,
      maxPlayers: room.maxPlayers,
      seatedCount: room.seats?.filter((seat) => !!seat.name).length ?? 0,
    }
  },
  async createRoom(playerName, capacity) {
    const data = await incanGoldApi.createRoom(playerName, capacity)
    return {
      success: data.success && !!data.room,
      roomId: data.room?.roomId,
      error: data.error,
    }
  },
}

export const GAME_ADAPTERS: Record<LastRoomGame, GameAdapter> = {
  xiangqi: xiangqiAdapter,
  splendor: splendorAdapter,
  halliGalli: halliGalliAdapter,
  magicalAthlete: magicalAthleteAdapter,
  incanGold: incanGoldAdapter,
}

/** 可在线对战的游戏（大厅房间层按此顺序探测房间号归属） */
export const PLAYABLE_ADAPTERS: GameAdapter[] = [
  xiangqiAdapter,
  splendorAdapter,
  halliGalliAdapter,
  magicalAthleteAdapter,
  incanGoldAdapter,
]

export function adapterOf(key: unknown): GameAdapter | undefined {
  if (typeof key !== 'string') return undefined
  return GAME_ADAPTERS[key as LastRoomGame]
}

/** 按数据库 game_code 反查适配器（战绩列表用；halli_galli → halliGalli） */
export function adapterByGameCode(code: unknown): GameAdapter | undefined {
  if (typeof code !== 'string') return undefined
  return PLAYABLE_ADAPTERS.find((adapter) => adapter.gameCode === code)
}
