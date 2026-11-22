<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import * as chessApi from '@/api/xiangqi'
import type { RoomInfo } from '@/api/xiangqi'
import ChessBoard from '@/components/ChessBoard.vue'
import SeatPicker from '@/components/SeatPicker.vue'
import { useChat } from '@/composables/useChat'
import { useGameWebSocket } from '@/composables/useGameWebSocket'
import { forgetLastRoom } from '@/composables/useLastRoom'
import { useRoomEntry } from '@/composables/useRoomEntry'
import { getPieceAt } from '@/utils/board'
import { copyText } from '@/utils/clipboard'
import { goBackOrLobby } from '@/utils/navigation'

/** 棋盘上的一个棋子（后端 ChessPieceVO） */
interface ChessPiece {
  type: string
  color: 'RED' | 'BLACK'
  row: number
  col: number
  name: string
  [key: string]: unknown
}

/** 棋盘快照（后端 BoardSnapshotVO） */
interface BoardSnapshot {
  pieces: ChessPiece[]
  currentTurn: 'RED' | 'BLACK'
  gameOver: boolean
  winner: 'RED' | 'BLACK' | null
}

/** 棋盘交叉点 */
interface Cell {
  row: number
  col: number
}

const router = useRouter()

const room = ref<RoomInfo | null>(null)
const pieces = ref<ChessPiece[]>([])
const currentTurn = ref<'RED' | 'BLACK'>('RED')
const viewerColor = ref<'RED' | 'BLACK' | null>(null)
const myName = ref('')

const selectedCell = ref<Cell | null>(null)
const validMoves = ref<Cell[]>([])
const gameOver = ref(false)
const winner = ref<'RED' | 'BLACK' | null>(null)
const gameReason = ref('')
const inCheck = ref(false)

const chatBox = ref<HTMLElement | null>(null)

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
  path: '/ws/chess',
  roomId: () => room.value?.roomId,
  playerName: () => myName.value,
  onMessage: (msg) => handleWSMessage(msg),
  onDisconnected: clearSelection,
})

const { chatMessages, chatText, addChatMsg, sendChat } = useChat({
  send: (text) => wsSend({ type: 'CHAT', payload: { text } }),
  classifyWho: (who) =>
    who === 'system'
      ? 'system'
      : who === room.value?.redPlayerName
        ? 'red'
        : who === room.value?.blackPlayerName
          ? 'black'
          : 'spectator',
  scrollTarget: chatBox,
})

/** 无座时的选座面板 / 已入座时主动换座 */
const pickerOpen = ref(false)
const switching = ref(false)

/** 是否房主（建房者） */
const isHost = computed(() => !!room.value?.hostName && room.value.hostName === myName.value)

/** 玩家是否待机 */
function isPlayerAfk(name: string | null | undefined): boolean {
  return !!name && !!room.value?.afkPlayers?.includes(name)
}

/** 是否红方视角轮到自己 */
const isRedTurn = computed(
  () => viewerColor.value === 'RED' && currentTurn.value === 'RED',
)

/** 是否黑方视角轮到自己 */
const isBlackTurn = computed(
  () => viewerColor.value === 'BLACK' && currentTurn.value === 'BLACK',
)

/** 选座面板：等待中且（无座未关面板 或 主动换座） */
const showPicker = computed(
  () =>
    room.value?.status === 'WAITING' &&
    ((pickerOpen.value && !viewerColor.value) || switching.value),
)

/** 获胜文案 */
const winnerText = computed(() => {
  if (!winner.value) return '🤝 和棋！'
  const name =
    winner.value === 'RED' ? room.value?.redPlayerName : room.value?.blackPlayerName
  const tag = winner.value === 'RED' ? '🔴 红方' : '⚫ 黑方'
  return `🏆 ${tag}（${name ?? '?'}）获胜！`
})

/** 对局结束原因文案 */
const gameReasonText = computed(() => {
  switch (gameReason.value) {
    case 'CHECKMATE':
      return '将死，无路可走！'
    case 'STALEMATE':
      return '困毙，对方无棋可走！'
    case 'RESIGN':
      return '对方已认输。'
    default:
      return '对局结束！'
  }
})

/** 将后端棋盘快照写入本地状态，并清空选中/合法走法 */
function applyBoard(snap: BoardSnapshot): void {
  pieces.value = snap.pieces ?? []
  currentTurn.value = snap.currentTurn ?? 'RED'
  gameOver.value = snap.gameOver ?? false
  winner.value = snap.winner ?? null
  selectedCell.value = null
  validMoves.value = []
}

