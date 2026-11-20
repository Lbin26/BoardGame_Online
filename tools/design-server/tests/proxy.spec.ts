import { describe, it, expect, afterEach } from 'vitest'
import http from 'node:http'
import type { AddressInfo } from 'node:net'
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { createApp } from '../src/app.js'
import { resolvePaths } from '../src/paths.js'

let app: http.Server | undefined
let upstream: http.Server | undefined
let baseDir = ''
let received: { method: string; url: string; body: string }[] = []

/** 原始 path 的 GET（fetch 会规范化 /../，必须用 http.request 保留原样） */
function rawGet(port: number, rawPath: string): Promise<{ status: number; body: string; headers: http.IncomingHttpHeaders }> {
  return new Promise((resolve, reject) => {
    const req = http.request({ host: '127.0.0.1', port, path: rawPath, method: 'GET' }, res => {
      const chunks: Buffer[] = []
      res.on('data', c => chunks.push(c))
      res.on('end', () => resolve({ status: res.statusCode!, body: Buffer.concat(chunks).toString('utf-8'), headers: res.headers }))
    })
    req.on('error', reject)
    req.end()
  })
}

function post(port: number, p: string, body: string): Promise<{ status: number; body: string }> {
  return new Promise((resolve, reject) => {
    const req = http.request({ host: '127.0.0.1', port, path: p, method: 'POST', headers: { 'Content-Type': 'application/json' } }, res => {
      const chunks: Buffer[] = []
      res.on('data', c => chunks.push(c))
      res.on('end', () => resolve({ status: res.statusCode!, body: Buffer.concat(chunks).toString('utf-8') }))
    })
    req.on('error', reject)
    req.end(body)
  })
}

async function startUpstream(): Promise<string> {
  received = []
  upstream = http.createServer((req, res) => {
    // 中途断连夹具：发 200 头 + 半截 body 后毁 socket（Final #2）
    if (req.url === '/api/midstream') {
      res.writeHead(200, { 'Content-Type': 'text/plain', 'Content-Length': '100' })
      res.write('partial')
      setTimeout(() => res.socket?.destroy(), 20)
      return
    }
    const chunks: Buffer[] = []
    req.on('data', c => chunks.push(c))
    req.on('end', () => {
      received.push({ method: req.method!, url: req.url!, body: Buffer.concat(chunks).toString('utf-8') })
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ echo: true }))
    })
  })
  upstream.on('upgrade', (_req, socket) => {
    // 拒绝 upgrade 夹具：回普通 404 而非 101（Final #3）
    if (_req.url === '/ws/reject') {
      socket.write('HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n')
      socket.end()
      return
    }
    socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n')
    socket.on('data', d => socket.write(d))
    // upgrade socket 半开：对端 FIN 只发 end，须自毁否则 close() 挂起
    socket.on('end', () => socket.destroy())
    socket.on('error', () => socket.destroy())
  })
  await new Promise<void>(r => upstream!.listen(0, '127.0.0.1', r))
  return `http://127.0.0.1:${(upstream!.address() as AddressInfo).port}`
}

async function startApp(uiDistDir: string, upstreamUrl: string): Promise<number> {
  app = createApp({ serveUi: true, paths: { ...resolvePaths(), uiDistDir, upstream: upstreamUrl } })
  await new Promise<void>(r => app!.listen(0, '127.0.0.1', r))
  return (app!.address() as AddressInfo).port
}

function makeDist(): { dist: string; base: string } {
  baseDir = mkdtempSync(path.join(tmpdir(), 'design-proxy-'))
  const dist = path.join(baseDir, 'dist')
  mkdirSync(path.join(dist, 'assets'), { recursive: true })
  writeFileSync(path.join(dist, 'index.html'), 'design-spa')
  writeFileSync(path.join(dist, 'assets/app.js'), 'console.log(1)')
  writeFileSync(path.join(baseDir, 'secret.txt'), 'TOP-SECRET')
  return { dist, base: baseDir }
}

afterEach(async () => {
  // upgrade 后的 socket 不受 close() 管辖，先全量断开避免钩子挂起
  if (app) {
    app.closeAllConnections()
    await new Promise(r => app!.close(r))
  }
  if (upstream) {
    upstream.closeAllConnections()
    await new Promise(r => upstream!.close(r))
  }
  app = undefined
  upstream = undefined
  if (baseDir) rmSync(baseDir, { recursive: true, force: true })
  baseDir = ''
})

