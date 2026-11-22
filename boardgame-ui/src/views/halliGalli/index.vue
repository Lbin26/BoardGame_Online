<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import * as api from '@/api/halliGalli'
import type {
  HalliGalliCardFace,
  HalliGalliRank,
  HalliGalliRing,
  HalliGalliRoomInfo,
  HalliGalliSnapshot,
} from '@/api/halliGalli'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { useGameSfx } from '@/composables/useGameSfx'
import { useRoomEntry } from '@/composables/useRoomEntry'
import { copyText } from '@/utils/clipboard'
import { goBackOrLobby } from '@/utils/navigation'
import { resolveMySeat } from '@/utils/halliSeat'
import { ringSlots } from '@/utils/ringSeats'

const router = useRouter()

const room = ref<HalliGalliRoomInfo | null>(null)
const snapshot = ref<HalliGalliSnapshot | null>(null)
const rankings = ref<HalliGalliRank[]>([])
const myName = ref('')
const ringPending = ref(false)
const lastFlip = ref<{ seatNo: number; card: HalliGalliCardFace | null; auto: boolean } | null>(null)
const lastRing = ref<HalliGalliRing | null>(null)
const clock = ref(Date.now())

const FRUITS = [
  { code: 'apple', name: '苹果', emoji: '🍎' },
  { code: 'lemon', name: '柠檬', emoji: '🍋' },
  { code: 'cherry', name: '樱桃', emoji: '🍒' },
  { code: 'grape', name: '葡萄', emoji: '🍇' },
]

/** 素材加载失败的 code 集合：回退到 emoji，避免整块空白 */
const imgFailed = ref<Record<string, boolean>>({})

/** 水果图标：public/cards/halli-galli/fruits/<code>.webp（透明底 256×256） */
function fruitImage(code: string): string {
  return `/cards/halli-galli/fruits/${code}.webp`
}

function markImgFailed(code: string): void {
  imgFailed.value = { ...imgFailed.value, [code]: true }
}

/** 本地连点节流：只防误触双击，不拦截正常抢铃；真正的裁决在服务端 */
const RING_THROTTLE_MS = 120

/** 抢铃按钮"判定中"视觉复位时间（服务端对防抖/迟到不回包，只能本地兜底） */
const RING_FEEDBACK_MS = 350

/** 抢铃提示：保持 2.5s 后触发 0.5s 淡出，总计不超过 3s */
const RING_TOAST_HOLD_MS = 2500

/** 抢铃结果浮层：支持手动关闭，自动消失只作兜底 */
function dismissRing(): void {
  lastRing.value = null
}

/** 对局音效：翻牌轻响、抢对铃声、抢错下滑音；静音状态跨局记忆 */
const {
  muted: sfxMuted,
  play: playSfx,
  toggle: toggleSfx,
  unlock: unlockSfx,
} = useGameSfx()

function emojiOf(fruit: string): string {
  return FRUITS.find((f) => f.code === fruit)?.emoji ?? '❓'
}

function nameOfFruit(fruit: string): string {
  return FRUITS.find((f) => f.code === fruit)?.name ?? fruit
}

/** 对局播报用玩家名；快照未覆盖到的座位再退回房间座位表 */
function nameOfSeat(seatNo: number | null | undefined): string {
  if (seatNo == null) return '—'
  return (
    snapshot.value?.players.find((p) => p.seatNo === seatNo)?.name ??
    room.value?.seats.find((s) => s.seatNo === seatNo)?.name ??
    `座位 ${seatNo}`
  )
}

/**
 * 采用服务端下发的房间信息，但**必须保住本人座位**。
 *
 * 开局 `GAME_START` 与入座广播都是中立视角（viewerName = null），`mySeatNo` 为 null；
 * 若直接整体覆盖，`canFlip` 会永久为 false——这正是"翻牌点了没反应"的根因。
 * 这里依次回退：下发值 → 座位表按名字反查 → 旧值。
 */
