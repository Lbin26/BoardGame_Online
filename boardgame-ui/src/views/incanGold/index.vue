<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import * as api from '@/api/incanGold'
import type {
  IncanGoldFieldCard,
  IncanGoldRanking,
  IncanGoldRoomInfo,
  IncanGoldSnapshot,
} from '@/api/incanGold'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { useRoomEntry } from '@/composables/useRoomEntry'
import { copyText } from '@/utils/clipboard'
import { resolveMySeat } from '@/utils/halliSeat'
import { goBackOrLobby } from '@/utils/navigation'

const router = useRouter()

const room = ref<IncanGoldRoomInfo | null>(null)
const snapshot = ref<IncanGoldSnapshot | null>(null)
const rankings = ref<IncanGoldRanking[]>([])
const myName = ref('')
const myChoice = ref<'CONTINUE' | 'RETREAT' | null>(null)
const clock = ref(Date.now())

const HAZARDS = [
  { code: 'fire', name: '火灾', emoji: '🔥' },
  { code: 'mummy', name: '木乃伊', emoji: '🧟' },
  { code: 'rock', name: '落石', emoji: '🪨' },
  { code: 'snake', name: '蛇', emoji: '🐍' },
  { code: 'spider', name: '蜘蛛', emoji: '🕷️' },
]

function hazardOf(code: string | null) {
  return HAZARDS.find((h) => h.code === code) ?? { code: '', name: '灾难', emoji: '⚠️' }
}

function cardIcon(card: IncanGoldFieldCard): string {
  if (card.kind === 'TREASURE') return '💎'
  if (card.kind === 'ARTIFACT') return '🏺'
  return hazardOf(card.hazard).emoji
}

function cardLabel(card: IncanGoldFieldCard): string {
  // 余数无论是否为 0 都要明示：0 代表本次分配没有找零，同样是结算信息
  if (card.kind === 'TREASURE') {
    return `${card.value} · 余数 ${card.remainder}`
  }
  if (card.kind === 'ARTIFACT') return `神器 #${card.value}`
  return hazardOf(card.hazard).name
}

/** 中立广播会丢掉 mySeatNo：沿用通用工具按名字反查，避免丢失本人座位 */
function adoptRoom(next: IncanGoldRoomInfo | null | undefined): void {
  if (!next) return
  room.value = {
    ...next,
    mySeatNo: resolveMySeat(next.seats, next.mySeatNo, myName.value, room.value?.mySeatNo ?? null),
  }
}

function syncMySeatFromSnapshot(): void {
  const snap = snapshot.value
  if (!snap || !room.value || room.value.mySeatNo !== null) return
  const mine = snap.players.find((p) => p.name === myName.value)?.seatNo
  if (mine !== undefined) room.value = { ...room.value, mySeatNo: mine }
}

function handleMessage(msg: { type?: string; payload?: Record<string, any> }): void {
  const type = msg?.type
  const payload = msg?.payload ?? {}
  switch (type) {
    case 'PLAYER_JOINED':
    case 'SEAT_CHANGED':
      adoptRoom(payload.room as IncanGoldRoomInfo | null)
      if (payload.snapshot) snapshot.value = payload.snapshot as IncanGoldSnapshot
      break
    case 'GAME_START':
      adoptRoom(payload.room as IncanGoldRoomInfo | null)
      if (payload.snapshot) snapshot.value = payload.snapshot as IncanGoldSnapshot
      rankings.value = []
      myChoice.value = null
      break
    case 'STATE_SYNC':
      if (payload) {
        snapshot.value = payload as unknown as IncanGoldSnapshot
        syncMySeatFromSnapshot()
      }
      break
    case 'GAME_OVER':
      rankings.value = (payload.rankings ?? []) as IncanGoldRanking[]
      break
    case 'ACTION_REJECT':
      if (payload.action === 'CHOICE') myChoice.value = null
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
  path: '/ws/incan-gold',
  roomId: () => room.value?.roomId,
  playerName: () => myName.value,
  onMessage: handleMessage,
})

useRoomEntry({
  gameKey: 'incanGold',
  loginRedirect: (roomId) => `/incan-gold/${roomId}`,
  fetchRoom: api.getRoom,
  isValid: (data) => !!data.room,
  applyRoom: (data) => adoptRoom(data.room),
  playerName: myName,
  connect,
})

