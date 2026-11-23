<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import * as splendorApi from '@/api/splendor'
import type { SplendorRoomInfo } from '@/api/splendor'
import { useChat, type ChatMessage } from '@/composables/useChat'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { forgetLastRoom } from '@/composables/useLastRoom'
import { useRoomEntry } from '@/composables/useRoomEntry'
import { copyText } from '@/utils/clipboard'
import { goBackOrLobby } from '@/utils/navigation'
import {
  COLOR_HEX,
  GEM_COLORS,
  computePayment,
  describeAction,
  gemToken,
  gemTotal,
  isTakeGemsValid,
  pendingLabel,
  takeTotal,
  type AnyColor,
  type GemMap,
} from '@/utils/splendor'

/** ---- 快照类型（后端 SplendorSnapshotVO，协议 §4） ---- */
interface SplendorCard {
  code: string
  level: number
  bonus: string
  vp: number
  cost: Record<string, number>
}
interface SplendorNoble {
  code: string
  name: string | null
  vp: number
  need: Record<string, number>
}
interface SplendorPlayerVO {
  seatNo: number
  name: string
  gems: Record<string, number>
  bonuses: Record<string, number>
  vp: number
  reservedCount: number
  reservedCards: SplendorCard[] | null
}
interface SplendorSnapshot {
  viewerSeatNo: number | null
  status: string
  roundNo: number
  currentSeatNo: number
  bank: Record<string, number>
  levels: Record<string, { cards: SplendorCard[]; deckLeft: number }>
  nobles: SplendorNoble[]
  players: SplendorPlayerVO[]
  pendingType: string | null
  pendingNobles: SplendorNoble[] | null
}
interface Ranking {
  seatNo: number
  name: string
  vp: number
  cards: number
  nobleCount?: number
}

const router = useRouter()

const room = ref<SplendorRoomInfo | null>(null)
const snapshot = ref<SplendorSnapshot | null>(null)
const myName = ref('')
const gameOver = ref(false)
const rankings = ref<Ranking[]>([])

/** 拿宝石选择（五色，不含 gold） */
const pick = reactive<Record<string, number>>({})
/** 弃宝石待办选择（含 gold） */
const discardSel = reactive<Record<string, number>>({})

const chatBox = ref<HTMLElement | null>(null)
const logBox = ref<HTMLElement | null>(null)

/** 玩家名 → 稳定颜色 class（同一玩家在不同消息里颜色一致） */
const NAME_PALETTE_SIZE = 8
function nameColorClass(name: string): string {
  let h = 0
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0
  return `pc-${h % NAME_PALETTE_SIZE}`
}

/** 玩家行动日志：与真人聊天分栏展示（进入/入座/开局/行动/待机/离开/终局等） */
const actionLogs = ref<ChatMessage[]>([])

