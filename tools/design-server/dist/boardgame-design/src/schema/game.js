import { z } from 'zod';
export const ZONE_KEYS = ['party', 'family', 'strategy', 'card', 'narrative', 'abstract', 'heavy'];
const ZoneEnum = z.enum(ZONE_KEYS);
const FaceSchema = z.union([z.string(), z.number()]);
const ResourceMapSchema = z.record(z.number().min(0));
const MetaSchema = z.object({
    key: z.string().regex(/^[a-z][a-zA-Z0-9]*$/, 'key 须为 camelCase（对齐 catalog 惯例）'),
    name: z.string().min(1),
    players: z.object({ min: z.number().int().min(1), max: z.number().int() })
        .refine(p => p.max >= p.min, { message: 'players.max 须 ≥ min' }),
    durationMin: z.number().int().positive(),
    targetWeight: z.number().min(1).max(5).optional(),
    zone: ZoneEnum.optional(),
});
const CardSchema = z.object({
    key: z.string().min(1),
    name: z.string().optional(),
    count: z.number().int().min(1),
    cost: ResourceMapSchema.optional(),
    gain: ResourceMapSchema.optional(),
    vp: z.number().optional(),
});
const SideSchema = z.object({
    diceCount: z.number().int().min(0),
    faces: z.array(FaceSchema).min(1),
    hitSymbols: z.array(FaceSchema).min(1),
    hp: z.number().int().min(1),
    bonus: z.number().int().default(0),
});
const EconomySchema = z.object({
    resources: z.array(z.string().min(1)).min(1),
    initial: ResourceMapSchema.optional(),
    perTurn: z.array(z.object({
        resource: z.string().min(1),
        produce: z.number().min(0),
        consume: z.number().min(0),
    })).optional(),
    actions: z.array(z.object({
        key: z.string().min(1),
        name: z.string().optional(),
        cost: ResourceMapSchema,
        gain: ResourceMapSchema.optional(),
        vp: z.number().optional(),
    })).optional(),
    stallK: z.number().int().min(1).default(3),
});
const AnalysisSchema = z.object({
    whyFun: z.string().min(1),
    remixable: z.array(z.string().min(1)).min(1),
    pitfalls: z.string().optional(),
    sources: z.array(z.object({ title: z.string().min(1), url: z.string().optional() })).min(1),
    confidence: z.enum(['high', 'medium', 'low']),
});
export const GameDocSchema = z.object({
    schemaVersion: z.literal(1),
    meta: MetaSchema,
    mechanisms: z.array(z.string().min(1)).min(1),
    components: z.object({
        resources: z.array(z.object({ key: z.string().min(1), name: z.string().min(1), supply: z.number().int().optional() })).optional(),
        dice: z.array(z.object({ key: z.string().min(1), faces: z.array(FaceSchema).min(1), count: z.number().int().min(1) })).optional(),
        decks: z.array(z.object({ key: z.string().min(1), name: z.string().optional(), cards: z.array(CardSchema).min(1) })).optional(),
    }),
    turnStructure: z.object({
        phases: z.array(z.object({ key: z.string().min(1), name: z.string().min(1), summary: z.string().min(1) })).min(1),
    }),
    subsystems: z.object({
        economy: EconomySchema.optional(),
        conflict: z.object({ attacker: SideSchema, defender: SideSchema, maxRounds: z.number().int().min(1).default(100) }).optional(),
        scoring: z.object({
            endTrigger: z.string().min(1),
            vpSources: z.array(z.object({ key: z.string().min(1), name: z.string().optional(), description: z.string().min(1) })).min(1),
        }).optional(),
    }),
    analysis: AnalysisSchema.optional(),
});
/** 样本档案 = 设计文档 + 必填 zone 与 analysis */
export const SampleDocSchema = GameDocSchema.extend({
    meta: MetaSchema.extend({ zone: ZoneEnum }),
    analysis: AnalysisSchema,
});