/** REST 响应为权威：room+viewerColor+board 一律以针对自己的响应为准 */
function applyRoomResponse(data: chessApi.RoomData): void {
  if (data.room) {
    room.value = data.room
    viewerColor.value = data.room.viewerColor ?? null
  }
  if (data.board) applyBoard(data.board as BoardSnapshot)
}

/**
 * WS 消息合并房间：广播只合并 names/status 等公共字段。
 * 后端广播一律中立视角 toVO(null)，故 viewerColor 只从自身同步的
 * PLAYER_JOINED 采纳；SEAT_CHANGED/REMATCH 的自身消息另行处理（见 switch）。
 */
function mergeRoom(msg: any): void {
  const incoming = msg.payload?.room as RoomInfo | undefined
  if (!incoming) return
  const { viewerColor: incomingColor, ...rest } = incoming
  room.value = { ...room.value, ...rest } as RoomInfo
  // 自身同步带 board；关于自己的中立加入广播不带 board，不得采纳其 viewerColor
  if (msg.playerName === myName.value && msg.type === 'PLAYER_JOINED' && msg.payload?.board) {
    viewerColor.value = incomingColor ?? null
  }
}

/** 清空选中与合法走法（断开/落子后避免残留高亮） */
function clearSelection(): void {
  selectedCell.value = null
  validMoves.value = []
}

