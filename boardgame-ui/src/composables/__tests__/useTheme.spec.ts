import { beforeEach, describe, expect, it } from 'vitest'

import { applyTheme, readTheme } from '@/composables/useTheme'

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
  })

  it('无记录时默认黑夜', () => {
    expect(readTheme()).toBe('dark')
  })

  it('读取已持久化的白天模式', () => {
    localStorage.setItem('dicego:theme', 'light')
    expect(readTheme()).toBe('light')
  })

  it('非法值按黑夜处理', () => {
    localStorage.setItem('dicego:theme', 'sepia')
    expect(readTheme()).toBe('dark')
  })

  it('applyTheme 同时写入 localStorage 与 html 属性', () => {
    applyTheme('light')
    expect(localStorage.getItem('dicego:theme')).toBe('light')
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')

    applyTheme('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })
})
