import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  chess: { listRooms: vi.fn(), getRoom: vi.fn(), createRoom: vi.fn() },
  splendor: { listRooms: vi.fn(), getRoom: vi.fn(), createRoom: vi.fn() },
  hg: { listRooms: vi.fn(), getRoom: vi.fn(), createRoom: vi.fn() },
  ma: { listRooms: vi.fn(), getRoom: vi.fn(), createRoom: vi.fn() },
}))

vi.mock('@/api/xiangqi', () => mocks.chess)
vi.mock('@/api/splendor', () => mocks.splendor)
vi.mock('@/api/halliGalli', () => mocks.hg)
vi.mock('@/api/magicalAthlete', () => mocks.ma)

import { PLAYABLE_ADAPTERS, adapterByGameCode, adapterOf } from '@/constants/gameAdapters'

describe('game adapters registry', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('四款可玩桌游都注册了路由与容量范围', () => {
    expect(PLAYABLE_ADAPTERS.map((a) => a.key)).toEqual(['xiangqi', 'splendor', 'halliGalli', 'magicalAthlete', 'incanGold'])
    expect(adapterOf('halliGalli')!.route('ABC123')).toBe('/halli-galli/ABC123')
    expect(adapterOf('magicalAthlete')!.route('ABC123')).toBe('/magical-athlete/ABC123')
    expect(adapterOf('splendor')!.capacities).toEqual([2, 3, 4])
    expect(adapterOf('halliGalli')!.capacities).toEqual([2, 3, 4, 5, 6])
    expect(adapterOf('magicalAthlete')!.capacities).toEqual([2, 3, 4, 5, 6])
    expect(adapterOf('unknown')).toBeUndefined()
  })

  it('数据库 game_code 能反查到适配器（halli_galli / magical_athlete）', () => {
    expect(adapterByGameCode('halli_galli')!.key).toBe('halliGalli')
    expect(adapterByGameCode('magical_athlete')!.key).toBe('magicalAthlete')
    expect(adapterByGameCode('splendor')!.label).toBe('璀璨宝石 Splendor')
    expect(adapterByGameCode('xiangqi')!.route('R1')).toBe('/game/R1')
    expect(adapterByGameCode('nope')).toBeUndefined()
    expect(adapterByGameCode(null)).toBeUndefined()
  })

  it('象棋房间列表归一化为房主/座位数/容量', async () => {
    mocks.chess.listRooms.mockResolvedValue({
      rooms: [{ roomId: 'C1', redPlayerName: 'alice', blackPlayerName: null, status: 'WAITING' }],
      waitingCount: 1,
      playingCount: 0,
    })

    const data = await adapterOf('xiangqi')!.listRooms()
    expect(data.rooms).toEqual([
      { roomId: 'C1', hostName: 'alice', maxPlayers: 2, seatedCount: 1, status: 'WAITING' },
    ])
  })

  it('德国心脏病探测按 name 字段统计座位', async () => {
    mocks.hg.getRoom.mockResolvedValue({
      success: true,
      room: {
        roomId: 'H1',
        hostName: 'alice',
        maxPlayers: 6,
        status: 'PLAYING',
        seats: [
          { seatNo: 1, name: 'alice', online: true, afk: false },
          { seatNo: 2, name: null, online: false, afk: false },
        ],
        mySeatNo: 1,
        afkPlayers: [],
      },
    })

    const probe = await adapterOf('halliGalli')!.probeRoom('H1', 'alice')
    expect(probe).toMatchObject({
      success: true,
      status: 'PLAYING',
      hostName: 'alice',
      maxPlayers: 6,
      seatedCount: 1,
    })
    expect(mocks.hg.getRoom).toHaveBeenCalledWith('H1', 'alice')
  })
})
