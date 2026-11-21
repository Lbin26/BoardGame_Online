import { resolveParams, resolveParamsFromText, setAtPath } from '../schema/simConfig.js';
import { runSim } from '../report.js';
/** 共享核：逐值覆盖（不可变），每值同种子保证可比；基础 params 不被污染 */
function sweepRows(base, config) {
    const rows = config.sweep.values.map(value => {
        const params = setAtPath(base.params, config.sweep.param, value);
        return { value, result: runSim(config.sim, params, base.seed, base.runs) };
    });
    return { rows, baseParams: base.params };
}
/** 参数扫描（文件版） */
export function runSweep(config, configDir) {
    return sweepRows(resolveParams(config, configDir), config);
}
/** 参数扫描（纯文本版，网页工作台用）：与 runSweep 同核 */
export function runSweepFromText(gameText, config) {
    return sweepRows(resolveParamsFromText(gameText, config), config);
}