describe('生产托管：静态 + SPA + 反代 + ws', () => {
  it('GET / 与 /design → SPA index.html', async () => {
    const { dist } = makeDist()
    const up = await startUpstream()
    const port = await startApp(dist, up)
    for (const p of ['/', '/design']) {
      const r = await rawGet(port, p)
      expect(r.status).toBe(200)
      expect(r.body).toBe('design-spa')
    }
  })

  it('GET /assets/app.js → content-type javascript', async () => {
    const { dist } = makeDist()
    const port = await startApp(dist, await startUpstream())
    const r = await rawGet(port, '/assets/app.js')
    expect(r.status).toBe(200)
    expect(r.headers['content-type']).toContain('javascript')
  })

  it('路径穿越（原始 /../ 与 %2e%2e）不得泄露 dist 外文件', async () => {
    const { dist } = makeDist()
    const port = await startApp(dist, await startUpstream())
    for (const p of ['/../secret.txt', '/%2e%2e/secret.txt']) {
      const r = await rawGet(port, p)
      expect(r.body).not.toContain('TOP-SECRET')
      expect([404, 200]).toContain(r.status) // 404 或 SPA index 均可
    }
  })

  it('畸形百分号编码 GET /% → 400 且进程存活（Final #1）', async () => {
    const { dist } = makeDist()
    const port = await startApp(dist, await startUpstream())
    for (const p of ['/%', '/%zz', '/%C0%AF']) {
      const r = await rawGet(port, p)
      expect(r.status).toBe(400)
      expect(r.body).toContain('路径编码非法')
    }
    const alive = await rawGet(port, '/')
    expect(alive.status).toBe(200)
    expect(alive.body).toBe('design-spa')
  })

  it('非法 URL 目标 //［ → 兜底 500 且进程存活（Final #1 安全网）', async () => {
    const { dist } = makeDist()
    const port = await startApp(dist, await startUpstream())
    const r = await rawGet(port, '//[')
    expect(r.status).toBe(500)
    const alive = await rawGet(port, '/')
    expect(alive.status).toBe(200)
  })

  it('POST /api /auth /system → 上游收到同 path/method/body，响应透传', async () => {
    const { dist } = makeDist()
    const port = await startApp(dist, await startUpstream())
    for (const p of ['/api/chess/rooms', '/auth/login', '/system/x']) {
      const r = await post(port, p, '{"a":1}')
      expect(r.status).toBe(200)
      expect(JSON.parse(r.body)).toEqual({ echo: true })
    }
    expect(received.map(r => [r.method, r.url, r.body])).toEqual([
      ['POST', '/api/chess/rooms', '{"a":1}'],
      ['POST', '/auth/login', '{"a":1}'],
      ['POST', '/system/x', '{"a":1}'],
    ])
  })

  it('上游不可达 → 502', async () => {
    const { dist } = makeDist()
    const up = await startUpstream()
    const port = await startApp(dist, up)
    await new Promise(r => upstream!.close(r))
    upstream = undefined
    const r = await rawGet(port, '/api/x')
    expect(r.status).toBe(502)
  })

  it('上游中途断连 → 客户端连接被拆而非永久挂起（Final #2）', async () => {
    const { dist } = makeDist()
    const port = await startApp(dist, await startUpstream())
    const outcome = await new Promise<string>((resolve, reject) => {
      const t = setTimeout(() => reject(new Error('客户端挂起：2s 内无 end/error/aborted')), 2000)
      const req = http.request({ host: '127.0.0.1', port, path: '/api/midstream' }, res => {
        res.on('aborted', () => { clearTimeout(t); resolve('aborted') })
        res.on('error', () => { clearTimeout(t); resolve('error') })
        res.on('end', () => { clearTimeout(t); resolve('end') })
        res.resume()
      })
      req.on('error', () => { clearTimeout(t); resolve('req-error') })
      req.end()
    })
    expect(['aborted', 'error', 'req-error']).toContain(outcome)
  })

  it('ws upgrade → 101 且双向 echo', async () => {
    const { dist } = makeDist()
    const port = await startApp(dist, await startUpstream())
    const echoed = await new Promise<string>((resolve, reject) => {
      const req = http.request({ host: '127.0.0.1', port, path: '/ws/echo', headers: { connection: 'Upgrade', upgrade: 'websocket' } })
      req.on('upgrade', (res, socket) => {
        expect(res.statusCode).toBe(101)
        socket.on('data', d => { resolve(d.toString()); socket.destroy() })
        socket.write('ping')
      })
      req.on('error', reject)
      req.end()
    })
    expect(echoed).toBe('ping')
  })

  it('ws 上游回非 101 → 客户端收到响应而非挂起（Final #3）', async () => {
    const { dist } = makeDist()
    const port = await startApp(dist, await startUpstream())
    const status = await new Promise<number>((resolve, reject) => {
      const t = setTimeout(() => reject(new Error('ws 客户端挂起：2s 内无 response/upgrade/error')), 2000)
      const req = http.request({ host: '127.0.0.1', port, path: '/ws/reject', headers: { connection: 'Upgrade', upgrade: 'websocket' } })
      req.on('upgrade', () => { clearTimeout(t); reject(new Error('不应收到 101')) })
      req.on('response', res => { clearTimeout(t); res.resume(); resolve(res.statusCode!) })
      req.on('error', () => { clearTimeout(t); resolve(-1) })
      req.end()
    })
    expect(status).toBe(404)
  })

  it('dist 不存在 → 500 前端未构建', async () => {
    const port = await startApp(path.join(tmpdir(), 'design-no-such-dist'), await startUpstream())
    const r = await rawGet(port, '/')
    expect(r.status).toBe(500)
    expect(r.body).toContain('前端未构建')
  })
})