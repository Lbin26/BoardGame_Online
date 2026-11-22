import { ref } from 'vue'

export type SfxName = 'flip' | 'bell' | 'buzz'

const STORAGE_KEY = 'dicego.sfx.muted'

/**
 * 对局音效：翻牌 / 抢对 / 抢错。
 *
 * 全部用 Web Audio 合成，不引入音频文件——三种音色都是极短的包络，素材收益远低于维护成本。
 * 浏览器的自动播放策略要求先有用户手势，因此 AudioContext 惰性创建，且 `unlock()`
 * 应在首次点击（入座、开始、抢铃）时调用，否则对手触发的音效会被静默丢弃。
 */
export function useGameSfx(storageKey = STORAGE_KEY) {
  const muted = ref(readMuted(storageKey))
  let ctx: AudioContext | null = null

  function audioCtx(): AudioContext | null {
    if (ctx) return ctx
    if (typeof window === 'undefined') return null
    const Ctor =
      window.AudioContext ??
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Ctor) return null
    ctx = new Ctor()
    return ctx
  }

  function setMuted(value: boolean): void {
    muted.value = value
    try {
      window.localStorage.setItem(storageKey, value ? '1' : '0')
    } catch {
      // 隐私模式下 localStorage 不可写：只影响持久化，不影响本次会话
    }
  }

  function toggle(): void {
    setMuted(!muted.value)
  }

  /** 首次用户手势时调用，解除自动播放限制 */
  function unlock(): void {
    const c = audioCtx()
    if (c && c.state === 'suspended') void c.resume()
  }

  function play(name: SfxName): void {
    if (muted.value) return
    const c = audioCtx()
    if (!c) return
    if (c.state === 'suspended') void c.resume()

    const t = c.currentTime
    switch (name) {
      case 'bell': {
        // 双分音叠加 + 快速指数衰减，模拟金属铃
        const partials: Array<[number, number]> = [
          [1760, 0.28],
          [2640, 0.13],
        ]
        for (const [freq, peak] of partials) {
          const osc = c.createOscillator()
          const gain = c.createGain()
          osc.type = 'sine'
          osc.frequency.setValueAtTime(freq, t)
          gain.gain.setValueAtTime(0.0001, t)
          gain.gain.exponentialRampToValueAtTime(peak, t + 0.008)
          gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.55)
          osc.connect(gain).connect(c.destination)
          osc.start(t)
          osc.stop(t + 0.6)
        }
        break
      }
      case 'buzz': {
        // 下滑音：抢错时的否定反馈，不用刺耳方波
        const osc = c.createOscillator()
        const gain = c.createGain()
        osc.type = 'triangle'
        osc.frequency.setValueAtTime(240, t)
        osc.frequency.exponentialRampToValueAtTime(110, t + 0.22)
        gain.gain.setValueAtTime(0.22, t)
        gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.26)
        osc.connect(gain).connect(c.destination)
        osc.start(t)
        osc.stop(t + 0.3)
        break
      }
      case 'flip': {
        // 极短轻响，避免每次翻牌都抢注意力
        const osc = c.createOscillator()
        const gain = c.createGain()
        osc.type = 'sine'
        osc.frequency.setValueAtTime(600, t)
        gain.gain.setValueAtTime(0.09, t)
        gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.07)
        osc.connect(gain).connect(c.destination)
        osc.start(t)
        osc.stop(t + 0.08)
        break
      }
      default:
        break
    }
  }

  return { muted, play, toggle, setMuted, unlock }
}

function readMuted(storageKey: string): boolean {
  try {
    return window.localStorage.getItem(storageKey) === '1'
  } catch {
    return false
  }
}
