import { describe, expect, it } from 'vitest'
import { collectNewGameKeys, gameCodeToKey, isNewGame } from '@/utils/newGames'

const NOW = new Date('2026-11-23T16:00:00').getTime()

describe('newGameKeys', () => {
  it('game_code 下划线转前端驼峰 key', () => {
    expect(gameCodeToKey('incan_gold')).toBe('incanGold')
    expect(gameCodeToKey('magical_athlete')).toBe('magicalAthlete')
    expect(gameCodeToKey('splendor')).toBe('splendor')
    expect(gameCodeToKey('xiangqi')).toBe('chess')
  })

  it('近期创建的游戏算新，超过 14 天的不算', () => {
    expect(isNewGame({ gameCode: 'incan_gold', gameName: '印加宝藏', createTime: '2026-11-23 13:22:00' }, NOW)).toBe(true)
    expect(isNewGame({ gameCode: 'splendor', gameName: '宝石商人', createTime: '2026-11-01 00:00:00' }, NOW)).toBe(false)
  })

  it('空时间与未来时间不标新', () => {
    expect(isNewGame({ gameCode: 'go', gameName: '围棋', createTime: null }, NOW)).toBe(false)
    expect(isNewGame({ gameCode: 'go', gameName: '围棋', createTime: '2026-12-01 00:00:00' }, NOW)).toBe(false)
  })

  it('只收集新游戏 key', () => {
    const keys = collectNewGameKeys(
      [
        { gameCode: 'incan_gold', gameName: '印加宝藏', createTime: '2026-11-23 13:22:00' },
        { gameCode: 'splendor', gameName: '宝石商人', createTime: '2026-11-01 00:00:00' },
      ],
      NOW,
    )
    expect([...keys]).toEqual(['incanGold'])
  })
})
