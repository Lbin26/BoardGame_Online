<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import * as chessApi from '@/api/chess'
import type { RoomListItem } from '@/api/chess'
import { useUserStore } from '@/store/user'
import { ZONES, gamesInZone, resolveLevel } from '@/constants/catalog'
import ZoneGrid from '@/components/ZoneGrid.vue'
import GameList from '@/components/GameList.vue'
import logoWordmark from '@/assets/logo-wordmark.png'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 三级导航：URL query 是唯一事实来源（0 分区网格 / 1 游戏列表 / 2 象棋房间层） */
const nav = computed(() => resolveLevel(route.query))

const rooms = ref<RoomListItem[]>([])
const waitingCount = ref(0)
const playingCount = ref(0)
const joinRoomId = ref('')
const loading = ref(false)
const refreshing = ref(false)

/** 轮询定时器与 focus/visibility 监听句柄 */
let pollTimer: number | undefined

/** 状态归一化 + 中文映射（与 demo 的 statusText 一致） */
function statusText(status: string): string {
  const s = status.toLowerCase()
  return (
    { waiting: '等待中', playing: '对局中', finished: '已结束' }[s] ?? s
  )
}

/** 当前用户名（一律取 store 中的 userName） */
function currentUserName(): string {
  return userStore.userInfo?.userName ?? ''
}

/** 从抛出的错误中提取可展示信息 */
function errMsg(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) return error.message
  return fallback
}

/** 拉取房间列表；silent 用于轮询/focus 刷新（失败不弹 toast） */
async function refreshRooms(silent = false): Promise<void> {
  if (refreshing.value) return
  refreshing.value = true
  try {
    const data = await chessApi.listRooms()
    rooms.value = data.rooms ?? []
    waitingCount.value = data.waitingCount ?? 0
    playingCount.value = data.playingCount ?? 0
  } catch (error) {
    if (!silent) ElMessage.error(errMsg(error, '获取房间列表失败，请稍后重试'))
  } finally {
    refreshing.value = false
  }
}

