import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

import Profile from '@/views/profile/index.vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  store: {
    userInfo: {
      userId: 1,
      userName: 'alice',
      nickName: '小爱',
      gender: '1',
      age: 20,
      favGameTypes: 'card,abstract',
    } as Record<string, unknown> | null,
    fetchProfile: vi.fn(),
  },
  api: { updateProfile: vi.fn(), uploadAvatar: vi.fn() },
  matchApi: { getRecentMatches: vi.fn() },
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/store/user', () => ({ useUserStore: () => mocks.store }))
vi.mock('@/api/user', () => mocks.api)
vi.mock('@/api/match', () => mocks.matchApi)
// 战绩卡片经 gameAdapters 反查中文名，这里切断它对三个游戏 api 的依赖链
vi.mock('@/api/xiangqi', () => ({}))
vi.mock('@/api/splendor', () => ({}))
vi.mock('@/api/halliGalli', () => ({}))

enableAutoUnmount(afterEach)

function mountProfile() {
  return mount(Profile, {
    global: {
      plugins: [ElementPlus],
      stubs: { 'el-input-number': { template: '<input />' } },
    },
  })
}

describe('profile page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.store.userInfo = {
      userId: 1,
      userName: 'alice',
      nickName: '小爱',
      gender: '1',
      age: 20,
      favGameTypes: 'card,abstract',
    }
    mocks.api.updateProfile.mockResolvedValue(undefined)
    mocks.api.uploadAvatar.mockResolvedValue({ ossId: 1, url: '/api/avatar/1' })
    mocks.store.fetchProfile.mockResolvedValue(undefined)
    mocks.matchApi.getRecentMatches.mockResolvedValue({ success: true, matches: [] })
  })

  it('hydrates form from store and splits favorite zone keys', async () => {
    const w = mountProfile()
    await flushPromises()
    expect((w.find('input[placeholder="请输入昵称"]').element as HTMLInputElement).value).toBe('小爱')
    expect((w.find('input:disabled').element as HTMLInputElement).value).toBe('alice')
    expect(w.text()).toContain('卡牌构筑')
    const checked = w.findAll('input[type="checkbox"]')
      .filter((cb) => (cb.element as HTMLInputElement).checked)
      .map((cb) => (cb.element as HTMLInputElement).value)
    expect(checked).toEqual(['card', 'abstract'])
  })

  it('submits joined zone keys and refreshes store then navigates', async () => {
    const w = mountProfile()
    await flushPromises()
    await w.find('input[placeholder="请输入昵称"]').setValue('新昵称')

    await w.find('.profile-actions button').trigger('click')
    await flushPromises()

    expect(mocks.api.updateProfile).toHaveBeenCalledWith({
      nickName: '新昵称',
      gender: '1',
      age: 20,
      favGameTypes: 'card,abstract',
    })
    expect(mocks.store.fetchProfile).toHaveBeenCalledOnce()
    expect(mocks.push).toHaveBeenCalledWith('/lobby')
  })

  it('does not submit when nickName is empty', async () => {
    const w = mountProfile()
    await flushPromises()
    await w.find('input[placeholder="请输入昵称"]').setValue('   ')
    await w.find('.profile-actions button').trigger('click')
    await flushPromises()
    expect(mocks.api.updateProfile).not.toHaveBeenCalled()
  })

  it('头像：无 avatarUrl 时显示昵称首字，有则显示图片', async () => {
    const w1 = mountProfile()
    await flushPromises()
    expect(w1.find('.avatar-fallback').exists()).toBe(true)
    expect(w1.find('.avatar-fallback').text()).toBe('小')
    expect(w1.find('.avatar-image').exists()).toBe(false)

    // 有头像地址时改显示图片
    mocks.store.userInfo!.avatarUrl = '/api/avatar/1'
    const w2 = mountProfile()
    await flushPromises()
    expect(w2.find('.avatar-image').exists()).toBe(true)
    expect(w2.find('.avatar-image').attributes('src')).toBe('/api/avatar/1')
    delete mocks.store.userInfo!.avatarUrl
  })

  it('头像：点击头像触发文件选择，选图后上传并刷新资料', async () => {
    const w = mountProfile()
    await flushPromises()
    const clickSpy = vi.spyOn(w.find('.avatar-file-input').element as HTMLInputElement, 'click')
    await w.find('.avatar-button').trigger('click')
    expect(clickSpy).toHaveBeenCalledOnce()

    const file = new File(['x'], 'a.png', { type: 'image/png' })
    const input = w.find('.avatar-file-input')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await flushPromises()

    expect(mocks.api.uploadAvatar).toHaveBeenCalledWith(file)
    expect(mocks.store.fetchProfile).toHaveBeenCalled()
  })

  it('头像：非图片文件被拒绝，不发起上传', async () => {
    const w = mountProfile()
    await flushPromises()
    const file = new File(['x'], 'a.txt', { type: 'text/plain' })
    const input = w.find('.avatar-file-input')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(mocks.api.uploadAvatar).not.toHaveBeenCalled()
  })

  it('最近对局：渲染游戏名与结果', async () => {
    mocks.matchApi.getRecentMatches.mockResolvedValue({
      success: true,
      matches: [
        {
          matchNo: 'M2',
          gameCode: 'halli_galli',
          playerCount: 4,
          seatNo: 1,
          rankNo: 1,
          score: 56,
          win: true,
          status: '2',
          startTime: '2026-11-21T20:00:00',
          endTime: '2026-11-21T20:12:00',
        },
        {
          matchNo: 'M1',
          gameCode: 'splendor',
          playerCount: 2,
          seatNo: 2,
          rankNo: 2,
          score: 9,
          win: false,
          status: '2',
          startTime: '2026-11-20T19:00:00',
          endTime: null,
        },
      ],
    })

    const w = mountProfile()
    await flushPromises()
    const items = w.findAll('.recent-item')
    expect(items).toHaveLength(2)
    // halli_galli 通过 gameAdapters 反查为中文名，而不是直接显示编码
    expect(items[0].find('.recent-game').text()).toBe('德国心脏病 Halli Galli')
    expect(items[0].find('.recent-result').text()).toBe('胜')
    expect(items[0].find('.recent-meta').text()).toContain('4 人')
    expect(items[1].find('.recent-result').text()).toBe('第 2 名')
  })

  it('最近对局：接口失败时按空列表处理', async () => {
    mocks.matchApi.getRecentMatches.mockRejectedValueOnce(new Error('401'))
    const w = mountProfile()
    await flushPromises()
    expect(w.findAll('.recent-item')).toHaveLength(0)
    expect(w.find('.recent-empty').text()).toContain('还没有对局记录')
  })
})
