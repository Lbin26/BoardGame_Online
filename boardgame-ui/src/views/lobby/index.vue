<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import * as chessApi from '@/api/xiangqi'
import * as splendorApi from '@/api/splendor'
import { useRoom } from '@/composables/useRoom'
import { useUserStore } from '@/store/user'
import { ZONES, gamesInZone, resolveLevel } from '@/constants/catalog'
import ZoneGrid from '@/components/ZoneGrid.vue'
import GameList from '@/components/GameList.vue'
import BackToRoom from '@/components/BackToRoom.vue'
import logoWordmark from '@/assets/logo-wordmark.png'
import { goBackOrLobby } from '@/utils/navigation'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const {
  rooms,
  waitingCount,
  playingCount,
  refreshing,
  listError,
  showSkeleton,
  refresh: refreshRooms,
  startPolling,
  stopPolling,
} = useRoom({
  listRooms: () =>
    nav.value.game?.key === 'splendor' ? splendorApi.listRooms() : chessApi.listRooms(),
  onError: (error) => ElMessage.error(errMsg(error, '获取房间列表失败，请稍后重试')),
})

/** 三级导航：URL query 是唯一事实来源（0 分区网格 / 1 游戏列表 / 2 房间层） */
const nav = computed(() => resolveLevel(route.query))

/** 房间层当前游戏是否为 Splendor（决定建房参数与跳转路由） */
const isSplendor = computed(() => nav.value.game?.key === 'splendor')

/** Splendor 建房容量（2-4） */
const capacity = ref(4)

const joinRoomId = ref('')
const loading = ref(false)

/** 顶栏头像取昵称首字 */
const avatarChar = computed(() => {
  const name = userStore.userInfo?.nickName || userStore.userInfo?.userName || ''
  return name ? name.charAt(0) : '?'
})

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