function adoptRoom(next: HalliGalliRoomInfo | null | undefined): void {
  if (!next) return
  room.value = {
    ...next,
    mySeatNo: resolveMySeat(next.seats, next.mySeatNo, myName.value, room.value?.mySeatNo ?? null),
  }
}

/** 用快照里的同名玩家补齐座位（广播 room 换过一轮后仍能自愈） */
function syncMySeatFromSnapshot(): void {
  const snap = snapshot.value
  if (!snap || !room.value || room.value.mySeatNo !== null) return
  const mine = snap.players.find((p) => p.name === myName.value)?.seatNo
  if (mine) room.value = { ...room.value, mySeatNo: mine }
}

function handleMessage(msg: {
  type?: string
  payload?: Record<string, any>
}): void {
  const type = msg?.type
  const payload = msg?.payload ?? {}
  switch (type) {
    case 'PLAYER_JOINED':
    case 'SEAT_CHANGED':
      adoptRoom(payload.room as HalliGalliRoomInfo | null)
      // 自身同步载荷同时带 snapshot（刷新/重连恢复对局画面），中立广播不带
      if (payload.snapshot) snapshot.value = payload.snapshot as HalliGalliSnapshot
      break
    case 'GAME_START':
      adoptRoom(payload.room as HalliGalliRoomInfo | null)
      rankings.value = []
      lastRing.value = null
      lastFlip.value = null
      break
    case 'STATE_SYNC':
      if (payload) snapshot.value = payload as unknown as HalliGalliSnapshot
      syncMySeatFromSnapshot()
      break
    case 'FLIP_DONE':
      lastFlip.value = {
        seatNo: payload.seatNo as number,
        card: (payload.card ?? null) as HalliGalliCardFace | null,
        auto: !!payload.auto,
      }
      playSfx('flip')
      break
    case 'RING_RESULT': {
      const result = payload as unknown as HalliGalliRing
      lastRing.value = result
      ringPending.value = false
      // 迟到/防抖是静默丢弃，不该给玩家任何声画反馈
      if (result.status === 'CORRECT') playSfx('bell')
      else if (result.status === 'WRONG') playSfx('buzz')
      // 2500ms 后开始淡出（transition 0.5s），总计 3s 内消失；早退由 × 按钮负责
      window.setTimeout(() => {
        if (lastRing.value === result) lastRing.value = null
      }, RING_TOAST_HOLD_MS)
      break
    }
    case 'PLAYER_OUT':
      ElMessage.warning(`${nameOfSeat(payload.seatNo as number)} 牌堆耗尽，出局（明牌仍在桌上计数）`)
      break
    case 'GAME_OVER':
      rankings.value = (payload.rankings ?? []) as HalliGalliRank[]
      break
    case 'ACTION_REJECT':
      ringPending.value = false
      ElMessage.warning((payload.message as string) ?? '操作被拒绝')
      break
    case 'ROOM_CLOSED':
      markServerClosed()
      ElMessage.warning(payload.reason ? `房间已关闭：${payload.reason}` : '房间已关闭')
      router.replace('/lobby')
      break
    default:
      break
  }
}

const { connected, replacedNotice, send, connect, markServerClosed } = useGameWebSocket({
  path: '/ws/halli-galli',
  roomId: () => room.value?.roomId,
  playerName: () => myName.value,
  onMessage: handleMessage,
  onDisconnected: () => {
    ringPending.value = false
  },
})

useRoomEntry({
  gameKey: 'halliGalli',
  loginRedirect: (roomId) => `/halli-galli/${roomId}`,
  fetchRoom: api.getRoom,
  isValid: (data) => !!data.room,
  applyRoom: (data) => adoptRoom(data.room),
  playerName: myName,
  connect,
})

