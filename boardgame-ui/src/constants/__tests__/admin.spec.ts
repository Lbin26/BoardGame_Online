import { describe, expect, it } from 'vitest'
import { isSuperAdmin } from '@/constants/admin'

describe('isSuperAdmin', () => {
  it('recognizes super admin by database role group', () => {
    expect(isSuperAdmin('超级管理员')).toBe(true)
    expect(isSuperAdmin('普通用户,超级管理员')).toBe(true)
    expect(isSuperAdmin(' 超级管理员 ')).toBe(true)
  })
  it('rejects normal users and empty role group', () => {
    expect(isSuperAdmin('普通用户')).toBe(false)
    expect(isSuperAdmin('')).toBe(false)
    expect(isSuperAdmin(undefined)).toBe(false)
    expect(isSuperAdmin(null)).toBe(false)
  })
})
