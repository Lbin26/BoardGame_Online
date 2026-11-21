import { readFileSync } from 'node:fs'
import { parse } from 'yaml'
import { ZodError } from 'zod'
import { GameDocSchema, SampleDocSchema, type GameDoc, type SampleDoc } from './game.js'
import { loadVocabulary } from './vocabulary.js'

/** UTF-8 读取 + 去 BOM + YAML 解析 */
export function loadYamlFile(path: string): unknown {
  const content = readFileSync(path, 'utf-8').replace(/^﻿/, '')
  return parse(content)
}

function summarizeIssues(path: string, e: ZodError): string {
  return `${path}: ` + e.issues.map(i => (i.path.join('.') || '(root)') + ': ' + i.message).join('; ')
}

export function loadGameDoc(path: string): GameDoc {
  const raw = loadYamlFile(path)
  try {
    return GameDocSchema.parse(raw)
  } catch (e) {
    if (e instanceof ZodError) throw new Error(summarizeIssues(path, e))
    throw e
  }
}

export function loadSampleDoc(path: string): SampleDoc {
  const raw = loadYamlFile(path)
  try {
    return SampleDocSchema.parse(raw)
  } catch (e) {
    if (e instanceof ZodError) throw new Error(summarizeIssues(path, e))
    throw e
  }
}

export type ValidateResult = { ok: true; doc: GameDoc | SampleDoc } | { ok: false; errors: string[] }

/** 校验文档：读文件后调 validateText（错误前缀为文件路径，格式不变） */
export function validateDoc(path: string): ValidateResult {
  let text: string
  try {
    text = readFileSync(path, 'utf-8')
  } catch (e) {
    return { ok: false, errors: [`${path}: ${(e as Error).message}`] }
  }
  return validateText(text, path)
}

/** 纯文本校验入口（网页工作台用）：zod 形状 + 机制词汇成员 + 经济资源交叉引用；错误收集不 throw */
export function validateText(text: string, source = '(文本)'): ValidateResult {
  let raw: unknown
  try {
    raw = parse(text.replace(/^﻿/, ''))
  } catch (e) {
    return { ok: false, errors: [`${source}: ${(e as Error).message}`] }
  }
  const obj = (raw ?? {}) as Record<string, unknown>
  const meta = (obj.meta ?? {}) as Record<string, unknown>
  const isSample = obj.analysis !== undefined || meta.zone !== undefined
  const errors: string[] = []
  let doc: GameDoc | SampleDoc
  try {
    doc = isSample ? SampleDocSchema.parse(raw) : GameDocSchema.parse(raw)
  } catch (e) {
    if (e instanceof ZodError) return { ok: false, errors: [summarizeIssues(source, e)] }
    return { ok: false, errors: [`${source}: ${(e as Error).message}`] }
  }
  // 机制 key 必须属于词汇表
  const vocabKeys = new Set(loadVocabulary().map(m => m.key))
  for (const m of doc.mechanisms) {
    if (!vocabKeys.has(m)) errors.push(`未知机制 key: ${m}（须先入 data/mechanisms.yaml 词汇表）`)
  }
  // 经济子系统引用的资源须在 components.resources 声明（若声明了 resources）
  const declared = doc.components.resources
  const eco = doc.subsystems.economy
  if (declared && eco) {
    const resKeys = new Set(declared.map(r => r.key))
    const check = (name: string, where: string) => {
      if (!resKeys.has(name)) errors.push(`经济子系统${where}引用未声明资源: ${name}`)
    }
    for (const r of eco.resources) check(r, 'resources')
    for (const pt of eco.perTurn ?? []) check(pt.resource, 'perTurn')
    for (const a of eco.actions ?? []) {
      for (const k of Object.keys(a.cost)) check(k, `actions[${a.key}].cost`)
      for (const k of Object.keys(a.gain ?? {})) check(k, `actions[${a.key}].gain`)
    }
  }
  return errors.length > 0 ? { ok: false, errors } : { ok: true, doc }
}