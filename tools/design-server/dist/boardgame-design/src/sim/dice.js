import { mulberry32 } from '../rng.js';
export function satisfies(x, op, value) {
    switch (op) {
        case '>=': return x >= value;
        case '>': return x > value;
        case '<=': return x <= value;
        case '<': return x < value;
        case '=': return x === value;
    }
}
/** 精确分布：每骰匹配 symbol 的概率 p，逐骰卷积得 pmf（二项分布） */
export function diceExact(p) {
    const matchProb = p.faces.filter(f => f === p.symbol).length / p.faces.length;
    let pmf = [1];
    for (let i = 0; i < p.count; i++) {
        const next = new Array(pmf.length + 1).fill(0);
        for (let k = 0; k < pmf.length; k++) {
            next[k] += pmf[k] * (1 - matchProb);
            next[k + 1] += pmf[k] * matchProb;
        }
        pmf = next;
    }
    const probability = pmf.reduce((s, pr, k) => s + (satisfies(k, p.op, p.value) ? pr : 0), 0);
    return { pmf, probability, mean: p.count * matchProb };
}
/** 蒙特卡洛交叉验证：带种子可复现 */
export function diceMC(p, seed, runs) {
    const rng = mulberry32(seed);
    let hits = 0;
    let totalMatches = 0;
    for (let r = 0; r < runs; r++) {
        let matches = 0;
        for (let i = 0; i < p.count; i++) {
            if (p.faces[Math.floor(rng() * p.faces.length)] === p.symbol)
                matches++;
        }
        totalMatches += matches;
        if (satisfies(matches, p.op, p.value))
            hits++;
    }
    return { probability: hits / runs, mean: totalMatches / runs };
}
