<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'

import {
  BOARD_H,
  BOARD_W,
  CELL,
  COLS,
  PADDING,
  ROWS,
  cellToPixel,
  getPieceAt,
  pixelToCell,
} from '@/utils/board'

/** 棋盘上的一个棋子（后端 ChessPieceVO） */
export interface ChessPiece {
  type: string
  color: 'RED' | 'BLACK'
  row: number
  col: number
  name: string
  [key: string]: unknown
}

/** 棋盘交叉点 */
interface Cell {
  row: number
  col: number
}

const props = defineProps<{
  pieces: ChessPiece[]
  currentTurn: string
  viewerColor: string | null
  selectedCell: Cell | null
  validMoves: Cell[]
}>()

const emit = defineEmits<{
  (e: 'cell-click', cell: Cell): void
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)

/** 绘制整张棋盘：网格、九宫斜线、楚河汉界、位标、选中/合法走法高亮、棋子 */
function drawBoard(): void {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  ctx.clearRect(0, 0, canvas.width, canvas.height)
  ctx.fillStyle = '#deb887'
  ctx.fillRect(0, 0, canvas.width, canvas.height)

  ctx.strokeStyle = '#8b4513'
  ctx.lineWidth = 1.5

  // 横线
  for (let r = 0; r < ROWS; r++) {
    const y = PADDING + r * CELL
    ctx.beginPath()
    ctx.moveTo(PADDING, y)
    ctx.lineTo(PADDING + (COLS - 1) * CELL, y)
    ctx.stroke()
  }

  // 竖线：两侧贯通，中间被楚河汉界隔断
  for (let c = 0; c < COLS; c++) {
    const x = PADDING + c * CELL
    if (c === 0 || c === COLS - 1) {
      ctx.beginPath()
      ctx.moveTo(x, PADDING)
      ctx.lineTo(x, PADDING + (ROWS - 1) * CELL)
      ctx.stroke()
    } else {
      ctx.beginPath()
      ctx.moveTo(x, PADDING)
      ctx.lineTo(x, PADDING + 4 * CELL)
      ctx.stroke()
      ctx.beginPath()
      ctx.moveTo(x, PADDING + 5 * CELL)
      ctx.lineTo(x, PADDING + 9 * CELL)
      ctx.stroke()
    }
  }

  // 九宫斜线（上方黑方）
  ctx.beginPath()
  ctx.moveTo(PADDING + 3 * CELL, PADDING)
  ctx.lineTo(PADDING + 5 * CELL, PADDING + 2 * CELL)
  ctx.moveTo(PADDING + 5 * CELL, PADDING)
  ctx.lineTo(PADDING + 3 * CELL, PADDING + 2 * CELL)
  ctx.stroke()

  // 九宫斜线（下方红方）
  ctx.beginPath()
  ctx.moveTo(PADDING + 3 * CELL, PADDING + 7 * CELL)
  ctx.lineTo(PADDING + 5 * CELL, PADDING + 9 * CELL)
  ctx.moveTo(PADDING + 5 * CELL, PADDING + 7 * CELL)
  ctx.lineTo(PADDING + 3 * CELL, PADDING + 9 * CELL)
  ctx.stroke()

  // 楚河汉界
  ctx.save()
  ctx.fillStyle = '#8b4513'
  ctx.font = 'bold 22px serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  const riverY = PADDING + 4.5 * CELL
  ctx.fillText('楚 河', PADDING + 1.5 * CELL, riverY)
  ctx.fillText('漢 界', PADDING + 6.5 * CELL, riverY)
  ctx.restore()

  // 兵/炮位标
  ctx.fillStyle = '#8b4513'
  ctx.font = '14px serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  const markerPositions: Array<[number, number]> = [
    [2, 1], [2, 7], [7, 1], [7, 7],
    [3, 0], [3, 2], [3, 4], [3, 6], [3, 8],
    [6, 0], [6, 2], [6, 4], [6, 6], [6, 8],
  ]
  markerPositions.forEach(([r, c]) => {
    const x = PADDING + c * CELL
    const y = PADDING + r * CELL
    const s = 6
    ctx.strokeStyle = '#8b4513'
    ctx.lineWidth = 1
    if (c > 0) {
      ctx.beginPath()
      ctx.moveTo(x - s, y - s / 2)
      ctx.lineTo(x - s / 2, y - s / 2)
      ctx.lineTo(x - s / 2, y - s)
      ctx.stroke()
      ctx.beginPath()
      ctx.moveTo(x - s, y + s / 2)
      ctx.lineTo(x - s / 2, y + s / 2)
      ctx.lineTo(x - s / 2, y + s)
      ctx.stroke()
    }
    if (c < COLS - 1) {
      ctx.beginPath()
      ctx.moveTo(x + s, y - s / 2)
      ctx.lineTo(x + s / 2, y - s / 2)
      ctx.lineTo(x + s / 2, y - s)
      ctx.stroke()
      ctx.beginPath()
      ctx.moveTo(x + s, y + s / 2)
      ctx.lineTo(x + s / 2, y + s / 2)
      ctx.lineTo(x + s / 2, y + s)
      ctx.stroke()
    }
  })

  // 选中交叉点高亮
  if (props.selectedCell) {
    const { x, y } = cellToPixel(props.selectedCell.row, props.selectedCell.col)
    ctx.fillStyle = 'rgba(46, 213, 115, 0.35)'
    ctx.beginPath()
    ctx.arc(x, y, CELL / 2 - 2, 0, Math.PI * 2)
    ctx.fill()
  }

  // 合法走法高亮：吃子为环，空位为点
  props.validMoves.forEach((m) => {
    const { x, y } = cellToPixel(m.row, m.col)
    const isCapture = getPieceAt(props.pieces, m.row, m.col)
    ctx.fillStyle = isCapture ? 'rgba(233, 69, 96, 0.5)' : 'rgba(46, 213, 115, 0.6)'
    ctx.beginPath()
    ctx.arc(x, y, isCapture ? CELL / 2 : CELL / 6, 0, Math.PI * 2)
    ctx.fill()
  })

  // 棋子
  props.pieces.forEach((p) => {
    const { x, y } = cellToPixel(p.row, p.col)
    drawPiece(ctx, x, y, p.name, p.color === 'RED')
  })
}

