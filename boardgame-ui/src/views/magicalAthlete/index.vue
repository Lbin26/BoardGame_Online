<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import * as api from '@/api/magicalAthlete'
import type {
  MagicalAthleteRoomInfo,
  MagicalAthleteRacerState,
  MagicalAthleteSnapshot,
} from '@/api/magicalAthlete'
import RacerCatalog from '@/components/RacerCatalog.vue'
import { useChat, type ChatMessage } from '@/composables/useChat'
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
/** 比赛页图鉴弹层（与掷骰同处下方操作层） */
const catalogOpen = ref(false)

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

/** 播报统一自动消失，组件卸载时清掉残留定时器 */
const toastTimers = new Set<number>()
const RESULT_TOAST_HOLD_MS = 3600

function autoDismiss(clear: () => void, ms: number): void {
  const id = window.setTimeout(() => {
    toastTimers.delete(id)
    clear()
  }, ms)
  toastTimers.add(id)
}

function clearToastTimers(): void {
  for (const id of toastTimers) window.clearTimeout(id)
  toastTimers.clear()
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

function handleMessage(msg: { type?: string; playerName?: string; payload?: Record<string, any> }): void {
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
    case 'MUSTER_READY': {
      const who = (payload.playerName as string) ?? '有玩家'
      ElMessage.info(`${who} 已准备出场选手`)
      appendLog(who, '已准备出场选手')
      break
    }
    case 'RACE_START': {
      const raceNo = payload.raceNo ?? snapshot.value?.raceNo ?? '?'
      lastDiceMsg.value = null
      ElMessage.success(`第 ${raceNo} 场开始，出场选手已公开`)
      appendLog('system', `第 ${raceNo} 场开始，出场选手已公开`)
      break
    }
    case 'DICE_RESULT': {
      lastDiceMsg.value = {
        seatNo: payload.seatNo as number,
        value: payload.value as number,
        tripSkipped: !!payload.tripSkipped,
        auto: !!payload.auto,
        effects: (payload.effects as string[]) ?? [],
      }
      autoDismiss(() => {
        const cur = lastDiceMsg.value
        if (cur && cur.seatNo === payload.seatNo && cur.value === payload.value) lastDiceMsg.value = null
      }, DICE_TOAST_HOLD_MS)
      {
        const who = (payload.playerName as string) || nameOfSeat(payload.seatNo as number)
        const skills = ((payload.effects as string[]) ?? [])
          .filter((e) => e.startsWith('ABILITY:') && !e.startsWith('ABILITY:CHAIN_LIMIT:'))
          .map((e) => abilityEffectText(e.slice('ABILITY:'.length)))
        appendLog(who, `掷出 ${payload.value}${skills.length ? `，触发 ${skills.join('、')}` : ''}`)
      }
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
      autoDismiss(() => { lastRaceResult.value = null }, RESULT_TOAST_HOLD_MS)
      {
        const rr = lastRaceResult.value
        appendLog('system', `第 ${payload.raceNo} 场结束：🥇${rr?.goldName} +${rr?.goldValue}　🥈${rr?.silverName} +${rr?.silverValue}`)
      }
      break
    case 'GAME_OVER': {
      const text = payload.tie ? '对局结束：并列第一' : '对局结束，查看最终排名'
      ElMessage.success(text)
      appendLog('system', text)
      break
    }
    case 'DRAFT_PICK': {
      lastPick.value = {
        seatNo: payload.seatNo as number,
        playerName: (payload.playerName as string) ?? '玩家',
        racerKey: (payload.racerKey as string) ?? '',
        auto: !!payload.auto,
      }
      autoDismiss(() => {
        const current = lastPick.value
        if (current && current.racerKey === payload.racerKey && current.seatNo === payload.seatNo) {
          lastPick.value = null
        }
      }, PICK_TOAST_HOLD_MS)
      appendLog((payload.playerName as string) ?? '玩家', `选走了 ${racerName((payload.racerKey as string) ?? '')}`)
      break
    }
    case 'CHAT':
      addChatMsg((msg.playerName as string) ?? '', (payload.text as string) ?? '')
      break
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

/* ---- 比赛页右侧：聊天 + 行动日志 ---- */
const chatBox = ref<HTMLElement | null>(null)
const logBox = ref<HTMLElement | null>(null)
const actionLogs = ref<ChatMessage[]>([])

/** 按快照玩家顺序给名字稳定配色，聊天与日志共用 */
function nameColorClass(who: string): string {
  const idx = (snapshot.value?.players ?? []).findIndex((p) => p.name === who)
  return `pc-${idx >= 0 ? idx % 8 : 0}`
}

const { chatMessages, chatText, addChatMsg, sendChat } = useChat({
  send: (text) => { send({ type: 'CHAT', payload: { text } }) },
  classifyWho: (who) => (who === 'system' ? 'system' : nameColorClass(who)),
  scrollTarget: chatBox,
})

function appendLog(who: string, text: string): void {
  actionLogs.value.push({ who, text, whoClass: who === 'system' ? 'system' : nameColorClass(who) })
  nextTick(() => {
    const el = logBox.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

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

/** M4：本回合可用的可选能力项（仅轮到我时服务端下发，服务端已按当前 lane 的选手生成） */
const myOptionalOptions = computed(() => snapshot.value?.myOptionalOptions ?? [])
const hasOptionalChoice = computed(() => isMyRoll.value && myOptionalOptions.value.length > 0)

/**
 * 当前轮到我行动的出场选手（双角色局按 currentLane 精确取；普通局 lane==seat）。
 * 用于把「启用能力」「指定目标」这类通用选项，解释成当前这张卡具体做什么。
 */
const myActingRacer = computed(() => {
  const lane = snapshot.value?.currentLane ?? mySeat.value
  const key = snapshot.value?.activeRacerKeysByLane?.[String(lane)]
    ?? snapshot.value?.activeRacerKeys?.[String(lane)]
  return key ? racerInfo(key) : null
})
/** 可选能力的目标输入（座位 / 格 / 猜骰），按选项类型显示 */
const optionalTarget = ref<number>(0)
const optionalUsed = ref(false)
/** 选项短标签 + 该类选项怎么填；具体能力文案由当前出场选手的 abilitySummary 提供 */
const OPTION_INFO: Record<string, { label: string; guide: string }> = {
  USE_PRIMARY: {
    label: '启用能力',
    guide: '点「使用」发动该能力；不声明则本回合按普通掷骰结算。',
  },
  TARGET_SEAT: {
    label: '指定目标选手',
    guide: '先选一名未冲线的其他选手，再点「使用」；选自己或目标已冲线则能力不发动。',
  },
  TARGET_SPACE: {
    label: '指定目标格',
    guide: '先填 1–30 的赛道格，再点「使用」；不满足人数等条件时能力不发动，不额外消耗回合。',
  },
  GUESS_DICE: {
    label: '猜骰点 (1–6)',
    guide: '先填 1–6 的预测点数，再点「使用」；猜错本回合照常结算，超时未声明视为放弃。',
  },
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
    : (OPTION_INFO[opt]?.label ?? opt)
}

function optionalDetail(opt: string): string {
  const racer = myActingRacer.value
  const head = racer
    ? `当前出场「${racer.name}」：${racer.abilitySummary || '见选手图鉴'}。`
    : '当前出场选手能力待公开。'
  return `${head}${OPTION_INFO[opt]?.guide ?? ''}`
}

/**
 * 把引擎的 ABILITY 效果码（如 banana@seat2:trip@seat1）翻成中文播报。
 * 效果码来自后端 Effect.reason()，格式固定为 <racerKey>@seat<N>:<detail>；
 * 未在 RACERS 中的 key 原样显示，避免后端新增选手时前端白屏。
 */
function abilityEffectText(reason: string): string {
  const at = reason.indexOf('@')
  const key = at >= 0 ? reason.slice(0, at) : reason
  const detail = at >= 0 ? (reason.slice(reason.indexOf(':', at) + 1) || '') : ''
  const name = racerInfo(key).name || key
  const colon = detail.indexOf(':')
  const head = colon >= 0 ? detail.slice(0, colon) : detail
  let effect = ''
  if (head.startsWith('trip@')) effect = '绊倒目标'
  else if (head.startsWith('warp@')) effect = '传送目标'
  else if (head.startsWith('warp->')) effect = `传送至 ${detail.slice(6)} 格`
  else if (head.startsWith('swap@')) effect = '交换位置'
  else if (head === 'cancel-main' || head === 'follow-cancel') effect = '取消主要移动'
  else if (head.startsWith('follow@')) effect = '跟随目标'
  else if (head.startsWith('cancel@')) effect = '取消目标移动'
  else if (head.startsWith('base=')) effect = `主骰改为 ${(detail.split('->')[1] ?? '').split('@')[0]}`
  else if (head === 'solo-lead-skip') effect = '独自领先·跳过移动'
  else if (/^[+-]\d+$/.test(head)) effect = head.startsWith('+') ? `前进 ${head.slice(1)} 格` : `后退 ${head.slice(1)} 格`
  else if (/^pre\+\d+$/.test(head)) effect = `赛前 +${head.slice(4)} 分`
  else if (/^\+\d+@/.test(head)) effect = `+${head.slice(1).split('@')[0]} 分`
  return effect ? `${name} · ${effect}` : `${name} · ${racerInfo(key).abilitySummary || '能力发动'}`
}

/** 本次掷骰实际触发的技能链（去重、过滤链上限系统项） */
const triggeredAbilities = computed(() => {
  const seen = new Set<string>()
  const out: string[] = []
  for (const raw of lastDiceMsg.value?.effects ?? []) {
    if (!raw.startsWith('ABILITY:') || raw.startsWith('ABILITY:CHAIN_LIMIT:')) continue
    const text = abilityEffectText(raw.slice('ABILITY:'.length))
    if (seen.has(text)) continue
    seen.add(text)
    out.push(text)
  }
  return out
})

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

/** 名字最多显示 max 个字；Array.from 避免 emoji/代理对被截成半个字符 */
function shortName(name: string | null | undefined, max = 5): string {
  return Array.from(name ?? '').slice(0, max).join('')
}

/** 往期已出场选手（公开字段），用于暗选手牌灰显 */
const usedRacerKeys = computed<Set<string>>(() => new Set(snapshot.value?.usedRacerKeys ?? []))

function isUsedRacer(key: string): boolean {
  return usedRacerKeys.value.has(key)
}

interface PlayerStanding {
  seatNo: number
  name: string
  order: number
  total: number
  bronze: number
  racers: MagicalAthleteRacerState[]
}

/** RACE 实时名次：finishRank 优先，未冲线按位置倒序；双角色按真实座位分组取最好一名 */
const playerStandings = computed<PlayerStanding[]>(() => {
  const snap = snapshot.value
  if (!snap) return []
  const bySeat = new Map<number, MagicalAthleteRacerState[]>()
  for (const r of snap.racers ?? []) {
    const seat = Number(r.ownerSeatNo ?? r.seatNo)
    const list = bySeat.get(seat) ?? []
    list.push(r)
    bySeat.set(seat, list)
  }
  const rankOf = (r: MagicalAthleteRacerState): number =>
    r.finishRank > 0 ? r.finishRank : 1000 - r.position
  const rows = (snap.players ?? []).map((p) => {
    const seat = Number(p.seatNo)
    const list = (bySeat.get(seat) ?? [])
      .slice()
      .sort((a, b) => rankOf(a) - rankOf(b) || a.seatNo - b.seatNo)
    return {
      seatNo: seat,
      name: p.name,
      total: Number(snap.scores?.[String(seat)] ?? 0),
      bronze: list.reduce((sum, r) => sum + (r.bronze || 0), 0),
      racers: list,
      best: list[0] as MagicalAthleteRacerState | undefined,
    }
  })
  rows.sort((a, b) => {
    if (!a.best && !b.best) return a.seatNo - b.seatNo
    if (!a.best) return 1
    if (!b.best) return -1
    return rankOf(a.best) - rankOf(b.best) || a.seatNo - b.seatNo
  })
  return rows.map((row, i) => ({
    seatNo: row.seatNo,
    name: row.name,
    order: i + 1,
    total: row.total,
    bronze: row.bronze,
    racers: row.racers,
  }))
})

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
interface TrackRow {
  cells: number[]        // 左到右渲染的格号
  dir: 'right' | 'left'  // 该行行进方向
  turn: string           // 出口格上的转向/终点标记
  offset?: number        // 起始列（0 基），末行 7 格右移一列才能对齐上一行
}

/** 四行严格 S 型：0→7、8→15、16→23、24→30，相邻行首尾竖直对齐，格子依次相连 */
const trackRows = computed<TrackRow[]>(() => [
  { cells: Array.from({ length: 8 }, (_, i) => i),      dir: 'right', turn: '↓' },  // 0..7
  { cells: Array.from({ length: 8 }, (_, i) => 15 - i), dir: 'left',  turn: '↓' },  // 8..15
  { cells: Array.from({ length: 8 }, (_, i) => 16 + i), dir: 'right', turn: '↓' },  // 16..23
  { cells: Array.from({ length: 7 }, (_, i) => 30 - i), dir: 'left',  turn: '🏁', offset: 1 }, // 24..30
])

function isTrackExit(row: TrackRow, ci: number): boolean {
  return row.dir === 'right' ? ci === row.cells.length - 1 : ci === 0
}

function specialAt(cell: number) {
  return isWildTrack.value ? WILD_SPECIALS[cell] : undefined
}

/** 赛道四段配色：0-7 / 8-15 / 16-23 / 24-30 各一套底色，避免整条赛道一片白 */
function cellZone(cell: number): string {
  if (cell < 8) return 'zone-a'
  if (cell < 16) return 'zone-b'
  if (cell < 24) return 'zone-c'
  return 'zone-d'
}

function racersAt(cell: number) {
  return racers.value
    .filter((r) => r.position === cell)
    .sort((a, b) => a.seatNo - b.seatNo)
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
  clearToastTimers()
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
        <el-button size="small" plain @click="goBackOrLobby(router)">⬅ 返回</el-button>
        <el-button size="small" plain @click="backToLobby">⬅ 回到大厅</el-button>
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
            :class="{
              pickable: !myActiveKeys.includes(key) && !isUsedRacer(key),
              locked: myActiveKeys.includes(key),
              used: isUsedRacer(key),
            }"
            :style="cardStyle(key)"
            :disabled="myActiveKeys.includes(key) || isUsedRacer(key)"
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

    <!-- 比赛：左侧实时排名 + 左到右再左回的 U 型赛道 + 中间掷骰/领先 -->
    <section v-else-if="phase === 'RACE' || phase === 'RACE_END'" class="race-view">
      <div class="race-layout">
        <aside class="rank-panel">
          <div class="rank-title">
            <h3>实时排名</h3>
            <span>第 {{ snapshot?.raceNo }} 场</span>
          </div>
          <div class="rank-list">
            <div
              v-for="s in playerStandings"
              :key="s.seatNo"
              class="rank-row"
              :class="{ mine: s.seatNo === mySeat, acting: s.seatNo === currentSeat }"
              tabindex="0"
            >
              <span class="rank-no">{{ s.order }}</span>
              <div class="rank-player">
                <span class="rank-name">
                  {{ shortName(s.name, 5) }}<small v-if="s.seatNo === mySeat">（我）</small>
                </span>
                <span class="rank-score"><b>{{ s.total }}</b> 分 · 🪙{{ s.bronze }} 分</span>
                <div class="card-popover">
                  <template v-if="s.racers.length">
                    <div
                      v-for="r in s.racers"
                      :key="r.seatNo"
                      class="pop-card"
                      :style="cardStyle(r.racerKey)"
                    >
                      <div class="pop-head">
                        <b>{{ shortName(racerName(r.racerKey), 5) }}</b>
                        <span>{{ typeLabel(racerInfo(r.racerKey).abilityType) }}</span>
                      </div>
                      <p class="pop-skill">{{ racerInfo(r.racerKey).abilitySummary }}</p>
                      <div class="pop-state">
                        第 {{ r.position }} 格<template v-if="r.tripped"> · 绊倒</template><template v-if="r.finishRank > 0"> · 已冲线</template>
                      </div>
                    </div>
                  </template>
                  <p v-else class="pop-empty">本场选手尚未公开</p>
                </div>
              </div>
            </div>
          </div>
          <p class="rank-tip">悬停 / 聚焦玩家名可查看出场选手与技能</p>
        </aside>

        <div class="race-main">
          <div class="draft-head">
            <h2>第 {{ snapshot?.raceNo }} 场 · {{ isWildTrack ? '狂野面' : '平缓面' }}</h2>
            <p class="hint">
              <template v-if="isMyRoll"><strong class="mine">轮到你掷骰！</strong></template>
              <template v-else>等待 <strong>{{ nameOfSeat(snapshot?.currentSeatNo) }}</strong> 掷骰</template>
              <span v-if="currentLaneRacerName" class="lane-hint">· 当前出场：{{ currentLaneRacerName }}</span>
            </p>
            <span class="countdown" :class="{ urgent: secondsLeft <= 5 }">⏱ {{ secondsLeft }}s</span>
          </div>

          <div class="track-board" :class="{ wild: isWildTrack }">
            <div
              v-for="(row, ri) in trackRows"
              :key="`row${ri}`"
              class="track-row"
              :class="row.dir"
            >
              <div
                v-for="(cell, ci) in row.cells"
                :key="cell"
                class="track-cell"
                :class="[
                  cellZone(cell),
                  {
                    finish: cell === 30,
                    start: cell === 0,
                    exit: isTrackExit(row, ci),
                    corner: isTrackExit(row, ci),
                    special: !!specialAt(cell),
                    alt: ci % 2 === 1,
                  },
                ]"
                :style="ci === 0 && row.offset ? { gridColumnStart: row.offset + 1 } : undefined"
              >
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
                  >{{ shortName(r.name, 5) }}</span>
                </div>
                <span v-if="isTrackExit(row, ci)" class="cell-turn">{{ row.turn }}</span>
                <span v-else class="cell-link">{{ row.dir === 'right' ? '›' : '‹' }}</span>
              </div>
            </div>
          </div>

          <div v-if="hasOptionalChoice" class="optional-panel">
            <span class="optional-title">
              本回合可选能力<template v-if="myActingRacer"> · 当前出场 {{ myActingRacer.name }}</template>：
            </span>
            <div v-for="opt in myOptionalOptions" :key="opt" class="optional-item">
              <span class="optional-copy">
                <b>{{ optionalLabel(opt) }}</b>
                <small>{{ optionalDetail(opt) }}</small>
              </span>
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
            <button type="button" class="catalog-trigger" @click="catalogOpen = true">
              📖 选手图鉴
            </button>
          </div>
        </div>

        <!-- 右侧：聊天 + 行动日志，与赛道同屏 -->
        <aside class="race-side">
          <section class="panel chat-panel">
            <h3 class="panel-title">💬 玩家聊天</h3>
            <div ref="chatBox" class="chat-messages">
              <div v-for="(msg, i) in chatMessages" :key="i" class="chat-msg">
                <span class="who" :class="msg.whoClass">{{ msg.who === 'system' ? '' : msg.who + ':' }}</span>
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
              <button type="button" class="chat-send" :disabled="!chatText.trim()" @click="sendChat">发送</button>
            </div>
          </section>

          <section class="panel log-panel">
            <h3 class="panel-title">📜 行动日志</h3>
            <div ref="logBox" class="chat-messages log-messages">
              <div v-for="(msg, i) in actionLogs" :key="i" class="chat-msg">
                <span class="who" :class="msg.whoClass">{{ msg.who === 'system' ? '' : msg.who + ':' }}</span>
                <span>{{ msg.text }}</span>
              </div>
            </div>
          </section>
        </aside>
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
          <span class="rank-medals">🥇{{ s.gold }} · 🥈{{ s.silver }} · 🪙{{ s.bronze }} 分</span>
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
        <span v-if="triggeredAbilities.length" class="effect-tag" :title="triggeredAbilities.join('；')">
          ⚡ 触发技能：{{ triggeredAbilities.join('、') }}
        </span>
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

    <!-- 选手图鉴弹层（与掷骰同处下方操作层触发） -->
    <Transition name="flash">
      <div v-if="catalogOpen" class="catalog-overlay" @click.self="catalogOpen = false">
        <div class="catalog-modal">
          <div class="catalog-modal-head">
            <h3>选手图鉴</h3>
            <button type="button" class="catalog-close" @click="catalogOpen = false">×</button>
          </div>
          <div class="catalog-modal-body"><RacerCatalog /></div>
        </div>
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

/* ---- 比赛：左侧实时排名 + S 型赛道 ---- */
.race-view {
  max-width: 1680px;
  margin: 0 auto;
}
.race-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 280px;
  gap: 16px;
  align-items: start;
}
/* ---- 比赛页右侧：聊天 + 行动日志 ---- */
.race-side {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;

  .panel {
    padding: 12px;
    border: 1px solid var(--line);
    border-radius: 12px;
    background: var(--surface-2, rgba(255, 255, 255, 0.03));
  }
  .panel-title { margin: 0 0 10px; font-size: 15px; font-weight: 700; }
  .chat-messages {
    height: 220px;
    overflow-y: auto;
    padding: 8px 10px;
    border-radius: 8px;
    background: var(--well-bg, rgba(0, 0, 0, 0.15));
    scroll-behavior: smooth;
    overscroll-behavior: contain;
    scrollbar-width: thin;
    scrollbar-color: var(--scroll-thumb, rgba(128, 128, 128, 0.4)) transparent;
    &::-webkit-scrollbar { width: 6px; }
    &::-webkit-scrollbar-track { background: transparent; }
    &::-webkit-scrollbar-thumb {
      background: var(--scroll-thumb, rgba(128, 128, 128, 0.4));
      border-radius: 6px;
    }
  }
  .log-messages { height: 300px; }
  .chat-msg { margin-bottom: 8px; font-size: 12px; line-height: 1.45; word-break: break-all; }
  .who {
    margin-right: 6px;
    font-weight: 600;
    &.system { color: var(--muted); }
    &.pc-0 { color: #4fc3f7; }
    &.pc-1 { color: #ff8a80; }
    &.pc-2 { color: #b9f6ca; }
    &.pc-3 { color: #ffd180; }
    &.pc-4 { color: #b388ff; }
    &.pc-5 { color: #84ffff; }
    &.pc-6 { color: #f48fb1; }
    &.pc-7 { color: #a5d6a7; }
  }
  .chat-input {
    display: flex;
    gap: 6px;
    margin-top: 8px;
    input {
      flex: 1;
      min-width: 0;
      padding: 6px 8px;
      border: 1px solid var(--line);
      border-radius: 6px;
      background: transparent;
      color: var(--ink);
      font-size: 12px;
    }
    .chat-send {
      padding: 6px 10px;
      border: 1px solid var(--line);
      border-radius: 6px;
      background: transparent;
      color: var(--ink);
      font-size: 12px;
      cursor: pointer;
      &:disabled { opacity: 0.5; cursor: default; }
      &:hover:not(:disabled) { border-color: var(--color-primary); }
    }
  }
}
.rank-panel {
  position: sticky;
  top: 16px;
  z-index: 50; /* 悬停卡必须压在赛道之上 */
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--line);
  background: var(--surface-2, rgba(255, 255, 255, 0.03));
}
.rank-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
  h3 { margin: 0; font-size: 19px; }
  span { font-size: 13px; color: var(--muted); }
}
/* 作用域限定，避免被下方终局排名的同名类覆盖 */
.rank-panel .rank-list { display: flex; flex-direction: column; gap: 10px; }
.rank-panel .rank-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid var(--line);
  outline: none;

  &.mine { border-color: var(--color-primary); }
  &.acting { box-shadow: inset 3px 0 0 var(--color-primary); }
}
.rank-panel .rank-no {
  flex: 0 0 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--surface-3);
  font-size: 16px;
  font-weight: 800;
}
.rank-player {
  position: relative;
  flex: 1;
  min-width: 0;
  cursor: help;
}
.rank-panel .rank-name {
  display: block;
  font-size: 17px;
  font-weight: 700;
  small { font-size: 12px; font-weight: 400; color: var(--muted); }
}
.rank-score {
  display: block;
  margin-top: 2px;
  font-size: 14px;
  color: var(--muted);
  b { color: var(--ink); font-size: 17px; }
}
.rank-tip { margin: 12px 0 0; font-size: 12px; color: var(--muted); line-height: 1.5; }
.card-popover {
  position: absolute;
  left: 0;
  top: calc(100% + 6px);
  z-index: 100;
  width: 280px;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid var(--color-primary);
  background: var(--surface-3);
  box-shadow: 0 14px 36px rgba(0, 0, 0, 0.28);
  opacity: 0;
  visibility: hidden;
  transform: translateY(-4px);
  transition: opacity 0.15s, transform 0.15s, visibility 0.15s;
  pointer-events: none;
}
.rank-player:hover .card-popover,
.rank-player:focus-within .card-popover,
.rank-row:focus .card-popover {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}
.pop-card {
  padding: 8px 10px;
  border-radius: 9px;
  border-left: 4px solid hsl(var(--hue), 55%, 45%);
  background: var(--surface-2, rgba(255, 255, 255, 0.03));
  + .pop-card { margin-top: 8px; }
}
.pop-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 14px;
  span { font-size: 12px; color: var(--muted); }
}
.pop-skill { margin: 5px 0; font-size: 13px; line-height: 1.5; color: var(--ink); }
.pop-state { font-size: 12px; color: var(--muted); }
.pop-empty { margin: 0; font-size: 13px; color: var(--muted); }

.race-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.track-board {
  display: flex;
  flex-direction: column;
  gap: 8px; /* 给行末 ↓ 转向标记留位置 */
  padding: 10px;
  border-radius: 14px;
  border: 1px solid var(--line);
  background: var(--surface-2, rgba(255, 255, 255, 0.03));
  /* 8 列自适应铺满，4 行 S 型连通，不再横向溢出 */
  overflow: visible;

  &.wild { border-color: rgba(224, 165, 58, 0.5); }
}
.track-row {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 6px;
}
.track-cell {
  --zone-tint: transparent;
  position: relative;
  min-height: 78px;
  padding: 4px;
  border-radius: 10px;
  border: 1px solid var(--line);
  background-color: var(--surface-1, transparent);
  background-image: linear-gradient(135deg, var(--zone-tint), transparent 78%);
  text-align: center;

  /* 四段分区：草原 → 湖泊 → 黄昏 → 终点，颜色只做底色不压棋子 */
  &.zone-a { --zone-tint: rgba(79, 157, 105, 0.22); border-color: rgba(79, 157, 105, 0.42); }
  &.zone-b { --zone-tint: rgba(70, 140, 235, 0.20); border-color: rgba(70, 140, 235, 0.40); }
  &.zone-c { --zone-tint: rgba(160, 110, 235, 0.20); border-color: rgba(160, 110, 235, 0.40); }
  &.zone-d { --zone-tint: rgba(224, 165, 58, 0.20); border-color: rgba(224, 165, 58, 0.40); }

  /* 相邻格叠一层浅色纹理，避免同段内仍是一片同色 */
  &.alt {
    background-image:
      linear-gradient(180deg, rgba(255, 255, 255, 0.07), transparent 55%),
      linear-gradient(135deg, var(--zone-tint), transparent 78%);
  }

  /* 狂野面特殊格：虚线 + 内发光 */
  &.special {
    border-style: dashed;
    border-width: 2px;
    box-shadow: inset 0 0 14px rgba(224, 165, 58, 0.22);
  }

  /* 拐弯格强调转向 */
  &.corner { border-color: var(--color-primary); }

  &.start {
    border-color: #4f9d69;
    background-image: linear-gradient(135deg, rgba(79, 157, 105, 0.4), transparent 85%);
  }
  &.finish {
    border-color: #e0a53a;
    background-image: linear-gradient(135deg, rgba(224, 165, 58, 0.4), transparent 85%);
  }
}
.cell-no {
  position: absolute;
  top: 3px;
  left: 5px;
  font-size: 13px;
  font-weight: 700;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}
.track-cell.start .cell-no { color: #4f9d69; }
.track-cell.finish .cell-no,
.track-cell.special .cell-no { color: #e0a53a; }
.cell-special {
  position: absolute;
  top: 2px;
  right: 4px;
  padding: 0 3px;
  border-radius: 6px;
  background: rgba(224, 165, 58, 0.16);
  font-size: 15px;
}
.piece-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-top: 14px;
}
.piece {
  width: 100%;
  min-height: 20px;
  padding: 0 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.25);

  &.mine { outline: 2px solid #fff; outline-offset: 1px; }
  &.tripped { opacity: 0.55; filter: grayscale(0.7); }
  &.finished { box-shadow: 0 0 0 2px #e0a53a; }
}
/* S 型格子连接：行内箭头 + 行末转向标记 */
.cell-link {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  font-size: 20px;
  font-weight: 700;
  line-height: 1;
  color: var(--color-primary);
  opacity: 0.55;
  pointer-events: none;
}
.track-row.right .cell-link { right: -8px; }
.track-row.left .cell-link { left: -8px; }
.cell-turn {
  position: absolute;
  bottom: -10px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  color: var(--color-primary);
  z-index: 2;
  pointer-events: none;
}
.optional-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 900px;
  margin: 8px auto;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--panel, transparent);
  .optional-title { font-size: 14px; font-weight: 600; color: var(--ink); }
  .optional-item {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    padding: 8px 10px;
    border-radius: 8px;
    background: var(--surface-1, transparent);
    font-size: 14px;
    color: var(--muted);
  }
  .optional-copy {
    flex: 1 1 320px;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
    b { font-size: 14px; color: var(--ink); }
    small { font-size: 12px; line-height: 1.5; color: var(--muted); }
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
  .optional-ok { font-size: 13px; color: #2e9e5b; }
}
.race-ops {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
}
.catalog-trigger {
  font: inherit;
  font-size: 15px;
  padding: 10px 16px;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  &:hover { border-color: var(--color-primary); color: var(--color-primary); }
}
.catalog-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.55);
}
.catalog-modal {
  width: min(980px, 100%);
  max-height: min(82vh, 860px);
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface-3);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
}
.catalog-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--line);
  h3 { margin: 0; font-size: 18px; }
}
.catalog-close {
  font: inherit;
  font-size: 20px;
  line-height: 1;
  border: 0;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  &:hover { color: var(--ink); }
}
.catalog-modal-body {
  padding: 16px 18px;
  overflow-y: auto;
}
.racer-card.used { opacity: 0.45; filter: grayscale(1); cursor: not-allowed; }

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
  font-size: 12px;
  color: #e0a53a;
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.dice-toast { flex-wrap: wrap; max-width: min(92vw, 780px); }
.result-toast { border-color: #e0a53a; gap: 10px; }

@media (max-width: 720px) {
  .racer-grid { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); }
  .hand-name { width: 84px; }
  .race-layout { grid-template-columns: 1fr; }
  .rank-panel { position: relative; top: auto; z-index: 50; }
  .rank-medals { display: none; }
  .optional-item { flex-wrap: wrap; }
  .optional-copy { flex-basis: 100%; }
  .effect-tag { max-width: 60vw; }
}
</style>
