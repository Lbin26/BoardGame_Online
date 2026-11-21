<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import * as splendorApi from '@/api/splendor'
import * as chessApi from '@/api/xiangqi'
import { findGame } from '@/constants/catalog'
import {
  forgetLastRoom,
  readLastRoom,
  type LastRoomEntry,
} from '@/composables/useLastRoom'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const entry = ref<LastRoomEntry | null>(null)
const label = ref('')
const count = ref('')

const gameName = computed(() =>
  entry.value ? (findGame(entry.value.gameKey)?.name ?? entry.value.gameKey) : '',
)
/** 目标路由：Splendor 与象棋的对局页路径不同 */
const target = computed(() =>
  entry.value
    ? entry.value.gameKey === 'splendor'
      ? `/splendor/${entry.value.roomId}`
      : `/game/${entry.value.roomId}`
    : '',
)

onMounted(async () => {
  const owner = userStore.userInfo?.userName ?? ''
  const saved = readLastRoom(owner)
  if (!saved) return

  try {
    if (saved.gameKey === 'splendor') {
      const data = await splendorApi.getRoom(saved.roomId, owner)
      if (!data.success || !data.room) {
        forgetLastRoom()
        return
      }
      const room = data.room
      const seated = room.seats?.filter((s) => !!s.playerName).length ?? 0
      count.value = `${seated}/${room.maxPlayers}`
      label.value = room.hostName ? `房主 ${room.hostName}` : ''
    } else {
      const data = await chessApi.getRoom(saved.roomId, owner)
      if (!data.success || !data.room) {
        forgetLastRoom()
        return
      }
      const room = data.room
      const seated = [room.redPlayerName, room.blackPlayerName].filter(Boolean).length
      count.value = `${seated}/2`
      label.value = ''
    }
    entry.value = saved
  } catch {
    // 网络异常：保留记录，本次不显示（下次进大厅再试）
  }
})

function goBack(): void {
  if (target.value) router.push(target.value)
}

function dismiss(): void {
  forgetLastRoom()
  entry.value = null
}
</script>

<template>
  <div v-if="entry" class="back-to-room" role="status" aria-live="polite">
    <button type="button" class="btr-main" :title="`点击返回房间 ${entry.roomId}`" @click="goBack">
      <span class="btr-icon">↩</span>
      <span class="btr-text">
        <span class="btr-title">回到房间 {{ entry.roomId }}</span>
        <span class="btr-sub">{{ gameName }} · {{ count }}<template v-if="label"> · {{ label }}</template></span>
      </span>
      <span class="btr-go">→</span>
    </button>
    <button type="button" class="btr-close" title="不再显示" @click="dismiss">×</button>
  </div>
</template>

<style scoped lang="scss">
.back-to-room {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 50;
  display: flex;
  align-items: stretch;
  border: 1px solid var(--accent-line, var(--line));
  border-radius: 12px;
  background: rgba(24, 24, 44, 0.94);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.4);
  overflow: hidden;
  animation: btr-in 0.35s var(--ease, ease) both;

  &:hover {
    border-color: var(--accent-hover, var(--color-primary));
  }
}
.btr-main {
  display: flex;
  align-items: center;
  gap: 10px;
  font: inherit;
  text-align: left;
  color: var(--ink);
  background: transparent;
  border: none;
  padding: 10px 8px 10px 14px;
  cursor: pointer;
}
.btr-icon {
  font-size: 22px;
  color: var(--color-primary);
  line-height: 1;
}
.btr-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.btr-title {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.03em;
}
.btr-sub {
  font-size: 12px;
  color: var(--muted);
}
.btr-go {
  color: var(--color-primary);
  font-size: 16px;
  margin-left: 4px;
}
.btr-close {
  font: inherit;
  font-size: 18px;
  line-height: 1;
  color: var(--muted);
  background: transparent;
  border: none;
  border-left: 1px solid var(--line);
  padding: 0 12px;
  cursor: pointer;

  &:hover {
    color: var(--ink);
  }
}
@keyframes btr-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .back-to-room {
    animation: none;
  }
}
</style>
