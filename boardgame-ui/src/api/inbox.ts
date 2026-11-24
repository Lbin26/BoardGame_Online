/**
 * 站内私信 / 意见反馈数据层，以 localStorage 持久化。
 *
 * 平台公告不在此处：邮件消息中心与大厅「平台消息」统一走 @/api/notice，
 * 数据源是 boardgame_online.sys_message。本模块只保留玩家私信与管理员意见，
 * 并会清理旧版本遗留的硬编码 system 通知（死通知）。
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

/**
 * 读取本地私信，并顺手清掉旧版本写入的硬编码 system 通知。
 * 平台公告已改由 sys_message 提供，这些遗留条目不能再出现在邮件面板里。
 */
function loadMessages(): InboxMessage[] {
  const raw = safeParse<InboxMessage>(localStorage.getItem(MESSAGES_KEY), [])
  const kept = raw.filter((m) => m.kind !== 'system')
  if (kept.length !== raw.length) persist(MESSAGES_KEY, kept)
  return kept
}

/** 当前用户可见的消息：仅私信（意见走管理员视图，平台公告走 sys_message） */
export function listMessages(userName: string): InboxMessage[] {
  return loadMessages()
    .filter((m) => m.kind !== 'opinion' && (m.to === 'all' || (!!userName && m.to === userName)))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

/** 未读私信数（平台公告未读由 useSystemNotices 单独统计） */
export function countUnread(userName: string): number {
  return listMessages(userName).filter((m) => !m.read).length
}

/** 标记某条消息已读 */
export function markMessageRead(id: string): void {
  const list = loadMessages()
  const next = list.map((m) => (m.id === id ? { ...m, read: true } : m))
  persist(MESSAGES_KEY, next)
}

/** 全部标为已读 */
export function markAllRead(userName: string): void {
  const list = loadMessages()
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
  persist(MESSAGES_KEY, [...loadMessages(), message])
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
  persist(MESSAGES_KEY, [...loadMessages(), message])
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
  return loadMessages()
    .filter((m) => m.kind === 'opinion')
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

/** 管理员标记意见类通知已读 */
export function markAdminMessageRead(id: string): void {
  const list = loadMessages()
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
