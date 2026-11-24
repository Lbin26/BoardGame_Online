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
  /** 房间层顶部展示的背景故事；未登记时回退到 desc */
  story?: string
  players?: number
  mechanisms?: string[]
}

/**
 * 七分区按「定位」重新收敛为六分区（架构文档 2026-11-23 更新）：
 * 用户提供的表格以「定位」为正列，桌游按机制归类；原来独立的 strategy/heavy
 * 合并为 strategy（策略/重策），避免同一玩法在两个区重复。
 * name/mechanisms 严格按表落地，状态由 gamesInZone 实时推导，不再写死。
 */
export const ZONES: Zone[] = [
  { key: 'party', name: '派对/聚会', icon: '🎉', mechanisms: ['秒反应', '同时出招', '笑到拍桌'] },
  { key: 'family', name: '家庭/亲子', icon: '🏠', mechanisms: ['合家欢', '轻松上手', '攒套收集'] },
  { key: 'card', name: '卡牌构筑', icon: '🃏', mechanisms: ['牌库构筑', '手牌博弈', '抽换轮转'] },
  { key: 'narrative', name: '叙事/身份', icon: '🎭', mechanisms: ['角色代入', '隐藏身份', '剧情抉择'] },
  { key: 'abstract', name: '抽象/双人', icon: '♟️', mechanisms: ['完全信息', '纯逻辑', '低随机'] },
  { key: 'strategy', name: '策略/重策', icon: '⚙️', mechanisms: ['长线布局', '资源运营', '多系统叠加'] },
]

export const GAMES: Game[] = [
  {
    key: 'xiangqi',
    name: '象棋',
    zone: 'abstract',
    playable: true,
    desc: '楚河汉界，运筹帷幄',
    story: '楚河汉界之间，车马炮列阵以待。千年攻守浓缩在方寸棋盘上，读透对手的意图，比对手多算一步，就是胜利。',
    players: 2,
  },
  {
    key: 'go',
    name: '围棋',
    zone: 'abstract',
    playable: false,
    desc: '敬请期待',
    story: '黑白子落下，格局随之生长。围地与侵消之间，最简单的规则孕育出最深的算度。',
  },
  {
    key: 'gomoku',
    name: '五子棋',
    zone: 'abstract',
    playable: false,
    desc: '敬请期待',
    story: '五子连珠，攻守瞬息易位。看似简单的横线，考验的是藏在日常里的先见之明。',
  },
  {
    key: 'splendor',
    name: '璀璨宝石 Splendor',
    zone: 'card',
    playable: true,
    desc: '引擎构建 · 成套收集 · 2-4 人',
    story: '文艺复兴的城邦里，宝石商人争相囤积矿脉、延请工匠、结交贵族。谁先把有限的宝石变成源源不断产出声望的商业帝国，谁就赢得这座城市。',
    players: 4,
    mechanisms: ['引擎构建', '成套收集'],
  },
  {
    key: 'halliGalli',
    name: '德国心脏病 Halli Galli',
    zone: 'party',
    playable: true,
    desc: '反应 · 同时行动 · 2-6 人',
    story: '果香四溢的牌堆一张接一张翻开，五枚相同水果一出现，铃声就是号令。比眼睛更快、比手更稳的人，才能把整桌牌收进怀里。',
    players: 6,
    mechanisms: ['反应', '同时行动'],
  },
  {
    key: 'magicalAthlete',
    name: '胡闹运动会 Magical Athlete',
    zone: 'party',
    playable: true,
    desc: '轮抽 · 掷骰竞速 · 角色能力 · 2-6 人',
    story: '一群性格古怪的选手踏上三十格赛道：香蕉绊人、飞艇传送、倒霉蛋垫底也要加分。轮抽出场阵容，在混乱与笑料里抢下金牌。',
    players: 6,
    mechanisms: ['轮抽', '骰子', '角色能力'],
  },
  {
    key: 'incanGold',
    name: '印加宝藏 Diamant',
    zone: 'party',
    playable: true,
    desc: '推运气 · 探险收集 · 3-8 人',
    story: '手电照亮神庙深处，宝石在火光里闪烁，蛇、蜘蛛与毒气却潜伏在下一张牌后。是见好就收带着宝石撤退，还是再赌一把深入宝藏？贪婪与恐惧只在一步之间。',
    players: 8,
    mechanisms: ['推运气', '探险收集'],
  },
  {
    key: 'diceThrone',
    name: '王权骰铸 Dice Throne',
    zone: 'narrative',
    playable: false,
    desc: '敬请期待',
    story: '英雄各持一枚命运之骰，组合出专属战技。掷出的点数既是武器，也是需要管理人品与风险的赌注。',
    mechanisms: ['骰子驱动', '角色能力（不对称）', '卡牌辅助'],
  },
  {
    key: 'avalon',
    name: '阿瓦隆 Avalon',
    zone: 'narrative',
    playable: false,
    desc: '敬请期待',
    story: '圆桌之上，忠诚与叛逆同席而坐。任务成败取决于你信谁、不信谁——而最危险的那个人，往往就坐在你身边。',
    mechanisms: ['隐藏身份', '推理/演绎', '投票'],
  },
  {
    key: 'brass',
    name: '伯明翰 Brass: Birmingham',
    zone: 'strategy',
    playable: false,
    desc: '敬请期待',
    story: '蒸汽与焦炭推动伯明翰的运河和铁路。建厂、铺轨、运煤、卖酒，在两个时代的产业浪潮里把小作坊经营成城市命脉。',
    mechanisms: ['网络构建', '经济/资源管理', '手牌管理（轻卡驱）'],
  },
]

/** 已配置海报的游戏 key，对应 public/posters/{key}.webp */
export const POSTER_GAME_KEYS = new Set([
  'magicalAthlete',
  'incanGold',
  'splendor',
  'diceThrone',
  'halliGalli',
  'avalon',
  'brass',
])

export function findZone(key: unknown): Zone | undefined {
  return ZONES.find((z) => z.key === key)
}

export function findGame(key: unknown): Game | undefined {
  return GAMES.find((g) => g.key === key)
}

export function gamesInZone(zoneKey: string, games: Game[] = GAMES): Game[] {
  return games.filter((g) => g.zone === zoneKey)
}

/**
 * 大厅层级推导：可玩且归属匹配才进房间层；已知不可玩/错配回落分区层；非法回落首屏。
 * games 默认静态目录，大厅加载 game_definition 成功后传入数据库列表。
 */
export function resolveLevel(query: Record<string, unknown>, games: Game[] = GAMES): {
  level: 0 | 1 | 2
  zone?: Zone
  game?: Game
} {
  const zone = findZone(query.zone)
  const game = games.find((g) => g.key === query.game)
  if (zone && game && game.zone === zone.key && game.playable) {
    return { level: 2, zone, game }
  }
  if (zone) return { level: 1, zone }
  return { level: 0 }
}
