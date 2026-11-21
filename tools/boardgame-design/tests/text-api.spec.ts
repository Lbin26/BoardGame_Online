import { describe, it, expect, afterEach } from 'vitest'
import { readFileSync, writeFileSync, unlinkSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { validateDoc, validateText } from '../src/schema/load'
import { resolveParams, resolveParamsFromText } from '../src/schema/simConfig'

const fx = (n: string) => fileURLToPath(new URL(`./fixtures/${n}`, import.meta.url))
const fixturesDir = fileURLToPath(new URL('./fixtures', import.meta.url))
const tmpFiles: string[] = []

afterEach(() => {
  for (const p of tmpFiles.splice(0)) if (existsSync(p)) unlinkSync(p)
})

describe('纯文本入口（网页工作台用）', () => {
  it('validateText(mini-game 文本) 与 validateDoc(文件) deep-equal', () => {
    const p = fx('mini-game.yaml')
    const r = validateText(readFileSync(p, 'utf-8'), p)
    expect(r.ok).toBe(true)
    expect(r).toEqual(validateDoc(p))
  })
  it('validateText(mini-sample 文本) 与 validateDoc(文件) deep-equal', () => {
    const p = fx('mini-sample.yaml')
    const r = validateText(readFileSync(p, 'utf-8'), p)
    expect(r.ok).toBe(true)
    expect(r).toEqual(validateDoc(p))
  })
  it('坏机制文本 → ok:false 点名 key，且与同内容文件版 deep-equal', () => {
    const base = readFileSync(fx('mini-game.yaml'), 'utf-8').replace(/^﻿/, '').replace(/\r\n/g, '\n')
    const bad = base.replace('- worker-placement', '- not-a-mechanism')
    const tmp = fx('.tmp-text-api-bad.yaml')
    writeFileSync(tmp, bad, 'utf-8')
    tmpFiles.push(tmp)
    const r = validateText(bad, tmp)
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.errors.join(' ')).toContain('not-a-mechanism')
    expect(r).toEqual(validateDoc(tmp))
  })
  it('空文本/纯字符串 → ok:false 不 throw', () => {
    expect(validateText('').ok).toBe(false)
    expect(validateText('纯字符串').ok).toBe(false)
  })
  it('resolveParamsFromText 与 resolveParams deep-equal（含 overrides 与默认 seed/runs）', () => {
    const config = { game: 'mini-game.yaml', sim: 'economy' as const, from: 'subsystems.economy', overrides: { turns: 5 } }
    const text = readFileSync(fx('mini-game.yaml'), 'utf-8')
    const r = resolveParamsFromText(text, config)
    expect(r.seed).toBe(42)
    expect(r.runs).toBe(10000)
    expect(r.params.turns).toBe(5)
    expect(r).toEqual(resolveParams(config, fixturesDir))
  })
  it('非法 YAML → throw；from 路径不存在 → throw 含路径', () => {
    const config = { game: 'mini-game.yaml', sim: 'economy' as const, from: 'subsystems.economy' }
    expect(() => resolveParamsFromText('不是: [合法', config)).toThrow()
    const text = readFileSync(fx('mini-game.yaml'), 'utf-8')
    expect(() => resolveParamsFromText(text, { ...config, from: 'subsystems.nope' })).toThrow(/路径不存在/)
  })
})