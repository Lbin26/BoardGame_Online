import { getCurrentInstance, onMounted, onUnmounted, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { rememberLastRoom, type LastRoomGame } from '@/composables/useLastRoom'

/** 房间接口的最小公共形状：两个游戏的 getRoom 都满足 */
export interface RoomEntryResponse {
  success: boolean
  error?: string
}

export interface UseRoomEntryOptions<T extends RoomEntryResponse> {
  /** 游戏 key，写入「上次所在房间」记录 */
  gameKey: LastRoomGame
  /** 未登录时的回跳路径（携带 roomId），如 id => `/splendor/${id}` */
  loginRedirect: (roomId: string) => string
  /** 拉取房间详情 */
  fetchRoom: (roomId: string, playerName: string) => Promise<T>
  /** 除 success 外的额外有效性校验（如 splendor 要求 data.room 存在） */
  isValid?: (data: T) => boolean
  /** 把房间数据写进页面状态 */
  applyRoom: (data: T) => void
  /** 连接 WS 前的游戏专属初始化（如象棋的选座面板开合） */
  afterRoom?: (data: T) => void
  /** 与 useGameWebSocket 的 playerName getter 共享同一个 ref */
  playerName: Ref<string>
  /** useGameWebSocket 暴露的 connect */
  connect: (roomId: string, playerName: string) => void
}

export interface RoomEntryApi {
  /** 手动执行进房引导；组件 setup 中会自动挂到 onMounted */
  enter: () => Promise<void>
}

/**
 * 对局页「进房引导」骨架。
 *
 * 收拢两页逐字重复的登录校验、profile 补齐、玩家名校验、getRoom、
 * 失败回退、applyRoom、记住房间、触发 WS 连接。游戏差异通过配置注入，
 * 不在这里碰心跳/AFK/重连——那些属于 useGameWebSocket。
 *
 * 关键约定：
 * - `playerName` 传 ref，写入后再 connect，否则 WS URL 会带空玩家名；
 * - 每次 await 之后都要检查 unmounted，避免用户已离开还建连；
 * - 未登录回跳 login（带 redirect），其余失败回退 lobby。
 */
export function useRoomEntry<T extends RoomEntryResponse>(options: UseRoomEntryOptions<T>): RoomEntryApi {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()
  let unmounted = false

  async function enter(): Promise<void> {
    const roomId = route.params.roomId as string

    if (!userStore.isLogin) {
      router.replace('/login?redirect=' + encodeURIComponent(options.loginRedirect(roomId)))
      return
    }

    // 刷新后 store 仅水合了 token，需先补齐 userInfo 才能取到 userName
    if (!userStore.userInfo) {
      try {
        await userStore.fetchProfile()
      } catch {
        // 下方统一按 userName 缺失处理
      }
    }
    if (unmounted) return

    const playerName = userStore.userInfo?.userName ?? ''
    if (!playerName) {
      ElMessage.error('用户信息未就绪，请重新登录')
      router.replace('/lobby')
      return
    }
    options.playerName.value = playerName

    let data: T
    try {
      data = await options.fetchRoom(roomId, playerName)
    } catch {
      ElMessage.error('进入房间失败')
      router.replace('/lobby')
      return
    }

    if (!data?.success || (options.isValid && !options.isValid(data))) {
      ElMessage.error(data?.error ?? '进入房间失败')
      router.replace('/lobby')
      return
    }
    if (unmounted) return

    options.applyRoom(data)
    options.afterRoom?.(data)
    rememberLastRoom(options.gameKey, roomId, playerName)
    options.connect(roomId, playerName)
  }

  // 仅在组件 setup 上下文中注册，避免单测里无实例时的告警
  if (getCurrentInstance()) {
    onMounted(enter)
    onUnmounted(() => {
      unmounted = true
    })
  }

  return { enter }
}
