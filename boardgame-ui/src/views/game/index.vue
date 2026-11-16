<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import * as chessApi from '@/api/chess'
import ChessBoard from '@/components/ChessBoard.vue'
import { useUserStore } from '@/store/user'
import { getPieceAt } from '@/utils/board'

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

/** 房间信息（后端 RoomVO） */
interface RoomVO {
  roomId: string
  redPlayerName: string | null
  blackPlayerName: string | null
  status: string
  viewerColor: 'RED' | 'BLACK' | null
}

/** 棋盘交叉点 */
interface Cell {
  row: number
  col: number
}

/** 聊天消息 */
interface ChatMessage {
  who: string
  text: string
  whoClass: string
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const room = ref<RoomVO | null>(null)
const pieces = ref<ChessPiece[]>([])
const currentTurn = ref<'RED' | 'BLACK'>('RED')
const viewerColor = ref<'RED' | 'BLACK' | null>(null)

const selectedCell = ref<Cell | null>(null)
const validMoves = ref<Cell[]>([])
const gameOver = ref(false)
const winner = ref<'RED' | 'BLACK' | null>(null)
const gameReason = ref('')
const inCheck = ref(false)

const chatMessages = ref<ChatMessage[]>([])
const chatText = ref('')
const chatBox = ref<HTMLElement | null>(null)

const connected = ref(false)

let ws: WebSocket | null = null

/** 是否红方视角轮到自己 */
const isRedTurn = computed(
  () => viewerColor.value === 'RED' && currentTurn.value === 'RED',
)

/** 是否黑方视角轮到自己 */
const isBlackTurn = computed(
  () => viewerColor.value === 'BLACK' && currentTurn.value === 'BLACK',
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

/** 清空选中与合法走法（断开/落子后避免残留高亮） */
function clearSelection(): void {
  selectedCell.value = null
  validMoves.value = []
}

function addChatMsg(who: string, text: string): void {
  const whoClass =
    who === 'system'
      ? 'system'
      : who === room.value?.redPlayerName
        ? 'red'
        : 'black'
  chatMessages.value.push({ who, text, whoClass })
  nextTick(() => {
    if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight
  })
}

function connectWS(roomId: string, playerName: string): void {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(
    `${proto}//${location.host}/ws/chess?roomId=${roomId}&playerName=${encodeURIComponent(playerName)}`,
  )

  ws.onopen = () => {
    connected.value = true
  }
  ws.onmessage = (evt) => {
    try {
      handleWSMessage(JSON.parse(evt.data))
    } catch (e) {
      console.warn('WS parse error', e)
    }
  }
  ws.onclose = () => {
    connected.value = false
    clearSelection()
    ElMessage.warning('连接断开')
  }
  ws.onerror = () => {
    connected.value = false
    clearSelection()
    ElMessage.error('连接出错，请刷新重试')
  }
}

function handleWSMessage(msg: any): void {
  const t = msg.type
  const p = msg.payload
  switch (t) {
    case 'PLAYER_JOINED':
      if (p?.room) {
        room.value = { ...room.value, ...p.room } as RoomVO
        viewerColor.value = p.room.viewerColor ?? viewerColor.value
      }
      if (p?.board) applyBoard(p.board as BoardSnapshot)
      addChatMsg('system', `${msg.playerName} 进入了房间`)
      break
    case 'GAME_START':
      if (p?.board) applyBoard(p.board as BoardSnapshot)
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
      addChatMsg('system', '认输了')
      break
    case 'UNDO':
      if (p?.board) applyBoard(p.board as BoardSnapshot)
      addChatMsg('system', '悔棋了')
      break
    case 'REMATCH':
      if (p?.board) applyBoard(p.board as BoardSnapshot)
      gameOver.value = false
      winner.value = null
      gameReason.value = ''
      inCheck.value = false
      addChatMsg('system', '新对局开始')
      break
    case 'PLAYER_LEAVE':
      addChatMsg('system', '离开了房间')
      break
    case 'CHAT':
      addChatMsg(msg.playerName, p?.text ?? '')
      break
  }
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
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(
          JSON.stringify({
            type: 'MOVE',
            payload: {
              fromRow: from.row,
              fromCol: from.col,
              toRow: cell.row,
              toCol: cell.col,
            },
          }),
        )
      }
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

function sendChat(): void {
  const text = chatText.value.trim()
  if (!text || !ws || ws.readyState !== WebSocket.OPEN) return
  ws.send(JSON.stringify({ type: 'CHAT', payload: { text } }))
  chatText.value = ''
}

function sendUndo(): void {
  if (!ws || ws.readyState !== WebSocket.OPEN) return
  ws.send(JSON.stringify({ type: 'UNDO', payload: {} }))
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
  if (!ws || ws.readyState !== WebSocket.OPEN) return
  ws.send(JSON.stringify({ type: 'RESIGN', payload: {} }))
}

function sendRematch(): void {
  if (!ws || ws.readyState !== WebSocket.OPEN) return
  inCheck.value = false
  ws.send(JSON.stringify({ type: 'REMATCH', payload: {} }))
}

function leaveGame(): void {
  router.push('/lobby')
}

onMounted(async () => {
  // 刷新后 store 仅水合了 token，需先补齐 userInfo 才能取到 userName
  if (userStore.isLogin && !userStore.userInfo) {
    await userStore.fetchProfile()
  }

  const roomId = route.params.roomId as string
  const playerName = userStore.userInfo?.userName ?? ''

  const data = await chessApi.getRoom(roomId, playerName)
  if (data.success === false) {
    ElMessage.error(data.error ?? '进入房间失败')
    router.push('/lobby')
    return
  }

  if (data.room) room.value = data.room as RoomVO
  if (data.board) applyBoard(data.board as BoardSnapshot)
  viewerColor.value = (data.room as RoomVO | undefined)?.viewerColor ?? null

  // 观看者（无座位）时回退为加入房间
  if (viewerColor.value === null) {
    const joined = await chessApi.joinRoom(roomId, playerName)
    if (joined.success) {
      if (joined.room) room.value = joined.room as RoomVO
      if (joined.board) applyBoard(joined.board as BoardSnapshot)
      viewerColor.value = (joined.room as RoomVO | undefined)?.viewerColor ?? null
    }
  }

  connectWS(roomId, playerName)
})

onUnmounted(() => {
  if (ws) {
    // 主动离开时避免触发断开提示
    ws.onclose = null
    ws.onerror = null
    ws.close()
    ws = null
  }
  clearSelection()
})
</script>

<template>
  <div class="game-page">
    <div class="game-layout">
      <!-- 黑方面板 -->
      <div class="side-panel">
        <div class="avatar avatar-black">
          {{ (room?.blackPlayerName ?? '?').charAt(0).toUpperCase() }}
        </div>
        <div class="player-name">{{ room?.blackPlayerName ?? '等待玩家...' }}</div>
        <div class="player-tag tag-black">⚫ 黑方</div>
        <div class="turn" :class="isBlackTurn ? 'turn-now' : 'turn-wait'">
          {{ isBlackTurn ? '🎯 轮到你了' : '⏳ 等待中' }}
        </div>
        <div v-if="inCheck && viewerColor === 'BLACK'" class="in-check">⚡ 将军！</div>
      </div>

      <!-- 棋盘 -->
      <div class="board-container">
        <ChessBoard
          :pieces="pieces"
          :current-turn="currentTurn"
          :viewer-color="viewerColor"
          :selected-cell="selectedCell"
          :valid-moves="validMoves"
          @cell-click="onCellClick"
        />
        <div class="actions">
          <el-button :disabled="gameOver" @click="sendUndo">↩️ 悔棋</el-button>
          <el-button type="danger" :disabled="gameOver" @click="sendResign">
            🏳️ 认输
          </el-button>
          <el-button type="success" :disabled="!gameOver" @click="sendRematch">
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
        <div class="player-name">{{ room?.redPlayerName ?? '等待玩家...' }}</div>
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

    <div v-if="!connected" class="conn-banner">⏳ 连接中 / 已断开</div>

    <!-- 对局结束弹层 -->
    <div v-if="gameOver" class="modal-overlay">
      <div class="modal">
        <h2>{{ winnerText }}</h2>
        <p>{{ gameReasonText }}</p>
        <div class="modal-actions">
          <el-button type="success" @click="sendRematch">🔄 再来一局</el-button>
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
