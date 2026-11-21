import { getCurrentInstance, onUnmounted, ref } from 'vue'

export interface HeartbeatOptions {
  /** 心跳间隔（毫秒），默认 25s——需小于中间层（Vite/Nginx/NAT）的空闲超时（通常 60s） */
  intervalMs?: number
  /** start 时是否立即发一次，默认 false（onopen 后服务端已有 PLAYER_JOINED 握手） */
  immediate?: boolean
}

/**
 * WebSocket 心跳保活。
 *
 * 目的只有一个：定时制造流量，重置中间设备的空闲计时器，避免静默连接被掐断。
 * 它不负责"判活/判超时"——连接是否真死了由 onclose + 重连逻辑判断。
 *
 * 用法：连接 onopen 调 start()，onclose 调 stop()；组件卸载自动清理。
 * send 返回 false（连接不可用，如 readyState 非 OPEN）时会自动停表，
 * 避免半开连接上空转；下次 onopen 再 start() 即可恢复。
 */
export function useHeartbeat(send: () => boolean, options: HeartbeatOptions = {}) {
  const intervalMs = options.intervalMs ?? 25_000
  const immediate = options.immediate ?? false
  const running = ref(false)
  let timer: number | null = null

  function tick(): void {
    // 连接不可用则停表，等下一次 start（onopen）重建，避免半开连接空转
    if (!send()) stop()
  }

  function start(): void {
    // 先清后建，保证重连后不会叠加多个定时器
    stop()
    running.value = true
    if (immediate) tick()
    // tick 里 send 失败会 stop() 置 running=false，此时不再建表
    if (running.value) timer = window.setInterval(tick, intervalMs)
  }

  function stop(): void {
    if (timer !== null) {
      window.clearInterval(timer)
      timer = null
    }
    running.value = false
  }

  // 仅在组件 setup 上下文中注册卸载钩子，避免单测里无实例时的告警
  if (getCurrentInstance()) {
    onUnmounted(stop)
  }

  return { start, stop, running }
}
