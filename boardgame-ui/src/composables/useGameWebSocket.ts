import { getCurrentInstance, onUnmounted, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAfk } from './useAfk'
import { useHeartbeat } from './useHeartbeat'

export interface UseGameWebSocketOptions {
  /** WebSocket 路径，如 '/ws/splendor' */
  path: string
  /** 房间号 getter（重连沿用建立连接时的值） */
  roomId: () => string | undefined
  /** 玩家名 getter */
  playerName: () => string
  /** 收到服务端消息后的业务分发 */
  onMessage: (msg: any) => void
  /** 连接断开时清理游戏临时态（如选中格、待办选择） */
  onDisconnected?: () => void
  /** 最多自动重连次数，默认 5 */
  maxRetries?: number
  /** 重连间隔毫秒，默认 2000 */
  reconnectDelayMs?: number
}

export interface GameWebSocketApi {
  connected: Ref<boolean>
  /** 同名连接被其他标签页替换：停止自动重连，等待手动接管 */
  replacedNotice: Ref<boolean>
  /** 发送消息；心跳与 AFK 自身不刷新本地活跃态 */
  send: (msg: object) => boolean
  connect: (roomId?: string, playerName?: string) => void
  /** 手动接管被替换的连接（在当前页重连） */
  resume: () => void
  /** 服务端关房：供 ROOM_CLOSED 分支调用，阻止 onclose 触发重连 */
  markServerClosed: () => void
  /** 主动断开（组件卸载自动调用） */
  close: () => void
}

/**
 * 对局页 WebSocket 生命周期。
 *
 * 收拢各游戏通用的连接骨架：URL 拼装、onopen 启动心跳/AFK、onclose 退避重连、
 * 互踢识别、onerror、以及卸载清理。游戏差异只通过 onMessage（消息分发）和
 * onDisconnected（临时态清理）注入。
 *
 * 关键约定：
 * - 心跳/AFK 实例收在内部，`send()` 里统一排除 HEARTBEAT/AFK 不计作真人操作；
 * - `markServerClosed()` 必须暴露：onclose 需要区分「服务端关房」与「网络断开」，
 *   否则关房时仍会触发重连；
 * - 连接被同名新连接替换时不重连（否则多标签页互踢成死循环）。
 */
export function useGameWebSocket(options: UseGameWebSocketOptions): GameWebSocketApi {
  const maxRetries = options.maxRetries ?? 5
  const reconnectDelayMs = options.reconnectDelayMs ?? 2000

  const connected = ref(false)
  const replacedNotice = ref(false)

  let ws: WebSocket | null = null
  let intentionalClose = false
  let roomClosedByServer = false
  let retries = 0
  let reconnectTimer: number | null = null

  function send(msg: object): boolean {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(msg))
      const t = (msg as { type?: string }).type
      // 真实操作刷新活跃态；心跳与 AFK 自身不算操作
      if (t && t !== 'HEARTBEAT' && t !== 'AFK') noteActivity()
      return true
    }
    return false
  }

  const { start: startHeartbeat, stop: stopHeartbeat } = useHeartbeat(() =>
    send({ type: 'HEARTBEAT', payload: {} }),
  )

  const { noteActivity, start: startAfk, stop: stopAfk } = useAfk({
    send: (active) => send({ type: active ? 'ACTIVE' : 'AFK', payload: {} }),
  })

  function connect(roomId = options.roomId(), playerName = options.playerName()): void {
    if (!roomId || !playerName) return

    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
    ws = new WebSocket(
      `${proto}//${location.host}${options.path}?roomId=${roomId}&playerName=${encodeURIComponent(playerName)}`,
    )

    ws.onopen = () => {
      connected.value = true
      replacedNotice.value = false
      retries = 0
      startHeartbeat()
      startAfk()
    }

    ws.onmessage = (evt) => {
      try {
        options.onMessage(JSON.parse(evt.data))
      } catch (e) {
        console.warn('WS parse error', e)
      }
    }

    ws.onclose = (evt: CloseEvent) => {
      connected.value = false
      stopHeartbeat()
      stopAfk()
      options.onDisconnected?.()

      if (roomClosedByServer) return
      if (intentionalClose) return
      // 同名连接被新标签页替换：不重连，否则两页互踢成死循环
      if (evt?.reason === 'replaced by new connection') {
        replacedNotice.value = true
        return
      }
      if (retries < maxRetries) {
        retries++
        ElMessage.warning(`连接断开，正在重连（${retries}/${maxRetries}）...`)
        reconnectTimer = window.setTimeout(() => connect(roomId, playerName), reconnectDelayMs)
      } else {
        ElMessage.error('连接已断开，请刷新页面重试')
      }
    }

    ws.onerror = () => {
      connected.value = false
    }
  }

  function resume(): void {
    replacedNotice.value = false
    retries = 0
    connect()
  }

  function markServerClosed(): void {
    roomClosedByServer = true
    intentionalClose = true
    stopAfk()
  }

  function close(): void {
    intentionalClose = true
    stopHeartbeat()
    stopAfk()
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (ws) {
      // 主动断开时屏蔽回调，避免触发断开提示与重连
      ws.onclose = null
      ws.onerror = null
      ws.close()
      ws = null
    }
  }

  // 仅在组件 setup 上下文中注册，避免单测里无实例时的告警
  if (getCurrentInstance()) onUnmounted(close)

  return { connected, replacedNotice, send, connect, resume, markServerClosed, close }
}
