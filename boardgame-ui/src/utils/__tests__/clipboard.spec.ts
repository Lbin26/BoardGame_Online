import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { copyText } from '@/utils/clipboard'

describe('copyText', () => {
  let execCommand: ReturnType<typeof vi.fn>

  beforeEach(() => {
    execCommand = vi.fn()
    ;(document as any).execCommand = execCommand
  })

  afterEach(() => {
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true })
    delete (document as any).execCommand
    vi.restoreAllMocks()
  })

  function stubClipboard(writeText: ReturnType<typeof vi.fn>) {
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
  }

  it('uses navigator.clipboard when available', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    stubClipboard(writeText)
    expect(await copyText('ABC123')).toBe(true)
    expect(writeText).toHaveBeenCalledWith('ABC123')
    expect(execCommand).not.toHaveBeenCalled()
  })

  it('falls back to execCommand when clipboard API missing (http LAN)', async () => {
    execCommand.mockReturnValue(true)
    expect(await copyText('ABC123')).toBe(true)
    expect(execCommand).toHaveBeenCalledWith('copy')
    // 临时 textarea 必须清理
    expect(document.querySelector('textarea')).toBeNull()
  })

  it('falls back when clipboard.writeText rejects', async () => {
    stubClipboard(vi.fn().mockRejectedValue(new Error('denied')))
    execCommand.mockReturnValue(true)
    expect(await copyText('ABC123')).toBe(true)
    expect(execCommand).toHaveBeenCalledWith('copy')
  })

  it('returns false and cleans up when both paths fail', async () => {
    execCommand.mockReturnValue(false)
    expect(await copyText('ABC123')).toBe(false)
    expect(document.querySelector('textarea')).toBeNull()
  })
})