/** 绘制单个棋子（圆形棋面 + 中文文字） */
function drawPiece(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  name: string,
  isRed: boolean,
): void {
  const r = CELL / 2 - 2

  ctx.beginPath()
  ctx.fillStyle = '#333'
  ctx.arc(x, y, r + 1, 0, Math.PI * 2)
  ctx.fill()

  const grad = ctx.createRadialGradient(x - r / 3, y - r / 3, 2, x, y, r)
  grad.addColorStop(0, '#fff8dc')
  grad.addColorStop(1, '#d2b48c')
  ctx.beginPath()
  ctx.fillStyle = grad
  ctx.arc(x, y, r, 0, Math.PI * 2)
  ctx.fill()

  ctx.beginPath()
  ctx.strokeStyle = isRed ? '#c0392b' : '#2c3e50'
  ctx.lineWidth = 2
  ctx.arc(x, y, r - 4, 0, Math.PI * 2)
  ctx.stroke()

  ctx.fillStyle = isRed ? '#c0392b' : '#2c3e50'
  ctx.font = `bold ${Math.floor(r * 1.1)}px "KaiTi", "STKaiti", serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(name, x, y + 1)
}

/** 画布点击：按缩放比例换算像素坐标，命中交叉点则向上抛出 */
function onClick(event: MouseEvent): void {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  const scaleX = canvas.width / rect.width
  const scaleY = canvas.height / rect.height
  const x = (event.clientX - rect.left) * scaleX
  const y = (event.clientY - rect.top) * scaleY
  const cell = pixelToCell(x, y)
  if (cell) emit('cell-click', cell)
}

// 棋子/选中/合法走法变化时重绘
watch(
  () => [props.pieces, props.selectedCell, props.validMoves],
  () => nextTick(drawBoard),
  { deep: true },
)

onMounted(() => {
  nextTick(drawBoard)
})
</script>

<template>
  <canvas
    ref="canvasRef"
    class="chess-board"
    :width="BOARD_W"
    :height="BOARD_H"
    @click="onClick"
  ></canvas>
</template>

<style scoped>
.chess-board {
  border-radius: 8px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
  background: #deb887;
  max-width: 100%;
  height: auto;
}
</style>
