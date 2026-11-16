import { describe, it, expect } from 'vitest'

import { unwrapR } from '@/utils/request'

describe('unwrapR', () => {
  it('returns data when code is 200', () => {
    expect(unwrapR({ code: 200, msg: 'ok', data: { x: 1 } })).toEqual({ x: 1 })
  })

  it('throws Error(msg) when code is not 200', () => {
    expect(() => unwrapR({ code: 500, msg: '失败', data: null })).toThrow('失败')
  })
})
