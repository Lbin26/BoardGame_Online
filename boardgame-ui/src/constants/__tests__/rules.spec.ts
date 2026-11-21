import { describe, expect, it } from 'vitest'
import { findRuleBook } from '@/constants/rules'

describe('rule books', () => {
  it('has a rule book for both playable games with non-empty sections', () => {
    for (const key of ['splendor', 'xiangqi']) {
      const book = findRuleBook(key)
      expect(book, `missing rule book for ${key}`).toBeDefined()
      expect(book!.gameKey).toBe(key)
      expect(book!.title).toBeTruthy()
      expect(book!.sections.length).toBeGreaterThanOrEqual(3)
      for (const section of book!.sections) {
        expect(section.title).toBeTruthy()
        expect((section.paragraphs?.length ?? 0) + (section.bullets?.length ?? 0)).toBeGreaterThan(0)
      }
    }
  })

  it('splendor book covers setup, four actions, nobles and endgame', () => {
    const text = JSON.stringify(findRuleBook('splendor'))
    expect(text).toContain('开局设置')
    expect(text).toContain('预留一张发展卡')
    expect(text).toContain('贵族拜访')
    expect(text).toContain('15 VP')
  })

  it('returns undefined for unknown keys and non-string input', () => {
    expect(findRuleBook('go')).toBeUndefined()
    expect(findRuleBook('unknown')).toBeUndefined()
    expect(findRuleBook(undefined)).toBeUndefined()
    expect(findRuleBook(123)).toBeUndefined()
  })
})
