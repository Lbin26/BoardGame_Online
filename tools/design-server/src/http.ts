import type { IncomingMessage, ServerResponse } from 'node:http'

/** 带 HTTP 状态码的业务错误（路由层统一转 JSON） */
export class HttpError extends Error {
  constructor(public status: number, message: string, public headers?: Record<string, string>) {
    super(message)
  }
}

export function sendJson(res: ServerResponse, status: number, body: unknown, headers?: Record<string, string>): void {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', ...headers })
  res.end(JSON.stringify(body))
}

/** 读请求体，超出 maxBytes → 400（含上限值）并销毁流 */
export function readBody(req: IncomingMessage, maxBytes: number): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = []
    let size = 0
    req.on('data', (c: Buffer) => {
      size += c.length
      if (size > maxBytes) {
        reject(new HttpError(400, `请求体超出上限 ${maxBytes} 字节`))
        // 不销毁 socket（否则客户端收不到 400 响应）：丢弃余下数据
        req.removeAllListeners('data')
        req.resume()
        return
      }
      chunks.push(c)
    })
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf-8')))
    req.on('error', reject)
  })
}