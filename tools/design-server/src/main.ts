import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createApp } from './app.js'
import { startupBanner } from './banner.js'

// 编译产物（路径含 dist 段）即生产布局，默认托管 UI；dev 源码需 NODE_ENV=production 或 --serve-ui
const isDist = fileURLToPath(import.meta.url).split(path.sep).includes('dist')
const serveUi = process.env.NODE_ENV === 'production' || process.argv.includes('--serve-ui') || isDist
const app = createApp({ serveUi })
const startPort = Number(process.env.DESIGN_PORT ?? 8090)

/** 端口占用顺延 +1（最多 10 次），成功后打印工作台网址（G6） */
function listen(port: number, attempt: number): void {
  app.once('error', (e: NodeJS.ErrnoException) => {
    if (e.code === 'EADDRINUSE' && attempt < 10) {
      console.log(`端口 ${port} 被占用，顺延 ${port + 1}`)
      listen(port + 1, attempt + 1)
    } else {
      console.error(e.message)
      process.exit(1)
    }
  })
  app.listen(port, () => {
    console.log(startupBanner({ serveUi, port }))
  })
}

listen(startPort, 1)