<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import * as api from '@/api/magicalAthlete'
import type { MagicalAthleteRoomInfo, MagicalAthleteSnapshot } from '@/api/magicalAthlete'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { useRoomEntry } from '@/composables/useRoomEntry'
import { racerHue, racerInfo, racerName, typeLabel } from '@/constants/racers'
import { copyText } from '@/utils/clipboard'
import { goBackOrLobby } from '@/utils/navigation'

const route = useRoute()
const router = useRouter()

const room = ref<MagicalAthleteRoomInfo | null>(null)
const snapshot = ref<MagicalAthleteSnapshot | null>(null)
const myName = ref('')
const clock = ref(Date.now())

/** 最近一次选人播报（手动可关，自动消失兜底） */
const lastPick = ref<{
  seatNo: number
  playerName: string
  racerKey: string
  auto: boolean
} | null>(null)
const PICK_TOAST_HOLD_MS = 2500

function dismissPick(): void {
  lastPick.value = null
}

/** 最近一次掷骰播报 */
const lastDiceMsg = ref<{
  seatNo: number
  value: number
  tripSkipped: boolean
  auto: boolean
  effects: string[]
} | null>(null)
const DICE_TOAST_HOLD_MS = 2200

/** 单场结算播报 */
const lastRaceResult = ref<{
  raceNo: number
  goldName: string
  silverName: string
  goldValue: number
  silverValue: number
} | null>(null)

function dismissDice(): void {
  lastDiceMsg.value = null
}

function dismissRaceResult(): void {
  lastRaceResult.value = null
}

/** 采用服务端房间信息但保住本人座位（中立广播 mySeatNo=null 时按名字反查） */
function adoptRoom(next: MagicalAthleteRoomInfo | null | undefined): void {
  if (!next) return
  let mine = next.mySeatNo
  if (mine == null) {
    mine = next.seats.find((s) => s.name === myName.value)?.seatNo ?? room.value?.mySeatNo ?? null
  }
  room.value = { ...next, mySeatNo: mine }
}

function syncMySeatFromSnapshot(): void {
  const snap = snapshot.value
  if (!snap || !room.value || room.value.mySeatNo !== null) return
  const mine = snap.players.find((p) => p.name === myName.value)?.seatNo
  if (mine) room.value = { ...room.value, mySeatNo: mine }
}

function handleMessage(msg: { type?: string; payload?: Record<string, any> }): void {
  const type = msg?.type
  const payload = msg?.payload ?? {}
  switch (type) {
    case 'PLAYER_JOINED':
    case 'SEAT_CHANGED':
      adoptRoom(payload.room as MagicalAthleteRoomInfo | null)
      if (payload.snapshot) snapshot.value = payload.snapshot as MagicalAthleteSnapshot
      break
    case 'GAME_START':
      adoptRoom(payload.room as MagicalAthleteRoomInfo | null)
      lastPick.value = null
      lastDiceMsg.value = null
      lastRaceResult.value = null
      break
    case 'DRAFT_STATE':
    case 'STATE_SYNC':
      if (payload) snapshot.value = payload as unknown as MagicalAthleteSnapshot
      syncMySeatFromSnapshot()
      break
    case 'MUSTER_READY':
      ElMessage.info(`${(payload.playerName as string) ?? '有玩家'} 已准备出场选手`)
      break
    case 'RACE_START':
      lastDiceMsg.value = null
      ElMessage.success(`第 ${payload.raceNo ?? snapshot.value?.raceNo ?? '?'} 场开始，出场选手已公开`)
      break
    case 'DICE_RESULT': {
      lastDiceMsg.value = {
        seatNo: payload.seatNo as number,
        value: payload.value as number,
        tripSkipped: !!payload.tripSkipped,
        auto: !!payload.auto,
        effects: (payload.effects as string[]) ?? [],
      }
      window.setTimeout(() => {
        const cur = lastDiceMsg.value
        if (cur && cur.seatNo === payload.seatNo && cur.value === payload.value) lastDiceMsg.value = null
      }, DICE_TOAST_HOLD_MS)
      break
    }
    case 'RACE_RESULT':
      lastRaceResult.value = {
        raceNo: payload.raceNo as number,
        goldName: (payload.goldName as string) ?? nameOfSeat(payload.goldSeat as number),
        silverName: (payload.silverName as string) ?? nameOfSeat(payload.silverSeat as number),
        goldValue: payload.goldValue as number,
        silverValue: payload.silverValue as number,
      }
      break
    case 'GAME_OVER':
      ElMessage.success(payload.tie ? '比赛结束：并列第一！' : '比赛结束，查看最终排名')
      break
    case 'DRAFT_PICK': {
      lastPick.value = {
        seatNo: payload.seatNo as number,
        playerName: (payload.playerName as string) ?? '玩家',
        racerKey: (payload.racerKey as string) ?? '',
        auto: !!payload.auto,
      }
      window.setTimeout(() => {
        const current = lastPick.value
        if (current && current.racerKey === payload.racerKey && current.seatNo === payload.seatNo) {
          lastPick.value = null
        }
      }, PICK_TOAST_HOLD_MS)
      break
    }
    case 'ACTION_REJECT':
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
  path: '/ws/magical-athlete',
  roomId: () => room.value?.roomId,
  playerName: () => myName.value,
  onMessage: handleMessage,
})

