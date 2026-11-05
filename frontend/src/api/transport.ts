export interface UploadRequest {
  url: string
  headers: Record<string, string>
  file: File
  signal?: AbortSignal
  onProgress?: (percent: number) => void
}

export class UploadError extends Error {
  constructor(message: string, public readonly status = 0) {
    super(message)
    this.name = 'UploadError'
  }
}

function storageError(xhr: XMLHttpRequest): UploadError {
  const body = typeof xhr.responseText === 'string' ? xhr.responseText : ''
  const code = /<Code>([^<]+)<\/Code>/.exec(body)?.[1]?.trim()
  if (xhr.status === 403) {
    if (code === 'SignatureDoesNotMatch' || code === 'InvalidAccessKeyId') {
      return new UploadError('The upload link is not valid for this file. Retry to request a new link.', xhr.status)
    }
    return new UploadError('The upload link expired or was rejected. Retry to request a new link.', xhr.status)
  }
  if (code === 'EntityTooLarge') {
    return new UploadError('This file is larger than storage accepts.', xhr.status)
  }
  return new UploadError('The storage upload failed. Please try again.', xhr.status)
}

export function putFile({ url, headers, file, signal, onProgress }: UploadRequest): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Upload cancelled', 'AbortError'))
      return
    }
    const xhr = new XMLHttpRequest()
    const abort = () => xhr.abort()
    let idle = 0
    let stalled = false
    const stop = () => { window.clearTimeout(idle); signal?.removeEventListener('abort', abort) }
    const fail = (error: Error) => { stop(); reject(error) }
    // A permitted 200 MB PUT needs longer than any total-transfer cap on a slow link,
    // so only a connection that stops sending bytes is treated as timed out.
    const restartIdle = () => {
      window.clearTimeout(idle)
      idle = window.setTimeout(() => { stalled = true; xhr.abort() }, 120_000)
    }
    xhr.open('PUT', url, true)
    Object.entries(headers).forEach(([name, value]) => {
      // Content-Length is browser-controlled even when included in signed headers.
      if (name.toLowerCase() !== 'content-length') xhr.setRequestHeader(name, value)
    })
    xhr.upload.addEventListener('progress', (event) => {
      restartIdle()
      if (event.lengthComputable) onProgress?.(Math.round(event.loaded / event.total * 100))
    })
    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        stop()
        onProgress?.(100)
        resolve()
      } else {
        fail(storageError(xhr))
      }
    })
    xhr.addEventListener('error', () => fail(new UploadError('Upload interrupted. Check your connection and try again.')))
    xhr.addEventListener('abort', () => fail(stalled
      ? new UploadError('Upload timed out. Check your connection and try again.')
      : new DOMException('Upload cancelled', 'AbortError')))
    signal?.addEventListener('abort', abort, { once: true })
    restartIdle()
    xhr.send(file)
  })
}