function handleWSMessage(msg: any): void {
  const t = msg.type
  const p = msg.payload
  switch (t) {
    case 'PLAYER_JOINED':
      mergeRoom(msg)
      // 仅自身同步消息带棋盘（刷新恢复）；他人加入广播不带
      if (p?.board && msg.playerName === myName.value) {
        applyBoard(p.board as BoardSnapshot)
      }
      addChatMsg('system', `${msg.playerName} 进入了房间`)
      break
    case 'SEAT_CHANGED':
      mergeRoom(msg)
      // 关于我自己的座位广播：颜色以 payload.color 为准（room 是中立视角）
      if (msg.playerName === myName.value) {
        viewerColor.value = p?.color ?? null
        if (p?.color) {
          pickerOpen.value = false
          switching.value = false
        } else if (room.value?.status === 'WAITING') {
          pickerOpen.value = true
        }
      }
      addChatMsg(
        'system',
        p?.color
          ? `${msg.playerName} 坐到了${p.color === 'RED' ? '红方' : '黑方'}`
          : `${msg.playerName} 起身了`,
      )
      break
    case 'GAME_START':
      if (p?.board) applyBoard(p.board as BoardSnapshot)
      pickerOpen.value = false
      switching.value = false
      addChatMsg('system', '对局开始')
      break
    case 'MOVE':
      if (p?.board) applyBoard(p.board as BoardSnapshot)
      inCheck.value = !!p?.inCheck
      break
    case 'MOVE_INVALID':
      addChatMsg('system', p?.error ?? '那步棋走不了哦')
      break
    case 'GAME_OVER':
      if (p?.board) applyBoard(p.board as BoardSnapshot)
      gameOver.value = true
      winner.value = p?.winner ?? null
      gameReason.value = p?.reason ?? ''
      inCheck.value = false
      break
    case 'RESIGN':
      addChatMsg('system', `${msg.playerName} 认输了`)
      break
    case 'UNDO':
      if (p?.board) applyBoard(p.board as BoardSnapshot)
      addChatMsg('system', `${msg.playerName} 悔棋了`)
      break
    case 'REMATCH':
      mergeRoom(msg)
      if (p?.board) applyBoard(p.board as BoardSnapshot)
      gameOver.value = false
      winner.value = null
      gameReason.value = ''
      inCheck.value = false
      addChatMsg('system', '新对局开始')
      break
    case 'PLAYER_LEAVE':
      addChatMsg('system', `${msg.playerName} 离开了房间`)
      break
    case 'PLAYER_AFK': {
      const name = msg.playerName as string
      const list = room.value?.afkPlayers ? [...room.value.afkPlayers] : []
      const idx = list.indexOf(name)
      if (p?.afk && idx < 0) list.push(name)
      if (!p?.afk && idx >= 0) list.splice(idx, 1)
      if (room.value) room.value = { ...room.value, afkPlayers: list }
      addChatMsg('system', `${name} ${p?.afk ? '进入待机' : '恢复了操作'}`)
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
  }
}

/** 选座：成功失败都以 REST 响应重同步（竞争败者立即看到占座者） */
async function chooseSeat(color: 'RED' | 'BLACK'): Promise<void> {
  const roomId = room.value?.roomId
  if (!roomId || !myName.value) return
  try {
    const data = await chessApi.takeSeat(roomId, myName.value, color)
    applyRoomResponse(data)
    if (data.success) {
      pickerOpen.value = false
      switching.value = false
      ElMessage.success(color === 'RED' ? '已坐到红方' : '已坐到黑方')
    } else {
      ElMessage.error(data.error ?? '选座失败')
    }
  } catch {
    ElMessage.error('选座失败，请重试')
  }
}

/** 起身离座（仅等待中） */
async function doStandUp(): Promise<void> {
  const roomId = room.value?.roomId
  if (!roomId || !myName.value) return
  try {
    const data = await chessApi.standUp(roomId, myName.value)
    applyRoomResponse(data)
    if (data.success) {
      pickerOpen.value = true
    } else {
      ElMessage.error(data.error ?? '当前无法起身')
    }
  } catch {
    ElMessage.error('起身失败，请重试')
  }
}

function onSpectate(): void {
  pickerOpen.value = false
}

function reopenPicker(): void {
  pickerOpen.value = true
}

function startSwitch(): void {
  switching.value = true
}

async function copyRoomId(): Promise<void> {
  const id = room.value?.roomId
  if (!id) return
  const ok = await copyText(id)
  if (ok) {
    ElMessage.success('房间号已复制')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
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

/** 棋盘点击：选中己方棋子 / 走子 / 清空选中 */
async function onCellClick(cell: Cell): Promise<void> {
  if (gameOver.value) return
  if (!viewerColor.value) return
  if (viewerColor.value !== currentTurn.value) return

  const piece = getPieceAt(pieces.value, cell.row, cell.col)

  if (selectedCell.value) {
    const from = selectedCell.value
    const isValid = validMoves.value.some(
      (m) => m.row === cell.row && m.col === cell.col,
    )

    if (isValid) {
      wsSend({
        type: 'MOVE',
        payload: {
          fromRow: from.row,
          fromCol: from.col,
          toRow: cell.row,
          toCol: cell.col,
        },
      })
      selectedCell.value = null
      validMoves.value = []
      return
    }

    if (piece && piece.color === viewerColor.value) {
      selectedCell.value = cell
      await loadValidMoves(cell.row, cell.col)
      return
    }

    clearSelection()
    return
  }

  if (piece && piece.color === viewerColor.value) {
    selectedCell.value = cell
    await loadValidMoves(cell.row, cell.col)
  }
}

async function loadValidMoves(row: number, col: number): Promise<void> {
  const roomId = room.value?.roomId
  if (!roomId) return
  try {
    const data = await chessApi.getValidMoves(roomId, row, col)
    validMoves.value = data.validMoves ?? []
  } catch {
    validMoves.value = []
  }
}

function sendUndo(): void {
  wsSend({ type: 'UNDO', payload: {} })
}

async function sendResign(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定要认输吗？', '提示', {
      confirmButtonText: '认输',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  wsSend({ type: 'RESIGN', payload: {} })
}

function sendRematch(): void {
  inCheck.value = false
  wsSend({ type: 'REMATCH', payload: {} })
}

/** 手动接管：在本页重连（会替换其他标签页的连接，对方收到通知后停止） */
function resumeHere(): void {
  resume()
}

function leaveGame(): void {
  router.push('/lobby')
}

/** 进房引导（登录校验 / profile 补齐 / getRoom / 记住房间 / 连 WS）由公共 composable 承担 */
useRoomEntry({
  gameKey: 'xiangqi',
  loginRedirect: (id) => `/game/${id}`,
  fetchRoom: (id, name) => chessApi.getRoom(id, name),
  applyRoom: (data) => applyRoomResponse(data),
  afterRoom: () => {
    pickerOpen.value = room.value?.status === 'WAITING' && !viewerColor.value
  },
  playerName: myName,
  connect,
})

onUnmounted(clearSelection)
</script>

<template>
  <div class="game-page">
    <!-- 房间号 + 复制 -->
    <div class="room-bar">
      <span class="room-id">
        房间号：<strong>{{ room?.roomId ?? '...' }}</strong>
      </span>
      <el-button size="small" @click="copyRoomId">📋 复制</el-button>
      <el-button size="small" plain @click="router.push('/rules/xiangqi')">📖 规则</el-button>
      <el-button v-if="isHost" size="small" type="danger" plain @click="dissolveRoom">
        🗑 解散房间
      </el-button>
      <el-button size="small" plain class="back-btn" @click="goBackOrLobby(router)">
        ← 返回上一页
      </el-button>
      <el-button size="small" plain class="back-btn" @click="leaveGame">
        ← 返回大厅
      </el-button>
    </div>

    <div class="game-layout">
      <!-- 黑方面板 -->
      <div class="side-panel">
        <div class="avatar avatar-black">
          {{ (room?.blackPlayerName ?? '?').charAt(0).toUpperCase() }}
        </div>
        <div class="player-name">
          {{ room?.blackPlayerName ?? '等待玩家...' }}
          <span v-if="isPlayerAfk(room?.blackPlayerName)" class="afk-tag">💤 待机</span>
        </div>
        <div class="player-tag tag-black">⚫ 黑方</div>
        <div class="turn" :class="isBlackTurn ? 'turn-now' : 'turn-wait'">
          {{ isBlackTurn ? '🎯 轮到你了' : '⏳ 等待中' }}
        </div>
        <div v-if="inCheck && viewerColor === 'BLACK'" class="in-check">⚡ 将军！</div>
      </div>

      <!-- 棋盘 -->
      <div class="board-container">
        <SeatPicker
          v-if="showPicker && room"
          :room="room"
          :my-name="myName"
          @select="chooseSeat"
          @spectate="onSpectate"
        />

        <div v-if="viewerColor && room?.status === 'WAITING'" class="seat-actions">
          <el-button size="small" @click="startSwitch">🔁 换个座位</el-button>
          <el-button size="small" @click="doStandUp">🚶 起身</el-button>
        </div>

        <div v-if="room && !viewerColor && !showPicker" class="spectate-banner">
          👀 你正在观战
          <el-button
            v-if="room.status === 'WAITING'"
            size="small"
            type="primary"
            @click="reopenPicker"
          >
            选座
          </el-button>
        </div>

        <ChessBoard
          :pieces="pieces"
          :current-turn="currentTurn"
          :viewer-color="viewerColor"
          :selected-cell="selectedCell"
          :valid-moves="validMoves"
          @cell-click="onCellClick"
        />
        <div class="actions">
          <el-button :disabled="gameOver || !viewerColor" @click="sendUndo">
            ↩️ 悔棋
          </el-button>
          <el-button
            type="danger"
            :disabled="gameOver || !viewerColor"
            @click="sendResign"
          >
            🏳️ 认输
          </el-button>
          <el-button
            v-if="viewerColor"
            type="success"
            :disabled="!gameOver"
            @click="sendRematch"
          >
            🔄 再来一局
          </el-button>
          <el-button @click="leaveGame">返回大厅</el-button>
        </div>
      </div>

      <!-- 红方面板 + 聊天 -->
      <div class="side-panel">
        <div class="avatar avatar-red">
          {{ (room?.redPlayerName ?? '?').charAt(0).toUpperCase() }}
        </div>
        <div class="player-name">
          {{ room?.redPlayerName ?? '等待玩家...' }}
          <span v-if="isPlayerAfk(room?.redPlayerName)" class="afk-tag">💤 待机</span>
        </div>
        <div class="player-tag tag-red">🔴 红方</div>
        <div class="turn" :class="isRedTurn ? 'turn-now' : 'turn-wait'">
          {{ isRedTurn ? '🎯 轮到你了' : '⏳ 等待中' }}
        </div>
        <div v-if="inCheck && viewerColor === 'RED'" class="in-check">⚡ 将军！</div>

        <div class="chat">
          <div ref="chatBox" class="chat-messages">
            <div v-for="(msg, i) in chatMessages" :key="i" class="chat-msg">
              <span class="who" :class="msg.whoClass">{{ msg.who }}:</span>
              <span>{{ msg.text }}</span>
            </div>
          </div>
          <div class="chat-input">
            <el-input
              v-model="chatText"
              placeholder="说点什么..."
              maxlength="100"
              @keyup.enter="sendChat"
            />
            <el-button type="primary" :disabled="!chatText.trim()" @click="sendChat">
              发送
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="replacedNotice" class="replaced-banner">
      ⚠️ 该房间已在其他标签页/窗口打开，本页已停止连接
      <el-button size="small" type="primary" @click="resumeHere">在此页继续</el-button>
    </div>

    <div v-if="!connected && !replacedNotice" class="conn-banner">⏳ 连接中 / 已断开</div>

    <!-- 对局结束弹层 -->
    <div v-if="gameOver" class="modal-overlay">
      <div class="modal">
        <h2>{{ winnerText }}</h2>
        <p>{{ gameReasonText }}</p>
        <div class="modal-actions">
          <el-button v-if="viewerColor" type="success" @click="sendRematch">
            🔄 再来一局
          </el-button>
          <el-button @click="leaveGame">返回大厅</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.game-page {
  min-height: 100vh;
  padding: 24px;
}

.room-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  max-width: 1100px;
  margin: 0 auto 16px;
  font-size: 14px;
  color: #ccc;

  .room-id strong {
    color: #fff;
    font-size: 16px;
    letter-spacing: 1px;
    user-select: all;
  }
}

.game-layout {
  display: grid;
  grid-template-columns: 240px 1fr 280px;
  gap: 20px;
  max-width: 1100px;
  margin: 0 auto;
}

@media (max-width: 1100px) {
  .game-layout {
    grid-template-columns: 1fr;
  }
}

.side-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 70px;
  height: 70px;
  border-radius: 50%;
  margin-bottom: 10px;
  font-size: 30px;
  font-weight: bold;
  color: #fff;
}

.avatar-red {
  background: linear-gradient(135deg, #e94560, #c73659);
}

.avatar-black {
  background: linear-gradient(135deg, #333, #555);
}

.player-name {
  font-size: 16px;
  font-weight: 600;
}

/* 待机标记（游戏页深色底） */
.afk-tag {
  display: inline-block;
  margin-left: 4px;
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 600;
  color: #f1c40f;
  background: rgba(241, 196, 15, 0.14);
  border: 1px solid rgba(241, 196, 15, 0.4);
  border-radius: 8px;
  vertical-align: middle;
  white-space: nowrap;
}

.player-tag {
  margin-top: 4px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}

.tag-red {
  background: rgba(233, 69, 96, 0.2);
  color: #e94560;
}

.tag-black {
  background: rgba(255, 255, 255, 0.1);
  color: #ccc;
}

.turn {
  margin-top: 12px;
  padding: 8px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
}

.turn-now {
  background: rgba(46, 213, 115, 0.2);
  color: #2ed573;
}

.turn-wait {
  background: rgba(255, 255, 255, 0.05);
  color: #888;
}

.in-check {
  margin-top: 8px;
  padding: 6px;
  border-radius: 8px;
  background: rgba(231, 76, 60, 0.3);
  color: #e74c3c;
  font-size: 12px;
  font-weight: 700;
}

.board-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.seat-actions {
  display: flex;
  gap: 8px;
}

.spectate-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  border-radius: 10px;
  background: rgba(243, 156, 18, 0.15);
  border: 1px solid rgba(243, 156, 18, 0.4);
  color: #f39c12;
  font-size: 13px;
  font-weight: 600;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.chat {
  width: 100%;
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat-messages {
  height: 240px;
  overflow-y: auto;
  padding: 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.2);
}

.chat-msg {
  margin-bottom: 8px;
  font-size: 13px;
  word-break: break-all;
}

.who {
  margin-right: 6px;
  font-weight: 600;

  &.red {
    color: #e94560;
  }

  &.black {
    color: #ccc;
  }

  &.spectator {
    color: #7f8fa6;
  }

  &.system {
    color: #f39c12;
    font-style: italic;
  }
}

.chat-input {
  display: flex;
  gap: 8px;
}

.conn-banner {
  margin-top: 12px;
  text-align: center;
  font-size: 13px;
  color: #f39c12;
}

.replaced-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  max-width: 1100px;
  margin: 12px auto 0;
  padding: 10px 16px;
  border-radius: 10px;
  background: rgba(231, 76, 60, 0.15);
  border: 1px solid rgba(231, 76, 60, 0.45);
  color: #e74c3c;
  font-size: 13px;
  font-weight: 600;
}

.back-btn {
  margin-left: auto;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  padding: 40px;
  text-align: center;
  max-width: 420px;
  border-radius: 20px;
  background: linear-gradient(135deg, #1a1a2e, #16213e);
  border: 1px solid rgba(255, 255, 255, 0.2);

  h2 {
    margin: 0 0 12px;
    font-size: 28px;
  }

  p {
    margin: 0 0 24px;
    color: #aaa;
  }
}

.modal-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
}
</style>
