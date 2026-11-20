import { describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import { useChat } from '../useChat'

describe('useChat', () => {
  it('空白文本不发送', () => {
    const send = vi.fn()
    const { chatText, sendChat } = useChat({ send })
    chatText.value = '   '
    sendChat()
    expect(send).not.toHaveBeenCalled()
    expect(chatText.value).toBe('   ')
  })

  it('非空文本发送并清空输入', () => {
    const send = vi.fn()
    const { chatText, sendChat } = useChat({ send })
    chatText.value = ' 你好 '
    sendChat()
    expect(send).toHaveBeenCalledWith('你好')
    expect(chatText.value).toBe('')
  })

  it('发送失败（send 返回 false）时保留输入不丢失', () => {
    const send = vi.fn(() => false)
    const { chatText, sendChat } = useChat({ send })
    chatText.value = '断线时打的字'
    sendChat()
    expect(send).toHaveBeenCalledWith('断线时打的字')
    expect(chatText.value).toBe('断线时打的字')
  })

  it('收到消息追加到列表', async () => {
    const { chatMessages, addChatMsg } = useChat({ send: vi.fn() })
    addChatMsg('system', '进入房间')
    expect(chatMessages.value).toHaveLength(1)
    expect(chatMessages.value[0]).toEqual({ who: 'system', text: '进入房间', whoClass: 'system' })
  })

  it('whoClass 由 classifyWho 决定', () => {
    const { chatMessages, addChatMsg } = useChat({
      send: vi.fn(),
      classifyWho: (who) => (who === 'alice' ? 'red' : 'spectator'),
    })
    addChatMsg('alice', 'hi')
    addChatMsg('bob', 'yo')
    expect(chatMessages.value[0].whoClass).toBe('red')
    expect(chatMessages.value[1].whoClass).toBe('spectator')
  })

  it('追加后自动滚动到底部', async () => {
    const el = { scrollTop: 0, scrollHeight: 100 }
    const box = ref<HTMLElement | null>(el as unknown as HTMLElement)
    const { addChatMsg } = useChat({ send: vi.fn(), scrollTarget: box })
    addChatMsg('system', 'x')
    await nextTick()
    expect(el.scrollTop).toBe(100)
  })
})
