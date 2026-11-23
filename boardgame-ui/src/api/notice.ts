/**
 * 系统通知数据层：对接 RuoYi 原生消息盒子与桌游超管发布接口。
 *
 * - 玩家拉取：GET /resource/message/box（noticeList + systemList），全局广播对所有用户可见。
 * - 超管发布：POST /api/dicego/notice/publish（服务端按超管名单校验）。
 * - 已读状态：后端 SysMessageVo 无 read 字段，前端用 localStorage 按 messageId 记录。
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

/** 消息盒子响应 */
export interface MessageBoxVo {
  systemList: SysMessageVo[]
  noticeList: SysMessageVo[]
  workflowList: SysMessageVo[]
}

/** 统一后的通知条目（消息中心消费） */
export interface SystemNotice {
  id: string
  kind: 'system' | 'notice'
  title: string
  content: string
  createdAt: string
  read: boolean
}

/** 拉取当前用户消息盒子（需登录） */
export function fetchMessageBox(): Promise<MessageBoxVo> {
  return request.get('/resource/message/box')
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

/** 把后端消息盒转换成通知列表（notice + system 合并，按时间倒序） */
export function toSystemNotices(box: MessageBoxVo): SystemNotice[] {
  const seen = readSet()
  const map = (m: SysMessageVo, kind: 'system' | 'notice'): SystemNotice => {
    const data = m.data ?? {}
    const title = (typeof data.title === 'string' && data.title) || m.title || m.message || '系统通知'
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
  return [...box.noticeList.map((m) => map(m, 'notice')), ...box.systemList.map((m) => map(m, 'system'))].sort(
    (a, b) => b.createdAt.localeCompare(a.createdAt),
  )
}

/** 超管发布系统通知 */
export function publishSystemNotice(title: string, content: string): Promise<void> {
  return request.post('/api/dicego/notice/publish', { title, content })
}