function addActionLog(who: string, text: string): void {
  actionLogs.value.push({
    who,
    text,
    whoClass: who === 'system' ? 'system' : nameColorClass(who),
  })
  nextTick(() => {
    const el = logBox.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/**
 * WS 生命周期（连接/重连/心跳/互踢/关房收尾）由公共 composable 承担；
 * 这里只注入游戏专属的消息分发与断连时的临时态清理。
 */
const {
  connected,
  replacedNotice,
  send: wsSend,
  connect,
  resume,
  markServerClosed,
} = useGameWebSocket({
  path: '/ws/splendor',
  roomId: () => room.value?.roomId,
  playerName: () => myName.value,
  onMessage: (msg) => handleWSMessage(msg),
  onDisconnected: clearPick,
})

const { chatMessages, chatText, addChatMsg, sendChat } = useChat({
  send: (text) => wsSend({ type: 'CHAT', payload: { text } }),
  classifyWho: (who) => (who === 'system' ? 'system' : nameColorClass(who)),
  scrollTarget: chatBox,
})

// ---- 派生状态 ----

/** 我的座位号：房间等待期取 room.viewerSeatNo，对局中以快照为准 */
const viewerSeatNo = computed(
  () => snapshot.value?.viewerSeatNo ?? room.value?.viewerSeatNo ?? null,
)

/** 轮到我行动（待办阶段也算，因为待办正是我触发的） */
const isMyTurn = computed(
  () => !!snapshot.value && snapshot.value.viewerSeatNo === snapshot.value.currentSeatNo,
)

/** 能否发起主行动：对局中 + 我的回合 + 无待办 */
const canAct = computed(
  () =>
    !!snapshot.value &&
    snapshot.value.status === 'PLAYING' &&
    isMyTurn.value &&
    !snapshot.value.pendingType,
)

const isHost = computed(() => room.value?.hostName === myName.value)

const seatedCount = computed(
  () => room.value?.seats.filter((s) => s.playerName).length ?? 0,
)

/** 我本人玩家面板 */
const me = computed(
  () => snapshot.value?.players.find((p) => p.seatNo === viewerSeatNo.value) ?? null,
)

/** 所有玩家（含我），按座位号升序，统一渲染在左侧固定面板列 */
const allPlayers = computed(() =>
  [...(snapshot.value?.players ?? [])].sort((a, b) => a.seatNo - b.seatNo),
)

/** 玩家是否待机（room 的 afkPlayers 为权威来源） */
function isPlayerAfk(name: string): boolean {
  return !!room.value?.afkPlayers?.includes(name)
}

/** 逐色宝石清单：五色 + 黄金（黄金无发展卡红利，恒为 0） */
const totalColors: AnyColor[] = [...GEM_COLORS, 'gold']

/** 某色宝石合计 = 手中数 + 该色发展卡红利数 */
function colorTotal(p: SplendorPlayerVO, c: AnyColor): number {
  return (p.gems[c] ?? 0) + (p.bonuses[c] ?? 0)
}

/** 圆形宝石图标改用 token 图片（透明底），保留数字叠在图上 */
function gemStyle(color: AnyColor): Record<string, string> {
  return { backgroundImage: `url(${gemToken(color)})` }
}

/** 拿宝石选择合计 */
const pickTotal = computed(() =>
  GEM_COLORS.reduce((s, c) => s + (pick[c] ?? 0), 0),
)

/** 弃宝石数量：手牌总数 - 10 */
const discardNeed = computed(() =>
  me.value ? Math.max(0, gemTotal(me.value.gems) - 10) : 0,
)
const discardSelected = computed(() =>
  [...GEM_COLORS, 'gold'].reduce((s, c) => s + (discardSel[c] ?? 0), 0),
)

/** 待办文案（pendingType 公开） */
const pendingText = computed(() =>
  pendingLabel(snapshot.value?.pendingType, isMyTurn.value),
)

/** 市场从上到下：一级 → 二级 → 三级 */
const levelRows = computed(() => [1, 2, 3])

// ---- 行动入口 ----

/** 点银行宝石堆：能加则 +1，加不动则 -1（取消） */
function onGemClick(color: AnyColor): void {
  if (!canAct.value || color === 'gold') return
  const bank = snapshot.value?.bank ?? {}
  const cur = pick[color] ?? 0
  const cand: GemMap = { ...pick, [color]: cur + 1 }
  const bankCount = bank[color] ?? 0
  const canAdd =
    cur < 2 &&
    cur < bankCount &&
    pickTotal.value < 3 &&
    !(cand[color] === 2 && bankCount < 4) &&
    (takeTotal(cand) < 3 || isTakeGemsValid(cand, bank).ok)
  if (canAdd) {
    pick[color] = cur + 1
  } else if (cur > 0) {
    pick[color] = cur - 1
  }
}

function clearPick(): void {
  for (const c of GEM_COLORS) delete pick[c]
}

/** 确认拿宝石：客户端终审一遍，服务端权威 */
function confirmTake(): void {
  if (!canAct.value) return
  const gems: Record<string, number> = {}
  for (const c of GEM_COLORS) {
    if ((pick[c] ?? 0) > 0) gems[c] = pick[c]
  }
  const check = isTakeGemsValid(gems, snapshot.value?.bank ?? {})
  if (!check.ok) {
    ElMessage.warning(check.hint ?? '组合非法')
    return
  }
  wsSend({ type: 'TAKE_GEMS', payload: { gems } })
  clearPick()
}

/** 买桌面明卡（支付方案由引擎自动结算，客户端只做付得起预检） */
function buyTableCard(card: SplendorCard): void {
  if (!canAct.value) return
  if (me.value && !computePayment(card, me.value)) {
    ElMessage.warning('宝石不足，买不起这张卡（可考虑预留）')
    return
  }
  wsSend({ type: 'BUY_CARD', payload: { cardId: card.code, source: 'table' } })
}

/** 买本人预留卡 */
function buyReservedCard(card: SplendorCard): void {
  if (!canAct.value) return
  if (me.value && !computePayment(card, me.value)) {
    ElMessage.warning('宝石不足，还买不起这张预留卡')
    return
  }
  wsSend({ type: 'BUY_CARD', payload: { cardId: card.code, source: 'reserved' } })
}

/** 预留桌面明卡 */
function reserveTableCard(card: SplendorCard): void {
  if (!canAct.value) return
  wsSend({ type: 'RESERVE_CARD', payload: { cardId: card.code } })
}

/** 盲留某级牌堆顶 */
function reserveDeck(level: number): void {
  if (!canAct.value) return
  wsSend({ type: 'RESERVE_CARD', payload: { level } })
}

/** 弃宝石待办：点自己手牌宝石计数 */
function onDiscardClick(color: AnyColor): void {
  if (!isMyTurn.value || snapshot.value?.pendingType !== 'DISCARD') return
  const held = me.value?.gems[color] ?? 0
  const cur = discardSel[color] ?? 0
  if (cur < held && discardSelected.value < discardNeed.value) {
    discardSel[color] = cur + 1
  } else if (cur > 0) {
    discardSel[color] = cur - 1
  }
}

function confirmDiscard(): void {
  if (discardSelected.value !== discardNeed.value) {
    ElMessage.warning(`需要弃掉 ${discardNeed.value} 颗`)
    return
  }
  const gems: Record<string, number> = {}
  for (const c of [...GEM_COLORS, 'gold']) {
    if ((discardSel[c] ?? 0) > 0) gems[c] = discardSel[c]
  }
  wsSend({ type: 'DISCARD_GEMS', payload: { gems } })
  for (const c of [...GEM_COLORS, 'gold']) delete discardSel[c]
}

/** 贵族选择待办 */
function chooseNoble(noble: SplendorNoble): void {
  if (!isMyTurn.value || snapshot.value?.pendingType !== 'NOBLE_SELECT') return
  wsSend({ type: 'NOBLE_SELECT', payload: { nobleId: noble.code } })
}

// ---- 房间操作（REST） ----

async function chooseSeat(seatNo: number): Promise<void> {
  const roomId = room.value?.roomId
  if (!roomId || !myName.value) return
  if (room.value?.seats[seatNo - 1]?.playerName === myName.value) return
  try {
    const data = await splendorApi.takeSeat(roomId, myName.value, seatNo)
    applyRoom(data)
    if (!data.success) ElMessage.error(data.error ?? '入座失败')
  } catch {
    ElMessage.error('入座失败，请重试')
  }
}

async function doStandUp(): Promise<void> {
  const roomId = room.value?.roomId
  if (!roomId || !myName.value) return
  try {
    const data = await splendorApi.standUp(roomId, myName.value)
    applyRoom(data)
    if (!data.success) ElMessage.error(data.error ?? '当前无法起身')
  } catch {
    ElMessage.error('起身失败，请重试')
  }
}

function sendStartGame(): void {
  if (!isHost.value) {
    ElMessage.warning('只有房主可以开局')
    return
  }
  if (seatedCount.value < 2) {
    ElMessage.warning('至少 2 名玩家入座才能开局')
    return
  }
  wsSend({ type: 'START_GAME', payload: {} })
}

function applyRoom(data: splendorApi.SplendorRoomData): void {
  if (data.room) room.value = data.room
}

function applySnapshot(snap: SplendorSnapshot): void {
  snapshot.value = snap
  if (snap.status === 'FINISHED') gameOver.value = true
}

/** 广播/自身同步里的 room 合并（一律中立，viewerSeatNo 不采纳） */
function mergeRoom(msg: any): void {
  const incoming = msg.payload?.room as SplendorRoomInfo | undefined
  if (!incoming) return
  const { viewerSeatNo: _ignored, ...rest } = incoming
  room.value = { ...room.value, ...rest } as SplendorRoomInfo
}

async function copyRoomId(): Promise<void> {
  const id = room.value?.roomId
  if (!id) return
  if (await copyText(id)) ElMessage.success('房间号已复制')
  else ElMessage.error('复制失败，请手动复制')
}

/** 房主解散房间：二次确认后发 WS，服务端广播 ROOM_CLOSED 并断开所有连接 */
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
  wsSend({ type: 'DISSOLVE_ROOM', payload: {} })
}

function leaveRoom(): void {
  router.push('/lobby')
}

function handleWSMessage(msg: any): void {
  const t = msg.type
  const p = msg.payload
  switch (t) {
    case 'PLAYER_JOINED':
      mergeRoom(msg)
      // 仅自身同步带个性化快照（刷新恢复）
      if (msg.playerName === myName.value && p?.snapshot) {
        applySnapshot(p.snapshot as SplendorSnapshot)
      }
      addActionLog('system', `${msg.playerName} 进入了房间`)
      break
    case 'SEAT_CHANGED':
      mergeRoom(msg)
      addActionLog(
        'system',
        p?.seatNo != null ? `${msg.playerName} 坐到了 ${p.seatNo} 号位` : `${msg.playerName} 起身了`,
      )
      break
    case 'GAME_START':
      mergeRoom(msg)
      clearPick()
      addActionLog('system', '对局开始！')
      break
    case 'STATE_SYNC':
      // 载荷即个性化快照本体（协议 §2.2）
      if (p) applySnapshot(p as SplendorSnapshot)
      break
    case 'ACTION_DONE':
      addActionLog(msg.playerName, describeAction(p?.action, p?.detail))
      break
    case 'ACTION_REJECT':
      ElMessage.error(p?.message ?? '行动失败')
      break
    case 'GAME_OVER':
      if (p?.rankings) rankings.value = p.rankings as Ranking[]
      gameOver.value = true
      break
    case 'PLAYER_LEAVE':
      addActionLog('system', `${msg.playerName} 离开了房间`)
      break
    case 'PLAYER_AFK': {
      const name = msg.playerName as string
      const list = room.value?.afkPlayers ? [...room.value.afkPlayers] : []
      const idx = list.indexOf(name)
      if (p?.afk && idx < 0) list.push(name)
      if (!p?.afk && idx >= 0) list.splice(idx, 1)
      if (room.value) room.value = { ...room.value, afkPlayers: list }
      addActionLog('system', `${name} ${p?.afk ? '进入待机' : '恢复了操作'}`)
      break
    }
    case 'ROOM_CLOSED':
      markServerClosed()
      forgetLastRoom()
      ElMessage.warning(p?.reason === '全员待机' ? '全员待机，房间已关闭' : '房主已解散房间')
      router.replace('/lobby')
      break
    case 'CHAT':
      addChatMsg(msg.playerName, p?.text ?? '')
      break
    case 'ERROR':
      ElMessage.error(p?.error ?? '发生错误')
      break
  }
}

/** 手动接管被替换的连接 */
function resumeHere(): void {
  resume()
}

/** 进房引导（登录校验 / profile 补齐 / getRoom / 记住房间 / 连 WS）由公共 composable 承担 */
useRoomEntry({
  gameKey: 'splendor',
  loginRedirect: (id) => `/splendor/${id}`,
  fetchRoom: (id, name) => splendorApi.getRoom(id, name),
  isValid: (data) => !!data.room,
  applyRoom,
  playerName: myName,
  connect,
})
</script>

<template>
  <div class="splendor-page">
    <!-- 顶栏：左=房间号/复制，中=轮数/规则/待办，右=返回/解散 -->
    <div class="room-bar">
      <div class="bar-left">
        <span class="room-id">房间号：<strong>{{ room?.roomId ?? '...' }}</strong></span>
        <el-button size="small" @click="copyRoomId">📋 复制</el-button>
      </div>
      <div class="bar-center">
        <span v-if="snapshot" class="round-info">第 {{ snapshot.roundNo }} 轮</span>
        <span v-if="snapshot?.pendingType" class="pending-tag">{{ pendingText }}</span>
        <el-button size="small" plain @click="router.push('/rules/splendor')">📖 规则</el-button>
      </div>
      <div class="bar-right">
        <el-button size="small" plain @click="goBackOrLobby(router)">⬅ 返回</el-button>
        <el-button size="small" plain @click="leaveRoom">⬅ 回到大厅</el-button>
        <el-button v-if="isHost" size="small" type="danger" plain @click="dissolveRoom">
          🗑 解散
        </el-button>
      </div>
    </div>

    <!-- 等待中：座位面板 -->
    <div v-if="room?.status === 'WAITING'" class="waiting">
      <div class="seat-grid">
        <div
          v-for="seat in room.seats"
          :key="seat.seatNo"
          class="seat-card"
          :class="{
            occupied: !!seat.playerName,
            mine: seat.playerName === myName,
            free: !seat.playerName,
          }"
          @click="chooseSeat(seat.seatNo)"
        >
          <div class="seat-no">{{ seat.seatNo }} 号位</div>
          <template v-if="seat.playerName">
            <div class="seat-player">
              {{ seat.playerName }}
              <span v-if="seat.afk || isPlayerAfk(seat.playerName)" class="afk-tag">💤 待机</span>
            </div>
            <div class="seat-conn" :class="seat.connected ? 'on' : 'off'">
              {{ seat.connected ? '在线' : '离线' }}
            </div>
          </template>
          <template v-else>
            <div class="seat-player">空位</div>
            <div class="seat-conn">点击入座</div>
          </template>
        </div>
      </div>

      <div class="waiting-ops">
        <el-button v-if="viewerSeatNo" size="small" @click="doStandUp">🚶 起身</el-button>
        <el-button
          v-if="isHost"
          type="primary"
          :disabled="seatedCount < 2"
          @click="sendStartGame"
        >
          🎮 开局（{{ seatedCount }}/{{ room.maxPlayers }} 已入座）
        </el-button>
        <span v-else-if="!viewerSeatNo" class="spectate-hint">
          👀 观战中 · 点击空位入座{{ room.hostName === '' ? '' : `（房主：${room.hostName}）` }}
        </span>
        <span v-else class="spectate-hint">等待房主 {{ room.hostName }} 开局…</span>
      </div>
    </div>

    <!-- 对局中：牌桌 -->
    <div v-else-if="snapshot" class="table-layout">
      <!-- 左列：所有玩家面板（含我），按座位号从上到下固定排列 -->
      <aside class="players-col">
        <section
          v-for="p in allPlayers"
          :key="p.seatNo"
          class="panel player-panel"
          :class="{
            acting: p.seatNo === snapshot.currentSeatNo,
            mine: p.seatNo === viewerSeatNo,
          }"
        >
          <header class="player-head">
            <span class="player-seat">{{ p.seatNo }} 号位</span>
            <span class="player-name">
              {{ p.name }}
              <span v-if="isPlayerAfk(p.name)" class="afk-tag" title="待机中">💤</span>
            </span>
            <span class="player-vp">{{ p.vp }} VP</span>
          </header>
          <!-- 宝石图标（圆形）与发展卡红利（长方形）垂直对齐成两行 -->
          <div class="player-assets">
            <div class="gem-row">
              <span
                v-for="(n, c) in p.gems"
                :key="c"
                class="gem-chip"
                :style="gemStyle(c as AnyColor)"
                :class="{ none: !n }"
              >
                {{ n }}
              </span>
            </div>
            <div class="bonus-row">
              <span
                v-for="(n, c) in p.bonuses"
                :key="c"
                class="bonus-chip"
                :style="{ borderColor: COLOR_HEX[c as AnyColor], background: COLOR_HEX[c as AnyColor] }"
                :class="{ none: !n }"
              >
                {{ n }}
              </span>
            </div>
          </div>
          <div class="reserved-info">✋ 预留 {{ p.reservedCount }} 张</div>

          <!-- 我的预留卡明细（长方形发展卡），仅本人可见 -->
          <div v-if="p.seatNo === viewerSeatNo && p.reservedCards?.length" class="reserved-cards">
            <div
              v-for="card in p.reservedCards"
              :key="card.code"
              class="card mini"
            >
              <span class="card-vp">{{ card.vp > 0 ? card.vp : '' }}</span>
              <span class="card-bonus" :style="gemStyle(card.bonus as AnyColor)"></span>
              <span class="mini-cost">
                <span
                  v-for="(n, c) in card.cost"
                  :key="c"
                  class="mini-gem"
                  :class="{ none: !n }"
                  :style="gemStyle(c as AnyColor)"
                >
                  {{ n > 0 ? n : '' }}
                </span>
              </span>
              <button
                v-if="canAct"
                type="button"
                class="card-btn buy"
                :disabled="!computePayment(card, p)"
                @click="buyReservedCard(card)"
              >
                买
              </button>
            </div>
          </div>

          <!-- 逐色宝石合计：与手持宝石同样的 token 图标，一行内展示（含发展卡红利） -->
          <div class="gem-total">
            <div class="gem-total-title">💎 各色合计</div>
            <div class="gem-total-row">
              <span
                v-for="c in totalColors"
                :key="c"
                class="gem-chip"
                :style="gemStyle(c)"
                :class="{ none: colorTotal(p, c) === 0 }"
              >
                {{ colorTotal(p, c) }}
              </span>
            </div>
          </div>
        </section>
      </aside>

      <!-- 中列：银行 + 三级市场 -->
      <main class="center-col">
        <!-- 银行（拿宝石） -->
        <section class="panel bank-panel">
          <h3 class="panel-title">💎 银行</h3>
          <div class="bank">
            <div
              v-for="c in [...GEM_COLORS, 'gold' as const]"
              :key="c"
              class="gem-pile"
              :class="{
                pickable: canAct && c !== 'gold',
                picked: (pick[c] ?? 0) > 0,
              }"
              @click="onGemClick(c)"
            >
              <span class="gem-orb" :style="gemStyle(c)"></span>
              <span class="gem-count">{{ snapshot.bank[c] ?? 0 }}</span>
              <span v-if="(pick[c] ?? 0) > 0" class="pick-badge">{{ pick[c] }}</span>
            </div>
          </div>
          <div v-if="pickTotal > 0" class="pick-actions">
            <el-button type="primary" size="small" @click="confirmTake">
              拿取（{{ pickTotal }} 颗）
            </el-button>
            <el-button size="small" @click="clearPick">清空</el-button>
          </div>
        </section>

        <!-- 三级市场（3 → 1） -->
        <section class="panel levels-panel">
          <div v-for="level in levelRows" :key="level" class="level-row">
            <div
              class="deck"
              :class="{ pickable: canAct, empty: snapshot.levels[String(level)]?.deckLeft === 0 }"
              @click="reserveDeck(level)"
            >
              <span class="deck-level">{{ level }} 级</span>
              <span class="deck-left">剩 {{ snapshot.levels[String(level)]?.deckLeft ?? 0 }}</span>
              <span class="deck-hint">点击盲留</span>
            </div>
            <div class="cards">
              <div
                v-for="card in snapshot.levels[String(level)]?.cards ?? []"
                :key="card.code"
                class="card"
                :class="{ affordable: canAct && (!me || !!computePayment(card, me)) }"
              >
                <header class="card-head">
                  <span class="card-vp">{{ card.vp > 0 ? card.vp : '' }}</span>
                  <span class="card-bonus" :style="gemStyle(card.bonus as AnyColor)"></span>
                </header>
                <div class="card-cost">
                  <span
                    v-for="(n, c) in card.cost"
                    :key="c"
                    class="mini-gem"
                    :class="{ none: !n }"
                    :style="gemStyle(c as AnyColor)"
                  >
                    {{ n > 0 ? n : '' }}
                  </span>
                </div>
                <footer v-if="canAct" class="card-actions">
                  <button
                    type="button"
                    class="card-btn buy"
                    :disabled="!!me && !computePayment(card, me)"
                    @click="buyTableCard(card)"
                  >
                    买
                  </button>
                  <button
                    type="button"
                    class="card-btn reserve"
                    :disabled="!!(me && me.reservedCount >= 3)"
                    @click="reserveTableCard(card)"
                  >
                    留
                  </button>
                </footer>
              </div>
            </div>
          </div>
        </section>

        <div v-if="pendingText" class="pending-bar">{{ pendingText }}</div>

        <!-- 贵族栏：中间列底部（市场下方） -->
        <section class="panel nobles-panel">
          <h3 class="panel-title">👑 贵族</h3>
          <div class="nobles">
            <div v-for="noble in snapshot.nobles" :key="noble.code" class="noble">
              <span class="noble-name">{{ noble.name || '贵族' }}</span>
              <span class="noble-vp">+{{ noble.vp }} VP</span>
              <span class="noble-need">
                <span
                  v-for="(n, c) in noble.need"
                  :key="c"
                  class="mini-gem"
                  :class="{ none: !n }"
                  :style="gemStyle(c as AnyColor)"
                >
                  {{ n > 0 ? n : '' }}
                </span>
              </span>
            </div>
          </div>
        </section>
      </main>

      <!-- 右列：聊天 + 行动日志 -->
      <aside class="side-col">
        <section class="panel chat-panel">
          <h3 class="panel-title">💬 玩家聊天</h3>
          <div ref="chatBox" class="chat-messages">
            <div v-for="(msg, i) in chatMessages" :key="i" class="chat-msg">
              <span class="who" :class="msg.whoClass">
                {{ msg.who === 'system' ? '' : msg.who + ':' }}
              </span>
              <span>{{ msg.text }}</span>
            </div>
          </div>
          <div class="chat-input">
            <input
              v-model="chatText"
              type="text"
              placeholder="说点什么..."
              maxlength="100"
              @keyup.enter="sendChat"
            />
            <el-button type="primary" size="small" :disabled="!chatText.trim()" @click="sendChat">
              发送
            </el-button>
          </div>
        </section>

        <section class="panel log-panel">
          <h3 class="panel-title">📜 行动日志</h3>
          <div ref="logBox" class="chat-messages log-messages">
            <div v-for="(msg, i) in actionLogs" :key="i" class="chat-msg">
              <span class="who" :class="msg.whoClass">
                {{ msg.who === 'system' ? '' : msg.who + ':' }}
              </span>
              <span>{{ msg.text }}</span>
            </div>
          </div>
        </section>
      </aside>
    </div>

    <!-- 弹层：贵族选择待办 -->
    <div v-if="snapshot?.pendingType === 'NOBLE_SELECT' && isMyTurn && snapshot.pendingNobles" class="modal-overlay">
      <div class="modal">
        <h2>👑 选择一位贵族拜访</h2>
        <div class="noble-options">
          <button
            v-for="noble in snapshot.pendingNobles"
            :key="noble.code"
            type="button"
            class="noble-option"
            @click="chooseNoble(noble)"
          >
            <span class="noble-name">{{ noble.name || '贵族' }}</span>
            <span class="noble-vp">+{{ noble.vp }} VP</span>
            <span class="noble-need">
              <span
                v-for="(n, c) in noble.need"
                :key="c"
                class="mini-gem"
                :class="{ none: !n }"
                :style="gemStyle(c as AnyColor)"
              >
                {{ n > 0 ? n : '' }}
              </span>
            </span>
          </button>
        </div>
      </div>
    </div>

    <!-- 弹层：弃宝石待办 -->
    <div v-if="snapshot?.pendingType === 'DISCARD' && isMyTurn && me" class="modal-overlay">
      <div class="modal">
        <h2>🫳 弃掉 {{ discardNeed }} 颗宝石</h2>
        <p class="modal-sub">手牌超过 10 颗上限，点击选择要弃掉的宝石（已选 {{ discardSelected }}）</p>
        <div class="discard-options">
          <button
            v-for="(n, c) in me.gems"
            :key="c"
            type="button"
            class="discard-gem"
            :style="gemStyle(c as AnyColor)"
            :class="{ none: !n, picked: (discardSel[c] ?? 0) > 0 }"
            @click="onDiscardClick(c as AnyColor)"
          >
            {{ discardSel[c] ?? 0 }} / {{ n }}
          </button>
        </div>
        <div class="modal-actions">
          <el-button
            type="primary"
            :disabled="discardSelected !== discardNeed"
            @click="confirmDiscard"
          >
            确认弃掉
          </el-button>
        </div>
      </div>
    </div>

    <!-- 弹层：终局名次 -->
    <div v-if="gameOver" class="modal-overlay">
      <div class="modal">
        <h2>🏁 对局结束</h2>
        <ol class="rankings">
          <li v-for="(r, i) in rankings" :key="r.seatNo" :class="{ winner: i === 0 }">
            <span class="rank-no">{{ i + 1 }}</span>
            <span class="rank-name">{{ r.name }}</span>
            <span class="rank-detail">{{ r.vp }} VP · {{ r.cards }} 卡 · 贵族 {{ r.nobleCount ?? 0 }}</span>
          </li>
        </ol>
        <div class="modal-actions">
          <el-button type="primary" @click="leaveRoom">回到大厅</el-button>
        </div>
      </div>
    </div>

    <div v-if="replacedNotice" class="replaced-banner">
      ⚠️ 该房间已在其他标签页/窗口打开，本页已停止连接
      <el-button size="small" type="primary" @click="resumeHere">在此页继续</el-button>
    </div>

    <div v-if="!connected && !replacedNotice" class="conn-banner">⏳ 连接中 / 已断开</div>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/room' as room;

