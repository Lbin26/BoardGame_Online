import http from 'node:http';
import { readFileSync } from 'node:fs';
import { resolvePaths } from './paths.js';
import { HttpError, sendJson, readBody } from './http.js';
import { loadSamples } from './samples.js';
import { parseMechanismsMd } from './mechanisms.js';
import { validateText } from '../../boardgame-design/src/schema/load.js';
import { renderReport } from '../../boardgame-design/src/report.js';
import { CAPS, assertTextSafe } from './guard.js';
import { prepareRun, prepareSweep, executeJob } from './simJobs.js';
import { serveStatic, proxyHttp, proxyUpgrade } from './proxy.js';
async function parseJsonBody(req) {
    const text = await readBody(req, CAPS.bodyBytes);
    let body;
    try {
        body = JSON.parse(text);
    }
    catch {
        throw new HttpError(400, '请求体须为合法 JSON');
    }
    if (typeof body !== 'object' || body === null || Array.isArray(body)) {
        throw new HttpError(400, '请求体须为 JSON 对象');
    }
    return body;
}
const PROXY_PREFIXES = ['/api', '/auth', '/system'];
/** 手写路由（端点少，零框架）；serveUi 时：/design-api 本地，/api /auth /system 反代，其余静态+SPA */
export function createApp(opts = {}) {
    const paths = opts.paths ?? resolvePaths();
    const budgetMs = opts.simBudgetMs ?? (Number(process.env.DESIGN_SIM_BUDGET_MS) || 15000);
    const getRoutes = {
        'GET /design-api/health': () => ({ ok: true }),
        'GET /design-api/samples': () => loadSamples(paths.samplesDir),
        'GET /design-api/mechanisms': () => parseMechanismsMd(readFileSync(paths.mechanismsMdPath, 'utf-8')),
    };
    async function handle(req, res) {
        const pathname = new URL(req.url ?? '/', 'http://localhost').pathname;
        const get = getRoutes[`${req.method} ${pathname}`];
        if (get) {
            sendJson(res, 200, get());
            return;
        }
        if (req.method === 'POST' && pathname === '/design-api/validate') {
            const body = await parseJsonBody(req);
            if (typeof body.text !== 'string')
                throw new HttpError(400, 'text 须为字符串');
            assertTextSafe(body.text);
            const r = validateText(body.text);
            sendJson(res, 200, r.ok ? { ok: true, errors: [] } : { ok: false, errors: r.errors });
            return;
        }
        if (req.method === 'POST' && (pathname === '/design-api/run' || pathname === '/design-api/sweep')) {
            const body = await parseJsonBody(req);
            if (typeof body.gameText !== 'string' || typeof body.configText !== 'string') {
                throw new HttpError(400, 'gameText 与 configText 须为字符串');
            }
            if (pathname === '/design-api/run') {
                const { job, config } = prepareRun(body.gameText, body.configText);
                const outcome = await executeJob(job, budgetMs);
                if (outcome.kind !== 'run')
                    throw new HttpError(500, '模拟结果类型不符');
                const { md, json } = renderReport({ title: `${job.sim} 模拟报告`, config, params: job.params, result: outcome.result });
                sendJson(res, 200, { result: outcome.result, md, json, seed: job.seed, runs: job.runs });
            }
            else {
                const { job, config, baseParams } = prepareSweep(body.gameText, body.configText);
                const outcome = await executeJob(job, budgetMs);
                if (outcome.kind !== 'sweep')
                    throw new HttpError(500, '扫描结果类型不符');
                const { md, json } = renderReport({ title: '参数扫描报告', config, params: baseParams, result: { rows: outcome.rows } });
                sendJson(res, 200, { rows: outcome.rows, md, json });
            }
            return;
        }
        throw new HttpError(404, `未知路径: ${req.method} ${pathname}`);
    }
    const server = http.createServer((req, res) => {
        if (opts.serveUi) {
            // 静态/反代分支同步抛错（如非法 URL 目标）统一兜底，绝不崩进程
            try {
                const pathname = new URL(req.url ?? '/', 'http://localhost').pathname;
                if (PROXY_PREFIXES.some(p => pathname.startsWith(p))) {
                    proxyHttp(req, res, paths.upstream);
                    return;
                }
                if (!pathname.startsWith('/design-api')) {
                    serveStatic(req, res, paths.uiDistDir);
                    return;
                }
            }
            catch (e) {
                if (res.headersSent)
                    res.destroy();
                else
                    sendJson(res, 500, { error: e.message });
                return;
            }
        }
        handle(req, res).catch(e => {
            if (res.headersSent) {
                res.destroy();
                return;
            }
            if (e instanceof HttpError) {
                sendJson(res, e.status, { error: e.message }, e.headers);
                return;
            }
            sendJson(res, 500, { error: e.message });
        });
    });
    if (opts.serveUi) {
        server.on('upgrade', (req, socket, head) => {
            try {
                const pathname = new URL(req.url ?? '/', 'http://localhost').pathname;
                if (pathname.startsWith('/ws'))
                    proxyUpgrade(req, socket, head, paths.upstream);
                else
                    socket.destroy();
            }
            catch {
                socket.destroy();
            }
        });
    }
    return server;
}
