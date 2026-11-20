import { describe, it, expect, vi, afterEach } from 'vitest'
import { designUrlPlugin } from '../../vite-plugin-design-url'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('designUrlPlugin（G6：dev 启动控制台输出网址）', () => {
  it('configureServer 打印工作台网址', () => {
    const spy = vi.spyOn(console, 'log').mockImplementation(() => {})
    const plugin = designUrlPlugin()
    // configureServer 钩子不依赖 server 实例参数
    ;(plugin.configureServer as () => void)()
    expect(spy).toHaveBeenCalledWith('桌游设计助手工作台 → http://localhost:5173/design')
  })
})