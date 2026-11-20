import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'

import Login from '@/views/login/index.vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  route: { query: {} as Record<string, string> },
  store: {
    login: vi.fn(),
  },
  api: {
    getCaptcha: vi.fn(),
  },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
  useRoute: () => mocks.route,
}))
vi.mock('@/store/user', () => ({ useUserStore: () => mocks.store }))
vi.mock('@/api/auth', () => ({
  CLIENT_ID: 'e5cd7e4891bf95d1d19206ce24a7b32e',
  getCaptcha: mocks.api.getCaptcha,
}))

enableAutoUnmount(afterEach)

const CAP1 = { captchaEnabled: true, uuid: 'u1', img: 'QUJD' }

function mountLogin() {
  return mount(Login, { global: { plugins: [ElementPlus] } })
}

type Wrapper = ReturnType<typeof mountLogin>

/** input 顺序：用户名/密码/验证码（验证码关闭时仅前两个） */
async function fillForm(
  wrapper: Wrapper,
  over: { username?: string; password?: string; code?: string } = {},
) {
  const inputs = wrapper.findAll('input')
  const values = [over.username ?? 'alice', over.password ?? 'alice123', over.code ?? '1234']
  for (let i = 0; i < Math.min(inputs.length, 3); i++) {
    await inputs[i].setValue(values[i])
  }
}

function submitButton(wrapper: Wrapper) {
  return wrapper.findAll('button').find((b) => b.text().includes('登录'))
}

async function submit(wrapper: Wrapper) {
  await submitButton(wrapper)!.trigger('click')
  await flushPromises()
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.route.query = {}
  mocks.api.getCaptcha.mockResolvedValue(CAP1)
  mocks.store.login.mockResolvedValue(undefined)
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('login brand', () => {
  it('shows the DiceGo brand wordmark', async () => {
    const wrapper = mountLogin()
    await flushPromises()
    expect(wrapper.find('img[alt="DiceGo"]').exists()).toBe(true)
  })
})

describe('login captcha single-use retry', () => {
  it('refreshes captcha after login failure and retry uses new uuid', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mocks.api.getCaptcha
      .mockResolvedValueOnce(CAP1)
      .mockResolvedValue({ captchaEnabled: true, uuid: 'u2', img: 'REVG' })
    mocks.store.login.mockRejectedValueOnce(new Error('验证码错误'))
    const wrapper = mountLogin()
    await flushPromises()
    await fillForm(wrapper, { code: '1234' })
    await submit(wrapper)
    expect(errorSpy).toHaveBeenCalledWith('验证码错误')
    expect(mocks.api.getCaptcha).toHaveBeenCalledTimes(2)
    // 重试必须带刷新后的 uuid（旧 uuid 已被服务端一次性消费）
    await fillForm(wrapper, { code: '5678' })
    await submit(wrapper)
    const last = mocks.store.login.mock.calls.at(-1)![0]
    expect(last.uuid).toBe('u2')
    expect(last.code).toBe('5678')
    errorSpy.mockRestore()
  })

})

describe('captcha refresh resilience', () => {
  /** 收集测试期间的 unhandled rejection（Node 在宏任务末尾派发） */
  async function drainUnhandled(fn: () => Promise<void>): Promise<unknown[]> {
    const unhandled: unknown[] = []
    const onUnhandled = (e: unknown) => unhandled.push(e)
    process.on('unhandledRejection', onUnhandled)
    try {
      await fn()
      await new Promise((r) => setTimeout(r, 0))
    } finally {
      process.off('unhandledRejection', onUnhandled)
    }
    return unhandled
  }

  it('contains refresh failure after login error (no unhandled rejection)', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mocks.api.getCaptcha.mockResolvedValueOnce(CAP1).mockRejectedValue(new Error('network down'))
    mocks.store.login.mockRejectedValueOnce(new Error('密码错误'))
    const wrapper = mountLogin()
    await flushPromises()
    await fillForm(wrapper)
    const unhandled = await drainUnhandled(() => submit(wrapper))
    expect(unhandled).toEqual([])
    expect(errorSpy).toHaveBeenCalledWith('密码错误')
    expect(errorSpy).toHaveBeenCalledWith('验证码刷新失败，请重试')
    expect(wrapper.find('button.is-loading').exists()).toBe(false)
    errorSpy.mockRestore()
  })

  it('hints when initial captcha load fails (no unhandled rejection)', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mocks.api.getCaptcha.mockRejectedValueOnce(new Error('network down'))
    const unhandled = await drainUnhandled(async () => {
      mountLogin()
      await flushPromises()
    })
    expect(unhandled).toEqual([])
    expect(errorSpy).toHaveBeenCalledWith('验证码加载失败，请重试')
    errorSpy.mockRestore()
  })

  it('manual refresh failure hints without unhandled rejection', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mocks.api.getCaptcha.mockResolvedValueOnce(CAP1).mockRejectedValue(new Error('network down'))
    const wrapper = mountLogin()
    await flushPromises()
    const unhandled = await drainUnhandled(async () => {
      await wrapper.find('img.captcha-img').trigger('click')
      await flushPromises()
    })
    expect(unhandled).toEqual([])
    expect(errorSpy).toHaveBeenCalledWith('验证码刷新失败，请重试')
    errorSpy.mockRestore()
  })
})