/* 顶栏 / 等待座位卡 / 待机标记与德国心脏病共用，见 src/styles/_room.scss */
@include room.room-bar;
@include room.round-info;
@include room.room-waiting;
@include room.afk-tag;

.splendor-page {
  min-height: 100vh;
  padding: 20px 24px 40px;
  color: var(--ink);
}

/* ---- 顶栏特有：待处理标签 ---- */
.pending-tag {
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(243, 156, 18, 0.15);
  border: 1px solid rgba(243, 156, 18, 0.4);
  color: #f39c12;
  font-size: 12px;
}

/* ---- 等待中样式见顶部 @include room.room-waiting ---- */

/* ---- 牌桌三列：左=玩家面板（固定），中=银行+市场，右=贵族+聊天 ---- */
.table-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 260px;
  gap: 16px;
  max-width: 1400px;
  margin: 0 auto;
  align-items: start;
}

.players-col {
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 32px);
  overflow-y: auto;
  scroll-behavior: smooth;
  overscroll-behavior: contain;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--scroll-thumb);
    border-radius: 6px;
    transition: background 0.2s;
  }
  &::-webkit-scrollbar-thumb:hover {
    background: var(--scroll-thumb-hover);
  }
  scrollbar-width: thin;
  scrollbar-color: var(--scroll-thumb) transparent;
}

