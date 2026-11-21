import { describe, it, expect, afterEach } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { AddressInfo } from 'node:net'
import type http from 'node:http'
import { createApp } from '../src/app.js'

const faces64 = `[${Array(63).fill('x').join(',')},hit]`
/** 慢任务夹具：每 run 必满 200 轮 ×40 骰 ×50000 runs ≈ 4 亿次 RNG，稳超 1.5s（全部在上限内） */
const slowGame = `schemaVersion: 1
meta:
  key: slowGame
  name: 慢任务夹具
  players: { min: 2, max: 2 }
  durationMin: 30
mechanisms:
  - worker-placement
components:
  resources:
    - key: gold
      name: 金币
turnStructure:
  phases:
    - key: income
      name: 收入
      summary: 每回合获得金币
subsystems:
  conflict:
    attacker:
      diceCount: 20
      faces: ${faces64}
      hitSymbols: [hit]
      hp: 1000
    defender:
      diceCount: 20
      faces: ${faces64}
      hitSymbols: [hit]
      hp: 1000
    maxRounds: 200
`
const slowConfig = 'game: slow.yaml\nsim: conflict\nfrom: subsystems.conflict\nseed: 42\nruns: 50000\n'

const miniGame = readFileSync(
  fileURLToPath(new URL('../../../tools/boardgame-design/tests/fixtures/mini-game.yaml', import.meta.url)),
  'utf-8',
)
const smallDiceConfig = `game: mini-game.yaml
sim: dice
from: subsystems.economy
seed: 42
runs: 100
overrides:
  count: 2
  faces: [1, 2, 3, 4, 5, 6]
  symbol: 6
  op: '>='
  value: 10
`
const selfSustainingGame = miniGame.replace(
  /actions:[\s\S]*$/,
  'actions:\n      - key: free\n        cost: {}\n        vp: 1\n',
)
const ecoConfig = 'game: mini-game.yaml\nsim: economy\nfrom: subsystems.economy\nseed: 42\nruns: 10\noverrides:\n  turns: 1\n'

let server: http.Server | undefined
let base = ''

async function start(budgetMs: number): Promise<void> {
  server = createApp({ simBudgetMs: budgetMs })
  await new Promise<void>(r => server!.listen(0, '127.0.0.1', r))
  base = `http://127.0.0.1:${(server!.address() as AddressInfo).port}`
}
afterEach(async () => {
  if (server) await new Promise(r => server!.close(r))
  server = undefined
})

const post = (p: string, body: unknown) =>
  fetch(`${base}/design-api/${p}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
const sleep = (ms: number) => new Promise(r => setTimeout(r, ms))

describe('worker 预算与并发闸', () => {
  it('超预算 → 408 terminate，闸释放后小任务可跑', async () => {
    // 预算 1500ms：冷启动 tsx worker ≈0.6s（dev/test 特有），须容得下小任务；慢任务 6s+ 仍必超时
    await start(1500)
    const res = await post('run', { gameText: slowGame, configText: slowConfig })
    expect(res.status).toBe(408)
    expect((await res.json()).error).toContain('降低')
    const small = await post('run', { gameText: miniGame, configText: smallDiceConfig })
    expect(small.status).toBe(200)
  }, 30000)

  it('并发闸 1：第二发 503 + Retry-After，期间 health/samples 不被阻塞', async () => {
    await start(1500)
    const p1 = post('run', { gameText: slowGame, configText: slowConfig })
    await sleep(150)
    const second = await post('run', { gameText: miniGame, configText: smallDiceConfig })
    expect(second.status).toBe(503)
    expect(second.headers.get('Retry-After')).toBe('1')
    expect((await fetch(`${base}/design-api/health`)).status).toBe(200)
    expect((await fetch(`${base}/design-api/samples`)).status).toBe(200)
    const first = await p1
    expect(first.status).toBe(408)
  }, 30000)

  it('worker 业务错误 → 400 原消息点名行动', async () => {
    await start(5000)
    const res = await post('run', { gameText: selfSustainingGame, configText: ecoConfig })
    expect(res.status).toBe(400)
    expect((await res.json()).error).toContain('free')
  }, 30000)

  it('worker 堆上限 resourceLimits：堆 env=1MB → 500 且闸释放（OOM 降级为 worker 死亡，Final #5）', async () => {
    await start(5000)
    process.env.DESIGN_SIM_HEAP_MB = '1'
    try {
      const res = await post('run', { gameText: miniGame, configText: smallDiceConfig })
      expect(res.status).toBe(500)
    } finally {
      delete process.env.DESIGN_SIM_HEAP_MB
    }
    const small = await post('run', { gameText: miniGame, configText: smallDiceConfig })
    expect(small.status).toBe(200)
  }, 30000)

  it('小任务经 worker 正常返回', async () => {
    await start(5000)
    const res = await post('run', { gameText: miniGame, configText: smallDiceConfig })
    expect(res.status).toBe(200)
    expect(typeof (await res.json()).result.exact.probability).toBe('number')
  }, 30000)
})