useRoomEntry({
  gameKey: 'magicalAthlete',
  loginRedirect: (roomId) => `/magical-athlete/${roomId}`,
  fetchRoom: api.getRoom,
  isValid: (data) => !!data.room,
  applyRoom: (data) => adoptRoom(data.room),
  playerName: myName,
  connect,
})

const roomId = computed(() => (route.params.roomId as string) ?? room.value?.roomId ?? '')
const status = computed(() => room.value?.status ?? 'WAITING')
const isHost = computed(() => !!room.value && room.value.hostName === myName.value)
const seatedCount = computed(() => room.value?.seats.filter((s) => !!s.name).length ?? 0)
const mySeat = computed(() => {
  const raw = room.value?.mySeatNo
    ?? snapshot.value?.players.find((p) => p.name === myName.value)?.seatNo
    ?? null
  return raw == null ? null : Number(raw)
})
const phase = computed(() => snapshot.value?.phase ?? 'WAITING')

/** M5 3 人双角色变体：普通局 racersPerSeat=1 / 总批次=2；双角色 2 / 4 */
const isDoubleVariant = computed(() => (snapshot.value?.variant ?? 'NORMAL') === 'THREE_DOUBLE')
const racersPerSeat = computed(() => snapshot.value?.racersPerSeat ?? 1)
const totalBatches = computed(() => (isDoubleVariant.value ? 4 : 2))
/** 房主开局时的变体开关（仅恰好 3 人入座时可开启） */
const useDoubleVariant = ref(false)
const canOfferDouble = computed(() => seatedCount.value === 3)
const statusText = computed(() =>
  status.value === 'WAITING' ? '等待中' : status.value === 'PLAYING' ? '对局中' : '已结束',
)

/**
 * 当前轮抽是否轮到我。
 * 座位号统一用 Number() 归一化后比较：服务端某些序列化路径会下发字符串座位号（如 "2"），
 * 严格 === 会误判为"没轮到我"，导致选人卡片 disabled（轮到我了却点不动）。
 */
const currentSeat = computed(() => {
  const raw = snapshot.value?.currentSeatNo
  return raw == null ? null : Number(raw)
})
const isMyPick = computed(
  () => phase.value === 'DRAFT' && currentSeat.value !== null && currentSeat.value === mySeat.value,
)

/** 轮抽倒计时秒数（与服务端 30s 对齐） */
const secondsLeft = computed(() => {
  const deadline = snapshot.value?.deadlineAt ?? 0
  if (!deadline) return 0
  return Math.max(0, Math.ceil((deadline - clock.value) / 1000))
})

function nameOfSeat(seatNo: number | null | undefined): string {
  if (seatNo == null) return '—'
  return (
    snapshot.value?.players.find((p) => p.seatNo === seatNo)?.name ??
    room.value?.seats.find((s) => s.seatNo === seatNo)?.name ??
    `座位 ${seatNo}`
  )
}

const pool = computed(() => snapshot.value?.pool ?? [])
const hands = computed(() => {
  const snap = snapshot.value
  if (!snap) return [] as { seatNo: number; name: string; racers: string[] }[]
  return Object.entries(snap.hands)
    .map(([seat, racers]) => ({
      seatNo: Number(seat),
      name: nameOfSeat(Number(seat)),
      racers: racers as string[],
    }))
    .sort((a, b) => a.seatNo - b.seatNo)
})

/** MUSTER：我的手牌（后端只下发本人手牌） */
const myHand = computed(() => {
  const snap = snapshot.value
  if (!snap || mySeat.value === null) return [] as string[]
  return snap.hands[String(mySeat.value)] ?? []
})

/** 我是否已暗选（普通局单值，M5 双角色用列表） */
const myActiveKey = computed(() => snapshot.value?.myActiveRacerKey ?? null)
const myActiveKeys = computed<string[]>(() => {
  const list = snapshot.value?.myActiveRacerKeys
  if (list && list.length) return list
  return myActiveKey.value ? [myActiveKey.value] : []
})
/** 暗选是否已完成：普通局选 1 名，双角色需选满 2 名（顺序即固定出场顺序） */
const musterDone = computed(() => myActiveKeys.value.length >= racersPerSeat.value)
/** 各真实座位的暗选进度（不含具体选手，服务端 MUSTER 个性化下发；key 序列化为字符串） */
const musterProgressMap = computed<Record<string, number>>(() => snapshot.value?.musterProgress ?? {})
/** 本座位暗选引导/状态文案 */
const musterHint = computed(() => {
  if (musterDone.value) {
    return isDoubleVariant.value
      ? `已锁定 ${myActiveKeys.value.map((k) => racerName(k)).join(' → ')}（按点选顺序固定出场），等待其他玩家…`
      : `已锁定 ${racerName(myActiveKeys.value[0])}，等待其他玩家…`
  }
  return isDoubleVariant.value
    ? `从手牌中依次暗选 ${racersPerSeat.value} 名选手（点选顺序即固定出场顺序），用过的选手不可再次出场`
    : '从你的手牌中暗选 1 名选手，用过的选手不可再次出场'
})

