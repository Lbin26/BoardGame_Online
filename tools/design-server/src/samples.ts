import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { loadSampleDoc } from '../../boardgame-design/src/schema/load.js'
import type { SampleDoc } from '../../boardgame-design/src/schema/game.js'

export interface SampleEntry { key: string; text: string; doc: SampleDoc }

/** 样本库只读传输：原文（预置编辑器用）+ 解析档案（浏览用） */
export function loadSamples(dir: string): SampleEntry[] {
  return readdirSync(dir).filter(f => f.endsWith('.yaml')).sort().map(f => {
    const p = path.join(dir, f)
    const doc = loadSampleDoc(p)
    return { key: doc.meta.key, text: readFileSync(p, 'utf-8'), doc }
  })
}