import { afterEach, describe, expect, it, vi } from 'vitest'

import { getGameDefinitions, toCatalogGames, type GameDefinition } from '@/api/game'
import { rawRequest } from '@/utils/request'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('game definitions', () => {
  it('从 /api/dicego/games 读取游戏定义', async () => {
    const get = vi.spyOn(rawRequest, 'get').mockResolvedValue({ success: true, games: [] })
    await getGameDefinitions()
    expect(get).toHaveBeenCalledWith('/api/dicego/games')
  })

  it('把 game_definition 行转成大厅 Game 卡片', () => {
    const definitions: GameDefinition[] = [
      {
        gameCode: 'halli_galli',
        gameName: '德国心脏病',
        zoneCode: 'party',
        playable: true,
        maxPlayers: 6,
        description: '抢铃',
        mechanisms: '反应,同时行动',
        story: '果香四溢',
        icon: '/posters/halliGalli.webp',
        createTime: '2026-11-23 10:00:00',
      },
      {
        gameCode: 'go',
        gameName: '围棋',
        zoneCode: 'abstract',
        playable: false,
        description: null,
        createTime: null,
      },
    ]

    const games = toCatalogGames(definitions)
    expect(games).toHaveLength(2)
    expect(games[0]).toMatchObject({
      key: 'halliGalli',
      name: '德国心脏病',
      zone: 'party',
      playable: true,
      players: 6,
      mechanisms: ['反应', '同时行动'],
    })
    expect(games[1]).toMatchObject({ key: 'go', playable: false, desc: '敬请期待' })
    expect(toCatalogGames([])).toEqual([])
  })
})