/** 已准备的座位 */
const readySeats = computed(() => snapshot.value?.readySeats ?? [])

/** RACE：是否轮到我掷骰（座位号同样归一化，避免字符串座位号误判） */
const isMyRoll = computed(
  () => phase.value === 'RACE' && currentSeat.value !== null && currentSeat.value === mySeat.value,
)

/** M4：本回合可用的可选能力项（仅轮到我时服务端下发） */
const myOptionalOptions = computed(() => snapshot.value?.myOptionalOptions ?? [])
const hasOptionalChoice = computed(() => isMyRoll.value && myOptionalOptions.value.length > 0)
/** 可选能力的目标输入（座位 / 格 / 猜骰），按选项类型显示 */
const optionalTarget = ref<number>(0)
const optionalUsed = ref(false)
const OPTION_LABELS: Record<string, string> = {
  USE_PRIMARY: '启用能力',
  TARGET_SEAT: '指定目标座位',
  TARGET_SPACE: '指定目标格',
  GUESS_DICE: '猜骰点(1-6)',
}

/**
 * M5 双角色局：TARGET_SEAT 的值是「参赛号 lane」(1..6)，不是真实座位(1..3)。
 * 后端 useOptional 按 race.racers() 的 seatNo() 校验，所以这里直接把赛道棋子作为选项，
 * 以「玩家 · 选手名」呈现，避免玩家把 lane 误当座位号输入非法值。
 * 普通局 lane==seat，继续用数字输入，行为零回归。
 */
const optionalTargetOptions = computed(() =>
  (snapshot.value?.racers ?? [])
    .filter((r) => r.finishRank === 0)
    .map((r) => ({ value: r.seatNo, label: `${r.name} · ${racerName(r.racerKey)}` })),
)

function optionalLabel(opt: string): string {
  return isDoubleVariant.value && opt === 'TARGET_SEAT'
    ? '指定目标选手'
    : (OPTION_LABELS[opt] ?? opt)
}

/** 双角色局：当前行动 lane 对应的出场选手名（普通局恒为空，不影响原提示） */
const currentLaneRacerName = computed(() => {
  if (!isDoubleVariant.value) return ''
  const lane = snapshot.value?.currentLane
  if (lane == null) return ''
  const key = snapshot.value?.activeRacerKeysByLane?.[String(lane)]
  return key ? racerName(key) : ''
})

/** 声明本回合可选能力意图（在掷骰前）；每一项发送后自动清空，可继续声明其它项 */
function useOptional(option: string): void {
  if (!hasOptionalChoice.value) return
  const payload: Record<string, number | boolean> = { usePrimary: false, targetSeat: 0, targetSpace: 0, guessDice: 0 }
  switch (option) {
    case 'USE_PRIMARY': payload.usePrimary = true; break
    case 'TARGET_SEAT': payload.targetSeat = optionalTarget.value; break
    case 'TARGET_SPACE': payload.targetSpace = optionalTarget.value; break
    case 'GUESS_DICE': payload.guessDice = optionalTarget.value; break
    default: return
  }
  send({ type: 'USE_OPTIONAL', payload })
  optionalUsed.value = true
  optionalTarget.value = 0
}

/** RACE：赛道上的棋子（含狂野面特殊格标记） */
const racers = computed(() => snapshot.value?.racers ?? [])

const WILD_SPECIALS: Record<number, { icon: string; label: string }> = {
  1: { icon: '🪙', label: '+1 铜币' },
  5: { icon: '💤', label: '绊倒' },
  7: { icon: '➡️', label: '+3' },
  11: { icon: '➡️', label: '+1' },
  13: { icon: '🪙', label: '+1 铜币' },
  16: { icon: '⬅️', label: '-4' },
  17: { icon: '💤', label: '绊倒' },
  23: { icon: '➡️', label: '+2' },
  24: { icon: '⬅️', label: '-2' },
  26: { icon: '💤', label: '绊倒' },
}

const isWildTrack = computed(() => (snapshot.value?.raceNo ?? 0) % 2 === 0)
const trackCells = computed(() => Array.from({ length: 31 }, (_, i) => i))

