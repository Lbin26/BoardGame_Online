import { resolveParams, resolveParamsFromText, setAtPath, type SweepConfigInput } from '../schema/simConfig.js'
import { runSim, type SimResult } from '../report.js'

export interface SweepOutcome {
  rows: { value: number | string; result: SimResult }[]
  baseParams: Record<string, unknown>
}

/** 共享核：逐值覆盖（不可变），每值同种子保证可比；基础 params 不被污染 */
function sweepRows(base: { params: Record<string, unknown>; seed: number; runs: number }, config: SweepConfigInput): SweepOutcome {
  const rows = config.sweep.values.map(value => {
    const params = setAtPath(base.params, config.sweep.param, value)
    return { value, result: runSim(config.sim, params, base.seed, base.runs) }
  })
  return { rows, baseParams: base.params }
}

/** 参数扫描（文件版） */
export function runSweep(config: SweepConfigInput, configDir: string): SweepOutcome {
  return sweepRows(resolveParams(config, configDir), config)
}

/** 参数扫描（纯文本版，网页工作台用）：与 runSweep 同核 */
export function runSweepFromText(gameText: string, config: SweepConfigInput): SweepOutcome {
  return sweepRows(resolveParamsFromText(gameText, config), config)
}