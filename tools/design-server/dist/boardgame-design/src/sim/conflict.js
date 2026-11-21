import { mulberry32 } from '../rng.js';
function rollHits(side, rng) {
    let hits = side.bonus ?? 0;
    for (let i = 0; i < side.diceCount; i++) {
        const face = side.faces[Math.floor(rng() * side.faces.length)];
        if (side.hitSymbols.includes(face))
            hits++;
    }
    return hits;
}
/** 参数化攻防蒙特卡洛：伤害同时结算，双亡/超时为平局；单 RNG 流贯穿全部 runs 保证可复现 */
export function conflictMC(p, seed, runs) {
    const maxRounds = p.maxRounds ?? 100;
    const rng = mulberry32(seed);
    let aWins = 0, dWins = 0, draws = 0, totalRounds = 0;
    for (let r = 0; r < runs; r++) {
        let aHp = p.attacker.hp;
        let dHp = p.defender.hp;
        let rounds = 0;
        for (;;) {
            rounds++;
            const aHits = rollHits(p.attacker, rng);
            const dHits = rollHits(p.defender, rng);
            dHp -= aHits;
            aHp -= dHits;
            const aDead = aHp <= 0;
            const dDead = dHp <= 0;
            if (aDead || dDead || rounds >= maxRounds) {
                if (aDead && dDead)
                    draws++;
                else if (dDead)
                    aWins++;
                else if (aDead)
                    dWins++;
                else
                    draws++;
                break;
            }
        }
        totalRounds += rounds;
    }
    return {
        attackerWinRate: aWins / runs,
        defenderWinRate: dWins / runs,
        drawRate: draws / runs,
        meanRounds: totalRounds / runs,
    };
}
