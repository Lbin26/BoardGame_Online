import path from 'node:path'
import { parse } from 'yaml'
import { z, ZodError } from 'zod'
import { loadYamlFile, loadGameDoc } from './load.js'
import { GameDocSchema, type GameDoc } from './game.js'

export const SimConfigSchema = z.object({
  game: z.string().min(1),
  sim: z.enum(['dice', 'cards', 'economy', 'conflict']),
  from: z.string().min(1),
  seed: z.number().int().default(42),
  runs: z.number().int().min(1).default(10000),
  overrides: z.record(z.unknown()).optional(),
  out: z.string().optional(),
})
export type SimConfig = z.infer<typeof SimConfigSchema>
export type SimConfigInput = z.input<typeof SimConfigSchema>

export const SweepConfigSchema = SimConfigSchema.extend({
  sweep: z.object({
    param: z.string().min(1),
    values: z.array(z.union([z.number(), z.string()])).min(2),
  }),
})
export type SweepConfig = z.infer<typeof SweepConfigSchema>
export type SweepConfigInput = z.input<typeof SweepConfigSchema>

function parseConfig<S extends z.ZodTypeAny>(schema: S, filePath: string): z.infer<S> {
  try {
    return schema.parse(loadYamlFile(filePath))
  } catch (e) {
    if (e instanceof ZodError) throw new Error(`${filePath}: ` + e.issues.map(i => i.path.join('.') + ': ' + i.message).join('; '))
    throw e
  }
}

export function loadSimConfig(filePath: string): SimConfig {
  return parseConfig(SimConfigSchema, filePath)
}

export function loadSweepConfig(filePath: string): SweepConfig {
  return parseConfig(SweepConfigSchema, filePath)
}

/** 点路径取值：对象段仅自有属性（拒绝原型链），数组段仅规范数字下标；任一段缺失 throw 含路径 */
export function getAtPath(obj: unknown, dotPath: string): unknown {
  let cur: unknown = obj
  for (const seg of dotPath.split('.')) {
    if (Array.isArray(cur)) {
      const idx = Number(seg)
      if (!Number.isInteger(idx) || String(idx) !== seg || idx < 0 || idx >= cur.length) {
        throw new Error('路径不存在: ' + dotPath)
      }
      cur = cur[idx]
    } else if (typeof cur === 'object' && cur !== null && Object.hasOwn(cur, seg)) {
      cur = (cur as Record<string, unknown>)[seg]
    } else {
      throw new Error('路径不存在: ' + dotPath)
    }
  }
  return cur
}

/** 不可变写路径：返回新对象，中间段缺失则建空对象；数组段保持数组 */
export function setAtPath(obj: Record<string, unknown>, dotPath: string, value: unknown): Record<string, unknown> {
  const [head, ...rest] = dotPath.split('.')
  const child = obj[head]
  let nextChild: unknown
  if (rest.length === 0) nextChild = value
  else if (Array.isArray(child)) nextChild = setInArray(child, rest, value)
  else nextChild = setAtPath(isPlainObject(child) ? child : {}, rest.join('.'), value)
  return { ...obj, [head]: nextChild }
}

function setInArray(arr: unknown[], segs: string[], value: unknown): unknown[] {
  const [head, ...rest] = segs
  const idx = Number(head)
  const child = arr[idx]
  const copy = [...arr]
  if (rest.length === 0) copy[idx] = value
  else if (Array.isArray(child)) copy[idx] = setInArray(child, rest, value)
  else copy[idx] = setAtPath(isPlainObject(child) ? child : {}, rest.join('.'), value)
  return copy
}

const isPlainObject = (v: unknown): v is Record<string, unknown> =>
  typeof v === 'object' && v !== null && !Array.isArray(v)

/** 对象递归合并；数组整体替换；其余 override 覆盖 */
export function deepMerge(base: unknown, override: unknown): unknown {
  if (isPlainObject(base) && isPlainObject(override)) {
    const out: Record<string, unknown> = { ...base }
    for (const [k, v] of Object.entries(override)) out[k] = deepMerge(base[k], v)
    return out
  }
  return override === undefined ? base : override
}

/** 共享核：取子系统 → 合并 overrides → 默认 seed/runs */
function resolveFromGame(game: GameDoc, config: SimConfigInput): {
  params: Record<string, unknown>
  game: GameDoc
  seed: number
  runs: number
} {
  const base = getAtPath(game, config.from)
  const params = deepMerge(base, config.overrides ?? {}) as Record<string, unknown>
  return { params, game, seed: config.seed ?? 42, runs: config.runs ?? 10000 }
}

/** game.yaml 为数值单一事实来源：加载 → 取子系统 → 合并 overrides */
export function resolveParams(config: SimConfigInput, configDir: string): {
  params: Record<string, unknown>
  game: GameDoc
  seed: number
  runs: number
} {
  const gamePath = path.resolve(configDir, config.game)
  return resolveFromGame(loadGameDoc(gamePath), config)
}

/** 纯文本版（网页工作台用）：game 来自文本而非文件路径，其余逻辑与 resolveParams 同源 */
export function resolveParamsFromText(gameText: string, config: SimConfigInput): {
  params: Record<string, unknown>
  game: GameDoc
  seed: number
  runs: number
} {
  const raw = parse(gameText.replace(/^﻿/, ''))
  let game: GameDoc
  try {
    game = GameDocSchema.parse(raw)
  } catch (e) {
    if (e instanceof ZodError) {
      throw new Error('(文本): ' + e.issues.map(i => (i.path.join('.') || '(root)') + ': ' + i.message).join('; '))
    }
    throw e
  }
  return resolveFromGame(game, config)
}