const maxPlayers = computed(() => room.value?.maxPlayers ?? 0)
const status = computed(() => room.value?.status ?? 'WAITING')
/** 本人座位：房间视图优先，其次快照同名玩家（刷新/中立广播后自愈） */
const mySeat = computed(() =>
  resolveMySeat(
    room.value?.seats,
    room.value?.mySeatNo,
    myName.value,
    snapshot.value?.players.find((p) => p.name === myName.value)?.seatNo ?? null,
  ),
)
const isHost = computed(() => !!room.value && room.value.hostName === myName.value)
const seatedCount = computed(
  () => room.value?.seats.filter((seat) => !!seat.name).length ?? 0,
)
const players = computed(() => snapshot.value?.players ?? [])

const statusText = computed(() =>
  status.value === 'WAITING' ? '等待中' : status.value === 'PLAYING' ? '对局中' : '已结束',
)

/**
 * 环形座位：自己恒在正下方，其余按座位号顺时针均分。
 * n=2 → 上下；n=3 → 倒三角；n=4 → 菱形；n=5 → 正五边形（顶点朝下）；n=6 → 正六边形。
 */
const seatSlots = computed(() => {
  const list = players.value
  const slots = ringSlots(
    list.map((p) => p.seatNo),
    mySeat.value,
  )
  return list.map((p, i) => ({ ...p, ...slots[i] }))
})

/** 生成 `calc(50% + 26%)` / `calc(50% - 26%)`，避免 `+ -26%` 这种边界写法 */
function ringOffset(v: number): string {
  return `calc(50% ${v < 0 ? '-' : '+'} ${Math.abs(v)}%)`
}

/** 当前翻牌方剩余秒数（倒计时与服务端 10s 超时代翻对齐） */
const secondsLeft = computed(() => {
  const deadline = snapshot.value?.flipDeadlineAt ?? 0
  if (!deadline) return 0
  return Math.max(0, Math.ceil((deadline - clock.value) / 1000))
})

const canFlip = computed(
  () =>
    status.value === 'PLAYING' &&
    mySeat.value !== null &&
    snapshot.value?.flipSeatNo === mySeat.value,
)

/** 自己是否已出局：出局后仍可点铃会让服务端反复回 ACTION_REJECT，直接禁用 */
const selfOut = computed(
  () => snapshot.value?.players.find((p) => p.seatNo === mySeat.value)?.out ?? false,
)

const canRing = computed(
  () => status.value === 'PLAYING' && mySeat.value !== null && !selfOut.value,
)

function seatAt(seatNo: number): void {
  if (!room.value) return
  const target = room.value.seats.find((seat) => seat.seatNo === seatNo)
  if (target?.name && target.name !== myName.value) {
    ElMessage.warning('该座位已被占用')
    return
  }
  void api
    .takeSeat(room.value.roomId, myName.value, seatNo)
    .then((data) => {
      if (!data.success) {
        ElMessage.warning(data.error ?? '入座失败')
        return
      }
      if (data.room) adoptRoom(data.room)
    })
    .catch(() => ElMessage.error('入座失败，请稍后重试'))
}

function leaveSeat(): void {
  if (!room.value) return
  void api
    .standUp(room.value.roomId, myName.value)
    .then((data) => {
      if (!data.success) {
        ElMessage.warning(data.error ?? '起身失败')
        return
      }
      if (data.room) adoptRoom(data.room)
    })
    .catch(() => ElMessage.error('起身失败，请稍后重试'))
}

function startGame(): void {
  if (!send({ type: 'START_GAME', payload: {} })) {
    ElMessage.warning('连接已断开，正在重连…')
  }
}

/**
 * 翻牌：唯一入口是点自己的牌堆（卡堆本身即按钮）。
 * 鼠标要翻牌就必须落在自己面板上，翻完想抢铃必须移到屏幕中心，
 * 无法把指针提前停在铃上"预瞄"。
 */
function flipFromStack(): void {
  if (!canFlip.value) return
  unlockSfx()
  send({ type: 'FLIP', payload: {} })
}

let lastRingSentAt = 0

