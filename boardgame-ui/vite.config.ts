/// <reference types="vitest" />
import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

import { designUrlPlugin } from './vite-plugin-design-url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), designUrlPlugin()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/system': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
      '/design-api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    server: {
      deps: {
        // 外部化的 EP 走 Node 原生 ESM，CJS interop 不认 __esModule（见下 alias 注释）；
        // inline 后其内部 import 经 vite 解析/interop，表单校验在测试里才真实生效
        inline: ['element-plus'],
      },
    },
    alias: {
      // vitest 将 element-plus 外部化走 Node 原生 ESM：CJS interop 不认 __esModule，
      // EP 内部 import AsyncValidator from 'async-validator' 拿到命名空间对象，
      // 表单校验在测试里静默放行。指向纯 ESM 构建修复（浏览器端走 vite interop，不受影响）。
      'async-validator': fileURLToPath(
        new URL('./node_modules/async-validator/dist-web/index.js', import.meta.url),
      ),
    },
  },
})
