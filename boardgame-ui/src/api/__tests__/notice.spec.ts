import { beforeEach, describe, expect, it } from 'vitest'

import {
  isNoticeRead,
  markAllNoticesRead,
  markNoticeRead,
  toSystemNotices,
  type MessageBoxVo,
  type SysMessageVo,
} from '@/api/notice'

function msg(over: Partial<SysMessageVo>): SysMessageVo {
  return {
    messageId: 1,
    category: 'notice',
    type: 'notice',
    source: 'notice',
    title: '默认标题',
    message: '默认消息',
    content: null,
    data: null,
    path: null,
    createTime: '2026-11-23T10:00:00',
    ...over,
  }
}

beforeEach(() => {
  localStorage.clear()
})

describe('toSystemNotices', () => {
  it('从 data 提取 title/content，并按时间倒序合并 notice 与 system', () => {
    const box: MessageBoxVo = {
      noticeList: [
        msg({
          messageId: 10,
          title: '兜底标题',
          data: { title: '版本更新 v6.0.1', content: '修复若干问题' },
          createTime: '2026-11-23T12:00:00',
        }),
      ],
      systemList: [
        msg({
          messageId: 20,
          category: 'system',
          title: '系统消息标题',
          message: '系统消息正文',
          createTime: '2026-11-23T14:00:00',
        }),
      ],
      workflowList: [],
    }

    const notices = toSystemNotices(box)
    expect(notices).toHaveLength(2)
    expect(notices[0].title).toBe('系统消息标题') // 14点在前
    expect(notices[0].content).toBe('系统消息正文')
    expect(notices[0].kind).toBe('system')
    expect(notices[1].title).toBe('版本更新 v6.0.1')
    expect(notices[1].content).toBe('修复若干问题')
    expect(notices[1].kind).toBe('notice')
  })

  it('通知 content 优先 data.content → data.noticeContent → content → message', () => {
    const box: MessageBoxVo = {
      noticeList: [
        msg({ messageId: 1, data: { noticeContent: '来自 noticeContent' }, content: '来自 content', message: '来自 message' }),
        msg({ messageId: 2, data: null, content: '来自 content', message: '来自 message' }),
      ],
      systemList: [],
      workflowList: [],
    }
    const notices = toSystemNotices(box)
    expect(notices.find((n) => n.id === '1')!.content).toBe('来自 noticeContent')
    expect(notices.find((n) => n.id === '2')!.content).toBe('来自 content')
  })
})

describe('notice read state', () => {
  it('markNoticeRead 持久化，markAllNoticesRead 批量标记', () => {
    expect(isNoticeRead('a')).toBe(false)
    markNoticeRead('a')
    expect(isNoticeRead('a')).toBe(true)
    markAllNoticesRead(['b', 'c'])
    expect(isNoticeRead('b')).toBe(true)
    expect(isNoticeRead('c')).toBe(true)
    expect(isNoticeRead('d')).toBe(false)
  })

  it('已读状态影响转换结果', () => {
    markNoticeRead('10')
    const box: MessageBoxVo = {
      noticeList: [msg({ messageId: 10, data: { title: 't', content: 'c' } })],
      systemList: [],
      workflowList: [],
    }
    expect(toSystemNotices(box)[0].read).toBe(true)
  })
})