const roomId = computed(() => room.value?.roomId ?? '')
const mySeat = computed(() =>
  resolveMySeat(
    room.value?.seats,
    room.value?.mySeatNo,
    myName.value,
    snapshot.value?.players.find((p) => p.name === myName.value)?.seatNo ?? null,
  ),
)
const isHost = computed(() => !!room.value && room.value.hostName === myName.value)
const seatCount = computed(() => room.value?.seats.filter((s) => !!s.name).length ?? 0)
const inGame = computed(() => !!snapshot.value && snapshot.value.phase !== 'WAITING')
const selfPlayer = computed(() => snapshot.value?.players.find((p) => p.name === myName.value) ?? null)

const phaseLabel = computed(() => {
  switch (snapshot.value?.phase) {
    case 'DECIDE': return '秘密决策'
    case 'ROUND_END': return '本轮结算'
    case 'FINISHED': return '探险结束'
    default: return '等待开始'
  }
})

const endCauseLabel = computed(() => {
  switch (snapshot.value?.endCause) {
    case 'COLLAPSE': return '灾难团灭'
    case 'ALL_RETREAT': return '全员撤退'
    case 'DECK_EMPTY': return '牌堆耗尽'
    default: return '结束'
  }
})

const countdown = computed(() => {
  const deadline = snapshot.value?.phaseDeadlineAt ?? 0
  if (!deadline) return 0
  return Math.max(0, Math.ceil((deadline - clock.value) / 1000))
})

const canChoose = computed(() =>
  connected.value &&
  snapshot.value?.phase === 'DECIDE' &&
  selfPlayer.value?.inExpedition === true &&
  myChoice.value === null,
)

/** 新一轮翻牌会刷新事件序号；只有非「已提交」事件才清除自己的本地选择记忆 */
watch(
  () => {
    const events = snapshot.value?.events
    return events && events.length ? events[events.length - 1].seq : 0
  },
  (seq, oldSeq) => {
    const events = snapshot.value?.events
    const last = events && events.length ? events[events.length - 1] : null
    if (seq !== oldSeq && last && last.code !== 'CHOICE_MADE' && snapshot.value?.phase === 'DECIDE') {
      myChoice.value = null
    }
  },
)

let clockTimer: number | undefined
onMounted(() => {
  clockTimer = window.setInterval(() => { clock.value = Date.now() }, 500)
})
onUnmounted(() => {
  if (clockTimer !== undefined) window.clearInterval(clockTimer)
})

function startGame(): void {
  send({ type: 'START_GAME', payload: {} })
}

function choose(choice: 'CONTINUE' | 'RETREAT'): void {
  if (!canChoose.value) return
  if (send({ type: 'CHOICE', payload: { choice } })) myChoice.value = choice
}

function nextRound(): void {
  send({ type: 'NEXT_ROUND', payload: {} })
}

function dissolveRoom(): void {
  ElMessageBox.confirm('确定解散房间？所有玩家会返回大厅。', '解散房间', { type: 'warning' })
    .then(() => send({ type: 'DISSOLVE_ROOM', payload: {} }))
    .catch(() => {})
}

async function refresh(): Promise<void> {
  if (!roomId.value) return
  try {
    const data = await api.getRoom(roomId.value, myName.value)
    if (data.success && data.room) adoptRoom(data.room)
  } catch {
    ElMessage.error('刷新房间失败')
  }
}

async function takeSeat(seatNo: number): Promise<void> {
  if (!myName.value) return
  try {
    const data = await api.takeSeat(roomId.value, myName.value, seatNo)
    if (data.success && data.room) room.value = data.room
    else ElMessage.error(data.error ?? '入座失败')
  } catch {
    ElMessage.error('入座失败')
  }
}

async function standUp(): Promise<void> {
  if (!myName.value) return
  try {
    const data = await api.standUp(roomId.value, myName.value)
    if (data.success && data.room) room.value = data.room
    else ElMessage.error(data.error ?? '起身失败')
  } catch {
    ElMessage.error('起身失败')
  }
}

function backToLobby(): void {
  router.push('/lobby')
}

async function copyRoomId(): Promise<void> {
  const ok = await copyText(roomId.value)
  ElMessage[ok ? 'success' : 'warning'](ok ? '房间号已复制' : '复制失败，请手动复制')
}
</script>

