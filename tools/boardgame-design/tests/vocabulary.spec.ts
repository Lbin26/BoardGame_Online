import { describe, it, expect } from 'vitest'
import { ZONES, GAMES } from '../../../boardgame-ui/src/constants/catalog'
import { loadVocabulary, coveredTags, assertCatalogCoverage } from '../src/schema/vocabulary'

const catalogTags = [...new Set([...ZONES.flatMap(z => z.mechanisms), ...GAMES.flatMap(g => g.mechanisms ?? [])])]

describe('mechanism vocabulary', () => {
  const entries = loadVocabulary()
  it('覆盖 catalog 全部标签', () => { expect(() => assertCatalogCoverage(entries, catalogTags)).not.toThrow() })
  it('key 唯一、kebab-case，zones 均在 7 区内且非空', () => {
    const keys = entries.map(e => e.key)
    expect(new Set(keys).size).toBe(keys.length)
    for (const e of entries) {
      expect(e.key).toMatch(/^[a-z][a-z0-9]*(-[a-z0-9]+)*$/)
      expect(e.zones.length).toBeGreaterThan(0)
      for (const z of e.zones) expect(ZONES.map(zz => zz.key)).toContain(z)
    }
  })
  it('含 10 个指定扩展机制与钉死的样本 key，条目总数 30~45', () => {
    for (const k of ['auction','secret-objectives','progress-track','dice-placement','pick-up-and-deliver','stock-holding','time-track','variable-player-powers','grid-movement','push-your-luck',
                     'abstract-chess','pattern-building','logical-deduction','engine-building','set-collection','dice-driven','hidden-roles','voting','network-building','economic-management','hand-management','worker-placement','card-assist'])
      expect(entries.map(e => e.key)).toContain(k)
    expect(entries.length).toBeGreaterThanOrEqual(30)
    expect(entries.length).toBeLessThanOrEqual(45)
  })
  it('aliases 覆盖推理/演绎、手牌管理（轻卡驱）、角色能力（不对称）', () => {
    const tags = coveredTags(entries)
    for (const t of ['推理/演绎','手牌管理（轻卡驱）','角色能力（不对称）']) expect(tags.has(t)).toBe(true)
  })
  it('assertCatalogCoverage 对缺失标签 throw 且列出缺失项', () => {
    expect(() => assertCatalogCoverage(entries, ['不存在的机制'])).toThrow(/不存在的机制/)
  })
})