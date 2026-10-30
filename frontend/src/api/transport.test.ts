import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { putFile } from './transport'

class TestXHR extends EventTarget {
  static last: TestXHR
  upload = new EventTarget()
  status = 0
  timeout = 0
  open = vi.fn()
  setRequestHeader = vi.fn()
  send = vi.fn()
  abort = vi.fn(() => this.dispatchEvent(new Event('abort')))
  constructor() { super(); TestXHR.last = this }
}

const file = new File(['video'], 'clip.mp4', { type: 'video/mp4' })
const options = { url: '/signed-put', headers: { 'Content-Type': 'video/mp4' }, file }

beforeEach(() => vi.stubGlobal('XMLHttpRequest', TestXHR))
afterEach(() => vi.unstubAllGlobals())

describe('presigned PUT transport', () => {
  it('uploads directly with signed headers and reports browser progress', async () => {
    const onProgress = vi.fn()
    const pending = putFile({ ...options, onProgress })
    const xhr = TestXHR.last
    expect(xhr.open).toHaveBeenCalledWith('PUT', '/signed-put', true)
    expect(xhr.setRequestHeader).toHaveBeenCalledExactlyOnceWith('Content-Type', 'video/mp4')
    expect(xhr.send).toHaveBeenCalledWith(file)
    xhr.upload.dispatchEvent(new ProgressEvent('progress', { lengthComputable: true, loaded: 2, total: 5 }))
    expect(onProgress).toHaveBeenCalledWith(40)
    xhr.status = 200
    xhr.dispatchEvent(new Event('load'))
    await expect(pending).resolves.toBeUndefined()
    expect(onProgress).toHaveBeenLastCalledWith(100)
  })
  it.each([403, 500])('rejects storage HTTP %s instead of confirming', async (status) => {
    const pending = putFile(options)
    TestXHR.last.status = status
    TestXHR.last.dispatchEvent(new Event('load'))
    await expect(pending).rejects.toThrow(status === 403 ? /expired|rejected/i : /upload/i)
  })
  it('makes a network error retryable', async () => {
    const pending = putFile(options)
    TestXHR.last.dispatchEvent(new Event('error'))
    await expect(pending).rejects.toThrow(/connection/i)
  })
  it('times out a transfer that stops making progress', async () => {
    vi.useFakeTimers()
    try {
      const pending = putFile(options)
      TestXHR.last.upload.dispatchEvent(new ProgressEvent('progress', { lengthComputable: true, loaded: 1, total: 4 }))
      const rejection = expect(pending).rejects.toThrow(/timed out/i)
      await vi.advanceTimersByTimeAsync(120_001)
      await rejection
      expect(TestXHR.last.abort).toHaveBeenCalledOnce()
    } finally {
      vi.useRealTimers()
    }
  })
  it('lets a slow transfer finish once its whole body has left the browser', async () => {
    vi.useFakeTimers()
    try {
      const pending = putFile(options)
      const xhr = TestXHR.last
      for (const loaded of [1, 2, 3, 4]) {
        await vi.advanceTimersByTimeAsync(60_000)
        xhr.upload.dispatchEvent(new ProgressEvent('progress', { lengthComputable: true, loaded, total: 4 }))
      }
      xhr.status = 200
      xhr.dispatchEvent(new Event('load'))
      await expect(pending).resolves.toBeUndefined()
    } finally {
      vi.useRealTimers()
    }
  })
  it('aborts an active request when the modal is cancelled', async () => {
    const controller = new AbortController()
    const pending = putFile({ ...options, signal: controller.signal })
    controller.abort()
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
    expect(TestXHR.last.abort).toHaveBeenCalledOnce()
  })
})
