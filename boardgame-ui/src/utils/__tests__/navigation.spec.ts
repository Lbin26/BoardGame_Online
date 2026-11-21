import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Router } from 'vue-router'
import { goBackOrLobby } from '@/utils/navigation'

const back = vi.fn()
const push = vi.fn()
const router = { back, push } as unknown as Router

describe('goBackOrLobby', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    back.mockClear()
    push.mockClear()
  })

  it('有历史时回退上一页', () => {
    vi.spyOn(window.history, 'length', 'get').mockReturnValue(3)
    goBackOrLobby(router)
    expect(back).toHaveBeenCalledTimes(1)
    expect(push).not.toHaveBeenCalled()
  })

  it('无历史（深链直达）时回退到大厅', () => {
    vi.spyOn(window.history, 'length', 'get').mockReturnValue(1)
    goBackOrLobby(router)
    expect(push).toHaveBeenCalledWith('/lobby')
    expect(back).not.toHaveBeenCalled()
  })

  it('支持自定义兜底路径', () => {
    vi.spyOn(window.history, 'length', 'get').mockReturnValue(1)
    goBackOrLobby(router, '/rules/xiangqi')
    expect(push).toHaveBeenCalledWith('/rules/xiangqi')
  })
})