<template>
  <div class="incan-room">
    <!-- 页眉：与宝石商人/德国心脏病/胡闹运动会同构的三段式（左=房间号/复制，中=连接/轮次/状态/规则，右=返回/解散） -->
    <header class="room-bar">
      <div class="bar-left">
        <span class="room-id">房间号：<strong>{{ room?.roomId ?? roomId ?? '...' }}</strong></span>
        <el-button size="small" @click="copyRoomId">📋 复制</el-button>
      </div>
      <div class="bar-center">
        <span class="conn">
          <i class="dot" :class="{ on: connected }"></i>{{ connected ? '已连接' : '重连中' }}
        </span>
        <span class="round-info">第 {{ snapshot?.round ?? '—' }} / 5 轮</span>
        <span class="round-info">{{ phaseLabel }}</span>
        <el-button size="small" plain @click="router.push('/rules/incanGold')">📖 规则</el-button>
        <el-button size="small" plain @click="refresh">🔄 刷新</el-button>
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

    <div v-if="room && !inGame" class="room-body">
      <section class="panel">
        <h2>探险营地</h2>
        <p class="hint">点击空位入座。至少 3 人后由房主开始，共 5 轮探险。</p>
        <div class="seat-grid">
          <div
            v-for="seat in room.seats"
            :key="seat.seatNo"
            class="seat"
            :class="{ mine: seat.name === myName }"
          >
            <span class="seat-no">{{ seat.seatNo }}</span>
            <template v-if="seat.name">
              <span class="seat-name">{{ seat.name }}</span>
              <span v-if="seat.name === room.hostName" class="host-badge">房主</span>
              <button v-if="seat.name === myName" type="button" class="seat-act" @click="standUp">
                起身
              </button>
            </template>
            <button v-else type="button" class="seat-empty" @click="takeSeat(seat.seatNo)">
              + 空位
            </button>
          </div>
        </div>
        <div class="waiting-actions">
          <button v-if="isHost" type="button" class="primary" :disabled="seatCount < 3" @click="startGame">
            🏺 开始探险（{{ seatCount }}/3）
          </button>
          <span v-else class="hint">等待房主开始探险…</span>
        </div>
      </section>
      <section class="panel">
        <h2>一句话规则</h2>
        <p class="rule-text">
          翻牌结算后所有在场玩家<strong>同时秘密</strong>选择继续或撤退。撤退者落袋为安；
          一人独走可收走牌上余数与全部神器，多人同时撤退则平分余数、都拿不到神器。
          同种灾难第二次出现即团灭，宝石每颗 1 分，神器另有加分，5 轮后分高者胜。
        </p>
      </section>
    </div>

    <div v-else-if="snapshot" class="room-body">
      <section class="panel status-bar">
        <div class="status-main">
          <strong>第 {{ snapshot.round }} / 5 轮</strong>
          <span v-if="mySeat" class="tag">座位 {{ mySeat }}</span>
          <span class="tag">{{ phaseLabel }}</span>
        </div>
        <div
          v-if="countdown > 0 && snapshot.phase !== 'FINISHED'"
          class="countdown-alert"
          :class="{ urgent: countdown <= 5 }"
          role="timer"
          :aria-label="`倒计时 ${countdown} 秒`"
        >
          <span class="cd-icon">⏳</span>
          <span class="cd-text">
            <strong>{{ snapshot.phase === 'DECIDE' ? '秘密决策倒计时' : '本轮结算倒计时' }}</strong>
            <small>
              {{
                snapshot.phase === 'DECIDE'
                  ? (countdown <= 5 ? '最后 5 秒！超时将自动继续深入' : '超时将自动继续深入，请尽快决定')
                  : '结算展示即将结束，马上进入下一轮'
              }}
            </small>
          </span>
          <strong class="cd-num">{{ countdown }}<small>秒</small></strong>
        </div>
        <p class="hint">
          <template v-if="snapshot.phase === 'DECIDE'">
            牌已结算。秘密决定：继续深入可能翻倍，撤退则落袋为安。
          </template>
          <template v-else-if="snapshot.phase === 'ROUND_END'">
            本轮{{ endCauseLabel }}。场上神器作废，即将进入下一轮。
          </template>
          <template v-else>探险结束，查看最终排名。</template>
        </p>
      </section>

      <section class="panel">
        <h2>神庙桌面</h2>
        <div v-if="snapshot.field.length" class="field-cards">
          <div v-for="(card, index) in snapshot.field" :key="index" class="field-card">
            <span class="card-icon">{{ cardIcon(card) }}</span>
            <span class="card-label">{{ cardLabel(card) }}</span>
          </div>
        </div>
        <p v-else class="hint">桌面暂无牌。</p>
        <div class="hazard-row">
          <span
            v-for="h in HAZARDS"
            :key="h.code"
            class="hazard-chip"
            :class="{ danger: (snapshot.hazardRemaining[h.code] ?? 0) < 3 }"
          >
            {{ h.emoji }} {{ h.name }} ×{{ snapshot.hazardRemaining[h.code] ?? 0 }}
          </span>
        </div>
        <p v-if="snapshot.fieldArtifacts" class="artifact-on-field">
          🏺 场上有 {{ snapshot.fieldArtifacts }} 张神器：只有一人撤退才能全部拿走，多人同撤则作废。
        </p>
      </section>

      <section class="panel">
        <h2>探险家</h2>
        <div class="player-grid">
          <div
            v-for="p in snapshot.players"
            :key="p.seatNo"
            class="player-card"
            :class="{ mine: p.name === myName, away: !p.inExpedition }"
          >
            <div class="player-name">
              <span class="seat-no">{{ p.seatNo }}</span>
              {{ p.name }}
            </div>
            <div class="player-state">
              {{ p.inExpedition ? '探险中' : '已回营地' }}
              <span v-if="p.submitted" class="submitted">· 已决策</span>
            </div>
            <div class="player-gems">
              <template v-if="p.name === myName">
                营地 <strong>{{ p.campGems ?? 0 }}</strong> · 本轮
                <strong class="round-gems">{{ p.roundGems ?? 0 }}</strong>
              </template>
              <template v-else>🎪 帐篷遮挡</template>
            </div>
            <div v-if="p.artifacts" class="player-artifacts">🏺 × {{ p.artifacts }}</div>
          </div>
        </div>
      </section>

      <section v-if="canChoose" class="panel decision">
        <h2>你的决策</h2>
        <div class="decision-actions">
          <button type="button" class="choice continue" @click="choose('CONTINUE')">
            <span class="choice-icon">🔥</span>
            <span><strong>继续深入</strong><small>留在神庙，等待下一次翻牌</small></span>
          </button>
          <button type="button" class="choice retreat" @click="choose('RETREAT')">
            <span class="choice-icon">🏕️</span>
            <span><strong>带着宝石撤退</strong><small>本轮所得落袋，不再有风险</small></span>
          </button>
        </div>
      </section>
      <section v-else-if="snapshot.phase === 'DECIDE' && myChoice" class="panel decision done">
        已提交{{ myChoice === 'CONTINUE' ? '继续深入' : '撤退' }}，等待其他探险家…
      </section>

      <section v-if="snapshot.phase === 'ROUND_END'" class="panel round-end">
        <strong>{{ endCauseLabel }}</strong>
        <button v-if="isHost" type="button" class="primary" @click="nextRound">进入下一轮</button>
      </section>

      <section v-if="snapshot.phase === 'FINISHED' && rankings.length" class="panel">
        <h2>最终排名</h2>
        <ol class="ranking-list">
          <li v-for="(r, i) in rankings" :key="r.seatNo" :class="{ mine: r.name === myName }">
            <span class="rank-no">{{ i + 1 }}</span>
            <span class="rank-name">{{ r.name }}</span>
            <span class="rank-detail">宝石 {{ r.gems }} · 神器 {{ r.artifacts }}</span>
            <strong>{{ r.score }} 分</strong>
          </li>
        </ol>
        <p class="hint">平分时神器多者胜。</p>
      </section>

      <section class="panel log">
        <h2>探险播报</h2>
        <ul>
          <li v-for="e in [...snapshot.events].slice(-6).reverse()" :key="e.seq">{{ e.message }}</li>
        </ul>
      </section>
    </div>

    <div v-else class="loading">正在进入房间…</div>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/room' as room;

