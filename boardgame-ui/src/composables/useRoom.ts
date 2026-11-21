import { computed, ref } from 'vue'

export interface RoomListItem {
  roomId: string
  /** 象棋行 */
  redPlayerName?: string | null
  blackPlayerName?: string | null
  /** Splendor 行 */
  hostName?: string
  maxPlayers?: number
  seatedCount?: number
  status: string
}

export interface RoomListResult {
  rooms?: RoomListItem[]
  waitingCount?: number
  playingCount?: number
}

export interface UseRoomOptions {
  /** 房间列表接口（各游戏注入自己的 api，如 xiangqiApi.listRooms） */
  listRooms: () => Promise<RoomListResult>
  /** 轮询间隔 ms，默认 5000；传 0 不启动轮询 */
  pollIntervalMs?: number
  /** 非静默失败时把原始错误交给视图（视图负责提取文案并 toast） */
  onError?: (error: unknown) => void
}

/** 房间大厅：列表拉取 + 可见性轮询；创建/加入跳转留在视图（导航各游戏不同） */
export function useRoom(options: UseRoomOptions) {
  const rooms = ref<RoomListItem[]>([])
  const waitingCount = ref(0)
  const playingCount = ref(0)
  const refreshing = ref(false)
  /** 仅手动刷新/首载失败置位；轮询静默失败不置位（契约） */
  const listError = ref(false)

  /** 骨架屏仅在「刷新中且尚无列表」时出现，避免轮询闪骨架 */
  const showSkeleton = computed(
    () => refreshing.value && rooms.value.length === 0 && !listError.value,
  )

  let pollTimer: number | undefined

  async function refresh(silent = false): Promise<void> {
    if (refreshing.value) return
    refreshing.value = true
    try {
      const data = await options.listRooms()
      rooms.value = data.rooms ?? []
      waitingCount.value = data.waitingCount ?? 0
      playingCount.value = data.playingCount ?? 0
      listError.value = false
    } catch (error) {
      if (!silent) {
        listError.value = true
        options.onError?.(error)
      }
    } finally {
      refreshing.value = false
    }
  }

  function onVisible(): void {
    if (document.visibilityState === 'visible') refresh(true)
  }

  /** 立即拉一次 + 轮询（仅页面可见时）+ focus/visibility 静默刷新 */
  function startPolling(): void {
    if (pollTimer !== undefined) return
    refresh()
    const interval = options.pollIntervalMs ?? 5000
    if (interval > 0) {
      pollTimer = window.setInterval(() => {
        if (document.visibilityState === 'visible') refresh(true)
      }, interval)
    }
    window.addEventListener('focus', onVisible)
    document.addEventListener('visibilitychange', onVisible)
  }

  function stopPolling(): void {
    if (pollTimer !== undefined) {
      window.clearInterval(pollTimer)
      pollTimer = undefined
    }
    window.removeEventListener('focus', onVisible)
    document.removeEventListener('visibilitychange', onVisible)
  }

  return { rooms, waitingCount, playingCount, refreshing, listError, showSkeleton, refresh, startPolling, stopPolling }
}
