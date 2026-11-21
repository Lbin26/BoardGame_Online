import { describe, it, expect } from 'vitest'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { findRepoRoot, resolvePaths } from '../src/paths.js'

const repoRoot = path.resolve(fileURLToPath(new URL('../../..', import.meta.url)))

describe('paths', () => {
  it('findRepoRoot 从深层目录向上找到仓库根', () => {
    const deep = fileURLToPath(new URL('../../../tools/boardgame-design/src/schema', import.meta.url))
    expect(findRepoRoot(deep)).toBe(repoRoot)
    expect(findRepoRoot(fileURLToPath(new URL('.', import.meta.url)))).toBe(repoRoot)
  })
  it('findRepoRoot 找不到 → throw', () => {
    expect(() => findRepoRoot('C:/')).toThrow()
  })
  it('resolvePaths env 覆盖生效', () => {
    const p = resolvePaths({ DESIGN_REPO_ROOT: 'X', DESIGN_UI_DIST: 'Y', DESIGN_UPSTREAM: 'Z' })
    expect(p.repoRoot).toBe('X')
    expect(p.uiDistDir).toBe('Y')
    expect(p.upstream).toBe('Z')
    expect(p.samplesDir).toBe(path.join('X', 'design/samples'))
  })
  it('resolvePaths 缺省值', () => {
    const p = resolvePaths({ DESIGN_REPO_ROOT: repoRoot })
    expect(p.upstream).toBe('http://127.0.0.1:8080')
    expect(p.uiDistDir).toBe(path.join(repoRoot, 'boardgame-ui/dist'))
    expect(p.mechanismsMdPath).toBe(path.join(repoRoot, '.claude/skills/design-boardgame/references/mechanisms.md'))
  })
})