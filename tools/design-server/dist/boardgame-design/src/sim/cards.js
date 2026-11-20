import { satisfies } from './dice.js';
const logFactCache = [0, 0];
function logFact(n) {
    for (let i = logFactCache.length; i <= n; i++)
        logFactCache[i] = logFactCache[i - 1] + Math.log(i);
    return logFactCache[n];
}
function logChoose(a, b) {
    return logFact(a) - logFact(b) - logFact(a - b);
}
/** 超几何分布 P(抽 n 张恰含 k 张目标)；对数阶乘防溢出 */
export function hypergeometric(N, K, n, k) {
    if ([N, K, n, k].some(v => !Number.isInteger(v) || v < 0))
        throw new Error('超几何参数须为非负整数');
    if (k > n || k > K || n > N)
        throw new Error(`超几何参数越界: N=${N} K=${K} n=${n} k=${k}`);
    if (n - k > N - K)
        return 0;
    return Math.exp(logChoose(K, k) + logChoose(N - K, n - k) - logChoose(N, n));
}
/** 抽卡精确概率 + 期望张数 + 首次抽到目标的期望抽数 */
export function cardsExact(p) {
    const entry = p.deck.find(d => d.key === p.target);
    if (!entry)
        throw new Error(`target 不在牌库中: ${p.target}`);
    const N = p.deck.reduce((s, d) => s + d.count, 0);
    const K = entry.count;
    let probability = 0;
    for (let k = 0; k <= Math.min(p.draw, K); k++) {
        if (satisfies(k, p.op, p.value))
            probability += hypergeometric(N, K, p.draw, k);
    }
    return {
        probability,
        expectedCount: p.draw * K / N,
        expectedDrawsToFirst: K === 0 ? Infinity : (N + 1) / (K + 1),
    };
}
