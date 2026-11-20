/** 经济模拟器：静态收支投影 + 结构检查 + 声明式消费策略（参数，不是 AI） */

export interface EconomyParams {
  resources: string[]
  initial?: Record<string, number>
  perTurn?: { resource: string; produce: number; consume: number }[]
  actions?: { key: string; cost: Record<string, number>; gain?: Record<string, number>; vp?: number }[]
  stallK?: number
}

export type Policy = { type: 'greedy' | 'priority'; order?: string[] }

interface EcoRow { turn: number; stocks: Record<string, number> }

function initialStocks(p: EconomyParams): Record<string, number> {
  const stocks: Record<string, number> = {}
  for (const r of p.resources) stocks[r] = p.initial?.[r] ?? 0
  return stocks
}

/** 每回合应用 perTurn：produce 加、consume 钳到库存 */
function applyPerTurn(p: EconomyParams, stocks: Record<string, number>): void {
  for (const pt of p.perTurn ?? []) {
    stocks[pt.resource] = (stocks[pt.resource] ?? 0) + pt.produce
    stocks[pt.resource] = Math.max(0, stocks[pt.resource] - pt.consume)
  }
}

/** 有产出但无任何消耗口（perTurn.consume 与 action.cost 均不触及）的资源 */
function findNoSink(p: EconomyParams): string[] {
  const hasInflow = (r: string) =>
    (p.perTurn ?? []).some(pt => pt.resource === r && pt.produce > 0) ||
    (p.actions ?? []).some(a => (a.gain?.[r] ?? 0) > 0)
  const hasSink = (r: string) =>
    (p.perTurn ?? []).some(pt => pt.resource === r && pt.consume > 0) ||
    (p.actions ?? []).some(a => (a.cost[r] ?? 0) > 0)
  return p.resources.filter(r => hasInflow(r) && !hasSink(r))
}

/** 基础层：N 回合收支投影 + 净流量 + 结构性 noSink 检查 */
export function projectEconomy(p: EconomyParams, turns: number): {
  rows: EcoRow[]
  netFlow: Record<string, number>
  noSink: string[]
} {
  const stocks = initialStocks(p)
  const rows: EcoRow[] = []
  for (let t = 1; t <= turns; t++) {
    applyPerTurn(p, stocks)
    rows.push({ turn: t, stocks: { ...stocks } })
  }
  const netFlow: Record<string, number> = {}
  for (const r of p.resources) {
    const pts = (p.perTurn ?? []).filter(pt => pt.resource === r)
    netFlow[r] = pts.reduce((s, pt) => s + pt.produce - pt.consume, 0)
  }
  return { rows, netFlow, noSink: findNoSink(p) }
}

function affordable(cost: Record<string, number>, stocks: Record<string, number>): boolean {
  return Object.entries(cost).every(([r, n]) => (stocks[r] ?? 0) >= n)
}

function policyOrder(p: EconomyParams, policy: Policy): string[] {
  const keys = (p.actions ?? []).map(a => a.key)
  if (policy.type === 'priority' && !policy.order) throw new Error('priority 策略必须提供 order')
  const order = policy.order ?? keys
  const unknown = order.filter(k => !keys.includes(k))
  if (unknown.length > 0) {
    throw new Error(`policy.order 含未知行动键: ${unknown.join(', ')}（可用行动: ${keys.join(', ') || '(无)'}）`)
  }
  return order
}

/** C1 护栏：自维持行动（cost 为空，或 gain 逐资源 ≥ cost）一旦可负担就永远可负担 → 策略循环无限执行，前置拒绝 */
function assertNoSelfSustaining(p: EconomyParams): void {
  for (const a of p.actions ?? []) {
    if (Object.entries(a.cost).every(([r, n]) => (a.gain?.[r] ?? 0) >= n)) {
      throw new Error(`行动 ${a.key} 自维持（cost 为空或 gain 逐资源 ≥ cost），策略循环将无限执行——请为其加入净消耗`)
    }
  }
}

/** 每回合行动数上限：兜底行动闭环互喂（单行动均非自维持，但组合起来无限循环） */
const MAX_ACTIONS_PER_TURN = 10000

/** 可选层：声明式策略跑 N 回合；stall = 连续 stallK 回合无行动可负担 */
export function simulateEconomy(p: EconomyParams, turns: number, policy: Policy): {
  rows: (EcoRow & { actionsTaken: string[]; vp: number })[]
  stalled: boolean
  stallFromTurn?: number
  totalVp: number
  noSink: string[]
} {
  const stallK = p.stallK ?? 3
  assertNoSelfSustaining(p)
  const order = policyOrder(p, policy)
  const actionByKey = new Map((p.actions ?? []).map(a => [a.key, a]))
  const stocks = initialStocks(p)
  const rows: (EcoRow & { actionsTaken: string[]; vp: number })[] = []
  let totalVp = 0
  let stallCount = 0
  let stalled = false
  let stallFromTurn: number | undefined
  for (let t = 1; t <= turns; t++) {
    applyPerTurn(p, stocks)
    const actionsTaken: string[] = []
    let vp = 0
    // 按 order 循环扫描：可负担则执行，从头重扫直到整轮无可负担
    for (;;) {
      const key = order.find(k => {
        const a = actionByKey.get(k)
        return a !== undefined && affordable(a.cost, stocks)
      })
      if (key === undefined) break
      const a = actionByKey.get(key)!
      for (const [r, n] of Object.entries(a.cost)) stocks[r] -= n
      for (const [r, n] of Object.entries(a.gain ?? {})) stocks[r] = (stocks[r] ?? 0) + n
      vp += a.vp ?? 0
      actionsTaken.push(key)
      if (actionsTaken.length >= MAX_ACTIONS_PER_TURN) {
        throw new Error(`第 ${t} 回合行动数达到上限 ${MAX_ACTIONS_PER_TURN}（疑似行动闭环互喂资源）——请检查 actions 的 cost/gain 是否构成循环`)
      }
    }
    totalVp += vp
    rows.push({ turn: t, stocks: { ...stocks }, actionsTaken, vp })
    if (actionsTaken.length === 0) {
      stallCount++
      if (stallCount >= stallK && !stalled) {
        stalled = true
        stallFromTurn = t - stallK + 1
      }
    } else {
      stallCount = 0
    }
  }
  return { rows, stalled, stallFromTurn, totalVp, noSink: findNoSink(p) }
}