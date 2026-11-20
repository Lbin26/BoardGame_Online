import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'

import Register from '@/views/register/index.vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  api: {
    register: vi.fn(),
    getCaptcha: vi.fn(),
  },
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/api/auth', () => ({
  CLIENT_ID: 'e5cd7e4891bf95d1d19206ce24a7b32e',
  register: mocks.api.register,
  getCaptcha: mocks.api.getCaptcha,
}))

enableAutoUnmount(afterEach)

const CAP1 = { captchaEnabled: true, uuid: 'u1', img: 'QUJD' }

function mountRegister() {
  return mount(Register, { global: { plugins: [ElementPlus] } })
}

type Wrapper = ReturnType<typeof mountRegister>

/** input 顺序：用户名/密码/确认密码/验证码（验证码关闭时仅前三个） */
async function fillForm(
  wrapper: Wrapper,
  over: { username?: string; password?: string; confirm?: string; code?: string } = {},
) {
  const inputs = wrapper.findAll('input')
  const password = over.password ?? '12345'
  const values = [over.username ?? 'newuser', password, over.confirm ?? password, over.code ?? '1234']
  for (let i = 0; i < Math.min(inputs.length, 4); i++) {
    await inputs[i].setValue(values[i])
  }
}

function submitButton(wrapper: Wrapper) {
  return wrapper.findAll('button').find((b) => b.text().includes('注册'))
}

async function submit(wrapper: Wrapper) {
  await submitButton(wrapper)!.trigger('click')
  await flushPromises()
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.api.getCaptcha.mockResolvedValue(CAP1)
  mocks.api.register.mockResolvedValue(undefined)
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('register brand', () => {
  it('shows the DiceGo brand wordmark', async () => {
    const wrapper = mountRegister()
    await flushPromises()
    expect(wrapper.find('img[alt="DiceGo"]').exists()).toBe(true)
  })
})

describe('captcha rendering', () => {
  it('renders captcha image as data URI and refreshes on click', async () => {
    const wrapper = mountRegister()
    await flushPromises()
    const img = wrapper.find('img.captcha-img')
    expect(img.attributes('src')).toBe('data:image/png;base64,QUJD')
    await img.trigger('click')
    await flushPromises()
    expect(mocks.api.getCaptcha).toHaveBeenCalledTimes(2)
  })
})

describe('validation parity with RegisterBody', () => {
  it('blocks username shorter than 2', async () => {
    const wrapper = mountRegister()
    await flushPromises()
    await fillForm(wrapper, { username: 'a' })
    await submit(wrapper)
    expect(mocks.api.register).not.toHaveBeenCalled()
  })

  it('blocks password shorter than 5', async () => {
    const wrapper = mountRegister()
    await flushPromises()
    await fillForm(wrapper, { password: '1234' })
    await submit(wrapper)
    expect(mocks.api.register).not.toHaveBeenCalled()
  })

  it('blocks mismatched confirm password with inline field error', async () => {
    const wrapper = mountRegister()
    await flushPromises()
    await fillForm(wrapper, { confirm: '99999' })
    await submit(wrapper)
    expect(mocks.api.register).not.toHaveBeenCalled()
    // EP 行内错误经 refDebounced(100ms) 渲染，等真实 DOM
    await vi.waitFor(() => expect(wrapper.text()).toContain('两次输入的密码不一致'))
  })

  it('accepts boundary lengths username 2/30 and password 5/30', async () => {
    const short = mountRegister()
    await flushPromises()
    await fillForm(short, { username: 'ab', password: '12345' })
    await submit(short)
    expect(mocks.api.register).toHaveBeenCalledTimes(1)

    const long = mountRegister()
    await flushPromises()
    await fillForm(long, { username: 'a'.repeat(30), password: 'p'.repeat(30) })
    await submit(long)
    expect(mocks.api.register).toHaveBeenCalledTimes(2)
  })
})

describe('submit flow', () => {
  it('refreshes captcha after register failure and retry uses new uuid', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mocks.api.getCaptcha
      .mockResolvedValueOnce(CAP1)
      .mockResolvedValue({ captchaEnabled: true, uuid: 'u2', img: 'REVG' })
    mocks.api.register.mockRejectedValueOnce(new Error('验证码错误'))
    const wrapper = mountRegister()
    await flushPromises()
    await fillForm(wrapper, { code: '1234' })
    await submit(wrapper)
    expect(errorSpy).toHaveBeenCalledWith('验证码错误')
    expect(mocks.api.getCaptcha).toHaveBeenCalledTimes(2)
    // 重试必须带刷新后的 uuid（旧 uuid 已被服务端一次性消费）
    await fillForm(wrapper, { code: '5678' })
    await submit(wrapper)
    const last = mocks.api.register.mock.calls.at(-1)![0]
    expect(last.uuid).toBe('u2')
    expect(last.code).toBe('5678')
    errorSpy.mockRestore()
  })

  it('success sends full body, toasts and redirects to login', async () => {
    const successSpy = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as any)
    const wrapper = mountRegister()
    await flushPromises()
    await fillForm(wrapper, { username: 'dave', password: 'dave12345', code: '42' })
    await submit(wrapper)
    expect(mocks.api.register).toHaveBeenCalledWith({
      clientId: 'e5cd7e4891bf95d1d19206ce24a7b32e',
      grantType: 'password',
      username: 'dave',
      password: 'dave12345',
      userType: 'sys_user',
      code: '42',
      uuid: 'u1',
    })
    expect(successSpy).toHaveBeenCalledWith('注册成功，请登录')
    expect(mocks.push).toHaveBeenCalledWith('/login')
    successSpy.mockRestore()
  })

  it('omits captcha fields when captcha disabled', async () => {
    mocks.api.getCaptcha.mockResolvedValue({ captchaEnabled: false, uuid: '', img: '' })
    const wrapper = mountRegister()
    await flushPromises()
    expect(wrapper.find('img.captcha-img').exists()).toBe(false)
    await fillForm(wrapper)
    await submit(wrapper)
    const body = mocks.api.register.mock.calls[0][0]
    expect(body).not.toHaveProperty('code')
    expect(body).not.toHaveProperty('uuid')
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

  it('contains refresh failure after register error (no unhandled rejection)', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mocks.api.getCaptcha.mockResolvedValueOnce(CAP1).mockRejectedValue(new Error('network down'))
    mocks.api.register.mockRejectedValueOnce(new Error('验证码错误'))
    const wrapper = mountRegister()
    await flushPromises()
    await fillForm(wrapper)
    const unhandled = await drainUnhandled(() => submit(wrapper))
    expect(unhandled).toEqual([])
    expect(errorSpy).toHaveBeenCalledWith('验证码错误')
    expect(errorSpy).toHaveBeenCalledWith('验证码刷新失败，请重试')
    expect(wrapper.find('button.is-loading').exists()).toBe(false)
    errorSpy.mockRestore()
  })

  it('hints when initial captcha load fails (no unhandled rejection)', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mocks.api.getCaptcha.mockRejectedValueOnce(new Error('network down'))
    const unhandled = await drainUnhandled(async () => {
      mountRegister()
      await flushPromises()
    })
    expect(unhandled).toEqual([])
    expect(errorSpy).toHaveBeenCalledWith('验证码加载失败，请重试')
    errorSpy.mockRestore()
  })

  it('manual refresh failure hints without unhandled rejection', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    mocks.api.getCaptcha.mockResolvedValueOnce(CAP1).mockRejectedValue(new Error('network down'))
    const wrapper = mountRegister()
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