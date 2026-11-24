/**
 * DiceGo 平台通知：轮询后端 sys_message 列表，聚合未读数，提供已读与发布能力。
 * 邮件消息中心与大厅「平台消息」共用本 composable，保证两处看到同一份数据。
 * 公告实时性要求不高，采用 30 秒轮询即可；列表接口公开，未登录也能看平台消息。
 */
import { onMounted, onUnmounted, ref, type Ref } from 'vue'

import {
  fetchPlatformNotices,
  isNoticeRead,
  markAllNoticesRead,
  markNoticeRead,
  publishSystemNotice,
  toSystemNotices,
  type SystemNotice,
} from '@/api/notice'

const POLL_MS = 30_000

export interface UseSystemNoticesOptions {
  /** 轮询间隔（毫秒），测试可缩短 */
  intervalMs?: number
}

export function useSystemNotices(options: UseSystemNoticesOptions = {}) {
  const notices: Ref<SystemNotice[]> = ref([])
  const loading = ref(false)
  const unread = ref(0)
  const lastError = ref('')

  let timer: number | null = null

  function recomputeUnread(): void {
    unread.value = notices.value.filter((n) => !n.read).length
  }

  async function refresh(): Promise<void> {
    loading.value = true
    lastError.value = ''
    try {
      const rows = await fetchPlatformNotices()
      notices.value = toSystemNotices(rows)
      recomputeUnread()
    } catch (e) {
      lastError.value = e instanceof Error ? e.message : '加载通知失败'
      // 拉取失败保留旧列表，不打扰用户
    } finally {
      loading.value = false
    }
  }

  function openNotice(n: SystemNotice): void {
    if (!n.read) {
      markNoticeRead(n.id)
      n.read = true
      recomputeUnread()
    }
  }

  function readAll(): void {
    markAllNoticesRead(notices.value.map((n) => n.id))
    notices.value.forEach((n) => (n.read = true))
    recomputeUnread()
  }

  async function publish(title: string, content: string): Promise<void> {
    await publishSystemNotice(title, content)
    await refresh()
  }

  function startPolling(): void {
    stopPolling()
    timer = window.setInterval(() => void refresh(), options.intervalMs ?? POLL_MS)
  }

  function stopPolling(): void {
    if (timer !== null) {
      window.clearInterval(timer)
      timer = null
    }
  }

  onMounted(() => {
    void refresh()
    startPolling()
  })

  onUnmounted(stopPolling)

  return { notices, loading, unread, lastError, refresh, openNotice, readAll, publish, startPolling, stopPolling, isNoticeRead }
}
