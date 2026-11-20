import path from 'node:path';
import { parse } from 'yaml';
import { z, ZodError } from 'zod';
import { loadYamlFile, loadGameDoc } from './load.js';
import { GameDocSchema } from './game.js';
export const SimConfigSchema = z.object({
    game: z.string().min(1),
    sim: z.enum(['dice', 'cards', 'economy', 'conflict']),
    from: z.string().min(1),
    seed: z.number().int().default(42),
    runs: z.number().int().min(1).default(10000),
    overrides: z.record(z.unknown()).optional(),
    out: z.string().optional(),
});
export const SweepConfigSchema = SimConfigSchema.extend({
    sweep: z.object({
        param: z.string().min(1),
        values: z.array(z.union([z.number(), z.string()])).min(2),
    }),
});
function parseConfig(schema, filePath) {
    try {
        return schema.parse(loadYamlFile(filePath));
    }
    catch (e) {
        if (e instanceof ZodError)
            throw new Error(`${filePath}: ` + e.issues.map(i => i.path.join('.') + ': ' + i.message).join('; '));
        throw e;
    }
}
export function loadSimConfig(filePath) {
    return parseConfig(SimConfigSchema, filePath);
}
export function loadSweepConfig(filePath) {
    return parseConfig(SweepConfigSchema, filePath);
}
/** 点路径取值：对象段仅自有属性（拒绝原型链），数组段仅规范数字下标；任一段缺失 throw 含路径 */
export function getAtPath(obj, dotPath) {
    let cur = obj;
    for (const seg of dotPath.split('.')) {
        if (Array.isArray(cur)) {
            const idx = Number(seg);
            if (!Number.isInteger(idx) || String(idx) !== seg || idx < 0 || idx >= cur.length) {
                throw new Error('路径不存在: ' + dotPath);
            }
            cur = cur[idx];
        }
        else if (typeof cur === 'object' && cur !== null && Object.hasOwn(cur, seg)) {
            cur = cur[seg];
        }
        else {
            throw new Error('路径不存在: ' + dotPath);
        }
    }
    return cur;
}
/** 不可变写路径：返回新对象，中间段缺失则建空对象；数组段保持数组 */
export function setAtPath(obj, dotPath, value) {
    const [head, ...rest] = dotPath.split('.');
    const child = obj[head];
    let nextChild;
    if (rest.length === 0)
        nextChild = value;
    else if (Array.isArray(child))
        nextChild = setInArray(child, rest, value);
    else
        nextChild = setAtPath(isPlainObject(child) ? child : {}, rest.join('.'), value);
    return { ...obj, [head]: nextChild };
}
function setInArray(arr, segs, value) {
    const [head, ...rest] = segs;
    const idx = Number(head);
    const child = arr[idx];
    const copy = [...arr];
    if (rest.length === 0)
        copy[idx] = value;
    else if (Array.isArray(child))
        copy[idx] = setInArray(child, rest, value);
    else
        copy[idx] = setAtPath(isPlainObject(child) ? child : {}, rest.join('.'), value);
    return copy;
}
const isPlainObject = (v) => typeof v === 'object' && v !== null && !Array.isArray(v);
/** 对象递归合并；数组整体替换；其余 override 覆盖 */
export function deepMerge(base, override) {
    if (isPlainObject(base) && isPlainObject(override)) {
        const out = { ...base };
        for (const [k, v] of Object.entries(override))
            out[k] = deepMerge(base[k], v);
        return out;
    }
    return override === undefined ? base : override;
}
/** 共享核：取子系统 → 合并 overrides → 默认 seed/runs */
function resolveFromGame(game, config) {
    const base = getAtPath(game, config.from);
    const params = deepMerge(base, config.overrides ?? {});
    return { params, game, seed: config.seed ?? 42, runs: config.runs ?? 10000 };
}
/** game.yaml 为数值单一事实来源：加载 → 取子系统 → 合并 overrides */
export function resolveParams(config, configDir) {
    const gamePath = path.resolve(configDir, config.game);
    return resolveFromGame(loadGameDoc(gamePath), config);
}
/** 纯文本版（网页工作台用）：game 来自文本而非文件路径，其余逻辑与 resolveParams 同源 */
export function resolveParamsFromText(gameText, config) {
    const raw = parse(gameText.replace(/^﻿/, ''));
    let game;
    try {
        game = GameDocSchema.parse(raw);
    }
    catch (e) {
        if (e instanceof ZodError) {
            throw new Error('(文本): ' + e.issues.map(i => (i.path.join('.') || '(root)') + ': ' + i.message).join('; '));
        }
        throw e;
    }
    return resolveFromGame(game, config);
}
