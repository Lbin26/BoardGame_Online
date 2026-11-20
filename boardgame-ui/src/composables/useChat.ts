import { nextTick, ref, type Ref } from 'vue'

export interface ChatMessage {
  who: string
  text: string
  whoClass: string
}

export interface UseChatOptions {
  /** 实际发送函数（如 WS send）；返回 false 表示未发出（如连接断开），输入保留不丢失 */
  send: (text: string) => boolean | void
  /** 聊天身份样式（红方/黑方/旁观/系统），默认按 who 映射 */
  classifyWho?: (who: string) => string
  /** 消息容器，追加后自动滚到底部 */
  scrollTarget?: Ref<HTMLElement | null>
}

/** 对局内聊天：消息列表 + 输入框 + 空白文本不发 */
export function useChat(options: UseChatOptions) {
  const chatMessages = ref<ChatMessage[]>([])
  const chatText = ref('')

  function addChatMsg(who: string, text: string): void {
    const whoClass = options.classifyWho
      ? options.classifyWho(who)
      : who === 'system'
        ? 'system'
        : 'spectator'
    chatMessages.value.push({ who, text, whoClass })
    nextTick(() => {
      const el = options.scrollTarget?.value
      if (el) el.scrollTop = el.scrollHeight
    })
  }

  function sendChat(): void {
    const text = chatText.value.trim()
    if (!text) return
    if (options.send(text) === false) return
    chatText.value = ''
  }

  return { chatMessages, chatText, addChatMsg, sendChat }
}
