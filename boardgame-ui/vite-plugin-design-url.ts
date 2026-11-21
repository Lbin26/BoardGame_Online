import type { Plugin } from 'vite'

/** G6：dev 启动时在控制台输出工作台网址（与 design-server 启动横幅同款文案） */
export function designUrlPlugin(): Plugin {
  return {
    name: 'design-url',
    configureServer() {
      console.log('桌游设计助手工作台 → http://localhost:5173/design')
    },
  }
}