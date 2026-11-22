import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGameSfx } from '@/composables/useGameSfx'

/** 最小 AudioContext 假实现：只覆盖 composable 用到的那几个方法 */
class FakeParam {
  setValueAtTime = vi.fn()
  exponentialRampToValueAtTime = vi.fn()
}

class FakeNode {
  type = ''
  frequency = new FakeParam()
  gain = new FakeParam()
  start = vi.fn()
  stop = vi.fn()
  connect = vi.fn((target: unknown) => target)
}

class FakeCtx {
  state: AudioContextState = 'running'
  currentTime = 0
  destination = {}
  oscillators: FakeNode[] = []
  resumes = 0

  createOscillator(): FakeNode {
    const node = new FakeNode()
    this.oscillators.push(node)
    return node
  }

  createGain(): FakeNode {
    return new FakeNode()
  }

  resume(): Promise<void> {
    this.resumes += 1
    this.state = 'running'
    return Promise.resolve()
  }
}

function installAudio(ctx: FakeCtx | null): void {
  const w = window as unknown as { AudioContext?: unknown }
  if (ctx) {
    w.AudioContext = function AudioContextMock() {
      return ctx
    }
  } else {
    delete w.AudioContext
  }
}

describe('useGameSfx', () => {
  let ctx: FakeCtx

  beforeEach(() => {
    window.localStorage.clear()
    ctx = new FakeCtx()
    installAudio(ctx)
  })

  it('抢对铃声由两个分音叠加，且有起停', () => {
    const sfx = useGameSfx()
    sfx.play('bell')

    expect(ctx.oscillators).toHaveLength(2)
    expect(ctx.oscillators.every((o) => o.start.mock.calls.length === 1)).toBe(true)
    expect(ctx.oscillators.every((o) => o.stop.mock.calls.length === 1)).toBe(true)
    // 高频分音是铃的特征
    expect(ctx.oscillators[0].frequency.setValueAtTime).toHaveBeenCalledWith(1760, 0)
  })

  it('抢错是单音下滑，不是叠加', () => {
    const sfx = useGameSfx()
    sfx.play('buzz')

    expect(ctx.oscillators).toHaveLength(1)
    expect(ctx.oscillators[0].frequency.exponentialRampToValueAtTime).toHaveBeenCalled()
  })

  it('翻牌是最轻的一次单音', () => {
    const sfx = useGameSfx()
    sfx.play('flip')

    expect(ctx.oscillators).toHaveLength(1)
  })

  it('静音后不再创建任何音频节点', () => {
    const sfx = useGameSfx()
    sfx.setMuted(true)
    sfx.play('bell')

    expect(ctx.oscillators).toHaveLength(0)
  })

  it('静音状态写入 localStorage 并可被下一次实例读回', () => {
    const first = useGameSfx()
    first.toggle()
    expect(window.localStorage.getItem('dicego.sfx.muted')).toBe('1')

    const second = useGameSfx()
    expect(second.muted.value).toBe(true)
  })

  it('未支持 Web Audio 时静默降级，不抛异常', () => {
    installAudio(null)
    const sfx = useGameSfx()

    expect(() => sfx.play('bell')).not.toThrow()
    expect(() => sfx.unlock()).not.toThrow()
  })

  it('上下文挂起时播放会先恢复，避免对手触发的音效被丢弃', () => {
    ctx.state = 'suspended'
    const sfx = useGameSfx()
    sfx.play('flip')

    expect(ctx.resumes).toBe(1)
  })

  it('unlock 只在挂起时恢复', () => {
    const sfx = useGameSfx()
    sfx.unlock()
    expect(ctx.resumes).toBe(0)

    ctx.state = 'suspended'
    sfx.unlock()
    expect(ctx.resumes).toBe(1)
  })
})
