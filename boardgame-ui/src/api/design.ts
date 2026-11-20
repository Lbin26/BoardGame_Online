import { rawRequest } from '@/utils/request'

/** 样本条目（key + 原始 yaml 文本 + 解析后文档） */
export interface SampleEntry {
  key: string
  text: string
  doc: Record<string, any>
}

/** 机制速查条目（五要素） */
export interface MechanismDetail {
  key: string
  name: string
  nameEn: string
  zone: string
  elements: Record<string, string>
}

/** 单次模拟返回体（md/json 与 CLI 报告逐位一致） */
export interface RunResponse {
  result: Record<string, unknown>
  md: string
  json: string
  seed: number
  runs: number
}

/** 参数扫描返回体 */
export interface SweepResponse {
  rows: { value: number | string; result: Record<string, unknown> }[]
  md: string
  json: string
}

/** 样本列表 */
export function getSamples(): Promise<SampleEntry[]> {
  return rawRequest.get('/design-api/samples')
}

/** 机制速查表 */
export function getMechanisms(): Promise<MechanismDetail[]> {
  return rawRequest.get('/design-api/mechanisms')
}

/** 校验游戏文档文本 */
export function validateGame(text: string): Promise<{ ok: boolean; errors: string[] }> {
  return rawRequest.post('/design-api/validate', { text })
}

/** 运行模拟：timeout 30000 覆盖实例默认 10s（worker 预算 15s，Review Focus #5） */
export function runSim(body: { gameText: string; configText: string }): Promise<RunResponse> {
  return rawRequest.post('/design-api/run', body, { timeout: 30000 })
}

/** 参数扫描：同上，每请求 timeout 30000 */
export function runSweep(body: { gameText: string; configText: string }): Promise<SweepResponse> {
  return rawRequest.post('/design-api/sweep', body, { timeout: 30000 })
}