/** 拍铃：只认鼠标点击中心铃，不绑任何快捷键；对局中随时可点，胜负由服务端到达顺序裁决 */
function sendRing(): void {
  if (!canRing.value) return
  const now = Date.now()
  if (now - lastRingSentAt < RING_THROTTLE_MS) return
  lastRingSentAt = now
  unlockSfx()
  ringPending.value = true
  send({ type: 'RING', payload: {} })
  // 防抖/迟到服务端不回包，本地到点自动复位"判定中"
  window.setTimeout(() => {
    ringPending.value = false
  }, RING_FEEDBACK_MS)
}

let ticker: number | null = null

onMounted(() => {
  // 首次任意点击解除浏览器的自动播放限制，否则对手触发的音效会被静默丢弃
  window.addEventListener('pointerdown', unlockSfx, { once: true })
  ticker = window.setInterval(() => {
    clock.value = Date.now()
  }, 200)
})

onUnmounted(() => {
  window.removeEventListener('pointerdown', unlockSfx)
  if (ticker) window.clearInterval(ticker)
})

function backToLobby(): void {
  router.push('/lobby')
}

async function copyRoomId(): Promise<void> {
  const id = room.value?.roomId
  if (!id) return
  if (await copyText(id)) ElMessage.success('房间号已复制')
  else ElMessage.error('复制失败，请手动复制')
}