/** 创建房间：成功即跳转对局页（入座在房内选择） */
async function handleCreate(): Promise<void> {
  const userName = currentUserName()
  if (!userName) {
    ElMessage.warning('用户信息未就绪，请刷新重试')
    return
  }
  loading.value = true
  try {
    if (isSplendor.value) {
      const data = await splendorApi.createRoom(userName, capacity.value)
      if (data.success && data.room) {
        router.push(`/splendor/${data.room.roomId}`)
        return
      }
      ElMessage.error(data.error ?? '创建房间失败')
    } else {
      const data = await chessApi.createRoom(userName)
      if (data.success && data.room) {
        const roomId = (data.room as { roomId?: string }).roomId
        if (roomId) {
          router.push(`/game/${roomId}`)
          return
        }
      }
      ElMessage.error(data.error ?? '创建房间失败')
    }
  } catch (error) {
    ElMessage.error(errMsg(error, '创建房间失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}

/** 加入指定房间：房间号可能属于任一游戏，并行探测按命中跳转，入座在房内选择 */
async function handleJoin(roomId: string): Promise<void> {
  loading.value = true
  try {
    const [chess, splendor] = await Promise.allSettled([
      chessApi.getRoom(roomId),
      splendorApi.getRoom(roomId),
    ])
    const chs = chess.status === 'fulfilled' && chess.value.success ? chess.value : null
    const spl = splendor.status === 'fulfilled' && splendor.value.success ? splendor.value : null
    if (spl && (!chs || isSplendor.value)) {
      router.push(`/splendor/${roomId}`)
      return
    }
    if (chs) {
      router.push(`/game/${roomId}`)
      return
    }
    ElMessage.error(
      (chess.status === 'fulfilled' ? chess.value.error : null) ?? '加入房间失败',
    )
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

/** 进入桌游设计助手工作台（独立工具，不进七分区） */
function goDesign(): void {
  router.push('/design')
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
      <div class="lobby-top-in">
        <img class="lobby-logo" :src="logoWordmark" alt="DiceGo" />
        <div class="lobby-top-actions">
          <button
            type="button"
            class="lobby-user-link"
            :disabled="!userStore.isLogin"
            :title="userStore.isLogin ? '个人资料' : '请先登录'"
            @click="userStore.isLogin && router.push('/profile')"
          >
            <span class="lobby-avatar">{{ avatarChar }}</span>
            <span class="lobby-user">{{
              userStore.userInfo?.nickName || userStore.userInfo?.userName || '未登录'
            }}</span>
          </button>
          <button type="button" class="btn btn-ghost btn-sm" @click="userStore.logout()">
            退出登录
          </button>
        </div>
      </div>
    </div>

    <div class="lobby-wrap">
      <nav v-if="nav.level > 0" class="lobby-crumb" aria-label="大厅层级">
        <button type="button" class="btn btn-ghost btn-sm" @click="goBackOrLobby(router)">
          ← 返回上一页
        </button>
        <button type="button" class="btn btn-ghost btn-sm" @click="backToLobby">
          ← 返回大厅
        </button>
        <span class="crumb-zone">{{ nav.zone?.name }}</span>
        <span v-if="nav.game" class="crumb-sep">/</span>
        <span v-if="nav.game" class="crumb-here">{{ nav.game.name }}</span>
      </nav>

      <!-- level 0：引导带 + 分区网格 -->
      <template v-if="nav.level === 0">
        <div class="lobby-guide">
          <div class="guide-copy rise" style="--i: 0">
            <h1 class="guide-title">选一款桌游，坐下。</h1>
            <p class="guide-lede">
              有房间号？<b>右侧直接入场</b>。想逛逛？下面七个分区按玩法机制划分——挑一个合你口味的，进去看桌游。
            </p>
          </div>
          <form class="fastjoin rise" style="--i: 1" @submit.prevent>
            <label class="fastjoin-label" for="join-input-0">房间号快速入场</label>
            <div class="fastjoin-row">
              <input
                id="join-input-0"
                v-model="joinRoomId"
                type="text"
                placeholder="房间号(如 ABC123)"
                maxlength="10"
                autocomplete="off"
                @keyup.enter="handleJoinInput"
              />
              <button
                type="button"
                class="btn btn-primary"
                :disabled="!joinRoomId.trim() || loading"
                @click="handleJoinInput"
              >
                <span v-if="loading" class="spin"></span>加入
              </button>
            </div>
            <p class="hint">从朋友那里拿到房间号，输入即可快速入场</p>
          </form>
        </div>
        <ZoneGrid :zones="ZONES" @select="openZone" />
        <section class="design-entry op rise" style="--i: 8" @click="goDesign">
          <div class="design-entry-copy">
            <h2 class="design-entry-title">桌游设计助手</h2>
            <p class="design-entry-lede">样本 · 机制 · 数值模拟工作台</p>
          </div>
          <span class="design-entry-arrow" aria-hidden="true">→</span>
        </section>
      </template>

      <!-- level 1：分区内游戏列表 -->
      <GameList
        v-else-if="nav.level === 1 && nav.zone"
        :zone="nav.zone!"
        :games="gamesInZone(nav.zone!.key)"
        @select="openGame"
        @rules="(key) => router.push(`/rules/${key}`)"
      />

      <!-- level 2：房间操作 + 房间列表 -->
      <template v-if="nav.level === 2">
        <div class="room-ops">
          <div class="op rise" style="--i: 0">
            <h2 class="op-title"><span class="stepno">开局</span>创建房间</h2>
            <div v-if="isSplendor" class="op-cap">
              <label class="cap-label" for="cap-select">人数上限</label>
              <select id="cap-select" v-model.number="capacity" class="cap-select">
                <option :value="2">2 人</option>
                <option :value="3">3 人</option>
                <option :value="4">4 人</option>
              </select>
            </div>
            <button
              type="button"
              class="btn btn-primary op-create"
              :disabled="loading"
              @click="handleCreate"
            >
              <span v-if="loading" class="spin"></span>创建房间
            </button>
            <p class="hint">
              {{
                isSplendor
                  ? '创建后进入房间选座（1-4 号位），2 人以上入座房主即可开局'
                  : '创建房间后进入房间选择红方/黑方座位，双方落座即开战'
              }}
            </p>
          </div>
          <form class="op rise" style="--i: 1" @submit.prevent>
            <h2 class="op-title"><span class="stepno">赴约</span>输入房间号加入</h2>
            <div class="op-row">
              <input
                id="join-input-2"
                v-model="joinRoomId"
                type="text"
                placeholder="房间号(如 ABC123)"
                maxlength="10"
                autocomplete="off"
                @keyup.enter="handleJoinInput"
              />
              <button
                type="button"
                class="btn btn-primary"
                :disabled="!joinRoomId.trim() || loading"
                @click="handleJoinInput"
              >
                <span v-if="loading" class="spin"></span>加入
              </button>
            </div>
            <p class="hint">从朋友那里拿到房间号，输入即可快速入场</p>
          </form>
        </div>

        <div class="roomlist rise" style="--i: 2">
          <header class="roomlist-head">
            <h2 class="roomlist-title">房间列表</h2>
            <span class="count" aria-live="polite">
              (共 {{ rooms.length }} 个 · 等待 {{ waitingCount }} · 对局 {{ playingCount }})
            </span>
            <button
              type="button"
              class="btn btn-ghost btn-sm refresh"
              :disabled="refreshing"
              @click="refreshRooms(false)"
            >
              <span v-if="refreshing" class="spin"></span>刷新
            </button>
          </header>

          <div v-if="showSkeleton" class="rooms">
            <div v-for="n in 3" :key="n" class="skel">
              <span class="a"></span><span class="b"></span><span class="c"></span
              ><span class="d"></span>
            </div>
          </div>

          <div v-else-if="listError" class="list-error">
            <span class="list-error-title">获取房间列表失败</span>
            <span>网络似乎开了个小差，请稍后重试。</span>
            <button type="button" class="btn btn-ghost btn-sm" @click="refreshRooms(false)">
              重试
            </button>
          </div>

          <div v-else-if="rooms.length > 0" class="rooms">
            <div
              v-for="(room, i) in rooms"
              :key="room.roomId"
              class="room rise"
              :style="{ '--i': i }"
            >
              <span class="rid">{{ room.roomId }}</span>
              <span v-if="isSplendor" class="seats">
                <span class="seat seat-red">
                  <span class="pip"></span>房主 {{ room.hostName ?? '?' }}
                </span>
                <span class="seat seat-black">
                  <span class="pip"></span>{{ room.seatedCount ?? 0 }}/{{ room.maxPlayers ?? 4 }} 人
                </span>
              </span>
              <span v-else class="seats">
                <span class="seat seat-red">
                  <span class="pip"></span>{{ room.redPlayerName || '空位' }}
                </span>
                <span v-if="room.blackPlayerName" class="seat seat-black">
                  <span class="pip"></span>{{ room.blackPlayerName }}
                </span>
                <span v-else class="seat seat-open"><span class="pip"></span>空位</span>
              </span>
              <span class="room-status" :class="'status-' + room.status.toLowerCase()">
                {{ statusText(room.status) }}
              </span>
              <button
                v-if="room.status.toLowerCase() === 'waiting'"
                type="button"
                class="btn btn-primary btn-sm"
                :disabled="loading"
                @click="handleJoin(room.roomId)"
              >
                <span v-if="loading" class="spin"></span>加入
              </button>
              <button
                v-else
                type="button"
                class="btn btn-ghost btn-sm"
                :disabled="loading"
                @click="handleJoin(room.roomId)"
              >
                <span v-if="loading" class="spin"></span>观战
              </button>
            </div>
          </div>

          <div v-else class="empty">
            <span class="empty-title">暂无房间，创建一个吧！</span>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="loading"
              @click="handleCreate"
            >
              <span v-if="loading" class="spin"></span>创建房间
            </button>
          </div>
        </div>
      </template>
    </div>

    <!-- 意外离开房间后，右下角一键返回 -->
    <BackToRoom />
  </div>
</template>

<style scoped lang="scss">
.lobby-page {
  min-height: 100vh;
  color: var(--ink);
  font-size: 16px;
  line-height: 1.65;
}

/* ---- 顶栏 ---- */
.lobby-top {
  border-bottom: 1px solid var(--line);
  background: rgba(26, 26, 46, 0.8);
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 40;
}
.lobby-top-in {
  max-width: 1080px;
  margin: 0 auto;
  padding: 0 clamp(16px, 3vw, 32px);
  display: flex;
  align-items: center;
  gap: 16px;
  height: 64px;
}
.lobby-logo {
  height: 26px;
}
.lobby-top-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: var(--muted);
  min-width: 0;
}
.lobby-user-link {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
}
.lobby-user-link:hover .lobby-user {
  color: var(--ink);
}
.lobby-user-link:disabled {
  cursor: default;
}
.lobby-user-link:disabled:hover .lobby-user {
  color: var(--muted);
}
.lobby-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-family: var(--font-display);
  flex: none;
}
.lobby-user {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 12em;
}

.lobby-wrap {
  max-width: 1080px;
  margin: 0 auto;
  padding: 0 clamp(16px, 3vw, 32px);
}

/* ---- 面包屑 ---- */
.lobby-crumb {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 0 0;
  font-size: 14px;
  color: var(--muted);
}
.crumb-here {
  color: var(--ink);
}
.crumb-sep {
  color: var(--line);
}

/* ---- 按钮 / 输入 / 通用 ---- */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 11px 22px;
  border-radius: var(--radius);
  font-size: 15px;
  font-weight: 600;
  font-family: var(--font-body);
  cursor: pointer;
  border: 1px solid transparent;
  transition:
    transform 0.2s var(--ease),
    box-shadow 0.2s var(--ease),
    border-color 0.2s var(--ease),
    color 0.2s var(--ease);
}
.btn-primary {
  background: var(--color-primary);
  color: #fff;
  box-shadow: var(--shadow-1);
  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: var(--shadow-2);
  }
  &:active:not(:disabled) {
    transform: translateY(0);
    box-shadow: var(--shadow-1);
  }
}
.btn-ghost {
  border-color: var(--line);
  color: var(--ink);
  background: transparent;
  &:hover:not(:disabled) {
    border-color: var(--color-primary);
    color: var(--color-primary);
  }
}
.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.btn-sm {
  padding: 6px 14px;
  font-size: 13px;
}
.btn:focus-visible,
input:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
input[type='text'] {
  background: var(--input-bg);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--ink);
  padding: 11px 14px;
  font-size: 15px;
  font-family: var(--font-body);
  width: 100%;
  transition: border-color 0.2s var(--ease);
  &::placeholder {
    color: var(--muted);
  }
  &:focus {
    border-color: var(--color-primary);
    outline: none;
  }
}
.hint {
  font-size: 13px;
  color: var(--muted);
  margin: 0;
}
.spin {
  width: 12px;
  height: 12px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: rot 0.8s linear infinite;
  flex: none;
}
@keyframes rot {
  to {
    transform: rotate(360deg);
  }
}

/* ---- level 0 引导带 ---- */
.lobby-guide {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: clamp(24px, 4vw, 56px);
  align-items: end;
  padding: clamp(32px, 5vw, 56px) 0 clamp(24px, 3vw, 36px);
}
.guide-title {
  margin: 0 0 10px;
  font-family: var(--font-display);
  font-weight: 400;
  font-size: clamp(34px, 4.2vw, 52px);
  line-height: 1.2;
  text-wrap: balance;
}
.guide-lede {
  color: var(--muted);
  font-size: 16px;
  max-width: 30em;
  text-wrap: pretty;
  b {
    color: var(--ink);
    font-weight: 600;
  }
}
.fastjoin {
  background: rgba(32, 32, 58, 0.7);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 18px 20px;
  box-shadow: var(--shadow-1);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.fastjoin-label {
  font-size: 13px;
  color: var(--muted);
  letter-spacing: 0.06em;
}
.fastjoin-row {
  display: flex;
  gap: 8px;
}
@media (max-width: 820px) {
  .lobby-guide {
    grid-template-columns: 1fr;
    align-items: start;
  }
}

/* ---- level 0 设计助手入口（独立于七分区） ---- */
.design-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 20px;
  padding: 18px 22px;
  background: rgba(32, 32, 58, 0.7);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow-1);
  cursor: pointer;
  transition: border-color 0.18s ease;
}
.design-entry:hover {
  border-color: var(--color-primary);
}
.design-entry-title {
  margin: 0 0 4px;
  font-family: var(--font-display);
  font-weight: 400;
  font-size: 20px;
}
.design-entry-lede {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}
.design-entry-arrow {
  color: var(--color-primary);
  font-size: 20px;
}

/* ---- level 2 操作卡 ---- */
.room-ops {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding: clamp(24px, 3vw, 36px) 0 16px;
}
@media (max-width: 820px) {
  .room-ops {
    grid-template-columns: 1fr;
  }
}
.op {
  background: rgba(32, 32, 58, 0.7);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.op-title {
  margin: 0;
  font-family: var(--font-display);
  font-weight: 400;
  font-size: 22px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.stepno {
  font-size: 13px;
  color: var(--color-primary);
  border: 1px solid var(--accent-line);
  border-radius: 999px;
  padding: 1px 10px;
  font-family: var(--font-body);
}
.op-create {
  width: 100%;
  padding: 14px;
}
.op-cap {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cap-label {
  font-size: 13px;
  color: var(--muted);
}
.cap-select {
  background: var(--input-bg);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--ink);
  padding: 8px 12px;
  font-size: 14px;
  font-family: var(--font-body);
  cursor: pointer;

  &:focus-visible {
    outline: 2px solid var(--color-primary);
    outline-offset: 2px;
  }
}
.op-row {
  display: flex;
  gap: 8px;
}

/* ---- 房间列表 ---- */
.roomlist {
  background: rgba(32, 32, 58, 0.5);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  margin-bottom: clamp(48px, 6vw, 80px);
}
.roomlist-head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
}
.roomlist-title {
  margin: 0;
  font-family: var(--font-display);
  font-weight: 400;
  font-size: 20px;
}
.count {
  font-size: 12px;
  color: var(--muted);
}
.refresh {
  margin-left: auto;
}
.rooms {
  display: flex;
  flex-direction: column;
}
.room {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--line);
  transition: background 0.2s var(--ease);
  &:last-child {
    border-bottom: none;
  }
  &:hover {
    background: rgba(32, 32, 58, 0.8);
  }
}
.rid {
  font-family: var(--font-display);
  font-size: 19px;
  letter-spacing: 0.14em;
  color: var(--color-primary);
  min-width: 6.5em;
}
.seats {
  flex: 1;
  display: flex;
  gap: 14px;
  font-size: 13px;
  color: var(--muted);
  flex-wrap: wrap;
}
.seat {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.pip {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex: none;
}
.seat-red .pip {
  background: var(--color-primary);
}
.seat-black .pip {
  background: var(--ink);
  opacity: 0.75;
}
.seat-open .pip {
  border: 1px dashed var(--muted);
  background: transparent;
}
.room-status {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 999px;
  letter-spacing: 0.06em;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.status-waiting {
  color: var(--color-primary);
  background: var(--accent-soft);
  border: 1px solid var(--accent-line);
  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--color-primary);
    animation: pulse 2s var(--ease) infinite;
  }
}
.status-playing {
  color: var(--ink);
  border: 1px solid var(--line);
}
.status-finished {
  color: var(--muted);
  border: 1px dashed var(--line);
}
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}
@media (max-width: 600px) {
  .room {
    flex-wrap: wrap;
    gap: 10px;
  }
  .rid {
    min-width: 0;
  }
  .seats {
    flex-basis: 100%;
    order: 3;
  }
}

