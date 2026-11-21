import http from 'node:http'
import type { IncomingMessage, ServerResponse } from 'node:http'
import type { Duplex } from 'node:stream'
import { createReadStream, existsSync, statSync } from 'node:fs'
import path from 'node:path'
import { sendJson } from './http.js'

const CONTENT_TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
}

/** 静态托管 + SPA fallback；decode 后 resolve 必须仍在 distDir 内（防穿越），未命中回 index.html */
export function serveStatic(req: IncomingMessage, res: ServerResponse, distDir: string): void {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    sendJson(res, 404, { error: `未知路径: ${req.method} ${req.url}` })
    return
  }
  if (!existsSync(distDir)) {
    sendJson(res, 500, { error: '前端未构建：先运行 boardgame-ui npm run build' })
    return
  }
  let pathname: string
  try {
    pathname = decodeURIComponent(new URL(req.url ?? '/', 'http://localhost').pathname)
  } catch {
    // 畸形百分号编码（/%、/%zz、截断多字节）→ 400，绝不让 URIError 冒泡崩进程
    sendJson(res, 400, { error: '路径编码非法' })
    return
  }
  const root = path.resolve(distDir)
  let file = path.resolve(root, '.' + pathname)
  if (file !== root && !file.startsWith(root + path.sep)) {
    sendJson(res, 404, { error: '路径越界' })
    return
  }
  if (!existsSync(file) || !statSync(file).isFile()) file = path.join(root, 'index.html')
  res.writeHead(200, { 'Content-Type': CONTENT_TYPES[path.extname(file).toLowerCase()] ?? 'application/octet-stream' })
  if (req.method === 'HEAD') {
    res.end()
    return
  }
  createReadStream(file).pipe(res)
}

/** HTTP 反代：method/url/headers/body 透传（host 改写为上游）；上游不可达 → 502 */
export function proxyHttp(req: IncomingMessage, res: ServerResponse, upstreamUrl: string): void {
  const up = new URL(upstreamUrl)
  const upstreamReq = http.request(
    {
      hostname: up.hostname,
      port: up.port,
      path: req.url,
      method: req.method,
      headers: { ...req.headers, host: up.host },
    },
    upstreamRes => {
      res.writeHead(upstreamRes.statusCode ?? 502, upstreamRes.headers)
      // 上游中途断连：拆客户端连接，绝不令其永久挂起
      upstreamRes.on('error', () => res.destroy())
      upstreamRes.on('aborted', () => res.destroy())
      upstreamRes.pipe(res)
    },
  )
  upstreamReq.on('error', () => {
    // 响应头已写后不能再 writeHead（ERR_HTTP_HEADERS_SENT 会崩进程）
    if (res.headersSent) res.destroy()
    else sendJson(res, 502, { error: '上游服务不可达' })
  })
  req.pipe(upstreamReq)
}

/** WebSocket 反代：向上游发 upgrade，101 后双向 pipe（先写双方 head） */
export function proxyUpgrade(req: IncomingMessage, socket: Duplex, head: Buffer, upstreamUrl: string): void {
  const up = new URL(upstreamUrl)
  const upstreamReq = http.request({
    hostname: up.hostname,
    port: up.port,
    path: req.url,
    method: 'GET',
    headers: { ...req.headers, host: up.host },
  })
  upstreamReq.on('upgrade', (upRes, upSocket, upHead) => {
    const headers = Object.entries(upRes.headers).map(([k, v]) => `${k}: ${v}`).join('\r\n')
    socket.write(`HTTP/1.1 101 Switching Protocols\r\n${headers}\r\n\r\n`)
    if (upHead.length) socket.write(upHead)
    if (head.length) upSocket.write(head)
    upSocket.pipe(socket)
    socket.pipe(upSocket)
    upSocket.on('error', () => socket.destroy())
    // 任一端关闭即拆另一端，避免上游 socket 泄漏；
    // upgrade socket 为半开模式，对端 FIN 只发 end 不发 close，须显式 destroy
    socket.on('end', () => socket.destroy())
    upSocket.on('end', () => upSocket.destroy())
    socket.on('close', () => upSocket.destroy())
    upSocket.on('close', () => socket.destroy())
  })
  // 上游拒绝 upgrade（回非 101，如鉴权失败 404/502）：转写状态行+头后关闭，客户端不得挂起
  upstreamReq.on('response', upRes => {
    const headers = Object.entries(upRes.headers).map(([k, v]) => `${k}: ${v}`).join('\r\n')
    socket.end(`HTTP/1.1 ${upRes.statusCode} ${upRes.statusMessage ?? ''}\r\n${headers}\r\n\r\n`)
    upRes.resume()
  })
  upstreamReq.on('error', () => socket.destroy())
  socket.on('error', () => upstreamReq.destroy())
  upstreamReq.end()
}