import { beforeEach, describe, expect, it } from 'vitest'

import {
  isNoticeRead,
  markAllNoticesRead,
  markNoticeRead,
  toSystemNotices,
  type MessageBoxVo,
  type SysMessageVo,
} from '@/api/notice'

/** 默认模拟 DiceGo 平台公告；测试隐藏逻辑时显式改成 RuoYi 来源 */
function msg(over: Partial<SysMessageVo>): SysMessageVo {
  return {
    messageId: 1,
    category: 'notice',
    type: 'notice',
    source: 'dicego',
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
  it('只展示 DiceGo 来源的公告与版本通知，按时间倒序', () => {
    const box: MessageBoxVo = {
      noticeList: [
        msg({
          messageId: 10,
          title: '公告兜底标题',
          data: { title: '平台公告', content: '修复若干问题' },
          createTime: '2026-11-23T12:00:00',
        }),
        msg({
          messageId: 30,
          source: 'version',
          title: 'DiceGo 版本更新 · v6.0.1',
          data: { content: '体验优化' },
          createTime: '2026-11-23T14:00:00',
        }),
      ],
      systemList: [],
      workflowList: [],
    }

    const notices = toSystemNotices(box)
    expect(notices).toHaveLength(2)
    expect(notices[0].title).toBe('DiceGo 版本更新 · v6.0.1')
    expect(notices[0].kind).toBe('version')
    expect(notices[1].title).toBe('平台公告')
    expect(notices[1].content).toBe('修复若干问题')
    expect(notices[1].kind).toBe('notice')
  })

  it('隐藏 RuoYi 原生系统消息、通知公告与工作流消息', () => {
    const box: MessageBoxVo = {
      // RuoYi 原生公告：source=notice（不在白名单）
      noticeList: [
        msg({ messageId: 1, source: 'notice', title: 'RuoYi 原生公告' }),
        msg({ messageId: 2, source: 'workflow', title: '工作流消息' }),
        msg({ messageId: 3, source: 'dicego', title: 'DiceGo 公告' }),
      ],
      // RuoYi 原生系统消息整组不消费
      systemList: [msg({ messageId: 4, source: 'backend', category: 'system', title: 'RuoYi 系统消息' })],
      workflowList: [msg({ messageId: 5, source: 'workflow', category: 'workflow', title: 'RuoYi 工作流' })],
    }

    const notices = toSystemNotices(box)
    expect(notices).toHaveLength(1)
    expect(notices[0].title).toBe('DiceGo 公告')
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
