import { writeFileSync, mkdirSync } from 'node:fs'
import path from 'node:path'
import { stringify } from 'yaml'
import type { SimConfig, SimConfigInput, SweepConfig } from './schema/simConfig.js'
import { diceExact, diceMC } from './sim/dice.js'
import { cardsExact } from './sim/cards.js'
import { simulateEconomy, projectEconomy } from './sim/economy.js'
import { conflictMC } from './sim/conflict.js'
import {
  parseParams, DiceParamsSchema, CardsParamsSchema, EconomyRunParamsSchema, ConflictParamsSchema,
} from './schema/params.js'

export type SimResult = Record<string, unknown>

/** 模拟分发：运行时 zod 校验参数（I1）；dice 精确+MC 双结果；economy 两层（static 投影 / 声明式策略）；cards 支持牌库投影（I5） */
export function runSim(sim: SimConfig['sim'], params: Record<string, unknown>, seed: number, runs: number): SimResult {
  switch (sim) {
    case 'dice': {
      const p = parseParams('dice', DiceParamsSchema, params)
      return { exact: diceExact(p), mc: diceMC(p, seed, runs) }
    }
    case 'cards': {
      const p = parseParams('cards', CardsParamsSchema, params)
      const deck = p.deck ?? p.cards!.map(c => ({ key: c.key, count: c.count }))
      return cardsExact({ ...p, deck }) as unknown as SimResult
    }
    case 'economy': {
      const p = parseParams('economy', EconomyRunParamsSchema, params)
      const turns = p.turns ?? 20
      const policy = p.policy ?? { type: 'greedy' }
      if (policy.type === 'static') return projectEconomy(p, turns) as unknown as SimResult
      return simulateEconomy(p, turns, policy) as unknown as SimResult
    }
    case 'conflict': {
      const p = parseParams('conflict', ConflictParamsSchema, params)
      return conflictMC(p, seed, runs) as unknown as SimResult
    }
    default:
      throw new Error('未知模拟类型: ' + String(sim))
  }
}

export interface ReportData {
  title: string
  config: SimConfig | SimConfigInput | SweepConfig
  params: unknown
  result: SimResult
}

/** 纯函数渲染 markdown 报告 + JSON 原始数据（不含时间戳，逐位可复现；网页工作台用，不落盘） */
export function renderReport(data: ReportData): { md: string; json: string } {
  const seed = (data.config as SimConfig).seed ?? 42
  const runs = (data.config as SimConfig).runs ?? 10000
  const scalars = Object.entries(data.result).filter(([, v]) => ['number', 'string', 'boolean'].includes(typeof v))
  const complex = Object.entries(data.result).filter(([, v]) => !['number', 'string', 'boolean'].includes(typeof v))
  let md = `# ${data.title}\n\n## 配置\n\n\`\`\`yaml\n${stringify(data.config)}\`\`\`\n\n`
  md += `- 种子: ${seed}\n- 局数: ${runs}\n\n## 结果\n\n`
  if (scalars.length > 0) {
    md += '| 指标 | 值 |\n| --- | --- |\n'
    for (const [k, v] of scalars) md += `| ${k} | ${v} |\n`
    md += '\n'
  }
  for (const [k, v] of complex) md += `### ${k}\n\n\`\`\`yaml\n${stringify(v)}\`\`\`\n\n`
  const json = JSON.stringify({ config: data.config, params: data.params, result: data.result }, null, 2)
  return { md, json }
}

/** markdown 报告 + JSON 原始数据落盘（调 renderReport，口径单一来源） */
export function writeReport(outPath: string, data: ReportData): { mdPath: string; jsonPath: string } {
  mkdirSync(path.dirname(outPath), { recursive: true })
  const jsonPath = outPath.replace(/\.md$/i, '') + '.json'
  const { md, json } = renderReport(data)
  writeFileSync(outPath, md, 'utf-8')
  writeFileSync(jsonPath, json, 'utf-8')
  return { mdPath: outPath, jsonPath }
}