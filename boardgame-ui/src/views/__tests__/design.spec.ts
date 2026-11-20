import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessage, ElSelect } from 'element-plus'

import Design from '@/views/design/index.vue'

const mocks = vi.hoisted(() => ({
  api: {
    getSamples: vi.fn(),
    getMechanisms: vi.fn(),
    validateGame: vi.fn(),
    runSim: vi.fn(),
    runSweep: vi.fn(),
  },
}))

vi.mock('@/api/design', () => mocks.api)

enableAutoUnmount(afterEach)

const SAMPLE_NAMES = ['璀璨宝石', '工业革命：伯明翰', '王权骰铸', '中国象棋', '围棋', '五子棋', '阿瓦隆']
const SAMPLE_KEYS = ['splendor', 'brass', 'diceThrone', 'xiangqi', 'go', 'gomoku', 'avalon']

function sampleEntries() {
  return SAMPLE_KEYS.map((key, i) => ({
    key,
    text: `meta:\n  key: ${key}\n  name: ${SAMPLE_NAMES[i]}\n`,
    doc: {
      meta: { key, name: SAMPLE_NAMES[i], zone: 'card' },
      mechanisms: ['engine-building'],
      analysis: {
        confidence: 'medium',
        whyFun: `乐趣-${key}`,
        remixable: [`可借鉴-${key}`],
        pitfalls: `坑-${key}`,
        sources: [{ title: '自身知识推理' }],
      },
    },
  }))
}

const MECHS = [
  { key: 'worker-placement', name: '工人放置', nameEn: 'Worker Placement', zone: '行动结构', elements: { 说明: '放置工人抢占行动位', 代表作: ' Agricola', 设计旋钮: '行动位数量', 常见坑: '先手优势', 常见组合: '引擎构建' } },
  { key: 'deck-building', name: '牌库构筑', nameEn: 'Deck Building', zone: '卡牌', elements: { 说明: '局内构筑牌库', 代表作: 'Dominion', 设计旋钮: '购买力曲线', 常见坑: '滚雪球', 常见组合: '池抽' } },
]

function mountDesign() {
  return mount(Design, { global: { plugins: [ElementPlus] } })
}

async function switchTab(wrapper: ReturnType<typeof mountDesign>, label: string) {
  const tab = wrapper.findAll('.el-tabs__item').find((t) => t.text().includes(label))
  await tab!.trigger('click')
  await flushPromises()
}

function buttonByText(wrapper: ReturnType<typeof mountDesign>, text: string) {
  return wrapper.findAll('button').find((b) => b.text().trim() === text)
}

/** jsdom 的 Blob 无 .text()，用 FileReader 读内容 */
function blobText(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => resolve(r.result as string)
    r.onerror = () => reject(r.error)
    r.readAsText(blob)
  })
}

let createObjectURL: ReturnType<typeof vi.fn>
let anchorClick: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  vi.clearAllMocks()
  mocks.api.getSamples.mockResolvedValue(sampleEntries())
  mocks.api.getMechanisms.mockResolvedValue(MECHS)
  // jsdom 无 Blob URL 实现；anchor 点击会触发未实现的导航，一并桩掉
  createObjectURL = vi.fn(() => 'blob:mock')
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() }))
  anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
})

afterEach(() => {
  vi.unstubAllGlobals()
  anchorClick.mockRestore()
})

