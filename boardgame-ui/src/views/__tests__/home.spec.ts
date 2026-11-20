import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

import Home from '@/views/home/index.vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  store: {
    isLogin: true,
    userInfo: { nickName: '小潭葛' } as { nickName: string } | null,
    fetchProfile: vi.fn(),
    logout: vi.fn(),
  },
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/store/user', () => ({ useUserStore: () => mocks.store }))

enableAutoUnmount(afterEach)

function mountHome() {
  return mount(Home, { global: { plugins: [ElementPlus] } })
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.store.isLogin = true
  mocks.store.userInfo = { nickName: '小潭葛' }
  mocks.store.fetchProfile.mockResolvedValue(undefined)
})

describe('home top bar', () => {
  it('shows nickname without a logout button when logged in', async () => {
    const wrapper = mountHome()
    await flushPromises()
    expect(wrapper.text()).toContain('小潭葛')
    // 登出只保留在大厅页
    expect(wrapper.findAll('button').some((b) => b.text().includes('退出'))).toBe(false)
  })

  it('renders the DiceGo wordmark logo (replaces old text brand)', async () => {
    const wrapper = mountHome()
    await flushPromises()
    expect(wrapper.find('img[alt="DiceGo"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('棋盘在线')
  })

  it('shows login/register buttons when logged out', async () => {
    mocks.store.isLogin = false
    mocks.store.userInfo = null
    const wrapper = mountHome()
    await flushPromises()
    const texts = wrapper.findAll('button').map((b) => b.text())
    expect(texts.some((t) => t.includes('登录'))).toBe(true)
    expect(texts.some((t) => t.includes('注册'))).toBe(true)
  })
})

describe('home game cards', () => {
  it('xiangqi card deep-links into the chess room level', async () => {
    const w = mountHome()
    await flushPromises()
    await w.findAll('.game-card')[0].trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/lobby?zone=abstract&game=xiangqi')
  })
})
