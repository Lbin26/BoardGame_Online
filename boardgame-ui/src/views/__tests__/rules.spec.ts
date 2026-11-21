import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import Rules from '@/views/rules/index.vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  back: vi.fn(),
  gameKey: 'splendor',
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { gameKey: mocks.gameKey } }),
  useRouter: () => ({ push: mocks.push, back: mocks.back }),
}))

function mountRules(key = 'splendor') {
  mocks.gameKey = key
  return mount(Rules)
}

describe('rules page', () => {
  it('只显示「返回」按钮，不出现「返回大厅」', async () => {
    const w = mountRules('splendor')
    await flushPromises()
    const texts = w.findAll('button').map((b) => b.text().trim())
    expect(texts).toContain('← 返回')
    expect(texts.join(' ')).not.toContain('返回大厅')
  })

  it('渲染规则书章节与目录', async () => {
    const w = mountRules('splendor')
    await flushPromises()
    expect(w.text()).toContain('璀璨宝石 Splendor')
    expect(w.text()).toContain('游戏目标')
    expect(w.findAll('.rule-section').length).toBeGreaterThanOrEqual(3)
  })

  it('未知游戏显示兜底且按钮同为「返回」', async () => {
    const w = mountRules('unknown')
    await flushPromises()
    expect(w.text()).toContain('暂无')
    expect(w.findAll('button').map((b) => b.text().trim())).toEqual(['← 返回', '返回'])
  })
})
