/** CLI：validate / dice / cards / economy / conflict / sweep（配置驱动）+ dice/cards ad-hoc（非证据） */
import { parseArgs } from 'node:util'
import path from 'node:path'
import { existsSync } from 'node:fs'
import { validateDoc } from './schema/load.js'
import { loadSimConfig, loadSweepConfig, resolveParams } from './schema/simConfig.js'
import { runSim, writeReport } from './report.js'
import { runSweep } from './sim/sweep.js'
import { diceExact, type CompareOp } from './sim/dice.js'
import { cardsExact } from './sim/cards.js'

const OPS: CompareOp[] = ['>=', '>', '<=', '<', '=']

function parseOp(s: string): CompareOp {
  if (!OPS.includes(s as CompareOp)) throw new Error(`非法 op: ${s}（允许 ${OPS.join(' ')}）`)
  return s as CompareOp
}

const maybeNumber = (s: string): string | number => (/^-?\d+(\.\d+)?$/.test(s) ? Number(s) : s)

/** ad-hoc 数值旗标须为有限数字（I1：拒绝 NaN 假证据） */
function num(s: string, flag: string): number {
  const n = Number(s)
  if (!Number.isFinite(n)) throw new Error(`--${flag} 须为数字: ${s}`)
  return n
}

function runValidate(args: string[]): void {
  const { positionals } = parseArgs({ args, allowPositionals: true, strict: true })
  const file = positionals[0]
  if (!file) throw new Error('用法: validate <file>')
  if (!existsSync(file)) {
    console.error(`文件不存在: ${file}`)
    process.exitCode = 2
    return
  }
  const r = validateDoc(file)
  if (r.ok) {
    console.log(`OK ${file}`)
  } else {
    for (const e of r.errors) console.error(e)
    process.exitCode = 1
  }
}

function runConfigSim(sim: 'dice' | 'cards' | 'economy' | 'conflict' | 'sweep', args: string[]): void {
  const { values } = parseArgs({
    args,
    options: { config: { type: 'string' }, out: { type: 'string' } },
    strict: true,
  })
  if (!values.config) throw new Error(`用法: ${sim} --config <path> [--out <path>]`)
  const configPath = path.resolve(values.config)
  const configDir = path.dirname(configPath)
  if (sim === 'sweep') {
    const config = loadSweepConfig(configPath)
    const { rows, baseParams } = runSweep(config, configDir)
    const out = values.out ?? (config.out ? path.resolve(configDir, config.out) : path.join(configDir, 'sweep-report.md'))
    const { mdPath, jsonPath } = writeReport(out, { title: '参数扫描报告', config, params: baseParams, result: { rows } })
    console.log(mdPath)
    console.log(jsonPath)
    return
  }
  const config = loadSimConfig(configPath)
  const { params, seed, runs } = resolveParams(config, configDir)
  const result = runSim(sim, params, seed, runs)
  const out = values.out ?? (config.out ? path.resolve(configDir, config.out) : path.join(configDir, `${sim}-report.md`))
  const { mdPath, jsonPath } = writeReport(out, { title: `${sim} 模拟报告`, config, params, result })
  console.log(mdPath)
  console.log(jsonPath)
  for (const [k, v] of Object.entries(result)) {
    if (typeof v === 'number') console.log(`${k}: ${v}`)
  }
}

function runAdHocDice(args: string[]): void {
  const { values } = parseArgs({
    args,
    options: {
      faces: { type: 'string' }, count: { type: 'string' }, symbol: { type: 'string' },
      op: { type: 'string' }, value: { type: 'string' }, seed: { type: 'string' }, runs: { type: 'string' },
    },
    strict: true,
  })
  if (!values.faces || !values.count || !values.symbol || !values.op || !values.value) {
    throw new Error('用法: dice --faces <逗号分隔> --count N --symbol S --op OP --value K')
  }
  const r = diceExact({
    count: num(values.count, 'count'),
    faces: values.faces.split(',').map(maybeNumber),
    symbol: maybeNumber(values.symbol),
    op: parseOp(values.op),
    value: num(values.value, 'value'),
  })
  console.log('AD-HOC（非证据）')
  console.log(`probability: ${r.probability.toFixed(4)}`)
  console.log(`mean: ${r.mean.toFixed(4)}`)
}

function runAdHocCards(args: string[]): void {
  const { values } = parseArgs({
    args,
    options: {
      deck: { type: 'string' }, draw: { type: 'string' }, target: { type: 'string' },
      op: { type: 'string' }, value: { type: 'string' },
    },
    strict: true,
  })
  if (!values.deck || !values.draw || !values.target || !values.op || !values.value) {
    throw new Error('用法: cards --deck <key:count,...> --draw N --target K --op OP --value K')
  }
  const deck = values.deck.split(',').map(entry => {
    const [key, count] = entry.split(':')
    if (!key || count === undefined) throw new Error(`非法牌库条目: ${entry}（应为 key:count）`)
    return { key, count: num(count, 'deck count') }
  })
  const r = cardsExact({ deck, draw: num(values.draw, 'draw'), target: values.target, op: parseOp(values.op), value: num(values.value, 'value') })
  console.log('AD-HOC（非证据）')
  console.log(`probability: ${r.probability.toFixed(4)}`)
  console.log(`expectedCount: ${r.expectedCount.toFixed(4)}`)
  console.log(`expectedDrawsToFirst: ${r.expectedDrawsToFirst.toFixed(4)}`)
}

function main(): void {
  const [cmd, ...rest] = process.argv.slice(2)
  try {
    switch (cmd) {
      case 'validate': return runValidate(rest)
      case 'dice':
        return rest.includes('--config') ? runConfigSim('dice', rest) : runAdHocDice(rest)
      case 'cards':
        return rest.includes('--config') ? runConfigSim('cards', rest) : runAdHocCards(rest)
      case 'economy':
      case 'conflict':
      case 'sweep':
        return runConfigSim(cmd, rest)
      default:
        throw new Error(`未知子命令: ${cmd ?? '(空)'}（允许 validate/dice/cards/economy/conflict/sweep）`)
    }
  } catch (e) {
    const err = e as NodeJS.ErrnoException
    console.error(err.message)
    process.exitCode = err.code === 'ENOENT' ? 2 : 1
  }
}

main()