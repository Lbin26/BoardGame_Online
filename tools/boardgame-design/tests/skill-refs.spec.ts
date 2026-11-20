import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { loadVocabulary } from '../src/schema/vocabulary'

const dir = fileURLToPath(new URL('../../../.claude/skills/design-boardgame/references/', import.meta.url))

describe('skill references', () => {
  it('mechanisms.md 覆盖每个词汇 key', () => {
    const md = readFileSync(dir + 'mechanisms.md', 'utf-8')
    for (const e of loadVocabulary()) expect(md).toContain(e.key)
  })
  it('design-rubric.md 含全部红旗条目', () => {
    const md = readFileSync(dir + 'design-rubric.md', 'utf-8')
    for (const k of ['滚雪球','国王制造','先手优势','downtime','分析瘫痪','人数缩放','资源卡死','随机性淹没决策','主题与机制脱节']) expect(md).toContain(k)
  })
  it('rulebook-template.md 含全部章节', () => {
    const md = readFileSync(dir + 'rulebook-template.md', 'utf-8')
    for (const k of ['概述','配件清单','设置','回合流程','结算细节','结束与计分','变体与FAQ','快速参考']) expect(md).toContain(k)
  })
})