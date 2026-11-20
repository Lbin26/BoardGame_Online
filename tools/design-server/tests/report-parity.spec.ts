import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'
import type { AddressInfo } from 'node:net'
import type http from 'node:http'
import { createApp } from '../src/app.js'

const toolboxDir = path.resolve(fileURLToPath(new URL('../../../tools/boardgame-design', import.meta.url)))
const gameText = readFileSync(path.join(toolboxDir, 'tests/fixtures/mini-game.yaml'), 'utf-8')

let server: http.Server
let base = ''
const tmpDirs: string[] = []

beforeAll(async () => {
  server = createApp()
  await new Promise<void>(r => server.listen(0, '127.0.0.1', r))
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`
})
afterAll(async () => { await new Promise(r => server.close(r)) })
afterEach(() => { for (const d of tmpDirs.splice(0)) rmSync(d, { recursive: true, force: true }) })

/** 跑 CLI 落盘报告，返回 md/json 文件内容 */
function cliReport(sim: 'dice' | 'sweep', configText: string): { md: string; json: string } {
  const dir = mkdtempSync(path.join(tmpdir(), 'parity-'))
  tmpDirs.push(dir)
  writeFileSync(path.join(dir, 'mini-game.yaml'), gameText, 'utf-8')
  const cfgPath = path.join(dir, 'config.yaml')
  writeFileSync(cfgPath, configText, 'utf-8')
  const out = path.join(dir, 'r.md')
  execFileSync(process.execPath, ['--import', 'tsx', path.join(toolboxDir, 'src/cli.ts'), sim, '--config', cfgPath, '--out', out], { cwd: toolboxDir, encoding: 'utf-8' })
  return { md: readFileSync(out, 'utf-8'), json: readFileSync(out.replace(/\.md$/, '.json'), 'utf-8') }
}

const post = (p: string, body: unknown) =>
  fetch(`${base}/design-api/${p}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })

describe('网页报告与 CLI 逐位一致（证据口径）', () => {
  it('dice：/run 的 md/json 与 CLI 落盘全等', async () => {
    const configText = `game: mini-game.yaml\nsim: dice\nfrom: subsystems.economy\nseed: 42\nruns: 1000\noverrides:\n  count: 2\n  faces: [1, 2, 3, 4, 5, 6]\n  symbol: 6\n  op: '>='\n  value: 10\n`
    const cli = cliReport('dice', configText)
    const res = await post('run', { gameText, configText })
    expect(res.status).toBe(200)
    const body = await res.json()
    expect(body.md).toBe(cli.md)
    expect(body.json).toBe(cli.json)
  })
  it('sweep：/sweep 的 md/json 与 CLI 落盘全等', async () => {
    const configText = `game: mini-game.yaml\nsim: economy\nfrom: subsystems.economy\nseed: 42\nruns: 1000\nsweep:\n  param: turns\n  values: [3, 5]\n`
    const cli = cliReport('sweep', configText)
    const res = await post('sweep', { gameText, configText })
    expect(res.status).toBe(200)
    const body = await res.json()
    expect(body.md).toBe(cli.md)
    expect(body.json).toBe(cli.json)
  })
})