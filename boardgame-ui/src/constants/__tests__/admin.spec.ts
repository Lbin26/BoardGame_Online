import { describe, expect, it } from 'vitest'
import { isSuperAdmin } from '@/constants/admin'

describe('isSuperAdmin', () => {
  it('recognizes 欧洲小跪族 by userName or nickName', () => {
    expect(isSuperAdmin('欧洲小跪族', undefined)).toBe(true)
    expect(isSuperAdmin('some-login', '欧洲小跪族')).toBe(true)
  })
  it('rejects normal users and empty names', () => {
    expect(isSuperAdmin('alice', '小爱')).toBe(false)
    expect(isSuperAdmin(undefined, undefined)).toBe(false)
  })
})
