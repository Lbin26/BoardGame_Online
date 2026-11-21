import { getCurrentInstance, onUnmounted, ref } from 'vue'

export interface UseAfkOptions {
  /** 上报回调：active=true 表示恢复操作，false 表示进入待机；返回 false 表示连接不可用 */
  send: (active: boolean) => boolean | void
  /** 多久无操作判定待机（毫秒），默认 60s */
  timeoutMs?: number
  /** 视为"有操作"的 DOM 事件，默认覆盖鼠标/键盘/触摸/滚动 */
  events?: string[]
}

const DEFAULT_EVENTS = ['pointerdown', 'keydown', 'wheel', 'touchstart']

/**
 * 用户"无操作"检测（AFK）。
 *
 * 与心跳职责分离：心跳定时发流量防连接被掐断；本 composable 只关心
 * "真人有没有在操作"——一段时间没有任何交互就上报 AFK，恢复交互上报 ACTIVE。
 * 心跳不属于用户操作，不会重置这里的计时器（它由独立定时器发，不触发 DOM 事件）。
 *
 * 用法：连接 onopen → start()；onclose / 组件卸载 → stop()。
 * 发送任意游戏消息后可调 noteActivity() 让本地状态即时回到活跃
 * （后端收到真实消息也会自动解除待机）。
 */
export function useAfk(options: UseAfkOptions) {
  const timeoutMs = options.timeoutMs ?? 60_000
  const events = options.events ?? DEFAULT_EVENTS

  const afk = ref(false)
  let timer: number | null = null
  let running = false
  let listening = false

  function report(nextAfk: boolean): void {
    if (afk.value === nextAfk) return
    afk.value = nextAfk
    options.send(!nextAfk)
  }

  function resetTimer(): void {
    if (timer !== null) window.clearTimeout(timer)
    timer = window.setTimeout(() => {
      timer = null
      report(true)
    }, timeoutMs)
  }

  /** 有一次用户操作：回到活跃态并重置计时 */
  function noteActivity(): void {
    if (!running) return
    if (afk.value) report(false)
    resetTimer()
  }

  function start(): void {
    if (running) return
    running = true
    if (!listening) {
      events.forEach((e) => window.addEventListener(e, noteActivity, { passive: true }))
      listening = true
    }
    afk.value = false
    resetTimer()
  }

  function stop(): void {
    running = false
    if (timer !== null) {
      window.clearTimeout(timer)
      timer = null
    }
    if (listening) {
      events.forEach((e) => window.removeEventListener(e, noteActivity))
      listening = false
    }
    afk.value = false
  }

  // 仅在组件 setup 上下文中注册卸载钩子，避免单测里无实例时的告警
  if (getCurrentInstance()) onUnmounted(stop)

  return { afk, noteActivity, start, stop }
}
