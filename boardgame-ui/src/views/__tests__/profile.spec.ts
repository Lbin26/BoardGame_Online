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
  api: { updateProfile: vi.fn() },
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/store/user', () => ({ useUserStore: () => mocks.store }))
vi.mock('@/api/user', () => mocks.api)

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
    mocks.store.fetchProfile.mockResolvedValue(undefined)
  })

  it('hydrates form from store and splits favorite zone keys', async () => {
    const w = mountProfile()
    await flushPromises()
    expect((w.find('input[placeholder="请输入昵称"]').element as HTMLInputElement).value).toBe('小爱')
    expect((w.find('input:disabled').element as HTMLInputElement).value).toBe('alice')
    expect(w.text()).toContain('卡牌区')
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
})
