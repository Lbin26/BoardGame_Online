import { describe, it, expect, afterEach } from 'vitest'
import type { AddressInfo } from 'node:net'
import type http from 'node:http'
import { createApp } from '../src/app.js'
import { startupBanner } from '../src/banner.js'

let server: http.Server | undefined
let base = ''

async function start(): Promise<void> {
  server = createApp()
  await new Promise<void>(r => server!.listen(0, '127.0.0.1', r))
  base = `http://127.0.0.1:${(server!.address() as AddressInfo).port}`
}

afterEach(async () => {
  if (server) await new Promise(r => server!.close(r))
  server = undefined
})

describe('只读端点（真实 HTTP）', () => {
  it('GET /design-api/health → {ok:true}', async () => {
    await start()
    const res = await fetch(`${base}/design-api/health`)
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ ok: true })
  })
  it('GET /design-api/samples → 7 份，含原文与解析档案', async () => {
    await start()
    const res = await fetch(`${base}/design-api/samples`)
    expect(res.status).toBe(200)
    const list = await res.json()
    expect(list.length).toBe(7)
    const splendor = list.find((s: any) => s.key === 'splendor')
    expect(splendor.text).toContain('schemaVersion')
    expect(splendor.doc.analysis.confidence).toBe('medium')
  })
  it('GET /design-api/mechanisms → 42 条', async () => {
    await start()
    const res = await fetch(`${base}/design-api/mechanisms`)
    expect(res.status).toBe(200)
    expect((await res.json()).length).toBe(42)
  })
  it('未知路径与方法 → 404', async () => {
    await start()
    expect((await fetch(`${base}/design-api/nope`)).status).toBe(404)
    expect((await fetch(`${base}/design-api/health`, { method: 'POST' })).status).toBe(404)
    expect((await fetch(`${base}/other`)).status).toBe(404)
  })
})

describe('启动横幅（G6）', () => {
  it('dev 模式打印 5173 工作台网址', () => {
    expect(startupBanner({ serveUi: false, port: 8090, env: {} })).toBe('桌游设计助手工作台 → http://localhost:5173/design')
  })
  it('prod 模式打印实际端口网址', () => {
    expect(startupBanner({ serveUi: true, port: 8090, env: {} })).toBe('桌游设计助手工作台 → http://localhost:8090/design')
  })
  it('PUBLIC_URL 覆盖两种模式', () => {
    expect(startupBanner({ serveUi: false, port: 8090, env: { PUBLIC_URL: 'http://x/design' } })).toBe('桌游设计助手工作台 → http://x/design')
    expect(startupBanner({ serveUi: true, port: 9000, env: { PUBLIC_URL: 'http://y/design' } })).toBe('桌游设计助手工作台 → http://y/design')
  })
})