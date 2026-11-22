/** 白天 / 黑夜主题切换：持久化到 localStorage，并在 <html> 上写 data-theme 驱动 CSS 变量 */

export type ThemeMode = 'dark' | 'light'

const STORAGE_KEY = 'dicego:theme'
/** HTML 根元素上的属性，CSS 通过 [data-theme='light'] 覆盖浅色 token */
const ATTR = 'data-theme'

function isValid(v: unknown): v is ThemeMode {
  return v === 'dark' || v === 'light'
}

/** 读取当前主题；默认黑夜。读取异常（隐私模式等）静默降级为 dark */
export function readTheme(): ThemeMode {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    return isValid(v) ? v : 'dark'
  } catch {
    return 'dark'
  }
}

/** 把主题应用到 <html>，并持久化。页面加载早期由 index.html 内联脚本先应用一次防闪烁 */
export function applyTheme(mode: ThemeMode): void {
  try {
    document.documentElement.setAttribute(ATTR, mode)
    localStorage.setItem(STORAGE_KEY, mode)
  } catch {
    // 存储不可用时仅应用当前文档，不影响使用
    try {
      document.documentElement.setAttribute(ATTR, mode)
    } catch {
      /* 无 DOM（如 SSR/测试无 jsdom）时忽略 */
    }
  }
}
