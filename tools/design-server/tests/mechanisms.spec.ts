import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { parseMechanismsMd } from '../src/mechanisms.js'
import { resolvePaths } from '../src/paths.js'
import { loadVocabulary } from '../../boardgame-design/src/schema/vocabulary.js'

const realMd = () => readFileSync(resolvePaths().mechanismsMdPath, 'utf-8')

describe('parseMechanismsMd（格式由测试钉死）', () => {
  it('真实 md → 42 条，key 集合与词汇表完全相等', () => {
    const list = parseMechanismsMd(realMd())
    expect(list.length).toBe(42)
    expect(new Set(list.map(m => m.key))).toEqual(new Set(loadVocabulary().map(m => m.key)))
  })
  it('首条 simultaneous-action 五要素齐全', () => {
    const first = parseMechanismsMd(realMd())[0]
    expect(first.key).toBe('simultaneous-action')
    expect(first.name).toBe('同时行动')
    expect(first.nameEn).toBe('Simultaneous Action Selection')
    expect(first.zone).toBe('轻策/派对')
    expect(first.elements['说明'].startsWith('所有玩家同时暗选')).toBe(true)
    for (const k of ['说明', '代表作', '设计旋钮', '常见坑', '常见组合'] as const) {
      expect(first.elements[k].length).toBeGreaterThan(0)
    }
  })
  it('缺要素 → throw 点名 key', () => {
    const md = '## 测试区\n### test-mech · 测试 (Test)\n- 说明：x\n- 代表作：y\n- 设计旋钮：z\n- 常见组合：w\n'
    expect(() => parseMechanismsMd(md)).toThrow(/test-mech/)
  })
  it('空文本 → throw', () => {
    expect(() => parseMechanismsMd('')).toThrow()
  })
})