function specialAt(cell: number) {
  return isWildTrack.value ? WILD_SPECIALS[cell] : undefined
}

function racersAt(cell: number) {
  return racers.value.filter((r) => r.position === cell)
}

function startGame(): void {
  // M5：3 人局房主可显式开启双角色变体；其余情况不带参数（服务端按默认变体）
  const payload: Record<string, string> = {}
  if (useDoubleVariant.value && canOfferDouble.value) payload.variant = 'THREE_DOUBLE'
  send({ type: 'START_GAME', payload })
}

function pickRacer(key: string): void {
  if (!isMyPick.value) return
  send({ type: 'PICK_RACER', payload: { racerKey: key } })
}

function readyRacer(key: string): void {
  // M5 双角色：未选满前可继续点选，已提交的 key 不可重复（提交顺序固定出场顺序）
  if (phase.value !== 'MUSTER' || musterDone.value) return
  if (myActiveKeys.value.includes(key)) return
  send({ type: 'READY_RACER', payload: { racerKey: key } })
}

function roll(): void {
  if (!isMyRoll.value) return
  send({ type: 'ROLL', payload: {} })
}

function backToLobby(): void {
  router.push('/lobby')
}

async function copyRoomId(): Promise<void> {
  const ok = await copyText(room.value?.roomId ?? roomId.value)
  ElMessage[ok ? 'success' : 'warning'](ok ? '房间号已复制' : '复制失败，请手动复制')
}

async function seatAt(seatNo: number): Promise<void> {
  if (!myName.value) return
  try {
    const data = await api.takeSeat(roomId.value, myName.value, seatNo)
    if (!data.success) ElMessage.warning(data.error ?? '入座失败')
  } catch {
    ElMessage.error('入座失败，请稍后重试')
  }
}

async function leaveSeat(): Promise<void> {
  if (!myName.value) return
  try {
    const data = await api.standUp(roomId.value, myName.value)
    if (!data.success) ElMessage.warning(data.error ?? '起身失败')
  } catch {
    ElMessage.error('起身失败，请稍后重试')
  }
}

async function dissolveRoom(): Promise<void> {
  send({ type: 'DISSOLVE_ROOM', payload: {} })
  router.replace('/lobby')
}

let ticker: number | null = null
onMounted(() => {
  ticker = window.setInterval(() => {
    clock.value = Date.now()
  }, 250)
})
onUnmounted(() => {
  if (ticker) window.clearInterval(ticker)
})

function cardStyle(key: string): Record<string, string> {
  return { '--hue': String(racerHue(key)) }
}
</script>

