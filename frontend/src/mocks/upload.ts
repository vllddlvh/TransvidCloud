import { putFile, type UploadRequest } from '../api/transport'

export async function mockPutFile(request: UploadRequest): Promise<void> {
  let percent = 0
  request.onProgress?.(0)
  // Service Worker interception cannot expose real upload-byte progress.
  const timer = window.setInterval(() => {
    percent = Math.min(90, percent + 8)
    request.onProgress?.(percent)
  }, 100)
  try {
    await putFile({ ...request, onProgress: undefined })
    request.onProgress?.(100)
  } finally {
    window.clearInterval(timer)
  }
}
