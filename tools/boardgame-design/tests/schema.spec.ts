import { describe, it, expect, afterEach } from 'vitest'
import { readFileSync, writeFileSync, unlinkSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { loadGameDoc, loadSampleDoc, validateDoc } from '../src/schema/load'

const fx = (n: string) => fileURLToPath(new URL(`./fixtures/${n}`, import.meta.url))
const tmpFiles: string[] = []

/** 基于 mini-game fixture 造变体（去 BOM/统一 LF 后替换），返回临时文件路径 */
function variant(name: string, transform: (text: string) => string): string {
  const base = readFileSync(fx('mini-game.yaml'), 'utf-8').replace(/^﻿/, '').replace(/\r\n/g, '\n')
  const p = fx(`.tmp-${name}.yaml`)
  writeFileSync(p, transform(base), 'utf-8')
  tmpFiles.push(p)
  return p
}

afterEach(() => {
  for (const p of tmpFiles.splice(0)) if (existsSync(p)) unlinkSync(p)
})

describe('schema & loader', () => {
  it('解析含中文/BOM/CRLF 的设计文档，默认值生效', () => {
    const doc = loadGameDoc(fx('mini-game.yaml'))
    expect(doc.meta.name).toBe('迷你测试游戏')
    expect(doc.subsystems.economy!.stallK).toBe(3)
  })
  it('样本文档 analysis/zone 必填校验通过', () => {
    const s = loadSampleDoc(fx('mini-sample.yaml'))
    expect(s.analysis!.confidence).toBe('medium')
    expect(s.meta.zone).toBe('strategy')
  })
  it('validateDoc 对合法文档 ok:true', () => {
    expect(validateDoc(fx('mini-game.yaml')).ok).toBe(true)
    expect(validateDoc(fx('mini-sample.yaml')).ok).toBe(true)
  })
  it('缺必填字段 → ok:false 且错误提及字段', () => {
    const p = variant('no-name', t => t.replace('  name: 迷你测试游戏\n', ''))
    const r = validateDoc(p)
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.errors.join(' ')).toMatch(/name/)
  })
  it('未知机制 key → ok:false 且错误列出该 key', () => {
    const p = variant('bad-mech', t => t.replace('- worker-placement', '- not-a-mechanism'))
    const r = validateDoc(p)
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.errors.join(' ')).toContain('not-a-mechanism')
  })
  it('economy 引用未声明资源 → ok:false', () => {
    const p = variant('bad-res', t => t.replace('resource: gold', 'resource: silver'))
    const r = validateDoc(p)
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.errors.join(' ')).toContain('silver')
  })
  it('C1：负数资源值（cost/produce/initial）→ ok:false', () => {
    const p1 = variant('neg-cost', t => t.replace('cost: { gold: 3 }', 'cost: { gold: -3 }'))
    const r1 = validateDoc(p1)
    expect(r1.ok).toBe(false)
    const p2 = variant('neg-produce', t => t.replace('produce: 2', 'produce: -2'))
    expect(validateDoc(p2).ok).toBe(false)
    const p3 = variant('neg-initial', t => t.replace('initial: { gold: 5 }', 'initial: { gold: -5 }'))
    expect(validateDoc(p3).ok).toBe(false)
  })
  it('文件不存在 → loadGameDoc throw 含路径', () => {
    expect(() => loadGameDoc('no/such/file.yaml')).toThrow(/no.such.file.yaml/)
  })
})