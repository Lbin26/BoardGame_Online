/** 桌游分区与游戏目录（静态数据源，原文照录设计 spec；植入新游戏时在此登记） */

export interface Zone {
  key: string
  name: string
  icon: string
  mechanisms: string[]
}

export interface Game {
  key: string
  name: string
  zone: string
  playable: boolean
  desc: string
  players?: number
  mechanisms?: string[]
}

export const ZONES: Zone[] = [
  { key: 'party', name: '轻策/派对区', icon: '🎉', mechanisms: ['同时行动', '反应', '猜词', '吹牛', '轮抽轻量版'] },
  { key: 'family', name: '家庭/聚会区', icon: '🏠', mechanisms: ['成套收集', '板块放置', '骰子轻策', '交易谈判'] },
  { key: 'strategy', name: '策略核心区', icon: '🧠', mechanisms: ['工人放置', '引擎构建', '区域控制', '路线构建', '行动点分配'] },
  { key: 'card', name: '卡牌区', icon: '🃏', mechanisms: ['手牌管理', '牌库构建', '卡牌驱动', '轮抽', '多用途卡牌'] },
  { key: 'narrative', name: '美式/叙事区', icon: '🎭', mechanisms: ['合作', '传承', '叙事', '隐藏身份', '战斗结算'] },
  { key: 'abstract', name: '抽象/双人区', icon: '♟️', mechanisms: ['抽象棋', '模式构建', '逻辑推理'] },
  { key: 'heavy', name: '硬核/重策区', icon: '⚙️', mechanisms: ['资源管理+引擎+路线+推轨的复合体'] },
]

export const GAMES: Game[] = [
  { key: 'xiangqi', name: '象棋', zone: 'abstract', playable: true, desc: '楚河汉界，运筹帷幄', players: 2 },
  { key: 'go', name: '围棋', zone: 'abstract', playable: false, desc: '敬请期待' },
  { key: 'gomoku', name: '五子棋', zone: 'abstract', playable: false, desc: '敬请期待' },
  {
    key: 'splendor',
    name: '璀璨宝石 Splendor',
    zone: 'card',
    playable: false,
    desc: '敬请期待',
    mechanisms: ['引擎构建', '成套收集'],
  },
  {
    key: 'diceThrone',
    name: '王权骰铸 Dice Throne',
    zone: 'narrative',
    playable: false,
    desc: '敬请期待',
    mechanisms: ['骰子驱动', '角色能力（不对称）', '卡牌辅助'],
  },
  {
    key: 'avalon',
    name: '阿瓦隆 Avalon',
    zone: 'narrative',
    playable: false,
    desc: '敬请期待',
    mechanisms: ['隐藏身份', '推理/演绎', '投票'],
  },
  {
    key: 'brass',
    name: '伯明翰 Brass: Birmingham',
    zone: 'heavy',
    playable: false,
    desc: '敬请期待',
    mechanisms: ['网络构建', '经济/资源管理', '手牌管理（轻卡驱）'],
  },
]

export function findZone(key: unknown): Zone | undefined {
  return ZONES.find((z) => z.key === key)
}

export function findGame(key: unknown): Game | undefined {
  return GAMES.find((g) => g.key === key)
}

export function gamesInZone(zoneKey: string): Game[] {
  return GAMES.filter((g) => g.zone === zoneKey)
}

/** 大厅层级推导：可玩且归属匹配才进房间层；已知不可玩/错配回落分区层；非法回落首屏 */
export function resolveLevel(query: Record<string, unknown>): {
  level: 0 | 1 | 2
  zone?: Zone
  game?: Game
} {
  const zone = findZone(query.zone)
  const game = findGame(query.game)
  if (zone && game && game.zone === zone.key && game.playable) {
    return { level: 2, zone, game }
  }
  if (zone) return { level: 1, zone }
  return { level: 0 }
}
