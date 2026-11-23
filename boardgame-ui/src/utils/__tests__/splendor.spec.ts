import { describe, expect, it } from 'vitest'

import {
  computePayment,
  describeAction,
  gemTotal,
  isTakeGemsValid,
  pendingLabel,
  takeTotal,
  type GemMap,
} from '@/utils/splendor'

const fullBank: GemMap = { white: 4, blue: 4, green: 4, red: 4, black: 4, gold: 5 }

describe('takeTotal / gemTotal', () => {
  it('只统计五色，不含黄金', () => {
    expect(takeTotal({ white: 2 })).toBe(2)
    expect(takeTotal({ white: 1, blue: 1, gold: 1 })).toBe(2)
  })

  it('gemTotal 含黄金', () => {
    expect(gemTotal({ white: 2, blue: 1, gold: 2 })).toBe(5)
    expect(gemTotal({})).toBe(0)
  })
})

describe('isTakeGemsValid', () => {
  it('三异色各 1 合法', () => {
    expect(isTakeGemsValid({ white: 1, blue: 1, red: 1 }, fullBank).ok).toBe(true)
  })

  it('同色 2 且供给 ≥4 合法', () => {
    expect(isTakeGemsValid({ white: 2 }, fullBank).ok).toBe(true)
  })

  it('同色 2 但供给 <4 非法', () => {
    expect(isTakeGemsValid({ white: 2 }, { ...fullBank, white: 3 }).ok).toBe(false)
  })

  it('同色 3 非法', () => {
    expect(isTakeGemsValid({ white: 3 }, fullBank).ok).toBe(false)
  })

  it('总数超过 3 非法', () => {
    expect(isTakeGemsValid({ white: 2, blue: 2 }, fullBank).ok).toBe(false)
  })

  it('3 颗但只有两色非法（非三异色）', () => {
    expect(isTakeGemsValid({ white: 2, blue: 1 }, fullBank).ok).toBe(false)
  })

  it('银行短缺时的两异色各 1 允许发出（由引擎终审）', () => {
    const shortBank: GemMap = { white: 1, blue: 1, green: 1, red: 0, black: 0, gold: 0 }
    const r = isTakeGemsValid({ white: 1, blue: 1 }, shortBank)
    expect(r.ok).toBe(true)
  })

  it('空选非法', () => {
    expect(isTakeGemsValid({}, fullBank).ok).toBe(false)
  })
})

describe('computePayment', () => {
  const card = { cost: { white: 3, blue: 2 }, bonus: 'black' }

  it('加成全额抵扣时免付', () => {
    const player = { gems: {}, bonuses: { white: 3, blue: 2 } }
    const r = computePayment(card, player)
    expect(r).not.toBeNull()
    expect(r!.paid).toEqual({})
    expect(r!.goldUsed).toBe(0)
  })

  it('宝石实付 + 黄金补差', () => {
    const player = { gems: { white: 2, blue: 1, gold: 2 }, bonuses: {} }
    const r = computePayment(card, player)
    expect(r).not.toBeNull()
    expect(r!.paid).toEqual({ white: 2, blue: 1 })
    expect(r!.goldUsed).toBe(2)
  })

  it('付不起返回 null', () => {
    expect(computePayment(card, { gems: { gold: 1 }, bonuses: {} })).toBeNull()
  })
})

describe('describeAction 中文战报', () => {
  it('买卡显示等级、加成色与声望，不再是纯 cardId', () => {
    const text = describeAction('BUY_CARD', {
      cardId: 'L2-B-03', source: 'table', level: 2, bonus: 'blue', vp: 3,
    })
    expect(text).toContain('二级')
    expect(text).toContain('蓝卡')
    expect(text).toContain('+3 声望')
    expect(text).not.toContain('L2-B-03')
  })

  it('预留明卡同样可读，盲留显示中文等级', () => {
    expect(describeAction('RESERVE_CARD', {
      cardId: 'L1-W-01', level: 1, bonus: 'white',
    })).toContain('一级白卡')
    expect(describeAction('RESERVE_CARD', { level: 3 })).toContain('三级牌堆顶')
  })

  it('贵族拜访优先显示中文名', () => {
    expect(describeAction('NOBLE_SELECT', { nobleId: 'N1', nobleName: '路易十四', vp: 3 }))
      .toContain('路易十四')
  })

  it('旧对局缺新字段时回退到 cardId，不抛错', () => {
    expect(describeAction('BUY_CARD', { cardId: 'L1-W-01', vp: 1 })).toContain('L1-W-01')
  })
})

describe('pendingLabel', () => {
  it('null 返回空', () => {
    expect(pendingLabel(null, true)).toBe('')
  })

  it('本人与他人视角文案不同', () => {
    expect(pendingLabel('NOBLE_SELECT', true)).toContain('选择')
    expect(pendingLabel('NOBLE_SELECT', false)).toContain('正在')
    expect(pendingLabel('DISCARD', true)).toContain('弃掉')
  })
})
