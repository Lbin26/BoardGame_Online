import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { parse } from 'yaml'

export interface MechanismEntry {
  key: string
  name: string
  nameEn: string
  zones: string[]
  aliases?: string[]
}

/** 读包内 data/mechanisms.yaml（相对模块定位，cwd 无关） */
export function loadVocabulary(): MechanismEntry[] {
  const path = fileURLToPath(new URL('../../data/mechanisms.yaml', import.meta.url))
  const doc = parse(readFileSync(path, 'utf-8')) as { mechanisms: MechanismEntry[] }
  return doc.mechanisms
}

/** 全部 name + aliases 的并集（即词汇覆盖的中文标签集合） */
export function coveredTags(entries: MechanismEntry[]): Set<string> {
  const tags = new Set<string>()
  for (const e of entries) {
    tags.add(e.name)
    for (const a of e.aliases ?? []) tags.add(a)
  }
  return tags
}

/** catalog 标签必须全部被词汇覆盖，否则 throw 并列出缺失项 */
export function assertCatalogCoverage(entries: MechanismEntry[], tags: string[]): void {
  const covered = coveredTags(entries)
  const missing = tags.filter(t => !covered.has(t))
  if (missing.length > 0) throw new Error('词汇未覆盖 catalog 标签: ' + missing.join(', '))
}