/** 房主解散房间：公共层广播 ROOM_CLOSED 并断开所有连接 */
async function dissolveRoom(): Promise<void> {
  try {
    await ElMessageBox.confirm('解散后所有玩家将返回大厅，对局不会保存。确定解散吗？', '解散房间', {
      type: 'warning',
      confirmButtonText: '解散',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  send({ type: 'DISSOLVE_ROOM', payload: {} })
}
</script>

<template>
  <div class="hg">
    <!-- 页眉：与宝石商人同构的三段式（左=房间号/复制，中=连接/状态/规则/音效，右=返回/解散） -->
    <header class="room-bar">
      <div class="bar-left">
        <span class="room-id">房间号：<strong>{{ room?.roomId ?? '...' }}</strong></span>
        <el-button size="small" @click="copyRoomId">📋 复制</el-button>
      </div>
      <div class="bar-center">
        <span class="conn">
          <i class="dot" :class="{ on: connected }"></i>{{ connected ? '已连接' : '重连中' }}
        </span>
        <span class="round-info">{{ statusText }}</span>
        <el-button size="small" plain @click="router.push('/rules/halliGalli')">📖 规则</el-button>
        <el-button size="small" plain @click="toggleSfx">
          {{ sfxMuted ? '🔇 音效' : '🔊 音效' }}
        </el-button>
      </div>
      <div class="bar-right">
        <el-button size="small" plain @click="goBackOrLobby(router)">← 返回上一页</el-button>
        <el-button size="small" plain @click="backToLobby">← 返回大厅</el-button>
        <el-button v-if="isHost" size="small" type="danger" plain @click="dissolveRoom">
          🗑 解散
        </el-button>
      </div>
    </header>

    <div v-if="replacedNotice" class="banner">
      已在其他标签页打开同一房间，本页停止重连。
      <button type="button" class="link" @click="router.go(0)">重新进入</button>
    </div>

    <main class="hg-body">
      <!-- 等待开局：与宝石商人同构的座位卡 + 居中操作区 -->
      <section v-if="status === 'WAITING'" class="waiting">
        <div class="seat-grid">
          <button
            v-for="no in maxPlayers"
            :key="no"
            type="button"
            class="seat-card"
            :class="{
              occupied: !!room?.seats.find((s) => s.seatNo === no)?.name,
              mine: mySeat === no,
              free: !room?.seats.find((s) => s.seatNo === no)?.name,
            }"
            @click="seatAt(no)"
          >
            <div class="seat-no">{{ no }} 号位</div>
            <template v-if="room?.seats.find((s) => s.seatNo === no)?.name">
              <div class="seat-player">
                {{ room?.seats.find((s) => s.seatNo === no)?.name }}
                <span
                  v-if="room?.seats.find((s) => s.seatNo === no)?.afk"
                  class="afk-tag"
                >💤 待机</span>
              </div>
              <div
                class="seat-conn"
                :class="room?.seats.find((s) => s.seatNo === no)?.online ? 'on' : 'off'"
              >
                {{ room?.seats.find((s) => s.seatNo === no)?.online ? '在线' : '离线' }}
              </div>
            </template>
            <template v-else>
              <div class="seat-player">空位</div>
              <div class="seat-conn">点击入座</div>
            </template>
          </button>
        </div>

        <div class="waiting-ops">
          <el-button v-if="mySeat" size="small" @click="leaveSeat">🚶 起身</el-button>
          <el-button
            v-if="isHost"
            type="primary"
            :disabled="seatedCount < 2"
            @click="startGame"
          >
            🎮 开局（{{ seatedCount }}/{{ maxPlayers }} 已入座）
          </el-button>
          <span v-else-if="!mySeat" class="spectate-hint">
            👀 观战中 · 点击空位入座{{ room?.hostName ? `（房主：${room.hostName}）` : '' }}
          </span>
          <span v-else class="spectate-hint">等待房主 {{ room?.hostName }} 开局…</span>
        </div>
      </section>

      <!-- 对局中 / 已结束 -->
      <template v-else>
        <section class="arena">
          <article
            v-for="p in seatSlots"
            :key="p.seatNo"
            class="player"
            :style="{ '--rx': ringOffset(p.dx), '--ry': ringOffset(p.dy) }"
            :class="{
              turn: snapshot?.flipSeatNo === p.seatNo && status === 'PLAYING',
              out: p.out,
              me: p.seatNo === mySeat,
              offline: !p.online,
            }"
          >
            <header class="p-head">
              <span class="p-seat">{{ p.seatNo }}</span>
              <span class="p-name">{{ p.name }}</span>
              <span v-if="p.out" class="tag">出局</span>
              <span v-else-if="p.afk" class="tag soft">待机</span>
            </header>

            <!-- 翻牌入口就是自己的卡堆本身：要翻牌鼠标必须先落到自己面板上 -->
            <button
              v-if="p.seatNo === mySeat"
              type="button"
              class="p-card my-stack"
              :class="{ empty: !p.topCard, 'can-flip': canFlip }"
              @pointerdown.prevent="flipFromStack"
            >
              <template v-if="p.topCard">
                <span class="p-fruits">
                  <template v-for="i in p.topCard.count" :key="i">
                    <img
                      v-if="!imgFailed[p.topCard.fruit]"
                      :src="fruitImage(p.topCard.fruit)"
                      alt=""
                      class="p-fruit-img"
                      @error="markImgFailed(p.topCard.fruit)"
                    />
                    <span v-else class="p-fruit">{{ emojiOf(p.topCard.fruit) }}</span>
                  </template>
                </span>
                <span class="p-caption">{{ nameOfFruit(p.topCard.fruit) }} ×{{ p.topCard.count }}</span>
              </template>
              <span v-else class="p-empty">尚未翻牌</span>
              <span v-if="status === 'PLAYING'" class="stack-hint">
                <template v-if="canFlip">点我翻牌 · {{ secondsLeft }}s</template>
                <template v-else>等 {{ nameOfSeat(snapshot?.flipSeatNo) }}</template>
              </span>
            </button>

            <div v-else class="p-card" :class="{ empty: !p.topCard }">
              <template v-if="p.topCard">
                <span class="p-fruits">
                  <template v-for="i in p.topCard.count" :key="i">
                    <img
                      v-if="!imgFailed[p.topCard.fruit]"
                      :src="fruitImage(p.topCard.fruit)"
                      alt=""
                      class="p-fruit-img"
                      @error="markImgFailed(p.topCard.fruit)"
                    />
                    <span v-else class="p-fruit">{{ emojiOf(p.topCard.fruit) }}</span>
                  </template>
                </span>
                <span class="p-caption">{{ nameOfFruit(p.topCard.fruit) }} ×{{ p.topCard.count }}</span>
              </template>
              <span v-else class="p-empty">尚未翻牌</span>
            </div>

            <footer class="p-foot">
              <span>牌堆 {{ p.deckCount }}</span>
              <span>明牌 {{ p.faceUpCount }}</span>
              <span class="p-total">共 {{ p.totalCards }}</span>
            </footer>
          </article>

          <!-- 铃：容器正中心，唯一拍铃入口（鼠标点击，无键盘快捷键） -->
          <div class="hub">
            <button
              type="button"
              class="bell"
              :class="{ pending: ringPending }"
              :disabled="!canRing"
              aria-label="拍铃"
              @pointerdown.prevent="sendRing"
            >
              <span class="bell-emoji">🔔</span>
              <span class="bell-text">{{ ringPending ? '判定中…' : '拍铃' }}</span>
              <span class="bell-tip">{{ selfOut ? '已出局' : '随时可点' }}</span>
            </button>
          </div>
        </section>

        <p v-if="lastFlip" class="flip-line">
          {{ nameOfSeat(lastFlip.seatNo) }}
          <template v-if="lastFlip.card">
            翻开 {{ nameOfFruit(lastFlip.card.fruit) }} ×{{ lastFlip.card.count }}
          </template>
          <template v-else>牌堆耗尽</template>
          <span v-if="lastFlip.auto" class="auto">（超时代翻）</span>
        </p>
      </template>

      <transition name="flash">
        <div
          v-if="lastRing"
          class="flash"
          :class="lastRing.status.toLowerCase()"
        >
          <strong v-if="lastRing.status === 'CORRECT'">
            {{ nameOfSeat(lastRing.seatNo) }} 抢对，收走 {{ lastRing.cardsTaken }} 张
          </strong>
          <strong v-else-if="lastRing.status === 'WRONG' && lastRing.beneficiarySeat">
            {{ nameOfSeat(lastRing.seatNo) }} 抢错：{{ nameOfSeat(lastRing.beneficiarySeat) }} 收走桌面，本局结束
          </strong>
          <strong v-else-if="lastRing.status === 'WRONG'">
            {{ nameOfSeat(lastRing.seatNo) }} 抢错，赔出 {{ lastRing.penalties.length }} 张
          </strong>
          <strong v-else>判定：{{ lastRing.status }}</strong>
          <button type="button" class="flash-close" aria-label="关闭提示" @click="dismissRing">
            ×
          </button>
        </div>
      </transition>

      <div v-if="rankings.length" class="over">
        <h2>对局结束</h2>
        <ol class="ranks">
          <li v-for="r in rankings" :key="r.seatNo">
            <span class="rk">{{ r.rank }}</span>
            <span class="rn">{{ r.name }}</span>
            <span class="rc">{{ r.cardCount }} 张</span>
          </li>
        </ol>
        <button type="button" class="primary" @click="backToLobby">返回大厅</button>
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/room' as room;

/* 顶栏、等待座位卡、待机标记的公共样式已提到 src/styles/_room.scss */
@include room.room-bar;
@include room.round-info;
@include room.room-waiting;
@include room.afk-tag;

.hg {
  min-height: 100vh;
  padding: 20px 24px 40px;
  color: var(--ink);
}

/* 连接状态点：仅德国心脏病顶栏有 */
.room-bar {
  .conn {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }
  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #b4544a;
    &.on {
      background: #4f9d69;
    }
  }
}

