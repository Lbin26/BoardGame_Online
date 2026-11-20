/** 模拟运行参数的运行时 zod 校验（I1）：resolveParams 之后、进入模拟器之前把关，错误点名 sim 与字段 */
import { z, ZodError } from 'zod'

const FaceSchema = z.union([z.string(), z.number()])
const OpSchema = z.enum(['>=', '>', '<=', '<', '='])
const NonNegMap = z.record(z.number().min(0))

export const DiceParamsSchema = z.object({
  count: z.number().int().min(0),
  faces: z.array(FaceSchema).min(1),
  symbol: FaceSchema,
  op: OpSchema,
  value: z.number(),
})

/** cards 接受 deck（[{key,count}]）或 game.yaml 牌库条目的 cards（自动投影为 deck，I5 单一事实来源） */
export const CardsParamsSchema = z.object({
  deck: z.array(z.object({ key: z.string().min(1), count: z.number().int().min(0) })).min(1).optional(),
  cards: z.array(z.object({ key: z.string().min(1), count: z.number().int().min(1) })).min(1).optional(),
  draw: z.number().int().min(0),
  target: z.string().min(1),
  op: OpSchema,
  value: z.number(),
}).refine(p => p.deck !== undefined || p.cards !== undefined, {
  message: '须提供 deck（[{key,count}]）或 cards（game.yaml 牌库条目，自动投影为 deck）',
})

export const PolicySchema = z.discriminatedUnion('type', [
  z.object({ type: z.literal('greedy'), order: z.array(z.string().min(1)).optional() }),
  z.object({ type: z.literal('priority'), order: z.array(z.string().min(1)).min(1) }),
  z.object({ type: z.literal('static') }),
])

export const EconomyRunParamsSchema = z.object({
  resources: z.array(z.string().min(1)).min(1),
  initial: NonNegMap.optional(),
  perTurn: z.array(z.object({
    resource: z.string().min(1),
    produce: z.number().min(0),
    consume: z.number().min(0),
  })).optional(),
  actions: z.array(z.object({
    key: z.string().min(1),
    cost: NonNegMap,
    gain: NonNegMap.optional(),
    vp: z.number().optional(),
  })).optional(),
  stallK: z.number().int().min(1).optional(),
  turns: z.number().int().min(1).optional(),
  policy: PolicySchema.optional(),
})

const ConflictSideSchema = z.object({
  diceCount: z.number().int().min(0),
  faces: z.array(FaceSchema).min(1),
  hitSymbols: z.array(FaceSchema).min(1),
  hp: z.number().int().min(1),
  bonus: z.number().int().optional(),
})

export const ConflictParamsSchema = z.object({
  attacker: ConflictSideSchema,
  defender: ConflictSideSchema,
  maxRounds: z.number().int().min(1).optional(),
})

/** 解析运行参数；ZodError 包装为点名 sim 与字段的可读错误 */
export function parseParams<S extends z.ZodTypeAny>(sim: string, schema: S, params: unknown): z.infer<S> {
  try {
    return schema.parse(params)
  } catch (e) {
    if (e instanceof ZodError) {
      throw new Error(`${sim} 参数非法: ` + e.issues.map(i => (i.path.join('.') || '(根)') + ': ' + i.message).join('; '))
    }
    throw e
  }
}
