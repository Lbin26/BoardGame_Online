<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import * as chessApi from '@/api/chess'
import type { RoomListItem } from '@/api/chess'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const rooms = ref<RoomListItem[]>([])
const waitingCount = ref(0)
const playingCount = ref(0)
const joinRoomId = ref('')
const loading = ref(false)

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

/** 拉取房间列表 */
async function refreshRooms(): Promise<void> {
  const data = await chessApi.listRooms()
  rooms.value = data.rooms ?? []
  waitingCount.value = data.waitingCount ?? 0
  playingCount.value = data.playingCount ?? 0
}

/** 创建房间：成功即跳转对局页 */
async function handleCreate(): Promise<void> {
  const userName = currentUserName()
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
  } finally {
    loading.value = false
  }
}

/** 加入指定房间：失败提示 error，不跳转 */
async function handleJoin(roomId: string): Promise<void> {
  const userName = currentUserName()
  loading.value = true
  try {
    const data = await chessApi.joinRoom(roomId, userName)
    if (data.success && data.room) {
      const rId = (data.room as { roomId?: string }).roomId
      if (rId) {
        router.push(`/game/${rId}`)
        return
      }
    }
    ElMessage.error(data.error ?? '加入房间失败')
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

onMounted(async () => {
  // 刷新后 store 仅水合了 token，需先补齐 userInfo 再拉列表
  if (userStore.isLogin && !userStore.userInfo) {
    await userStore.fetchProfile()
  }
  await refreshRooms()
})
</script>

<template>
  <div class="lobby-page">
    <el-card class="lobby-card">
      <template #header>
        <h2 class="lobby-title">🏠 创建房间</h2>
      </template>
      <el-button type="primary" class="create-btn" :loading="loading" @click="handleCreate">
        创建房间
      </el-button>
      <p class="hint">创建房间后你是红方，等别人加入即可开战</p>
    </el-card>

    <el-card class="lobby-card">
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

    <el-card class="lobby-card room-list-card">
      <template #header>
        <div class="room-list-header">
          <h2 class="lobby-title">
            📋 房间列表
            <span class="count">(共 {{ rooms.length }} 个 · 等待 {{ waitingCount }} · 对局 {{ playingCount }})</span>
          </h2>
          <el-button @click="refreshRooms">🔄 刷新</el-button>
        </div>
      </template>
      <div v-if="rooms.length > 0" class="room-list">
        <div v-for="room in rooms" :key="room.roomId" class="room-item">
          <div class="room-info">
            <div class="room-id">{{ room.roomId }}</div>
            <div class="room-players">
              🔴 {{ room.redPlayerName || '等待中' }}
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
