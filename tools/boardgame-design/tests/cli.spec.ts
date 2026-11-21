import { describe, it, expect, afterEach } from 'vitest'
import { execFileSync } from 'node:child_process'
import { writeFileSync, readFileSync, existsSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const pkgRoot = fileURLToPath(new URL('..', import.meta.url))
const cli = join(pkgRoot, 'src', 'cli.ts')
const miniGame = fileURLToPath(new URL('./fixtures/mini-game.yaml', import.meta.url))
const tmpDirs: string[] = []
const tmp = () => { const d = mkdtempSync(join(tmpdir(), 'bgd-cli-')); tmpDirs.push(d); return d }
afterEach(() => { for (const d of tmpDirs.splice(0)) rmSync(d, { recursive: true, force: true }) })

function run(args: string[]): { status: number; stdout: string; stderr: string } {
  try {
    const stdout = execFileSync(process.execPath, ['--import', 'tsx', cli, ...args], { cwd: pkgRoot, encoding: 'utf-8' })
    return { status: 0, stdout, stderr: '' }
  } catch (e) {
    const err = e as { status?: number; stdout?: string; stderr?: string }
    return { status: err.status ?? -1, stdout: err.stdout ?? '', stderr: err.stderr ?? '' }
  }
}

describe('cli', () => {
  it('validate 合法文档 → OK + exit 0', () => {
    const r = run(['validate', miniGame])
    expect(r.status).toBe(0)
    expect(r.stdout).toContain('OK')
  })
  it('validate 坏文档 → exit 1 + stderr 非空', () => {
    const d = tmp()
    const bad = join(d, 'bad.yaml')
    writeFileSync(bad, 'schemaVersion: 1\n', 'utf-8')
    const r = run(['validate', bad])
    expect(r.status).toBe(1)
    expect(r.stderr.length).toBeGreaterThan(0)
  })
  it('dice --config → 缺省报告生成于配置旁，stdout 含路径，md 含 seed', () => {
    const d = tmp()
    const cfg = join(d, 'dice.yaml')
    writeFileSync(cfg, `game: ${miniGame}\nsim: dice\nfrom: components.dice.0\noverrides:\n  symbol: 6\n  op: ">="\n  value: 1\n`, 'utf-8')
    const r = run(['dice', '--config', cfg])
    expect(r.status).toBe(0)
    const report = join(d, 'dice-report.md')
    expect(existsSync(report)).toBe(true)
    expect(r.stdout).toContain(report)
    expect(readFileSync(report, 'utf-8')).toContain('seed')
  })
  it('sweep --config → sweep-report.md，rows 数与 values 一致', () => {
    const d = tmp()
    const cfg = join(d, 'sweep.yaml')
    writeFileSync(cfg, `game: ${miniGame}\nsim: economy\nfrom: subsystems.economy\noverrides:\n  turns: 2\n  policy:\n    type: greedy\nsweep:\n  param: initial.gold\n  values: [0, 5, 10]\n`, 'utf-8')
    const r = run(['sweep', '--config', cfg])
    expect(r.status).toBe(0)
    const json = JSON.parse(readFileSync(join(d, 'sweep-report.json'), 'utf-8'))
    expect(json.result.rows.length).toBe(3)
  })
  it('economy config.out 自定义报告路径（相对配置目录）→ 报告落该路径，缺省名不生成', () => {
    const d = tmp()
    const cfg = join(d, 'eco.yaml')
    writeFileSync(cfg, `game: ${miniGame}\nsim: economy\nfrom: subsystems.economy\noverrides:\n  turns: 2\n  policy:\n    type: greedy\nout: custom.md\n`, 'utf-8')
    const r = run(['economy', '--config', cfg])
    expect(r.status).toBe(0)
    const custom = join(d, 'custom.md')
    expect(existsSync(custom)).toBe(true)
    expect(r.stdout).toContain(custom)
    expect(existsSync(join(d, 'economy-report.md'))).toBe(false)
  })
  it('sweep config.out 自定义报告路径 → 报告落该路径，缺省名不生成', () => {
    const d = tmp()
    const cfg = join(d, 'sw.yaml')
    writeFileSync(cfg, `game: ${miniGame}\nsim: economy\nfrom: subsystems.economy\noverrides:\n  turns: 2\n  policy:\n    type: greedy\nsweep:\n  param: initial.gold\n  values: [0, 5]\nout: sw-custom.md\n`, 'utf-8')
    const r = run(['sweep', '--config', cfg])
    expect(r.status).toBe(0)
    expect(existsSync(join(d, 'sw-custom.md'))).toBe(true)
    expect(existsSync(join(d, 'sweep-report.md'))).toBe(false)
  })
  it('ad-hoc dice → 首行 AD-HOC 标注，概率≈0.3056，不产生报告文件', () => {
    const d = tmp()
    const r = run(['dice', '--faces', '1,2,3,4,5,6', '--count', '2', '--symbol', '6', '--op', '>=', '--value', '1'])
    expect(r.status).toBe(0)
    expect(r.stdout.split('\n')[0]).toContain('AD-HOC（非证据）')
    expect(r.stdout).toContain('0.3056')
    expect(existsSync(join(dirname(cli), 'dice-report.md'))).toBe(false)
    expect(existsSync(join(d, 'dice-report.md'))).toBe(false)
  })
  it('配置文件不存在 → exit 2', () => {
    const r = run(['dice', '--config', 'no-such-config.yaml'])
    expect(r.status).toBe(2)
  })
  it('I6：validate 文件不存在 → exit 2（契约：文件不存在 2，而非 1）', () => {
    const r = run(['validate', 'no-such-doc.yaml'])
    expect(r.status).toBe(2)
    expect(r.stderr.length).toBeGreaterThan(0)
  })
  it('I1：ad-hoc dice --value 非数字 → exit 1 + stderr 点名 value（而非 probability 0 假证据）', () => {
    const r = run(['dice', '--faces', '1,2,3,4,5,6', '--count', '2', '--symbol', '6', '--op', '>=', '--value', 'abc'])
    expect(r.status).toBe(1)
    expect(r.stderr).toMatch(/value/)
  })
  it('I1：ad-hoc cards --deck count 非数字 → exit 1（而非 expectedCount NaN）', () => {
    const r = run(['cards', '--deck', 'silk:abc', '--draw', '2', '--target', 'silk', '--op', '>=', '--value', '1'])
    expect(r.status).toBe(1)
    expect(r.stderr.length).toBeGreaterThan(0)
  })
})