@media (max-width: 1100px) {
  .table-layout {
    grid-template-columns: 1fr;
  }
  .players-col {
    position: static;
    max-height: none;
  }
}

.panel {
  background: var(--surface-1);
  border: 1px solid var(--line);
  border-radius: var(--radius, 12px);
  padding: 14px;
  margin-bottom: 14px;
}

.panel-title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
}

/* ---- 银行 ---- */
.bank {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  justify-content: center;
}

.gem-pile {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 12px;
  border: 2px solid transparent;
  transition: border-color 0.15s;

  &.pickable {
    cursor: pointer;
    &:hover {
      border-color: var(--color-primary);
    }
  }

  &.picked {
    border-color: var(--color-primary);
    box-shadow: 0 0 8px rgba(0, 170, 255, 0.35);
  }
}

.gem-orb {
  width: 34px;
  height: 34px;
  display: block;
  /* token 图片自带形状与高光，contain 保证星形/六边形不被裁成圆 */
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
}

.gem-count {
  font-size: 13px;
  color: var(--muted);
}

.pick-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  min-width: 20px;
  height: 20px;
  border-radius: 10px;
  background: var(--color-primary);
  color: #fff;
  font-size: 12px;
  display: grid;
  place-items: center;
  padding: 0 4px;
}

.pick-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

