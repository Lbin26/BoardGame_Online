import { describe, it, expect } from 'vitest'

import {
  ROWS,
  COLS,
  CELL,
  PADDING,
  BOARD_W,
  BOARD_H,
  cellToPixel,
  pixelToCell,
  getPieceAt,
} from '@/utils/board'

describe('board constants', () => {
  it('exposes the standard 10x9 board geometry', () => {
    expect(ROWS).toBe(10)
    expect(COLS).toBe(9)
    expect(CELL).toBe(56)
    expect(PADDING).toBe(28)
    expect(BOARD_W).toBe(504)
    expect(BOARD_H).toBe(560)
  })
})

describe('cellToPixel', () => {
  it('maps the top-left intersection to the padding offset', () => {
    expect(cellToPixel(0, 0)).toEqual({ x: 28, y: 28 })
  })
})

describe('pixelToCell', () => {
  it('maps an exact intersection back to its cell', () => {
    expect(pixelToCell(28, 28)).toEqual({ row: 0, col: 0 })
  })

  it('returns null for points outside the board', () => {
    expect(pixelToCell(0, 0)).toBeNull()
  })
})

describe('getPieceAt', () => {
  it('finds the piece occupying the given cell', () => {
    expect(getPieceAt([{ row: 0, col: 0 }], 0, 0)).toEqual({ row: 0, col: 0 })
  })

  it('returns undefined when the cell is empty', () => {
    expect(getPieceAt([], 1, 1)).toBeUndefined()
  })
})
