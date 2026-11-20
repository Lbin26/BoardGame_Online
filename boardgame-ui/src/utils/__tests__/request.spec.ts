import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import {
  TOKEN_KEY,
  attachAuthToken,
  rawResponse,
  unwrapR,
  unwrapResponse,
} from '@/utils/request'
import { CLIENT_ID } from '@/constants/client'

describe('unwrapR', () => {
  it('returns data when code is 200', () => {
    expect(unwrapR({ code: 200, msg: 'ok', data: { x: 1 } })).toEqual({ x: 1 })
  })

  it('throws Error(msg) when code is not 200', () => {
    expect(() => unwrapR({ code: 500, msg: '失败', data: null })).toThrow('失败')
  })
})

describe('attachAuthToken', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => localStorage.clear())

  function fakeConfig() {
    return { headers: {} } as any
  }

  it('always injects clientid header even without token', () => {
    const config = attachAuthToken(fakeConfig())
    expect(config.headers.clientid).toBe(CLIENT_ID)
    expect(config.headers.Authorization).toBeUndefined()
  })

  it('injects Authorization Bearer when token present', () => {
    localStorage.setItem(TOKEN_KEY, 'tok123')
    const config = attachAuthToken(fakeConfig())
    expect(config.headers.clientid).toBe(CLIENT_ID)
    expect(config.headers.Authorization).toBe('Bearer tok123')
  })
})

describe('envelope 401 handling', () => {
  const realLocation = window.location

  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem(TOKEN_KEY, 'stale-token')
    Object.defineProperty(window, 'location', {
      value: { href: '', pathname: '/game/ABC123', search: '' },
      configurable: true,
      writable: true,
    })
  })

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      value: realLocation,
      configurable: true,
      writable: true,
    })
    localStorage.clear()
  })

  it('unwrapResponse returns data on code 200 without redirect', () => {
    const out = unwrapResponse({ data: { code: 200, msg: 'ok', data: { x: 1 } } } as any)
    expect(out).toEqual({ x: 1 })
    expect(localStorage.getItem(TOKEN_KEY)).toBe('stale-token')
    expect(window.location.href).toBe('')
  })

  it('unwrapResponse throws msg on code 500 without redirect', () => {
    expect(() =>
      unwrapResponse({ data: { code: 500, msg: '失败', data: null } } as any),
    ).toThrow('失败')
    expect(window.location.href).toBe('')
  })

  it('unwrapResponse clears token and redirects to login with redirect on code 401', async () => {
    await expect(
      unwrapResponse({ data: { code: 401, msg: '客户端ID与Token不匹配', data: null } } as any),
    ).rejects.toThrow('客户端ID与Token不匹配')
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(window.location.href).toBe('/login?redirect=%2Fgame%2FABC123')
  })

  it('does not redirect when already on /login', async () => {
    ;(window.location as any).pathname = '/login'
    await expect(
      unwrapResponse({ data: { code: 401, msg: '登录已过期', data: null } } as any),
    ).rejects.toThrow()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(window.location.href).toBe('')
  })

  it('rawResponse passes bare map through untouched', () => {
    const bare = { success: true, rooms: [] }
    expect(rawResponse({ data: bare } as any)).toBe(bare)
    expect(window.location.href).toBe('')
  })

  it('rawResponse also rejects and redirects on envelope 401', async () => {
    await expect(
      rawResponse({ data: { code: 401, msg: '登录已过期', data: null } } as any),
    ).rejects.toThrow('登录已过期')
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(window.location.href).toContain('/login?redirect=')
  })
})