button.primary,
button.bell {
  font: inherit;
  cursor: pointer;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  padding: 8px 14px;
  transition: border-color 0.2s var(--ease, ease), background 0.2s var(--ease, ease);
  &:disabled {
    cursor: not-allowed;
    opacity: 0.45;
  }
}
button.primary {
  background: var(--color-primary);
  border-color: transparent;
  color: #fff;
  font-weight: 700;
  &:hover:not(:disabled) {
    filter: brightness(1.06);
  }
}

.banner {
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--surface-3);
  border: 1px solid var(--accent-line, var(--line));
  font-size: 13px;
}
.link {
  background: none;
  border: none;
  color: var(--color-primary);
  cursor: pointer;
  font: inherit;
}

.hg-body {
  position: relative;
}

/* 德国心脏病的座位卡是 <button>，需要重置浏览器默认外观 */
.seat-card {
  background: var(--surface-1, transparent);
  color: var(--ink);
  font: inherit;
}

/* 等待中样式见顶部 @include room.room-waiting */

/* 环形牌桌：玩家面板按人数围成一圈，自己恒在正下方，正中心是铃 */
.arena {
  position: relative;
  width: 100%;
  max-width: 1080px;
  min-height: 620px;
  margin: 0 auto;
}
.player {
  /* 面板半宽/半高：clamp 保证任何人数下都完整留在牌桌内，不会被页眉裁掉 */
  --player-half-w: 84px;
  --player-half-h: 78px;
  position: absolute;
  left: clamp(var(--player-half-w), var(--rx), calc(100% - var(--player-half-w)));
  top: clamp(var(--player-half-h), var(--ry), calc(100% - var(--player-half-h)));
  width: 168px;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
  border-radius: 14px;
  background: var(--surface-3);
  border: 1px solid var(--line);
  transition:
    border-color 0.25s var(--ease, ease),
    box-shadow 0.25s var(--ease, ease);

  &.turn {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 35%, transparent);
  }
  &.me {
    border-color: color-mix(in srgb, var(--color-primary) 55%, var(--line));
    background: var(--surface-2, rgba(255, 255, 255, 0.06));
  }
  &.me .p-name {
    color: var(--color-primary);
  }
  &.out {
    opacity: 0.55;
  }
  &.offline .p-name::after {
    content: ' · 离线';
    font-size: 11px;
    color: var(--muted);
  }
}
.p-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.p-seat {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--line);
  font-size: 11px;
  font-weight: 700;
}
.p-name {
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tag {
  margin-left: auto;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(180, 84, 74, 0.16);
  color: #d2756a;
  &.soft {
    background: rgba(150, 150, 150, 0.16);
    color: var(--muted);
  }
}
.p-card {
  width: 100%;
  min-height: 68px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border-radius: 10px;
  background: var(--surface-2, rgba(255, 255, 255, 0.04));
  border: 1px dashed var(--line);
  padding: 6px;
  font: inherit;
  color: inherit;
  text-align: center;
  appearance: none;
  -webkit-appearance: none;
  box-sizing: border-box;
  transition: border-color 0.2s var(--ease, ease), background 0.2s var(--ease, ease);

  &.empty {
    color: var(--muted);
    font-size: 12px;
  }
}

/* 水果图片单独成一行并限制在卡片内换行，绝不压到标题/提示文字 */
.p-fruits {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 2px;
  max-width: 100%;
}

/* 自己的卡堆就是翻牌按钮：轮到自己时高亮呼吸，明确"点这里" */
.p-card.my-stack {
  cursor: pointer;
  border-style: solid;

  &:hover {
    border-color: color-mix(in srgb, var(--color-primary) 55%, var(--line));
  }
}
.p-card.my-stack.can-flip {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 12%, transparent);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 30%, transparent);
  animation: stack-pulse 1.6s ease-in-out infinite;

  &:hover {
    background: color-mix(in srgb, var(--color-primary) 20%, transparent);
  }
}
@keyframes stack-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 30%, transparent);
  }
  50% {
    box-shadow: 0 0 0 5px color-mix(in srgb, var(--color-primary) 14%, transparent);
  }
}
.p-fruit {
  font-size: 16px;
  line-height: 1;
}
.p-fruit-img {
  width: 19px;
  height: 19px;
  object-fit: contain;
  display: inline-block;
  vertical-align: middle;
}
.p-caption {
  font-size: 10px;
  line-height: 1;
  color: var(--muted);
}
.stack-hint {
  font-size: 9px;
  line-height: 1;
  color: var(--muted);
  white-space: nowrap;
}
.can-flip .stack-hint {
  color: var(--color-primary);
  font-weight: 700;
}
.p-foot {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--muted);
}
.p-total {
  color: var(--ink);
  font-weight: 700;
}