// 页眉、连接点、轮次标签、顶号提示条全部复用公共 mixin，和其他游戏保持同构
@include room.room-bar;
@include room.round-info;
@include room.conn-dot;
@include room.room-banner;

.incan-room {
  min-height: 100vh;
  padding: 20px 24px 40px;
  color: var(--ink);
}
.room-body {
  display: grid;
  gap: 18px;
  max-width: 980px;
  margin: 0 auto;
}
.panel {
  padding: 20px 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface-4);
  h2 {
    font-family: var(--font-display);
    font-size: 20px;
    font-weight: 400;
    margin: 0 0 12px;
  }
}
.hint {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}
.rule-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  strong { color: var(--color-primary); }
}
.seat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}
.seat {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  &.mine { border-color: var(--color-primary); background: var(--accent-soft); }
}
.seat-no {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex: none;
  border-radius: 50%;
  background: var(--accent-soft);
  font-size: 12px;
}
.seat-name { flex: 1; font-size: 14px; }
.host-badge {
  font-size: 11px;
  color: var(--color-primary);
}
.seat-act,
.seat-empty {
  font: inherit;
  font-size: 12px;
  color: var(--muted);
  background: transparent;
  border: 1px dashed var(--line);
  border-radius: 6px;
  padding: 3px 8px;
  cursor: pointer;
  &:hover { color: var(--ink); border-color: var(--accent-hover); }
}
.waiting-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
}
.primary {
  font: inherit;
  font-size: 14px;
  color: #fff;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius);
  padding: 9px 18px;
  cursor: pointer;
  &:disabled { opacity: .45; cursor: not-allowed; }
}
.status-bar {
  .status-main {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 18px;
  }
  .hint { margin: 8px 0 0; }
}
.tag {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--color-primary);
}
// 倒计时：紧贴轮数下方，用实心警示色 + 脉冲呼吸，最后一分钟（<=5s）加强闪烁
.countdown-alert {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 12px;
  padding: 12px 16px;
  border: 1px solid var(--danger);
  border-radius: 12px;
  background: var(--danger);
  color: #fff;
  box-shadow: 0 0 0 3px rgba(186, 55, 77, .16), 0 6px 18px rgba(186, 55, 77, .22);
  animation: cd-breathe 1.4s ease-in-out infinite;

  &.urgent {
    background-image: repeating-linear-gradient(
      45deg,
      rgba(255, 255, 255, .1) 0 10px,
      transparent 10px 20px
    );
    animation-duration: .6s;
  }

  .cd-icon { font-size: 28px; line-height: 1; }
  .cd-text {
    flex: 1;
    min-width: 0;
    strong {
      display: block;
      font-size: 16px;
      letter-spacing: .5px;
      color: #fff;
    }
    small {
      display: block;
      margin-top: 3px;
      font-size: 12px;
      color: rgba(255, 255, 255, .9);
    }
  }
  .cd-num {
    flex: none;
    min-width: 82px;
    padding: 6px 12px;
    border-radius: 10px;
    background: rgba(0, 0, 0, .28);
    font: 700 32px/1 ui-monospace, SFMono-Regular, Menlo, monospace;
    text-align: center;
    color: #fff;
    small { font-size: 12px; font-weight: 400; margin-left: 3px; }
  }
}
@keyframes cd-breathe {
  0%, 100% { box-shadow: 0 0 0 3px rgba(186, 55, 77, .16), 0 6px 18px rgba(186, 55, 77, .22); }
  50% { box-shadow: 0 0 0 7px rgba(186, 55, 77, .28), 0 0 26px rgba(186, 55, 77, .5); }
}
@media (prefers-reduced-motion: reduce) {
  .countdown-alert { animation: none; }
}
.field-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}
.field-card {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: rgba(0, 0, 0, .08);
  font-size: 14px;
}
.card-icon { font-size: 20px; }
.card-label { color: var(--ink); }
.hazard-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.hazard-chip {
  font-size: 12px;
  padding: 4px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--muted);
  &.danger { color: var(--color-primary); border-color: var(--color-primary); }
}
.artifact-on-field {
  margin: 12px 0 0;
  font-size: 13px;
  color: #d9a441;
}
.player-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 10px;
}
.player-card {
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  font-size: 13px;
  &.mine { border-color: var(--color-primary); background: var(--accent-soft); }
  &.away { opacity: .65; }
}
.player-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  margin-bottom: 6px;
}
.player-state { color: var(--muted); font-size: 12px; }
.submitted { color: #4caf50; }
.player-gems { margin-top: 8px; }
.round-gems { color: var(--color-primary); }
.player-artifacts { margin-top: 4px; color: #d9a441; }
.decision-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}
.choice {
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
  font: inherit;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  &:hover { border-color: var(--accent-hover); }
  strong { display: block; font-size: 15px; }
  small { display: block; margin-top: 4px; color: var(--muted); font-size: 12px; }
  &.continue .choice-icon { color: var(--color-primary); }
  &.retreat .choice-icon { color: #4caf50; }
}
.choice-icon { font-size: 26px; }
.decision.done { color: var(--muted); font-size: 14px; }
.round-end {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.ranking-list {
  margin: 0;
  padding: 0;
  list-style: none;
  li {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 4px;
    border-bottom: 1px solid var(--line);
    font-size: 14px;
    &.mine { color: var(--color-primary); }
  }
}
.rank-no {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--accent-soft);
  font-size: 12px;
}
.rank-name { flex: 1; }
.rank-detail { color: var(--muted); font-size: 12px; }
.log {
  ul { margin: 0; padding-left: 18px; }
  li { font-size: 13px; line-height: 1.9; color: var(--muted); }
}
.loading {
  max-width: 980px;
  margin: 80px auto;
  text-align: center;
  color: var(--muted);
}
</style>