/** 创建房间：成功即跳转对局页（入座在房内选择） */
async function handleCreate(): Promise<void> {
  const userName = currentUserName()
  if (!userName) {
    ElMessage.warning('用户信息未就绪，请刷新重试')
    return
  }
  loading.value = true
  try {
    const data = await chessApi.createRoom(userName)
    if (data.success && data.room) {
      const roomId = (data.room as { roomId?: string }).roomId
      if (roomId) {
        router.push(`/game/${roomId}`)
        return
      }
    }
    ElMessage.error(data.error ?? '创建房间失败')
  } catch (error) {
    ElMessage.error(errMsg(error, '创建房间失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}

/** 加入指定房间：校验存在即跳转，入座在房内选择 */
async function handleJoin(roomId: string): Promise<void> {
  loading.value = true
  try {
    const data = await chessApi.getRoom(roomId)
    if (data.success) {
      router.push(`/game/${roomId}`)
      return
    }
    ElMessage.error(data.error ?? '加入房间失败')
  } catch (error) {
    ElMessage.error(errMsg(error, '加入房间失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}

/** 输入房间号加入 */
async function handleJoinInput(): Promise<void> {
  const roomId = joinRoomId.value.trim()
  if (!roomId) return
  await handleJoin(roomId)
}

/** 页面重新可见时静默刷新 */
function onVisible(): void {
  if (document.visibilityState === 'visible') refreshRooms(true)
}

/** 进入分区（level 0 → 1） */
function openZone(zoneKey: string): void {
  router.push({ path: '/lobby', query: { zone: zoneKey } })
}

/** 进入可玩游戏（level 1 → 2） */
function openGame(gameKey: string): void {
  router.push({ path: '/lobby', query: { zone: nav.value.zone!.key, game: gameKey } })
}

/** 返回大厅首页（任意层级 → 0） */
function backToLobby(): void {
  router.push('/lobby')
}

/** 房间层轮询：立即拉一次 + 5s 间隔（仅可见时）+ focus/visibility 静默刷新 */
function startPolling(): void {
  if (pollTimer !== undefined) return
  refreshRooms()
  pollTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') refreshRooms(true)
  }, 5000)
  window.addEventListener('focus', onVisible)
  document.addEventListener('visibilitychange', onVisible)
}

/** 离开房间层/卸载：停表并摘监听，杜绝泄漏 */
function stopPolling(): void {
  if (pollTimer !== undefined) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
  window.removeEventListener('focus', onVisible)
  document.removeEventListener('visibilitychange', onVisible)
}

// 轮询生命周期严格绑定 level 2；immediate 覆盖深链直达房间层
watch(
  () => nav.value.level,
  (level) => {
    if (level === 2) startPolling()
    else stopPolling()
  },
  { immediate: true },
)

onMounted(async () => {
  // 刷新后 store 仅水合了 token，需先补齐 userInfo；失败不阻断匿名列表
  if (userStore.isLogin && !userStore.userInfo) {
    try {
      await userStore.fetchProfile()
    } catch {
      // 401 已由拦截器清 token 并硬跳转；其余错误下面提示
    }
  }
  if (!currentUserName()) {
    ElMessage.error('获取用户信息失败，请刷新重试')
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="lobby-page">
    <div class="lobby-top">
      <img class="lobby-logo" :src="logoWordmark" alt="DiceGo" />
      <div class="lobby-top-actions">
        <span class="lobby-user">
          👤 {{ userStore.userInfo?.nickName || userStore.userInfo?.userName || '未登录' }}
        </span>
        <el-button size="small" plain @click="userStore.logout()">退出登录</el-button>
      </div>
    </div>

    <div v-if="nav.level > 0" class="lobby-nav">
      <el-button size="small" plain @click="backToLobby">← 返回大厅</el-button>
      <span class="lobby-crumb">
        {{ nav.zone?.name }}<template v-if="nav.game"> / {{ nav.game.name }}</template>
      </span>
    </div>

    <!-- level 0：分区网格 -->
    <ZoneGrid v-if="nav.level === 0" :zones="ZONES" @select="openZone" />

    <!-- level 1：分区内游戏列表 -->
    <GameList
      v-else-if="nav.level === 1 && nav.zone"
      :zone="nav.zone!"
      :games="gamesInZone(nav.zone!.key)"
      @select="openGame"
    />

    <!-- level 2：象棋房间操作 -->
    <el-card v-if="nav.level === 2" class="lobby-card">
      <template #header>
        <h2 class="lobby-title">🏠 创建房间</h2>
      </template>
      <el-button type="primary" class="create-btn" :loading="loading" @click="handleCreate">
        创建房间
      </el-button>
      <p class="hint">创建房间后进入房间选择红方/黑方座位，双方落座即开战</p>
    </el-card>

    <!-- level 0 与 level 2 均可用房间号快速入场 -->
    <el-card v-if="nav.level !== 1" class="lobby-card">
      <template #header>
        <h2 class="lobby-title">🚪 输入房间号加入</h2>
      </template>
      <div class="join-form">
        <el-input
          v-model="joinRoomId"
          placeholder="房间号(如 ABC123)"
          maxlength="10"
          @keyup.enter="handleJoinInput"
        />
        <el-button
          type="primary"
          :disabled="!joinRoomId.trim()"
          :loading="loading"
          @click="handleJoinInput"
        >
          加入
        </el-button>
      </div>
      <p class="hint">从朋友那里拿到房间号，输入即可快速入场</p>
    </el-card>

    <el-card v-if="nav.level === 2" class="lobby-card room-list-card">
      <template #header>
        <div class="room-list-header">
          <h2 class="lobby-title">
            📋 房间列表
            <span class="count">(共 {{ rooms.length }} 个 · 等待 {{ waitingCount }} · 对局 {{ playingCount }})</span>
          </h2>
          <el-button @click="refreshRooms(false)">🔄 刷新</el-button>
        </div>
      </template>
      <div v-if="rooms.length > 0" class="room-list">
        <div v-for="room in rooms" :key="room.roomId" class="room-item">
          <div class="room-info">
            <div class="room-id">{{ room.roomId }}</div>
            <div class="room-players">
              🔴 {{ room.redPlayerName || '空位' }}
              <span v-if="room.blackPlayerName"> ⚫ {{ room.blackPlayerName }}</span>
              <span v-else> ⚫ 空位</span>
            </div>
          </div>
          <div class="room-action">
            <span class="room-status" :class="'status-' + room.status.toLowerCase()">
              {{ statusText(room.status) }}
            </span>
            <el-button
              v-if="room.status.toLowerCase() === 'waiting'"
              type="primary"
              size="small"
              :loading="loading"
              @click="handleJoin(room.roomId)"
            >
              加入
            </el-button>
            <el-button
              v-else
              size="small"
              :loading="loading"
              @click="handleJoin(room.roomId)"
            >
              观战
            </el-button>
          </div>
        </div>
      </div>
      <div v-else class="empty">暂无房间，创建一个吧！</div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.lobby-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 640px;
  margin: 0 auto;
  padding: 24px;
}

.lobby-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.lobby-logo {
  height: 28px;
}

.lobby-top-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.lobby-user {
  font-size: 14px;
  color: #f4a2b0;
  word-break: break-all;
}

.lobby-nav {
  display: flex;
  align-items: center;
  gap: 12px;
}

.lobby-crumb {
  font-size: 14px;
  color: #888;
}

.lobby-card {
  background: #1a1a2e;
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: #eee;

  :deep(.el-card__header) {
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  }

  :deep(.el-card__body) {
    color: #eee;
  }
}

.lobby-title {
  margin: 0;
  font-size: 18px;
  color: #eee;
}

.count {
  color: #888;
  font-size: 13px;
}

.create-btn {
  width: 100%;
}

.join-form {
  display: flex;
  gap: 8px;
}

.hint {
  margin: 12px 0 0;
  font-size: 13px;
  color: #aaa;
}

.room-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.room-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.room-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: #16213e;
  border-radius: 8px;
}

.room-info {
  flex: 1;
}

.room-id {
  font-family: monospace;
  font-weight: bold;
  color: #f39c12;
  font-size: 18px;
}

.room-players {
  font-size: 13px;
  color: #aaa;
  margin-top: 4px;
}

.room-action {
  display: flex;
  align-items: center;
  gap: 8px;
}

.room-status {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
}

.status-waiting {
  background: rgba(46, 213, 115, 0.2);
  color: #2ed573;
}

.status-playing {
  background: rgba(233, 69, 96, 0.2);
  color: #e94560;
}

.status-finished {
  background: rgba(136, 136, 136, 0.2);
  color: #888;
}

.empty {
  text-align: center;
  color: #666;
  padding: 40px;
}
</style>
