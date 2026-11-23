/**
 * 站内消息 / 意见反馈数据层。
 * 当前以 localStorage 持久化（同一浏览器跨账号可见），接口形状刻意贴近后端 R<T>，
 * 后续接真实接口时只需替换函数实现，调用组件无需改动。
 */

const MESSAGES_KEY = 'dicego:messages'
const OPINIONS_KEY = 'dicego:opinions'

/** 通知分类：system 系统公告 / chat 玩家私信 / opinion 用户意见（管理员可见） */
export type MessageKind = 'system' | 'chat' | 'opinion'

export interface InboxMessage {
  id: string
  kind: MessageKind
  /** 发送者 userName；系统通知为 'system' */
  from: string
  fromName: string
  /** 接收者 userName；'all' 为全体广播，意见为 ''（仅管理员可见） */
  to: string
  title: string
  content: string
  /** ISO 时间字符串 */
  createdAt: string
  read: boolean
}

export interface Opinion {
  id: string
  userName: string
  nickName: string
  content: string
  /** 0 未处理 1 已查看 */
  status: 0 | 1
  createdAt: string
}

function safeParse<T>(raw: string | null, fallback: T[]): T[] {
  if (!raw) return fallback
  try {
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as T[]) : fallback
  } catch {
    return fallback
  }
}

function persist(key: string, list: unknown[]): void {
  try {
    localStorage.setItem(key, JSON.stringify(list))
  } catch {
    // 隐私模式或存储配额不足时静默降级：功能仍在本会话内可用
  }
}

function newId(prefix: string): string {
  const seq = Date.now().toString(36) + Math.random().toString(36).slice(2, 7)
  return `${prefix}-${seq}`
}

function seedIfEmpty(): void {
  if (localStorage.getItem(MESSAGES_KEY) !== null) return
  const now = Date.now()
  const seed: InboxMessage[] = [
    {
      id: newId('sys'),
      kind: 'system',
      from: 'system',
      fromName: 'DiceGo 平台',
      to: 'all',
      title: '欢迎来到 DiceGo',
      content: '选一款桌游开局，或在右下角「发送意见」告诉我们你的想法。',
      createdAt: new Date(now - 3_600_000).toISOString(),
      read: false,
    },
    {
      id: newId('sys'),
      kind: 'system',
      from: 'system',
      fromName: 'DiceGo 平台',
      to: 'all',
      title: '胡闹运动会已上线',
      content: '轮抽选手、掷骰竞速，2–6 人同场开跑，快去派对区开一局。',
      createdAt: new Date(now - 86_400_000).toISOString(),
      read: true,
    },
  ]
  persist(MESSAGES_KEY, seed)
}

/** 当前用户可见的消息：全体广播 + 发给自己的私信（不含意见，意见走管理员视图） */
export function listMessages(userName: string): InboxMessage[] {
  seedIfEmpty()
  return safeParse<InboxMessage>(localStorage.getItem(MESSAGES_KEY), [])
    .filter((m) => m.kind !== 'opinion' && (m.to === 'all' || (!!userName && m.to === userName)))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

/** 未读通知数（全体广播未读也算在内） */
export function countUnread(userName: string): number {
  return listMessages(userName).filter((m) => !m.read).length
}

/** 标记某条消息已读 */
export function markMessageRead(id: string): void {
  const list = safeParse<InboxMessage>(localStorage.getItem(MESSAGES_KEY), [])
  const next = list.map((m) => (m.id === id ? { ...m, read: true } : m))
  persist(MESSAGES_KEY, next)
}

/** 全部标为已读 */
export function markAllRead(userName: string): void {
  const list = safeParse<InboxMessage>(localStorage.getItem(MESSAGES_KEY), [])
  const next = list.map((m) =>
    !m.read && (m.to === 'all' || (!!userName && m.to === userName)) ? { ...m, read: true } : m,
  )
  persist(MESSAGES_KEY, next)
}

/** 发送私信：接收方按 userName 精确匹配，不存在也能落库（登录后可见） */
export function sendMessage(
  from: string,
  fromName: string,
  to: string,
  content: string,
): InboxMessage {
  seedIfEmpty()
  const message: InboxMessage = {
    id: newId('msg'),
    kind: 'chat',
    from,
    fromName: fromName || from,
    to,
    title: '私信',
    content,
    createdAt: new Date().toISOString(),
    read: false,
  }
  persist(MESSAGES_KEY, [...safeParse<InboxMessage>(localStorage.getItem(MESSAGES_KEY), []), message])
  return message
}

/** 提交意见：同时写入消息表（供管理员在通知中心看到）与意见表（供管理员意见页查看） */
export function sendOpinion(userName: string, nickName: string, content: string): Opinion {
  const opinion: Opinion = {
    id: newId('op'),
    userName,
    nickName: nickName || userName,
    content,
    status: 0,
    createdAt: new Date().toISOString(),
  }
  persist(OPINIONS_KEY, [...listOpinions(), opinion])

  seedIfEmpty()
  const message: InboxMessage = {
    id: newId('op-msg'),
    kind: 'opinion',
    from: userName,
    fromName: nickName || userName,
    to: '',
    title: '收到一条新意见',
    content,
    createdAt: opinion.createdAt,
    read: false,
  }
  persist(MESSAGES_KEY, [...safeParse<InboxMessage>(localStorage.getItem(MESSAGES_KEY), []), message])
  return opinion
}

/** 管理员视角：所有意见（含来自所有用户的意见） */
export function listOpinions(): Opinion[] {
  return safeParse<Opinion>(localStorage.getItem(OPINIONS_KEY), []).sort((a, b) =>
    b.createdAt.localeCompare(a.createdAt),
  )
}

/** 管理员视角：所有意见类通知 */
export function listAdminMessages(): InboxMessage[] {
  seedIfEmpty()
  return safeParse<InboxMessage>(localStorage.getItem(MESSAGES_KEY), [])
    .filter((m) => m.kind === 'opinion')
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

/** 管理员标记意见类通知已读 */
export function markAdminMessageRead(id: string): void {
  const list = safeParse<InboxMessage>(localStorage.getItem(MESSAGES_KEY), [])
  persist(
    MESSAGES_KEY,
    list.map((m) => (m.id === id ? { ...m, read: true } : m)),
  )
}

/** 管理员把某条意见标记为已查看 */
export function markOpinionHandled(id: string): void {
  const list = safeParse<Opinion>(localStorage.getItem(OPINIONS_KEY), [])
  persist(
    OPINIONS_KEY,
    list.map((o) => (o.id === id ? { ...o, status: 1 } : o)),
  )
}