/* ---- 市场 ---- */
.level-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;

  &:last-child {
    margin-bottom: 0;
  }
}

.deck {
  flex: none;
  width: 74px;
  border-radius: 10px;
  background: repeating-linear-gradient(
    45deg,
    #3d3d5c,
    #3d3d5c 6px,
    #33334f 6px,
    #33334f 12px
  );
  border: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: 12px;
  color: var(--muted);
  transition: border-color 0.15s;

  &.pickable {
    cursor: pointer;
    &:hover {
      border-color: var(--color-primary);
    }
  }

  &.empty {
    opacity: 0.35;
    cursor: not-allowed;
  }
}

.deck-level {
  font-weight: 700;
  font-size: 14px;
  color: var(--ink);
}

.deck-hint {
  font-size: 11px;
}

.cards {
  flex: 1;
  display: flex;
  gap: 8px;
  /* 翻开的 4 张明卡强制单行，不换行 */
  flex-wrap: nowrap;
  min-width: 0;
}

.card {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  max-width: 150px;
  border-radius: 10px;
  background: #f5f1e8;
  color: #2d3436;
  padding: 8px 10px 30px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.25);

  &.affordable {
    outline: 3px solid rgba(46, 213, 115, 0.85);
  }

  &.mini {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 8px;
    margin-bottom: 8px;
    max-width: 100%;
    width: auto;

    .card-vp {
      font-size: 13px;
    }

    .card-bonus {
      width: 14px;
      height: 14px;
      flex: none;
    }

    .mini-cost {
      gap: 3px;
      flex: 0 1 auto;
    }

    /* 0 值宝石点不占位，避免预留卡溢出玩家面板 */
    .mini-gem {
      min-width: 16px;
      height: 16px;
      font-size: 10px;

      &.none {
        display: none;
      }
    }

    .card-btn {
      flex: none;
      padding: 3px 8px;
    }
  }
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.card-vp {
  font-weight: 800;
  font-size: 16px;
  color: #444;
  min-width: 1em;
}

