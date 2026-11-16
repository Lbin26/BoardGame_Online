/** 棋盘行数（10 行，0-9） */
export const ROWS = 10

/** 棋盘列数（9 列，0-8） */
export const COLS = 9

/** 相邻交叉点之间的像素间距 */
export const CELL = 56

/** 棋盘四周的像素留白 */
export const PADDING = 28

/** 棋盘画布宽度（像素） */
export const BOARD_W = PADDING * 2 + CELL * (COLS - 1)

/** 棋盘画布高度（像素） */
export const BOARD_H = PADDING * 2 + CELL * (ROWS - 1)

/** 棋盘上的一个棋子，至少包含所在的行列坐标 */
export interface Piece {
  row: number
  col: number
  [key: string]: unknown
}

/** 棋盘交叉点坐标 */
export interface Cell {
  row: number
  col: number
}

/** 像素坐标 */
export interface Pixel {
  x: number
  y: number
}

/**
 * 将棋盘行列坐标转换为画布上的像素坐标。
 *
 * @param row 行号，0 为最上方
 * @param col 列号，0 为最左侧
 */
export function cellToPixel(row: number, col: number): Pixel {
  return { x: PADDING + col * CELL, y: PADDING + row * CELL }
}

/**
 * 将画布像素坐标转换为最近的棋盘交叉点。
 * 超出棋盘范围，或与最近交叉点的距离超过 `CELL / 2` 时返回 `null`。
 *
 * @param x 画布横坐标
 * @param y 画布纵坐标
 */
export function pixelToCell(x: number, y: number): Cell | null {
  const col = Math.round((x - PADDING) / CELL)
  const row = Math.round((y - PADDING) / CELL)
  if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return null
  const { x: cx, y: cy } = cellToPixel(row, col)
  if (Math.hypot(x - cx, y - cy) > CELL / 2) return null
  return { row, col }
}

/**
 * 在棋子列表中查找占据指定交叉点的棋子。
 *
 * @param pieces 当前棋盘上的棋子列表
 * @param row 行号
 * @param col 列号
 * @returns 命中的棋子；该点为空时返回 `undefined`
 */
export function getPieceAt(
  pieces: Piece[],
  row: number,
  col: number,
): Piece | undefined {
  return pieces.find((p) => p.row === row && p.col === col)
}
