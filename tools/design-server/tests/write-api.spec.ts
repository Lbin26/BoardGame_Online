import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { AddressInfo } from 'node:net'
import type http from 'node:http'
import { createApp } from '../src/app.js'

const gameText = readFileSync(
  fileURLToPath(new URL('../../../tools/boardgame-design/tests/fixtures/mini-game.yaml', import.meta.url)),
  'utf-8',
)

let server: http.Server
let base = ''

beforeAll(async () => {
  server = createApp()
  await new Promise<void>(r => server.listen(0, '127.0.0.1', r))
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`
})
afterAll(async () => { await new Promise(r => server.close(r)) })

const post = (path: string, body: unknown) =>
  fetch(`${base}/design-api/${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: typeof body === 'string' ? body : JSON.stringify(body),
  })

const cfg = (sim: string, extra = '') =>
  `game: mini-game.yaml\nsim: ${sim}\nfrom: subsystems.economy\nseed: 42\nruns: 10000\n${extra}`

const DICE_OVERRIDES = `overrides:\n  count: 2\n  faces: [1, 2, 3, 4, 5, 6]\n  symbol: 6\n  op: '>='\n  value: 10\n`

describe('POST /run /sweep /validate（真实 HTTP）', () => {
  it('run economy（static 投影）→ 200，rows/seed/runs/md/json', async () => {
    const res = await post('run', { gameText, configText: cfg('economy', 'overrides:\n  turns: 3\n  policy:\n    type: static\n') })
    expect(res.status).toBe(200)
    const body = await res.json()
    expect(body.result.rows.length).toBe(3)
    expect(body.seed).toBe(42)
    expect(body.runs).toBe(10000)
    expect(body.md).toContain('# economy 模拟报告')
    expect(() => JSON.parse(body.json)).not.toThrow()
  })
  it('run dice（overrides 注入，zod 剥离 economy 键）→ exact.probability', async () => {
    const res = await post('run', { gameText, configText: cfg('dice', DICE_OVERRIDES) })
    expect(res.status).toBe(200)
    const body = await res.json()
    expect(typeof body.result.exact.probability).toBe('number')
  })
  it('run conflict / cards → 200', async () => {
    const conflictCfg = cfg('conflict', `overrides:\n  attacker: { diceCount: 2, faces: [1,2,3,4,5,6], hitSymbols: [6], hp: 5 }\n  defender: { diceCount: 2, faces: [1,2,3,4,5,6], hitSymbols: [6], hp: 5 }\n`)
    const r1 = await post('run', { gameText, configText: conflictCfg })
    expect(r1.status).toBe(200)
    expect(typeof (await r1.json()).result.attackerWinRate).toBe('number')
    const cardsCfg = cfg('cards', `overrides:\n  deck: [{ key: a, count: 3 }]\n  draw: 2\n  target: a\n  op: '>='\n  value: 1\n`)
    const r2 = await post('run', { gameText, configText: cardsCfg })
    expect(r2.status).toBe(200)
    expect(typeof (await r2.json()).result.probability).toBe('number')
  })
  it('sweep economy turns [3,5] → rows 2 + 扫描报告', async () => {
    const res = await post('sweep', { gameText, configText: cfg('economy', 'sweep:\n  param: turns\n  values: [3, 5]\n') })
    expect(res.status).toBe(200)
    const body = await res.json()
    expect(body.rows.length).toBe(2)
    expect(body.md).toContain('# 参数扫描报告')
    expect(() => JSON.parse(body.json)).not.toThrow()
  })
  it('validate：合法 ok:true errors:[]；坏机制 ok:false；text 非 string → 400', async () => {
    const ok = await post('validate', { text: gameText })
    expect(await ok.json()).toEqual({ ok: true, errors: [] })
    const bad = await post('validate', { text: gameText.replace('- worker-placement', '- not-a-mechanism') })
    const badBody = await bad.json()
    expect(badBody.ok).toBe(false)
    expect(badBody.errors.join(' ')).toContain('not-a-mechanism')
    expect((await post('validate', { text: 123 })).status).toBe(400)
  })
  it('400 矩阵：畸形输入与全部上限（含 overrides/sweep 推高）', async () => {
    expect((await post('run', 'not json')).status).toBe(400)
    expect((await post('run', { configText: cfg('dice') })).status).toBe(400)
    expect((await post('run', { gameText, configText: 'game: [unclosed' })).status).toBe(400)
    expect((await post('run', { gameText, configText: cfg('nope') })).status).toBe(400)
    expect((await post('run', { gameText, configText: cfg('dice', DICE_OVERRIDES).replace('runs: 10000', 'runs: 50001') })).status).toBe(400)
    expect((await post('run', { gameText, configText: cfg('dice', DICE_OVERRIDES.replace('count: 2', 'count: 201')) })).status).toBe(400)
    expect((await post('run', { gameText, configText: cfg('economy', 'overrides:\n  turns: 501\n') })).status).toBe(400)
    const values21 = Array.from({ length: 21 }, (_, i) => i + 1).join(', ')
    expect((await post('sweep', { gameText, configText: cfg('economy', `sweep:\n  param: turns\n  values: [${values21}]\n`) })).status).toBe(400)
    const escalate = await post('sweep', { gameText, configText: cfg('dice', DICE_OVERRIDES + 'sweep:\n  param: count\n  values: [2, 201]\n') })
    expect(escalate.status).toBe(400)
    expect((await escalate.json()).error).toContain('201')
    expect((await post('validate', { text: 'x'.repeat(600 * 1024) })).status).toBe(400)
    const proto = await post('run', { gameText, configText: cfg('dice', DICE_OVERRIDES + '  __proto__:\n    polluted: 1\n') })
    expect(proto.status).toBe(400)
    expect((Object.prototype as any).polluted).toBeUndefined()
    expect(({} as any).polluted).toBeUndefined()
  })
  it('危险键绕过形态 → 400：flow/引号/gameText/validate（Final #4）', async () => {
    const flow = await post('run', { gameText, configText: cfg('dice', 'overrides: {__proto__: {polluted: 1}}') })
    expect(flow.status).toBe(400)
    const quoted = await post('run', { gameText, configText: cfg('dice', 'overrides:\n  "__proto__":\n    polluted: 1\n') })
    expect(quoted.status).toBe(400)
    const gameFlow = await post('run', { gameText: gameText + '\nextra: {__proto__: {polluted: 1}}\n', configText: cfg('dice', DICE_OVERRIDES) })
    expect(gameFlow.status).toBe(400)
    const val = await post('validate', { text: 'meta:\n  name: x\n__proto__:\n  polluted: 1\n' })
    expect(val.status).toBe(400)
    expect((Object.prototype as any).polluted).toBeUndefined()
    expect(({} as any).polluted).toBeUndefined()
  })
  it('cards 巨 deck/draw → 400 点名上限且进程存活（Final #5：logFact OOM 护栏）', async () => {
    const cardsCfg = (extra: string) => cfg('cards', `overrides:\n  deck: [{ key: a, count: 3 }]\n  draw: 2\n  target: a\n  op: '>='\n  value: 1\n${extra}`)
    const big = await post('run', { gameText, configText: cardsCfg('').replace('count: 3', 'count: 100000000') })
    expect(big.status).toBe(400)
    expect((await big.json()).error).toContain('100000')
    const bigDraw = await post('run', { gameText, configText: cardsCfg('').replace('draw: 2', 'draw: 100001') })
    expect(bigDraw.status).toBe(400)
    expect((await bigDraw.json()).error).toContain('100000')
    const alive = await fetch(`${base}/design-api/health`)
    expect(alive.status).toBe(200)
  })
  it('未知路径 POST → 404', async () => {
    expect((await post('nope', {})).status).toBe(404)
  })
})