import path from 'node:path';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
/** 逐级向上找仓库根（同时含 design/samples 与 tools/boardgame-design），dev/prod 产物布局均适用 */
export function findRepoRoot(startDir) {
    let dir = path.resolve(startDir);
    for (let i = 0; i < 10; i++) {
        if (existsSync(path.join(dir, 'design/samples')) && existsSync(path.join(dir, 'tools/boardgame-design')))
            return dir;
        const parent = path.dirname(dir);
        if (parent === dir)
            break;
        dir = parent;
    }
    throw new Error('找不到仓库根（须含 design/samples 与 tools/boardgame-design）: ' + startDir);
}
/** 数据/上游位置解析；env 名钉死：DESIGN_REPO_ROOT / DESIGN_UI_DIST / DESIGN_UPSTREAM */
export function resolvePaths(env = process.env) {
    const repoRoot = env.DESIGN_REPO_ROOT ?? findRepoRoot(path.dirname(fileURLToPath(import.meta.url)));
    return {
        repoRoot,
        samplesDir: path.join(repoRoot, 'design/samples'),
        mechanismsMdPath: path.join(repoRoot, '.claude/skills/design-boardgame/references/mechanisms.md'),
        uiDistDir: env.DESIGN_UI_DIST ?? path.join(repoRoot, 'boardgame-ui/dist'),
        upstream: env.DESIGN_UPSTREAM ?? 'http://127.0.0.1:8080',
    };
}