/* ---- 骨架 / 错误 / 空态 ---- */
.skel {
  padding: 14px 20px;
  border-bottom: 1px solid var(--line);
  display: flex;
  gap: 16px;
  align-items: center;
  &:last-child {
    border-bottom: none;
  }
  span {
    display: block;
    height: 14px;
    border-radius: 4px;
    background: linear-gradient(
      90deg,
      rgba(242, 240, 235, 0.07) 25%,
      rgba(242, 240, 235, 0.14) 50%,
      rgba(242, 240, 235, 0.07) 75%
    );
    background-size: 200% 100%;
    animation: shimmer 1.2s linear infinite;
  }
  .a {
    width: 6.5em;
  }
  .b {
    flex: 1;
  }
  .c {
    width: 5em;
  }
  .d {
    width: 4em;
  }
}
@keyframes shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}
.list-error {
  padding: 40px 20px;
  text-align: center;
  color: var(--muted);
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: center;
}
.list-error-title {
  font-family: var(--font-display);
  font-size: 22px;
  color: var(--danger);
}
.empty {
  padding: 48px 20px;
  text-align: center;
  color: var(--muted);
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
}
.empty-title {
  font-family: var(--font-display);
  font-size: 24px;
  color: var(--ink);
}

/* ---- 入场动效 ---- */
@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.rise {
  animation: rise 0.5s var(--ease) both;
  animation-delay: calc(var(--i, 0) * 55ms);
}
@media (prefers-reduced-motion: reduce) {
  .rise,
  .spin,
  .skel span,
  .status-waiting::before {
    animation: none;
  }
  .btn,
  .room,
  input[type='text'] {
    transition: none;
  }
}
</style>