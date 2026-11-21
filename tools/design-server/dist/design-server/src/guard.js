import { parse } from 'yaml';
import { HttpError } from './http.js';
/** 服务器护栏精确值（spec 钉死；conflictFaces 为 Review Focus #3 的 builder 加固，与 dice 对齐） */
export const CAPS = {
    bodyBytes: 512 * 1024,
    runs: 50000,
    economyTurns: 500,
    diceCount: 200,
    diceFaces: 64,
    conflictMaxRounds: 200,
    conflictDiceCount: 20,
    conflictHp: 1000,
    conflictFaces: 64,
    sweepValues: 20,
    // cards logFact(n) 建 n 元素缓存数组：无上限时 1e8 count ≈ 800MB → 进程级 OOM（15s 预算兜不住内存维度）
    cardsDeckTotal: 100000,
    cardsDraw: 100000,
};
const over = (field, actual, cap) => new HttpError(400, `${field} 超出上限: ${actual} > ${cap}`);
/** 上限在 overrides 合并后的最终参数上校验；sweep 逐行调用 */
export function assertCaps(sim, params, runs) {
    if (runs > CAPS.runs)
        throw over('runs', runs, CAPS.runs);
    if (sim === 'dice') {
        if (params.count > CAPS.diceCount)
            throw over('dice.count', params.count, CAPS.diceCount);
        if (Array.isArray(params.faces) && params.faces.length > CAPS.diceFaces)
            throw over('dice.faces 长度', params.faces.length, CAPS.diceFaces);
    }
    else if (sim === 'economy') {
        const turns = params.turns ?? 20;
        if (turns > CAPS.economyTurns)
            throw over('economy.turns', turns, CAPS.economyTurns);
    }
    else if (sim === 'conflict') {
        const maxRounds = params.maxRounds ?? 100;
        if (maxRounds > CAPS.conflictMaxRounds)
            throw over('conflict.maxRounds', maxRounds, CAPS.conflictMaxRounds);
        for (const side of ['attacker', 'defender']) {
            const s = params[side];
            if (!s)
                continue;
            if (s.diceCount > CAPS.conflictDiceCount)
                throw over(`conflict.${side}.diceCount`, s.diceCount, CAPS.conflictDiceCount);
            if (s.hp > CAPS.conflictHp)
                throw over(`conflict.${side}.hp`, s.hp, CAPS.conflictHp);
            if (Array.isArray(s.faces) && s.faces.length > CAPS.conflictFaces)
                throw over(`conflict.${side}.faces 长度`, s.faces.length, CAPS.conflictFaces);
        }
    }
    else if (sim === 'cards') {
        const deck = Array.isArray(params.deck) ? params.deck : [];
        const total = deck.reduce((s, d) => s + (typeof d.count === 'number' ? d.count : 0), 0);
        if (total > CAPS.cardsDeckTotal)
            throw over('cards.deck 总数', total, CAPS.cardsDeckTotal);
        if (params.draw > CAPS.cardsDraw)
            throw over('cards.draw', params.draw, CAPS.cardsDraw);
    }
}
const DANGEROUS_KEYS = new Set(['__proto__', 'constructor', 'prototype']);
const dangerousKeyError = () => new HttpError(400, '输入含危险键（__proto__/constructor/prototype），已拒绝');
/** YAML 解析可能经 __proto__ 键直接改原型 → 文本前置拒绝（Review Focus #4；仅块式形态，深度检查见 assertTextSafe） */
export function assertNoDangerousKeys(text) {
    if (/(^|\n)\s*(__proto__|constructor|prototype)\s*:/.test(text)) {
        throw dangerousKeyError();
    }
}
/** 解析后遍历自有键：覆盖文本正则漏网的 flow 风格/引号键/数组嵌套；seen 集防 YAML 别名成环栈溢出 */
export function assertNoDangerousKeysDeep(value, seen = new Set()) {
    if (value === null || typeof value !== 'object')
        return;
    if (seen.has(value))
        return;
    seen.add(value);
    if (Array.isArray(value)) {
        for (const v of value)
            assertNoDangerousKeysDeep(v, seen);
        return;
    }
    for (const k of Object.keys(value)) {
        if (DANGEROUS_KEYS.has(k))
            throw dangerousKeyError();
        assertNoDangerousKeysDeep(value[k], seen);
    }
}
/** 文本前置拒绝 + 解析后深度检查；解析失败交由后续流程报 400（此处不重复报错） */
export function assertTextSafe(text) {
    assertNoDangerousKeys(text);
    let doc;
    try {
        doc = parse(text);
    }
    catch {
        return;
    }
    assertNoDangerousKeysDeep(doc);
}
