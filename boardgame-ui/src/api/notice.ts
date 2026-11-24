/**
 * DiceGo 平台通知数据层：唯一数据源是 boardgame_online.sys_message。
 *
 * - 拉取：GET /api/dicego/notice/list，后端直接查询 sys_message，
 *   只返回 DiceGo 公告（source=dicego）与版本更新（source=version）的全局广播。
 * - 邮件消息中心与大厅「平台消息」共用本模块，杜绝各写一套硬编码通知。
 * - 超管发布：POST /api/dicego/notice/publish（服务端按超管名单校验），发布后同样落到 sys_message。
 * - 已读状态：sys_message 无 read 字段，前端用 localStorage 按 messageId 记录。
 */
import { request } from '@/utils/request'

/** 后端 SysMessageVo */
export interface SysMessageVo {
  messageId: number
  category: 'system' | 'notice' | 'workflow'
  type: string
  source: string
  title: string | null
  message: string | null
  content: string | null
  data: Record<string, unknown> | null
  path: string | null
  createTime: string | null
}

/** 统一后的通知条目（消息中心与大厅共用） */
export interface SystemNotice {
  id: string
  /** version=版本更新，notice=平台公告；均为 DiceGo 平台消息 */
  kind: 'version' | 'notice'
  title: string
  content: string
  createdAt: string
  read: boolean
}

/** DiceGo 平台通知的来源白名单；后端已过滤，前端再兜底一次防止接口变更 */
const DICEGO_SOURCES = new Set(['dicego', 'version'])

/** 拉取平台消息：直读后端 sys_message 列表，未登录也可查看 */
export function fetchPlatformNotices(): Promise<SysMessageVo[]> {
  return request.get('/api/dicego/notice/list')
}

/** 系统通知已读记录的本地键 */
const READ_KEY = 'dicego:notice-read'

function readSet(): Set<string> {
  try {
    const raw = localStorage.getItem(READ_KEY)
    const arr: unknown = raw ? JSON.parse(raw) : []
    return new Set(Array.isArray(arr) ? (arr as string[]) : [])
  } catch {
    return new Set()
  }
}

function persistSet(set: Set<string>): void {
  try {
    localStorage.setItem(READ_KEY, JSON.stringify([...set]))
  } catch {
    /* 隐私模式忽略 */
  }
}

/** 是否已读 */
export function isNoticeRead(id: string): boolean {
  return readSet().has(id)
}

/** 标记一条已读 */
export function markNoticeRead(id: string): void {
  const set = readSet()
  set.add(id)
  persistSet(set)
}

/** 全部标记已读 */
export function markAllNoticesRead(ids: string[]): void {
  const set = readSet()
  ids.forEach((id) => set.add(id))
  persistSet(set)
}

/** 把 sys_message 记录转换成 DiceGo 通知列表：仅平台来源，按时间倒序 */
export function toSystemNotices(messages: SysMessageVo[]): SystemNotice[] {
  const seen = readSet()
  const map = (m: SysMessageVo, kind: SystemNotice['kind']): SystemNotice => {
    const data = m.data ?? {}
    const title = (typeof data.title === 'string' && data.title) || m.title || m.message || '平台通知'
    // content 优先取 data.content，其次后端解析出的 content，最后 message
    const content =
      (typeof data.content === 'string' && data.content) ||
      (typeof data.noticeContent === 'string' && data.noticeContent) ||
      m.content ||
      m.message ||
      ''
    return {
      id: String(m.messageId),
      kind,
      title,
      content,
      createdAt: m.createTime ?? '',
      read: seen.has(String(m.messageId)),
    }
  }
  return messages
    .filter((m) => DICEGO_SOURCES.has(m.source))
    .map((m) => map(m, m.source === 'version' ? 'version' : 'notice'))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

/** 超管发布系统通知 */
export function publishSystemNotice(title: string, content: string): Promise<void> {
  return request.post('/api/dicego/notice/publish', { title, content })
}