<template>
  <div class="ma-page">
    <!-- 页眉：与宝石商人/德国心脏病同构的三段式（左=房间号/复制，中=连接/轮次/规则，右=返回/解散） -->
    <header class="room-bar">
      <div class="bar-left">
        <span class="room-id">房间号：<strong>{{ room?.roomId ?? roomId ?? '...' }}</strong></span>
        <el-button size="small" @click="copyRoomId">📋 复制</el-button>
      </div>
      <div class="bar-center">
        <span class="conn">
          <i class="dot" :class="{ on: connected }"></i>{{ connected ? '已连接' : '重连中' }}
        </span>
        <span class="round-info">第 {{ snapshot?.raceNo || '—' }} 场</span>
        <span class="round-info">{{ statusText }}</span>
        <el-button size="small" plain @click="router.push('/rules/magicalAthlete')">📖 规则</el-button>
      </div>
      <div class="bar-right">
        <el-button size="small" plain @click="goBackOrLobby(router)">← 返回上一页</el-button>
        <el-button size="small" plain @click="backToLobby">← 返回大厅</el-button>
        <el-button v-if="isHost" size="small" type="danger" plain @click="dissolveRoom">🗑 解散</el-button>
      </div>
    </header>

    <div v-if="replacedNotice" class="banner">
      已在其他标签页打开同一房间，本页停止重连。
      <button type="button" class="link" @click="router.go(0)">重新进入</button>
    </div>

    <!-- 等待开局：与宝石商人同构的座位卡 + 居中操作区 -->
    <section v-if="status === 'WAITING'" class="waiting">
      <div class="seat-grid">
        <button
          v-for="no in room?.maxPlayers ?? 4"
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
              <span v-if="room?.seats.find((s) => s.seatNo === no)?.afk" class="afk-tag">💤 待机</span>
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
        <!-- M5：恰好 3 人入座时房主可开启双角色变体（四批轮抽 / 每场 2 名选手出场） -->
        <label v-if="isHost && canOfferDouble" class="variant-toggle">
          <input v-model="useDoubleVariant" type="checkbox" />
          3 人双角色变体（每人 8 张手牌 / 每场 2 名选手出场）
        </label>
        <el-button
          v-if="isHost"
          type="primary"
          :disabled="seatedCount < 2"
          @click="startGame"
        >
          🎮 开局（{{ seatedCount }}/{{ room?.maxPlayers }} 已入座）
        </el-button>
        <span v-else-if="!mySeat" class="spectate-hint">
          👀 观战中 · 点击空位入座{{ room?.hostName ? `（房主：${room.hostName}）` : '' }}
        </span>
        <span v-else class="spectate-hint">等待房主 {{ room?.hostName }} 开局…</span>
      </div>
    </section>

    <!-- 轮抽阶段 -->
    <section v-else-if="phase === 'DRAFT'" class="draft-view">
      <div class="draft-head">
        <h2>轮抽组队</h2>
        <p class="hint">
          第 {{ (snapshot?.batch ?? 0) + 1 }}/{{ totalBatches }} 批 · 按蛇形顺序选人 ·
          <strong :class="{ mine: isMyPick }">{{ isMyPick ? '轮到你了！' : `等待 ${nameOfSeat(snapshot?.currentSeatNo)} 选择` }}</strong>
        </p>
        <span class="countdown" :class="{ urgent: secondsLeft <= 5 }">
          ⏱ {{ secondsLeft }}s
        </span>
      </div>

      <!-- 蛇形选序指示 -->
      <div class="turn-strip">
        <span
          v-for="(seat, i) in snapshot?.pickOrder ?? []"
          :key="i"
          class="turn-cell"
          :class="{
            current: i === snapshot?.pickIndex,
            done: i < (snapshot?.pickIndex ?? 0),
            mine: seat === mySeat,
          }"
        >{{ nameOfSeat(seat) }}</span>
      </div>

      <!-- 当前牌池 -->
      <div class="pool">
        <h3>当前牌池 <span class="sub">点选一名选手</span></h3>
        <div class="racer-grid">
          <button
            v-for="key in pool"
            :key="key"
            type="button"
            class="racer-card"
            :class="{ pickable: isMyPick }"
            :style="cardStyle(key)"
            :disabled="!isMyPick"
            @click="pickRacer(key)"
          >
            <span class="racer-badge" :style="{ background: `hsl(${racerHue(key)}, 55%, 45%)` }">
              {{ racerName(key).slice(0, 1) }}
            </span>
            <span class="racer-name">{{ racerName(key) }}</span>
            <span class="racer-type">{{ typeLabel(racerInfo(key).abilityType) }}</span>
            <span class="racer-ability">{{ racerInfo(key).abilitySummary }}</span>
          </button>
        </div>
      </div>

      <!-- 各人已选手牌 -->
      <div class="hands">
        <h3>已选选手</h3>
        <div class="hand-list">
          <div v-for="hand in hands" :key="hand.seatNo" class="hand-row" :class="{ mine: hand.seatNo === mySeat }">
            <span class="hand-name">{{ hand.name }}{{ hand.seatNo === mySeat ? '（我）' : '' }}</span>
            <div class="hand-racers">
              <span
                v-for="(key, i) in hand.racers"
                :key="i"
                class="hand-chip"
                :style="{ borderColor: `hsl(${racerHue(key)}, 55%, 45%)` }"
              >{{ racerName(key) }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 赛前暗选：只显示自己的手牌；他人只广播"已准备" -->
    <section v-else-if="phase === 'MUSTER'" class="draft-view">
      <div class="draft-head">
        <h2>第 {{ (snapshot?.raceNo ?? 0) + 1 }} 场 · 赛前准备</h2>
        <p class="hint"><strong :class="{ mine: musterDone }">{{ musterHint }}</strong></p>
        <span class="countdown" :class="{ urgent: secondsLeft <= 5 }">⏱ {{ secondsLeft }}s</span>
      </div>
      <div class="ready-strip">
        <span
          v-for="p in snapshot?.players ?? []"
          :key="p.seatNo"
          class="ready-chip"
          :class="{ on: readySeats.includes(p.seatNo), mine: p.seatNo === mySeat }"
        >{{ p.name }}{{ readySeats.includes(p.seatNo) ? ' ✓ 已准备' : (isDoubleVariant ? ` · ${musterProgressMap[p.seatNo] ?? 0}/${racersPerSeat}` : ' · 选择中') }}</span>
      </div>
      <!-- M5 双角色：展示本人已锁定的出场顺序（普通局不显示） -->
      <div v-if="isDoubleVariant && myActiveKeys.length" class="muster-locked">
        <span v-for="(key, i) in myActiveKeys" :key="key" class="locked-chip">
          出场 {{ i + 1 }} · {{ racerName(key) }}
        </span>
      </div>
      <div v-if="!musterDone" class="pool">
        <h3>我的手牌 <span class="sub">点选锁定出场选手（暗选，对手不可见）</span></h3>
        <div class="racer-grid">
          <button
            v-for="key in myHand"
            :key="key"
            type="button"
            class="racer-card"
            :class="{ pickable: !myActiveKeys.includes(key), locked: myActiveKeys.includes(key) }"
            :style="cardStyle(key)"
            :disabled="myActiveKeys.includes(key)"
            @click="readyRacer(key)"
          >
            <span class="racer-badge" :style="{ background: `hsl(${racerHue(key)}, 55%, 45%)` }">
              {{ racerName(key).slice(0, 1) }}
            </span>
            <span class="racer-name">{{ racerName(key) }}</span>
            <span class="racer-type">{{ typeLabel(racerInfo(key).abilityType) }}</span>
            <span class="racer-ability">{{ racerInfo(key).abilitySummary }}</span>
          </button>
        </div>
      </div>
    </section>

    <!-- 比赛：横向赛道 + 棋子 + 掷骰 -->
    <section v-else-if="phase === 'RACE' || phase === 'RACE_END'" class="race-view">
      <div class="draft-head">
        <h2>第 {{ snapshot?.raceNo }} 场 · {{ isWildTrack ? '狂野面' : '平缓面' }}</h2>
        <p class="hint">
          <template v-if="isMyRoll"><strong class="mine">轮到你掷骰！</strong></template>
          <template v-else>等待 <strong>{{ nameOfSeat(snapshot?.currentSeatNo) }}</strong> 掷骰</template>
          <span v-if="currentLaneRacerName" class="lane-hint">· 当前出场：{{ currentLaneRacerName }}</span>
        </p>
        <span class="countdown" :class="{ urgent: secondsLeft <= 5 }">⏱ {{ secondsLeft }}s</span>
      </div>

      <div class="track" :class="{ wild: isWildTrack }">
        <div v-for="cell in trackCells" :key="cell" class="track-cell" :class="{ finish: cell === 30, start: cell === 0 }">
          <span class="cell-no">{{ cell }}</span>
          <span v-if="specialAt(cell)" class="cell-special" :title="specialAt(cell)?.label">
            {{ specialAt(cell)?.icon }}
          </span>
          <div class="piece-row">
            <span
              v-for="r in racersAt(cell)"
              :key="r.seatNo"
              class="piece"
              :class="{ mine: (r.ownerSeatNo ?? r.seatNo) === mySeat, tripped: r.tripped, finished: r.finishRank > 0 }"
              :style="{ background: `hsl(${racerHue(r.racerKey)}, 55%, 45%)` }"
              :title="`${r.name} · ${racerName(r.racerKey)}${r.tripped ? ' · 绊倒' : ''}`"
            >{{ r.name.slice(0, 1) }}</span>
          </div>
        </div>
      </div>

      <div v-if="hasOptionalChoice" class="optional-panel">
        <span class="optional-title">本回合可选能力：</span>
        <div v-for="opt in myOptionalOptions" :key="opt" class="optional-item">
          <span>{{ optionalLabel(opt) }}</span>
          <!-- M5 双角色：TARGET_SEAT 提交的是参赛号 lane，用具体选手下拉避免座位/lane 混淆 -->
          <select
            v-if="opt === 'TARGET_SEAT' && isDoubleVariant"
            v-model.number="optionalTarget"
            class="optional-input"
          >
            <option :value="0" disabled>选择目标选手</option>
            <option v-for="o in optionalTargetOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
          <input
            v-else-if="opt !== 'USE_PRIMARY'"
            v-model.number="optionalTarget"
            class="optional-input"
            type="number"
            min="1"
            :max="opt === 'GUESS_DICE' ? 6 : 30"
            placeholder="数字"
          />
          <button type="button" class="optional-btn" @click="useOptional(opt)">使用</button>
        </div>
        <span v-if="optionalUsed" class="optional-ok">已声明，掷骰后生效</span>
      </div>

      <div class="race-ops">
        <button type="button" class="primary" :disabled="!isMyRoll" @click="roll">
          🎲 掷骰
        </button>
        <div class="score-strip">
          <span v-for="(total, seat) in snapshot?.scores ?? {}" :key="seat" class="score-chip">
            {{ nameOfSeat(Number(seat)) }} <b>{{ total }}</b> 分
          </span>
        </div>
      </div>
    </section>

    <!-- 终局排名 -->
    <section v-else-if="phase === 'FINISHED'" class="draft-view">
      <div class="draft-head">
        <h2>比赛结束 · 最终排名</h2>
        <p class="hint">四场累计金牌、银牌与铜币面值；总分相同并列第一</p>
      </div>
      <div class="rank-list">
        <div
          v-for="s in snapshot?.rankings ?? []"
          :key="s.seatNo"
          class="rank-row"
          :class="{ mine: s.seatNo === mySeat }"
        >
          <span class="rank-no">{{ s.rank }}</span>
          <span class="rank-name">{{ s.name }}{{ s.seatNo === mySeat ? '（我）' : '' }}</span>
          <span class="rank-medals">🥇{{ s.gold }} · 🥈{{ s.silver }} · 🪙{{ s.bronze }}</span>
          <strong class="rank-total">{{ s.total }} 分</strong>
        </div>
      </div>
    </section>

    <!-- 选人播报 -->
    <Transition name="flash">
      <div v-if="lastPick" class="pick-toast" :class="{ auto: lastPick.auto }">
        <strong>{{ lastPick.playerName }}</strong>
        {{ lastPick.auto ? '超时由系统代选' : '选走了' }}
        <b>{{ racerName(lastPick.racerKey) }}</b>
        <button type="button" class="close" @click="dismissPick">×</button>
      </div>
    </Transition>

    <!-- 掷骰播报 -->
    <Transition name="flash">
      <div v-if="lastDiceMsg" class="pick-toast dice-toast" :class="{ auto: lastDiceMsg.auto }">
        <strong>{{ nameOfSeat(lastDiceMsg.seatNo) }}</strong>
        {{ lastDiceMsg.auto ? '超时代掷' : '掷出' }}
        <b class="dice-value">🎲 {{ lastDiceMsg.value }}</b>
        <span v-if="lastDiceMsg.tripSkipped" class="trip-tag">绊倒 · 跳过主要移动</span>
        <span v-else-if="lastDiceMsg.effects.length" class="effect-tag">
          {{ lastDiceMsg.effects.filter((e) => !e.startsWith('MAIN_MOVE')).join(' / ') }}
        </span>
        <button type="button" class="close" @click="dismissDice">×</button>
      </div>
    </Transition>

    <!-- 单场结算播报 -->
    <Transition name="flash">
      <div v-if="lastRaceResult" class="pick-toast result-toast">
        <span>第 {{ lastRaceResult.raceNo }} 场结束</span>
        <b>🥇 {{ lastRaceResult.goldName }} +{{ lastRaceResult.goldValue }}</b>
        <b>🥈 {{ lastRaceResult.silverName }} +{{ lastRaceResult.silverValue }}</b>
        <button type="button" class="close" @click="dismissRaceResult">×</button>
      </div>
    </Transition>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/room' as room;

@include room.room-bar;
@include room.round-info;
@include room.room-waiting;
@include room.afk-tag;
@include room.conn-dot;
@include room.room-banner;

.ma-page {
  min-height: 100vh;
  padding: 20px 24px 40px;
  color: var(--ink);
}

/* 座位卡是 <button>，需重置浏览器默认外观（与德国心脏病一致） */
.seat-card {
  background: var(--surface-1, transparent);
  color: var(--ink);
  font: inherit;
}

button.primary {
  font: inherit;
  cursor: pointer;
  border-radius: 10px;
  border: 1px solid var(--line);
  padding: 8px 14px;
  background: var(--color-primary);
  border-color: transparent;
  color: #fff;
  font-weight: 700;
  &:disabled { cursor: not-allowed; opacity: 0.45; }
}

.draft-view {
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.draft-head {
  position: relative;
  text-align: center;
  h2 { margin: 0 0 6px; font-size: 22px; }
  strong.mine { color: #4f9d69; }
  .countdown {
    position: absolute;
    right: 0;
    top: 4px;
    font-size: 15px;
    font-variant-numeric: tabular-nums;
    color: var(--muted);
    &.urgent { color: #d9604e; font-weight: 800; }
  }
}

.turn-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
}
.turn-cell {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--line);
  font-size: 12px;
  color: var(--muted);
  &.done { opacity: 0.4; }
  &.current { border-color: var(--color-primary); color: var(--ink); font-weight: 700; }
  &.mine { background: rgba(79, 157, 105, 0.12); }
}

.pool h3,
.hands h3 {
  margin: 0 0 10px;
  font-size: 15px;
  .sub { font-size: 12px; color: var(--muted); font-weight: 400; margin-left: 6px; }
}
.racer-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}
.racer-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid var(--line);
  background: var(--surface-2, rgba(255, 255, 255, 0.03));
  color: var(--ink);
  text-align: left;
  cursor: default;
  transition: transform 0.15s, border-color 0.15s, box-shadow 0.15s;
  &.pickable {
    cursor: pointer;
    &:hover {
      transform: translateY(-3px);
      border-color: hsl(var(--hue), 55%, 45%);
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.18);
    }
  }
  &:disabled { opacity: 0.72; }
}
.racer-badge {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 800;
  font-size: 15px;
}
.racer-name { font-size: 15px; font-weight: 700; }
.racer-type { font-size: 11px; color: var(--muted); }
.racer-ability { font-size: 11px; line-height: 1.4; color: var(--muted); }

