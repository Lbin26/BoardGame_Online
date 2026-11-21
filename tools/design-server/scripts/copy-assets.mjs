// 编译产物布局下 vocabulary.ts 的 new URL('../../data/…', import.meta.url) 指向 dist/boardgame-design/data，须拷贝
import { cpSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const src = path.resolve(here, '../../boardgame-design/data')
const dest = path.resolve(here, '../dist/boardgame-design/data')
cpSync(src, dest, { recursive: true })
console.log(`已拷贝数据资产: ${src} → ${dest}`)