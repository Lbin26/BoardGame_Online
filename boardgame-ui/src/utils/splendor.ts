/**
 * Splendor 前端纯工具：色名映射、拿宝石组合校验、买卡支付测算、行动文案。
 * 全部与后端规则对齐（协议 §2/§4），但服务端永远权威——这里只做交互前置提示。
 */

/** 五色宝石 + 黄金的 wire 色名（后端 GemColor.getCode()） */
export const GEM_COLORS = ['white', 'blue', 'green', 'red', 'black'] as const
export type GemColor = (typeof GEM_COLORS)[number]
export type AnyColor = GemColor | 'gold'

export const COLOR_LABELS: Record<AnyColor, string> = {
  white: '白',
  blue: '蓝',
  green: '绿',
  red: '红',
  black: '黑',
  gold: '金',
}

/** 色名 → 展示用 CSS 色（中国桌游惯例：白蓝绿红黑 + 金） */
export const COLOR_HEX: Record<AnyColor, string> = {
  white: '#e8e4da',
  blue: '#3d7eff',
  green: '#27ae60',
  red: '#e74c3c',
  black: '#2d3436',
  gold: '#f1c40f',
}

/** 宝石 token 图片路径（public/cards/splendor/tokens/<色>.webp，透明底） */
export function gemToken(color: AnyColor): string {
  return `/cards/splendor/tokens/${color}.webp`
}

export type GemMap = Partial<Record<AnyColor, number>>

/** 选中的拿宝石数量（不含 gold） */
export function takeTotal(gems: GemMap): number {
  return GEM_COLORS.reduce((sum, c) => sum + (gems[c] ?? 0), 0)
}

/**
 * 拿宝石组合合法性（协议 §2.1）：三异色各 1，或同色 2（该色银行供给 ≥4）。
 * 银行短缺允许少拿（合计 2 或 1）由引擎判定，这里允许发出不完整组合，
 * 仅拦截明显非法形态，避免把合法的"少拿"挡在客户端。
 */
export function isTakeGemsValid(gems: GemMap, bank: GemMap): { ok: boolean; hint?: string } {
  const colors = GEM_COLORS.filter((c) => (gems[c] ?? 0) > 0)
  if (colors.some((c) => (gems[c] ?? 0) > 2)) return { ok: false, hint: '同色最多拿 2 颗' }
  const total = takeTotal(gems)
  if (total > 3) return { ok: false, hint: '一轮最多拿 3 颗' }
  if (total === 0) return { ok: false, hint: '请先选择要拿的宝石' }
  if (total === 3 && colors.length !== 3) {
    return { ok: false, hint: '3 颗必须为三异色各 1' }
  }
  if (total === 2 && colors.length !== 1 && colors.length !== 2) {
    return { ok: false, hint: '2 颗须为同色 2 或两异色各 1（仅银行短缺时）' }
  }
  if (total === 2 && colors.length === 1) {
    const c = colors[0]
    if ((bank[c] ?? 0) < 4) return { ok: false, hint: '同色拿 2 需要银行该色 ≥4' }
  }
  for (const c of colors) {
    if ((gems[c] ?? 0) > (bank[c] ?? 0)) return { ok: false, hint: '银行该色宝石不足' }
  }
  return { ok: true }
}

/** 手牌宝石总数（含黄金，10 上限） */
export function gemTotal(gems: GemMap): number {
  return [...GEM_COLORS, 'gold' as const].reduce((s, c) => s + (gems[c] ?? 0), 0)
}

export interface CostLike {
  cost: GemMap
  bonus: string
}

export interface PlayerLike {
  gems: GemMap
  bonuses: GemMap
}

/**
 * 买卡支付测算（引擎同款规则）：加成抵扣后按白蓝绿红黑顺序实付，缺口由黄金补。
 * 返回 null 表示付不起（CANNOT_AFFORD），否则返回各色实付 + 动用黄金数。
 */
export function computePayment(
  card: CostLike,
  player: PlayerLike,
): { paid: GemMap; goldUsed: number } | null {
  let goldNeeded = 0
  const paid: GemMap = {}
  for (const c of GEM_COLORS) {
    const need = Math.max(0, (card.cost[c] ?? 0) - (player.bonuses[c] ?? 0))
    const have = player.gems[c] ?? 0
    const pay = Math.min(need, have)
    if (pay > 0) paid[c] = pay
    goldNeeded += need - pay
  }
  const goldHave = player.gems.gold ?? 0
  if (goldNeeded > goldHave) return null
  return { paid, goldUsed: goldNeeded }
}

/** ACTION_DONE → 战报文案（中立 detail，协议 §5） */
export function describeAction(
  action: string,
  detail: Record<string, any> | null | undefined,
): string {
  const d = detail ?? {}
  switch (action) {
    case 'TAKE_GEMS': {
      const parts = Object.entries(d.gems ?? {})
        .map(([c, n]) => `${COLOR_LABELS[c as AnyColor] ?? c}×${n}`)
      return parts.length ? `拿走了 ${parts.join(' ')}` : '拿走了宝石'
    }
    case 'BUY_CARD':
      return `购买了发展卡 ${d.cardId ?? '?'}（${d.source === 'reserved' ? '预留区' : '桌面'}）`
    case 'RESERVE_CARD':
      return d.cardId
        ? `预留了明卡 ${d.cardId}${d.goldGained ? '，获得 1 黄金' : ''}`
        : `盲留了 ${d.level ?? '?'} 级牌堆顶${d.goldGained ? '，获得 1 黄金' : ''}`
    case 'DISCARD_GEMS': {
      const parts = Object.entries(d.discarded ?? {})
        .map(([c, n]) => `${COLOR_LABELS[c as AnyColor] ?? c}×${n}`)
      return parts.length ? `弃掉了 ${parts.join(' ')}` : '弃掉了宝石'
    }
    case 'NOBLE_SELECT':
      return `拜访了贵族 ${d.nobleId ?? '?'}（+${d.vp ?? 0} 声望）`
    default:
      return action
  }
}

/** 待办阶段文案（快照 pendingType：公开字段） */
export function pendingLabel(pendingType: string | null | undefined, myTurn: boolean): string {
  if (!pendingType) return ''
  if (pendingType === 'NOBLE_SELECT') return myTurn ? '请选择一位贵族拜访' : '行动方正在选择贵族…'
  if (pendingType === 'DISCARD') return myTurn ? '手牌超过 10 颗，请弃掉多余的宝石' : '行动方正在弃宝石…'
  return pendingType
}