.hand-list { display: flex; flex-direction: column; gap: 8px; }
.hand-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid var(--line);
  &.mine { border-color: var(--color-primary); }
}
.hand-name { width: 120px; font-size: 13px; font-weight: 600; flex-shrink: 0; }
.hand-racers { display: flex; flex-wrap: wrap; gap: 6px; }
.hand-chip {
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--line);
  font-size: 12px;
  &.empty { color: var(--muted); border-style: dashed; }
}

.pick-toast {
  position: fixed;
  left: 50%;
  top: 76px;
  transform: translateX(-50%);
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border-radius: 12px;
  background: var(--surface-3);
  border: 1px solid var(--color-primary);
  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.3);
  font-size: 14px;
  &.auto { border-color: #e0a53a; }
  .close {
    border: none;
    background: transparent;
    color: var(--muted);
    font-size: 18px;
    cursor: pointer;
    line-height: 1;
    margin-left: 6px;
  }
}
.flash-enter-active,
.flash-leave-active { transition: opacity 0.5s ease, transform 0.5s ease; }
.flash-enter-from,
.flash-leave-to { opacity: 0; transform: translate(-50%, -10px); }

/* ---- M2：暗选就绪 ---- */
.ready-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
.ready-chip {
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid var(--line);
  font-size: 12px;
  color: var(--muted);
  &.on { border-color: #4f9d69; color: #7fd39a; }
  &.mine { font-weight: 700; }
}

/* ---- M5：3 人双角色变体 ---- */
.variant-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
  cursor: pointer;
  input { cursor: pointer; }
}
.muster-locked {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
.locked-chip {
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid #4f9d69;
  color: #7fd39a;
  font-size: 12px;
  font-weight: 700;
}
.lane-hint { font-size: 12px; color: var(--muted); margin-left: 6px; }
.racer-card.locked { opacity: 0.45; filter: grayscale(0.6); }
.optional-panel select.optional-input {
  width: auto;
  min-width: 170px;
  cursor: pointer;
}

/* ---- M2：赛道 ---- */
.race-view {
  max-width: 1320px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.track {
  display: grid;
  grid-template-columns: repeat(10, minmax(0, 1fr));
  gap: 4px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid var(--line);
  background: var(--surface-2, rgba(255, 255, 255, 0.03));

  &.wild { border-color: rgba(224, 165, 58, 0.5); }
}
.track-cell {
  position: relative;
  min-height: 60px;
  padding: 4px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: var(--surface-1, transparent);
  text-align: center;

  &.start { border-color: #4f9d69; }
  &.finish {
    border-color: #e0a53a;
    background: rgba(224, 165, 58, 0.12);
  }
}
.cell-no {
  position: absolute;
  top: 2px;
  left: 4px;
  font-size: 10px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}
.cell-special {
  position: absolute;
  top: 0;
  right: 3px;
  font-size: 12px;
}
.piece-row {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  justify-content: center;
  padding-top: 12px;
}
.piece {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.25);

  &.mine { outline: 2px solid #fff; outline-offset: 1px; }
  &.tripped { opacity: 0.55; filter: grayscale(0.7); }
  &.finished { box-shadow: 0 0 0 2px #e0a53a; }
}
.optional-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin: 8px auto;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--panel, transparent);
  .optional-title { font-size: 13px; font-weight: 600; color: var(--ink); }
  .optional-item {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: var(--muted);
  }
  .optional-input {
    width: 76px;
    padding: 4px 6px;
    border: 1px solid var(--line);
    border-radius: 6px;
    background: transparent;
    color: var(--ink);
  }
  .optional-btn {
    padding: 4px 12px;
    border: 1px solid var(--line);
    border-radius: 6px;
    cursor: pointer;
    color: var(--ink);
    background: transparent;
    &:hover { border-color: var(--brand, #409eff); }
  }
  .optional-ok { font-size: 12px; color: #2e9e5b; }
}
.race-ops {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
}
.score-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.score-chip {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--line);
  font-size: 12px;
  color: var(--muted);
  b { color: var(--ink); }
}

/* ---- M2：终局排名 ---- */
.rank-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 720px;
  margin: 0 auto;
}
.rank-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid var(--line);
  &.mine { border-color: var(--color-primary); }
}
.rank-no {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--surface-3);
  font-weight: 800;
}
.rank-name { flex: 1; font-size: 15px; font-weight: 600; }
.rank-medals { font-size: 13px; color: var(--muted); }
.rank-total { font-size: 16px; }

/* ---- M2：播报 ---- */
.dice-toast { border-color: var(--color-primary); }
.dice-value { font-size: 20px; }
.trip-tag {
  font-size: 12px;
  color: #e08c81;
  border: 1px solid rgba(180, 84, 74, 0.5);
  border-radius: 6px;
  padding: 1px 6px;
}
.effect-tag {
  font-size: 11px;
  color: var(--muted);
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.result-toast { border-color: #e0a53a; gap: 10px; }

@media (max-width: 720px) {
  .racer-grid { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); }
  .hand-name { width: 84px; }
  .track { grid-template-columns: repeat(6, minmax(0, 1fr)); }
  .rank-medals { display: none; }
}
</style>
