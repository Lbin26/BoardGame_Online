import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  rawRequest: { get: vi.fn(), post: vi.fn() },
}))

import { rawRequest } from '@/utils/request'
import { getSamples, getMechanisms, validateGame, runSim, runSweep } from '@/api/design'
import { router } from '@/router'

const get = vi.mocked(rawRequest.get)
const post = vi.mocked(rawRequest.post)

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

describe('api/design', () => {
  it('getSamples → GET /design-api/samples，无 timeout 覆盖', async () => {
    get.mockResolvedValue([])
    await getSamples()
    expect(get).toHaveBeenCalledWith('/design-api/samples')
  })

  it('getMechanisms → GET /design-api/mechanisms', async () => {
    get.mockResolvedValue([])
    await getMechanisms()
    expect(get).toHaveBeenCalledWith('/design-api/mechanisms')
  })

  it('validateGame → POST /design-api/validate {text}', async () => {
    post.mockResolvedValue({ ok: true, errors: [] })
    const r = await validateGame('schemaVersion: 1')
    expect(post).toHaveBeenCalledWith('/design-api/validate', { text: 'schemaVersion: 1' })
    expect(r).toEqual({ ok: true, errors: [] })
  })

  it('runSim → POST /design-api/run，每请求 timeout 30000（默认 10s < worker 预算）', async () => {
    post.mockResolvedValue({ result: {}, md: '', json: '', seed: 42, runs: 100 })
    const body = { gameText: 'g', configText: 'c' }
    await runSim(body)
    expect(post).toHaveBeenCalledWith('/design-api/run', body, { timeout: 30000 })
  })

  it('runSweep → POST /design-api/sweep，每请求 timeout 30000', async () => {
    post.mockResolvedValue({ rows: [], md: '', json: '' })
    const body = { gameText: 'g', configText: 'c' }
    await runSweep(body)
    expect(post).toHaveBeenCalledWith('/design-api/sweep', body, { timeout: 30000 })
  })
})

describe('/design 路由', () => {
  it('resolve /design → name design，requiresAuth true', () => {
    const r = router.resolve('/design')
    expect(r.name).toBe('design')
    expect(r.meta.requiresAuth).toBe(true)
  })
})