import { getCurrentInstance, onUnmounted, ref } from 'vue'

/**
 * 骰子翻滚动画（纯前端表演，服务端权威）：
 * 收到 DICE_RESULT 后调用 rollTo(finalValue)，先高频随机换面滚动约 700ms，
 * 再减速定格到服务端给的真实点数。网络只传一次最终值，翻滚帧全在本地生成。
 *
 * - face：当前朝上的点数（1-6），供组件渲染
 * - rolling：是否正在翻滚，供组件加 CSS 旋转动画类
 * - settleVersion：定格一次自增，组件可据此触发回弹/音效收尾
 */
export function useDiceRoll(options: { rollMs?: number; tickMs?: number } = {}) {
  const rollMs = options.rollMs ?? 720
  const tickMs = options.tickMs ?? 80

  const face = ref<number>(1)
  const rolling = ref(false)
  const settleVersion = ref(0)

  let tickTimer: number | undefined
  let settleTimer: number | undefined

  function clearTimers(): void {
    if (tickTimer !== undefined) window.clearInterval(tickTimer)
    if (settleTimer !== undefined) window.clearTimeout(settleTimer)
    tickTimer = undefined
    settleTimer = undefined
  }

  function randomFace(): number {
    return 1 + Math.floor(Math.random() * 6)
  }

  /** 开始翻滚并在结束时定格到 finalValue；重复调用会取消上一次翻滚 */
  function rollTo(finalValue: number): void {
    clearTimers()
    const target = clampFace(finalValue)
    rolling.value = true
    face.value = randomFace()

    tickTimer = window.setInterval(() => {
      face.value = randomFace()
    }, tickMs)

    settleTimer = window.setTimeout(() => {
      if (tickTimer !== undefined) window.clearInterval(tickTimer)
      tickTimer = undefined
      face.value = target
      rolling.value = false
      settleVersion.value++
      settleTimer = undefined
    }, rollMs)
  }

  /** 直接定格（例如 STATE_SYNC 恢复、房间关闭时停止动画） */
  function settle(value: number): void {
    clearTimers()
    face.value = clampFace(value)
    rolling.value = false
    settleVersion.value++
  }

  function stop(): void {
    clearTimers()
    rolling.value = false
  }

  // 仅在组件 setup 上下文注册卸载清理；单测直接调用时 getCurrentInstance 为 null
  if (getCurrentInstance()) onUnmounted(clearTimers)

  return { face, rolling, settleVersion, rollTo, settle, stop }
}

function clampFace(value: number): number {
  return Number.isInteger(value) && value >= 1 && value <= 6 ? value : 1
}
