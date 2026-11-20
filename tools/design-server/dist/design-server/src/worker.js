/** 模拟工作线程：只做数值执行（解析/护栏在主线程）；数值单一来源 = 工具箱 runSim */
import { parentPort, workerData } from 'node:worker_threads';
import { runSim } from '../../boardgame-design/src/report.js';
const job = workerData;
try {
    if (job.kind === 'run') {
        parentPort.postMessage({ ok: true, kind: 'run', result: runSim(job.sim, job.params, job.seed, job.runs) });
    }
    else {
        parentPort.postMessage({
            ok: true,
            kind: 'sweep',
            rows: job.rows.map(r => ({ value: r.value, result: runSim(job.sim, r.params, job.seed, job.runs) })),
        });
    }
}
catch (e) {
    parentPort.postMessage({ ok: false, error: e.message });
}
