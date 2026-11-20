import { describe, it, expect, afterEach } from 'vitest'
import { readFileSync, readdirSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { runSim, writeReport, renderReport } from '../src/report'
import { resolveParamsFromText } from '../src/schema/simConfig'
import { runSweep, runSweepFromText } from '../src/sim/sweep'

const fx = (n: string) => fileURLToPath(new URL(`./fixtures/${n}`, import.meta.url))
const fixturesDir = fileURLToPath(new URL('./fixtures', import.meta.url))
const gameText = () => readFileSync(fx('mini-game.yaml'), 'utf-8')
const tmpDirs: string[] = []
const tmp = () => {
  const d = mkdtempSync(path.join(tmpdir(), 'report-text-'))
  tmpDirs.push(d)
  return d
}

afterEach(() => {
  for (const d of tmpDirs.splice(0)) rmSync(d, { recursive: true, force: true })
})

describe('renderReport / runSweepFromText', () => {
  it('renderReport 与 writeReport 落盘内容逐位一致', () => {
    const config = { game: 'mini-game.yaml', sim: 'economy' as const, from: 'subsystems.economy' }
    const { params } = resolveParamsFromText(gameText(), config)
    const result = runSim('economy', params, 42, 10000)
    const data = { title: 'economy 模拟报告', config, params, result }
    const out = path.join(tmp(), 'r.md')
    const { mdPath, jsonPath } = writeReport(out, data)
    const rendered = renderReport(data)
    expect(rendered.md).toBe(readFileSync(mdPath, 'utf-8'))
    expect(rendered.json).toBe(readFileSync(jsonPath, 'utf-8'))
  })
  it('renderReport 是纯函数：不落盘', () => {
    const dir = tmp()
    const config = { game: 'mini-game.yaml', sim: 'economy' as const, from: 'subsystems.economy' }
    const { params } = resolveParamsFromText(gameText(), config)
    const result = runSim('economy', params, 42, 10000)
    const before = readdirSync(dir)
    renderReport({ title: 't', config, params, result })
    expect(readdirSync(dir)).toEqual(before)
  })
  it('runSweepFromText 与 runSweep deep-equal，baseParams 不被污染', () => {
    const config = {
      game: 'mini-game.yaml', sim: 'economy' as const, from: 'subsystems.economy',
      sweep: { param: 'turns', values: [3, 5] },
    }
    const fromFile = runSweep(config, fixturesDir)
    const fromText = runSweepFromText(gameText(), config)
    expect(fromText.rows.length).toBe(2)
    expect(fromText).toEqual(fromFile)
    expect(fromText.baseParams.turns).toBeUndefined()
  })
})