describe('design 工作台', () => {
  it('渲染三页签与对话式提示；样本卡片名可见', async () => {
    const w = mountDesign()
    await flushPromises()
    const tabs = w.findAll('.el-tabs__item').map((t) => t.text())
    expect(tabs.some((t) => t.includes('样本库'))).toBe(true)
    expect(tabs.some((t) => t.includes('机制库'))).toBe(true)
    expect(tabs.some((t) => t.includes('模拟工作台'))).toBe(true)
    expect(w.text()).toContain('对话式设计请在 Claude Code 使用 /design-boardgame')
    expect(w.findAll('.sample-card')).toHaveLength(7)
    expect(w.text()).toContain('璀璨宝石')
  })

  it('机制搜索按 key/name/nameEn 过滤', async () => {
    const w = mountDesign()
    await flushPromises()
    await switchTab(w, '机制库')
    expect(w.findAll('.mech-item')).toHaveLength(2)
    await w.find('.mech-search input').setValue('工人')
    expect(w.findAll('.mech-item')).toHaveLength(1)
    expect(w.find('.mech-item').text()).toContain('工人放置')
    await w.find('.mech-search input').setValue('Deck Building')
    expect(w.findAll('.mech-item')).toHaveLength(1)
    expect(w.find('.mech-item').text()).toContain('牌库构筑')
  })

  it('选 diceThrone 预置填充两 textarea；xiangqi 预置显示演示警示', async () => {
    const w = mountDesign()
    await flushPromises()
    await switchTab(w, '模拟工作台')
    const select = w.findComponent(ElSelect)
    await select.emit('update:modelValue', 'diceThrone')
    await flushPromises()
    const areas = w.findAll('textarea')
    expect(areas[0].element.value).toContain('diceThrone')
    expect(areas[1].element.value).toContain('conflict')
    expect(w.text()).not.toContain('不构成样本平衡证据')
    await select.emit('update:modelValue', 'xiangqi')
    await flushPromises()
    expect(w.text()).toContain('演示数据，非样本数值——不构成样本平衡证据')
    expect(w.findAll('textarea')[1].element.value).toContain('演示数据，非样本数值')
  })

  it('运行模拟：端点收到双文本，标量表格 + md pre，下载 md 为 Blob', async () => {
    mocks.api.runSim.mockResolvedValue({
      result: { probability: 0.5, note: 'ok' },
      md: '# dice 模拟报告\n\n## 结果\n',
      json: '{"result":{}}',
      seed: 42,
      runs: 100,
    })
    const w = mountDesign()
    await flushPromises()
    await switchTab(w, '模拟工作台')
    const areas = w.findAll('textarea')
    await areas[0].setValue('G')
    await areas[1].setValue('C')
    await buttonByText(w, '运行模拟')!.trigger('click')
    await flushPromises()
    expect(mocks.api.runSim).toHaveBeenCalledWith({ gameText: 'G', configText: 'C' })
    expect(w.find('.result-table').text()).toContain('probability')
    expect(w.find('.result-table').text()).toContain('0.5')
    expect(w.find('.report-md').text()).toContain('# dice 模拟报告')
    await buttonByText(w, '下载 md')!.trigger('click')
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    const blob = createObjectURL.mock.calls[0][0] as Blob
    expect(await blobText(blob)).toContain('# dice 模拟报告')
  })

  it('校验文档：失败列出错误，成功显示通过', async () => {
    mocks.api.validateGame.mockResolvedValue({ ok: false, errors: ['未知机制 key: x'] })
    const w = mountDesign()
    await flushPromises()
    await switchTab(w, '模拟工作台')
    await w.findAll('textarea')[0].setValue('bad')
    await buttonByText(w, '校验文档')!.trigger('click')
    await flushPromises()
    expect(mocks.api.validateGame).toHaveBeenCalledWith('bad')
    expect(w.text()).toContain('未知机制 key: x')
    mocks.api.validateGame.mockResolvedValue({ ok: true, errors: [] })
    await buttonByText(w, '校验文档')!.trigger('click')
    await flushPromises()
    expect(w.text()).toContain('校验通过')
  })

  it('参数扫描：runSweep 被调，md 渲染，json 下载可用', async () => {
    mocks.api.runSweep.mockResolvedValue({
      rows: [
        { value: 3, result: { vp: 1 } },
        { value: 5, result: { vp: 2 } },
      ],
      md: '# 参数扫描报告\n',
      json: '{"rows":[]}',
    })
    const w = mountDesign()
    await flushPromises()
    await switchTab(w, '模拟工作台')
    const areas = w.findAll('textarea')
    await areas[0].setValue('G')
    await areas[1].setValue('C')
    await buttonByText(w, '参数扫描')!.trigger('click')
    await flushPromises()
    expect(mocks.api.runSweep).toHaveBeenCalledWith({ gameText: 'G', configText: 'C' })
    expect(w.find('.report-md').text()).toContain('# 参数扫描报告')
    await buttonByText(w, '下载 json')!.trigger('click')
    const blob = createObjectURL.mock.calls[0][0] as Blob
    expect(await blobText(blob)).toBe('{"rows":[]}')
  })

  it('runSim 失败 → ElMessage.error 展示后端消息', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mocks.api.runSim.mockRejectedValue(new Error('runs 超出上限: 60000 > 50000'))
    const w = mountDesign()
    await flushPromises()
    await switchTab(w, '模拟工作台')
    await buttonByText(w, '运行模拟')!.trigger('click')
    await flushPromises()
    expect(errorSpy).toHaveBeenCalledWith('runs 超出上限: 60000 > 50000')
    errorSpy.mockRestore()
  })
})