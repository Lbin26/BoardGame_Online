import { beforeEach, describe, expect, it } from 'vitest'

import {
  countUnread,
  listAdminMessages,
  listMessages,
  listOpinions,
  markAllRead,
  markMessageRead,
  markOpinionHandled,
  sendMessage,
  sendOpinion,
} from '@/api/inbox'

beforeEach(() => {
  localStorage.clear()
})

describe('inbox messages', () => {
  it('不再注入硬编码系统通知', () => {
    expect(listMessages('alice')).toHaveLength(0)
  })

  it('清理旧版本遗留的 system 死通知', () => {
    localStorage.setItem(
      'dicego:messages',
      JSON.stringify([
        {
          id: 'sys-old',
          kind: 'system',
          from: 'system',
          fromName: 'DiceGo 平台',
          to: 'all',
          title: '胡闹运动会已上线',
          content: '死通知',
          createdAt: new Date().toISOString(),
          read: false,
        },
      ]),
    )

    expect(listMessages('alice')).toHaveLength(0)
    expect(JSON.parse(localStorage.getItem('dicego:messages') ?? '[]')).toHaveLength(0)
  })

  it('sendMessage is visible to recipient, not to others; unread count works', () => {
    sendMessage('alice', '小爱', 'bob', '来一局象棋')
    expect(countUnread('bob')).toBe(1)
    expect(listMessages('bob').some((m) => m.content === '来一局象棋')).toBe(true)
    expect(listMessages('carol').some((m) => m.content === '来一局象棋')).toBe(false)

    const msg = listMessages('bob').find((m) => m.content === '来一局象棋')!
    markMessageRead(msg.id)
    expect(listMessages('bob').find((m) => m.id === msg.id)!.read).toBe(true)
  })

  it('markAllRead clears unread for current user', () => {
    sendMessage('alice', '小爱', 'bob', 'hello')
    markAllRead('bob')
    expect(countUnread('bob')).toBe(0)
  })
})

describe('opinions', () => {
  it('submits an opinion visible to the admin view and marks handled', () => {
    const opinion = sendOpinion('alice', '小爱', '希望增加UNO')
    expect(listOpinions().map((o) => o.content)).toContain('希望增加UNO')

    const adminMsgs = listAdminMessages()
    expect(adminMsgs.length).toBe(1)
    expect(adminMsgs[0].kind).toBe('opinion')
    expect(adminMsgs[0].fromName).toBe('小爱')

    markOpinionHandled(opinion.id)
    expect(listOpinions()[0].status).toBe(1)
  })

  it('opinions do not enter normal users notice list', () => {
    sendOpinion('alice', '小爱', '意见内容')
    expect(listMessages('alice').some((m) => m.kind === 'opinion')).toBe(false)
  })
})
