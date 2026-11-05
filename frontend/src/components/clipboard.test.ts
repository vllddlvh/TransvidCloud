import { beforeEach, describe, expect, it, vi } from 'vitest'
import { copyText } from './clipboard'

function stubClipboard(value: Clipboard | undefined) {
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value })
}

beforeEach(() => {
  stubClipboard(undefined)
  Object.defineProperty(document, 'execCommand', { configurable: true, value: vi.fn(() => true) })
})

describe('copyText', () => {
  it('prefers the Clipboard API', async () => {
    const writeText = vi.fn<(value: string) => Promise<void>>().mockResolvedValue(undefined)
    stubClipboard({ writeText } as unknown as Clipboard)
    const select = vi.spyOn(HTMLTextAreaElement.prototype, 'select')
    await copyText('https://example.test/media/master.m3u8')
    expect(writeText).toHaveBeenCalledWith('https://example.test/media/master.m3u8')
    expect(select).not.toHaveBeenCalled()
    expect(document.querySelector('textarea')).toBeNull()
  })

  it('falls back to a temporary readonly textarea and execCommand', async () => {
    const select = vi.spyOn(HTMLTextAreaElement.prototype, 'select')
    await copyText('<iframe src="embed"></iframe>')
    const execCommand = document.execCommand as unknown as ReturnType<typeof vi.fn>
    expect(execCommand).toHaveBeenCalledWith('copy')
    expect(select).toHaveBeenCalledTimes(1)
    expect(document.querySelector('textarea')).toBeNull()
  })

  it('falls back when the Clipboard API rejects', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('denied'))
    stubClipboard({ writeText } as unknown as Clipboard)
    await copyText('fallback text')
    expect(document.execCommand).toHaveBeenCalledWith('copy')
  })

  it('rejects and cleans up when both paths fail', async () => {
    Object.defineProperty(document, 'execCommand', { configurable: true, value: vi.fn(() => false) })
    await expect(copyText('unavailable')).rejects.toThrow('Copy failed.')
    expect(document.querySelector('textarea')).toBeNull()
  })

  it('rejects when the legacy command throws, still removing the textarea', async () => {
    Object.defineProperty(document, 'execCommand', {
      configurable: true,
      value: vi.fn(() => { throw new Error('blocked') }),
    })
    await expect(copyText('blocked')).rejects.toThrow('blocked')
    expect(document.querySelector('textarea')).toBeNull()
  })
})