/* 铃：绝对居中，圆形大按钮，全场视觉焦点 */
.hub {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
}
.bell {
  width: 132px;
  height: 132px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border-radius: 50%;
  border-color: var(--accent-line, var(--line));
  background: radial-gradient(circle at 50% 28%, rgba(255, 214, 102, 0.22), transparent 72%);
  font-size: 18px;
  font-weight: 800;
  line-height: 1.15;

  &:hover:not(:disabled) {
    border-color: #ffd666;
    box-shadow: 0 0 0 6px rgba(255, 214, 102, 0.12);
  }
  &:active:not(:disabled) {
    transform: scale(0.97);
  }
  &.pending {
    opacity: 0.7;
  }
}
.bell-emoji {
  font-size: 30px;
}
.bell-tip {
  font-size: 10px;
  font-weight: 400;
  color: var(--muted);
}
.flip-line {
  text-align: center;
  font-size: 13px;
  color: var(--muted);
  /* 固定占位：这行出现/消失时不让环形面板跟着上下跳 */
  min-height: 20px;
  margin: 12px 0 0;
}
.auto {
  color: #e0a53a;
}

.flash {
  position: fixed;
  left: 50%;
  top: 22%;
  transform: translate(-50%, 0);
  padding: 14px 46px 14px 24px;
  border-radius: 14px;
  background: var(--surface-3);
  border: 1px solid var(--line);
  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.35);
  font-size: 15px;
  /* 可手动关闭：整块可交互，不能再用 pointer-events:none */
  pointer-events: auto;

  &.correct {
    border-color: #4f9d69;
    color: #7fd39a;
  }
  &.wrong {
    border-color: #b4544a;
    color: #e08c81;
  }
}
.flash-close {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: inherit;
  font-size: 21px;
  line-height: 1;
  cursor: pointer;

  &:hover {
    background: rgba(255, 255, 255, 0.12);
  }
}
.flash-enter-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
/* 2.5s 保持 + 0.5s 淡出：总计 3s 内消失 */
.flash-leave-active {
  transition: opacity 0.5s ease, transform 0.5s ease;
}
.flash-enter-from,
.flash-leave-to {
  opacity: 0;
  transform: translate(-50%, -8px);
}

.over {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: rgba(10, 10, 12, 0.72);
  backdrop-filter: blur(6px);

  h2 {
    margin: 0;
    font-size: 22px;
  }
}
.ranks {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 260px;

  li {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 14px;
    border-radius: 10px;
    background: var(--surface-3);
    border: 1px solid var(--line);
  }
  .rk {
    width: 22px;
    height: 22px;
    display: grid;
    place-items: center;
    border-radius: 50%;
    background: var(--color-primary);
    color: #fff;
    font-size: 12px;
    font-weight: 700;
  }
  .rn {
    flex: 1;
  }
  .rc {
    color: var(--muted);
    font-size: 13px;
  }
}

@media (max-width: 720px) {
  .arena {
    min-height: 470px;
  }
  .player {
    --player-half-w: 66px;
    width: 132px;
    padding: 8px;
  }
  .p-fruit-img {
    width: 15px;
    height: 15px;
  }
  .bell {
    width: 108px;
    height: 108px;
    font-size: 15px;
  }
  .bell-emoji {
    font-size: 24px;
  }
}
</style>
