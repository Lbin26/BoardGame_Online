import { parse } from 'yaml';
import { Worker } from 'node:worker_threads';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
import { SimConfigSchema, SweepConfigSchema, resolveParamsFromText, setAtPath, } from '../../boardgame-design/src/schema/simConfig.js';
import { HttpError } from './http.js';
import { assertCaps, assertTextSafe, CAPS } from './guard.js';
function parseConfig(raw) {
    try {
        return parse(raw);
    }
    catch (e) {
        throw new HttpError(400, `sim 配置 YAML 解析失败: ${e.message}`);
    }
}
/** 鸭子类型判 ZodError：工具箱与服务各持一份 zod 实例，跨实例 instanceof 恒 false */
function isZodError(e) {
    return e instanceof Error && e.name === 'ZodError' && Array.isArray(e.issues);
}
function schemaParse(schema, raw) {
    try {
        return schema.parse(raw);
    }
    catch (e) {
        if (isZodError(e)) {
            throw new HttpError(400, 'sim 配置非法: ' + e.issues.map(i => (i.path.join('.') || '(根)') + ': ' + i.message).join('; '));
        }
        throw e;
    }
}
function resolve(gameText, config) {
    try {
        return resolveParamsFromText(gameText, config);
    }
    catch (e) {
        if (e instanceof HttpError)
            throw e;
        throw new HttpError(400, e.message);
    }
}
/** 解析 + 护栏全部在主线程完成（400 同步可答）；worker 只做数值执行 */
export function prepareRun(gameText, configText) {
    assertTextSafe(gameText);
    assertTextSafe(configText);
    const config = schemaParse(SimConfigSchema, parseConfig(configText));
    const { params, seed, runs } = resolve(gameText, config);
    assertCaps(config.sim, params, runs);
    return { job: { kind: 'run', sim: config.sim, params, seed, runs }, config };
}
export function prepareSweep(gameText, configText) {
    assertTextSafe(gameText);
    assertTextSafe(configText);
    const config = schemaParse(SweepConfigSchema, parseConfig(configText));
    if (config.sweep.values.length > CAPS.sweepValues) {
        throw new HttpError(400, `sweep.values 超出上限: ${config.sweep.values.length} > ${CAPS.sweepValues}`);
    }
    const base = resolve(gameText, config);
    assertCaps(config.sim, base.params, base.runs);
    const rows = config.sweep.values.map(value => {
        const params = setAtPath(base.params, config.sweep.param, value);
        try {
            assertCaps(config.sim, params, base.runs);
        }
        catch (e) {
            if (e instanceof HttpError)
                throw new HttpError(400, `sweep 值 ${value} 推高参数${e.message}`);
            throw e;
        }
        return { value, params };
    });
    return {
        job: { kind: 'sweep', sim: config.sim, rows, seed: base.seed, runs: base.runs },
        config,
        baseParams: base.params,
    };
}
/** 并发闸 1：同时只跑一个模拟，其余 503 + Retry-After；主线程只做 I/O */
let busy = false;
export async function executeJob(job, budgetMs) {
    if (busy)
        throw new HttpError(503, '模拟进行中，请稍后重试', { 'Retry-After': '1' });
    busy = true;
    try {
        return await runInWorker(job, budgetMs);
    }
    finally {
        busy = false;
    }
}
/** dev/test 源码 .ts：Node 20 worker execArgv 忽略 --import，改 eval 引导在 worker 内 register tsx（与 CLI 同编译路径）；prod .js 直接 spawn */
function spawnWorker(job) {
    // 堆上限把未枚举维度的 OOM 从进程级死亡降级为 worker error → 500 + 闸释放（V8 fatal OOM 默认杀整进程）
    const heapMb = Number(process.env.DESIGN_SIM_HEAP_MB) || 1024;
    const resourceLimits = { maxOldGenerationSizeMb: heapMb };
    const isTs = fileURLToPath(import.meta.url).endsWith('.ts');
    const workerUrl = new URL(isTs ? './worker.ts' : './worker.js', import.meta.url);
    if (!isTs)
        return new Worker(workerUrl, { workerData: job, resourceLimits });
    // require 条件解析到 index.cjs（其内部相对导入在 ESM 下失效）；取同目录 ESM 孪生 index.mjs
    const apiPath = createRequire(import.meta.url).resolve('tsx/esm/api').replace(/\.cjs$/, '.mjs');
    const tsxApi = pathToFileURL(apiPath).href;
    const code = `const { register } = await import(${JSON.stringify(tsxApi)}); register(); await import(${JSON.stringify(workerUrl.href)})`;
    return new Worker(code, { eval: true, workerData: job, resourceLimits });
}
/** worker 派发：墙钟预算超时 terminate → 408；业务错误 → 400；意外退出 → 500 */
function runInWorker(job, budgetMs) {
    return new Promise((resolve, reject) => {
        const worker = spawnWorker(job);
        let settled = false;
        const finish = (fn) => {
            if (settled)
                return;
            settled = true;
            clearTimeout(timer);
            fn();
        };
        const timer = setTimeout(() => {
            void worker.terminate();
            finish(() => reject(new HttpError(408, `模拟超时（${budgetMs}ms 预算）——请降低 runs/回合数后重试`)));
        }, budgetMs);
        worker.on('message', (m) => {
            finish(() => {
                if (m.ok) {
                    resolve(m.kind === 'run' ? { kind: 'run', result: m.result } : { kind: 'sweep', rows: m.rows });
                }
                else {
                    reject(new HttpError(400, m.error ?? '模拟失败'));
                }
            });
            void worker.terminate();
        });
        worker.on('error', e => finish(() => reject(new HttpError(500, e.message))));
        worker.on('exit', code => finish(() => reject(new HttpError(500, `模拟线程意外退出: ${code}`))));
    });
}
