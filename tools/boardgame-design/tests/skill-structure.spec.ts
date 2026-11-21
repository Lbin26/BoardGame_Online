import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../..', import.meta.url))
const skill = readFileSync(root + '/.claude/skills/design-boardgame/SKILL.md', 'utf-8')
const toolbox = readFileSync(root + '/.claude/skills/design-boardgame/references/toolbox.md', 'utf-8')

describe('skill structure', () => {
  it('SKILL.md 含必备段落与 frontmatter name', () => {
    expect(skill).toMatch(/name: design-boardgame/)
    for (const h of ['## 触发','## 设计主流程','## analyze 工作流','## 纪律','## 导出']) expect(skill).toContain(h)
  })
  it('toolbox.md 覆盖全部子命令', () => {
    for (const c of ['validate','dice','cards','economy','conflict','sweep','AD-HOC']) expect(toolbox).toContain(c)
  })
  it('SKILL.md 引用真实存在的路径', () => {
    for (const p of ['design/samples/','design/games/','references/toolbox.md']) expect(skill).toContain(p)
  })
})