.card-bonus {
  width: 18px;
  height: 18px;
  flex: none;
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
}

.card-cost {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
}

.mini-gem {
  display: inline-grid;
  place-items: center;
  min-width: 20px;
  height: 20px;
  font-size: 11px;
  font-weight: 800;
  color: #1b1b1b;
  /* 深色宝石上保证数字可读：白色描边 */
  text-shadow:
    0 0 3px #fff,
    0 0 2px #fff;
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;

  &.none {
    opacity: 0.18;
  }
}

.card-actions {
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: 6px;
  display: flex;
  gap: 6px;
}

.card-btn {
  flex: 1;
  border: none;
  border-radius: 6px;
  padding: 3px 0;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;

  &.buy {
    background: #27ae60;
    color: #fff;
  }

  &.reserve {
    background: #f39c12;
    color: #fff;
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.pending-bar {
  margin-top: 12px;
  text-align: center;
  padding: 8px;
  border-radius: 10px;
  background: rgba(243, 156, 18, 0.15);
  border: 1px solid rgba(243, 156, 18, 0.4);
  color: #f39c12;
  font-size: 13px;
  font-weight: 600;
}

/* ---- 玩家面板 ---- */
.player-panel {
  &.acting {
    border-color: var(--color-primary);
    box-shadow: 0 0 10px rgba(0, 170, 255, 0.25);
  }
}

.player-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

.player-seat {
  font-size: 11px;
  color: var(--muted);
}

.player-name {
  font-size: 15px;
  font-weight: 700;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.player-vp {
  font-size: 13px;
  font-weight: 800;
  color: #f1c40f;
}

/* 待机标记：见顶部 @include room.afk-tag */

/* 玩家宝石/加成与发展卡统一左对齐 */
.player-assets {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 6px;
}

.gem-row,
.bonus-row {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
}

.gem-chip {
  display: inline-grid;
  place-items: center;
  width: 26px;
  height: 26px;
  font-size: 12px;
  font-weight: 800;
  color: #1b1b1b;
  text-shadow:
    0 0 3px #fff,
    0 0 2px #fff;
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;

  &.none {
    opacity: 0.18;
  }
}

/* 发展卡红利：长方形小卡（与圆形宝石图标形成区分），同高以便对齐 */
.bonus-chip {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 26px;
  border-radius: 4px;
  border: 2px solid;
  font-size: 11px;
  font-weight: 800;
  color: #1b1b1b;
  box-shadow: inset -2px -2px 4px rgba(0, 0, 0, 0.28);

  &.none {
    opacity: 0.18;
  }
}

.reserved-info {
  font-size: 12px;
  color: var(--muted);
}

/* 逐色宝石合计：与手持宝石同款 token，一行内横排 */
.gem-total {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}

.gem-total-title {
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 6px;
}

.gem-total-row {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.reserved-title {
  font-size: 12px;
  color: var(--muted);
  margin: 8px 0 6px;
}

.mini-cost {
  display: flex;
  gap: 4px;
  flex: 1;
}

/* ---- 贵族：方形卡片，中间列底部横向铺开 ---- */
.nobles {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.noble {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 96px;
  padding: 10px 8px;
  border-radius: 10px;
  background: #f5f1e8;
  color: #2d3436;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.25);
}

.noble-name {
  font-size: 13px;
  font-weight: 800;
  text-align: center;
  line-height: 1.2;
}

.noble-vp {
  font-size: 13px;
  font-weight: 800;
  color: #b8860b;
}

.noble-need {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: center;
}

/* ---- 聊天 / 行动日志 ---- */
.chat-panel,
.log-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.chat-panel {
  margin-bottom: 14px;
}
.log-panel {
  margin-bottom: 0;
}

.chat-messages {
  height: 200px;
  overflow-y: auto;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--well-bg);
  scroll-behavior: smooth;
  overscroll-behavior: contain;

  /* WebKit 细滚动条：默认淡化，悬停容器时才加深 */
  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--scroll-thumb);
    border-radius: 6px;
    transition: background 0.2s;
  }
  &::-webkit-scrollbar-thumb:hover {
    background: var(--scroll-thumb-hover);
  }

  /* Firefox */
  scrollbar-width: thin;
  scrollbar-color: var(--scroll-thumb) transparent;
}

/* 行动日志：只读，略高一点容纳更多条目 */
.log-messages {
  height: 240px;
}

.chat-msg {
  margin-bottom: 8px;
  font-size: 13px;
  word-break: break-all;
}

.who {
  margin-right: 6px;
  font-weight: 600;

  /* 按玩家名稳定映射的颜色，聊天与行动日志共用 */
  &.pc-0 {
    color: #4fc3f7;
  }
  &.pc-1 {
    color: #ff8a80;
  }
  &.pc-2 {
    color: #b9f6ca;
  }
  &.pc-3 {
    color: #ffd180;
  }
  &.pc-4 {
    color: #b388ff;
  }
  &.pc-5 {
    color: #84ffff;
  }
  &.pc-6 {
    color: #f48fb1;
  }
  &.pc-7 {
    color: #a5d6a7;
  }

  /* 兼容旧身份样式（如外部传入） */
  &.me {
    color: #2ed573;
  }
  &.spectator {
    color: #7f8fa6;
  }
  &.action {
    color: #00aaff;
  }
  &.system {
    color: #f39c12;
    font-style: italic;
  }
}

/* 白天模式：部分浅色在白底偏淡，统一加深以保证可读 */
[data-theme='light'] .who {
  &.pc-0 {
    color: #0277bd;
  }
  &.pc-1 {
    color: #c62828;
  }
  &.pc-2 {
    color: #2e7d32;
  }
  &.pc-3 {
    color: #ef6c00;
  }
  &.pc-4 {
    color: #6a1b9a;
  }
  &.pc-5 {
    color: #00838f;
  }
  &.pc-6 {
    color: #ad1457;
  }
  &.pc-7 {
    color: #558b2f;
  }
}

.chat-input {
  display: flex;
  gap: 8px;

  input {
    flex: 1;
    background: var(--input-bg, #20203a);
    border: 1px solid var(--line);
    border-radius: var(--radius, 10px);
    color: var(--ink);
    padding: 8px 12px;
    font-size: 13px;

    &:focus {
      border-color: var(--color-primary);
      outline: none;
    }
  }
}

/* ---- 弹层 ---- */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  padding: 36px 40px;
  text-align: center;
  max-width: 460px;
  border-radius: 20px;
  background: linear-gradient(135deg, #1a1a2e, #16213e);
  border: 1px solid rgba(255, 255, 255, 0.2);

  h2 {
    margin: 0 0 18px;
    font-size: 24px;
  }
}

.modal-sub {
  color: #aaa;
  font-size: 13px;
  margin: 0 0 16px;
}

.noble-options {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

.noble-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 110px;
  padding: 14px 18px;
  border-radius: 10px;
  border: 1px solid #d8d2c4;
  background: #f5f1e8;
  color: #2d3436;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.25);
  cursor: pointer;
  transition:
    border-color 0.15s,
    transform 0.15s;

  &:hover {
    border-color: #b8860b;
    transform: translateY(-2px);
  }
}

.discard-options {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.discard-gem {
  width: 54px;
  height: 54px;
  border-radius: 10px;
  border: 3px solid transparent;
  font-size: 13px;
  font-weight: 800;
  color: #1b1b1b;
  cursor: pointer;
  text-shadow:
    0 0 3px #fff,
    0 0 2px #fff;
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;

  &.none {
    opacity: 0.25;
    cursor: not-allowed;
  }

  &.picked {
    border-color: #e74c3c;
  }
}

.modal-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.rankings {
  list-style: none;
  margin: 0 0 20px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;

  li {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 14px;
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.05);

    &.winner {
      background: rgba(241, 196, 15, 0.15);
      border: 1px solid rgba(241, 196, 15, 0.5);
    }
  }
}

.rank-no {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 14px;
}

.rank-name {
  font-size: 16px;
  font-weight: 700;
  flex: 1;
  text-align: left;
}

.rank-detail {
  font-size: 12px;
  color: #aaa;
}

/* ---- 通知条 ---- */
.replaced-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  max-width: 1200px;
  margin: 12px auto 0;
  padding: 10px 16px;
  border-radius: 10px;
  background: rgba(231, 76, 60, 0.15);
  border: 1px solid rgba(231, 76, 60, 0.45);
  color: #e74c3c;
  font-size: 13px;
  font-weight: 600;
}

.conn-banner {
  margin-top: 12px;
  text-align: center;
  font-size: 13px;
  color: #f39c12;
}
</style>
