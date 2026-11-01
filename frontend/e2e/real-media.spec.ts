import { expect, test } from '@playwright/test'
import { join } from 'node:path'

type HlsEventData = {
  levels: { height: number; bitrate: number }[]
  fatal: boolean
  details: string
}

type HlsHandle = {
  loadSource: (source: string) => void
  attachMedia: (media: HTMLMediaElement) => void
  destroy: () => void
  on: (event: string, handler: (event: string, data: HlsEventData) => void) => void
}

type HlsConstructor = {
  new (): HlsHandle
  Events: { MANIFEST_PARSED: string; ERROR: string }
}

const mediaPath = process.env.E2E_REAL_MEDIA_PATH

test.skip(!mediaPath, 'Set E2E_REAL_MEDIA_PATH and E2E_BASE_URL to a served nginx origin')

test('plays worker-generated HLS with the expected renditions and storage content types', async ({ page }) => {
  await page.goto('/login')
  const origin = new URL(page.url()).origin
  const mediaUrl = new URL(mediaPath!, origin).href

  await page.addScriptTag({ path: join(process.cwd(), 'node_modules/hls.js/dist/hls.min.js') })
  const levels = await page.evaluate((url) => new Promise<{ height: number; bitrate: number }[]>((resolve, reject) => {
    const HlsCtor = (window as unknown as { Hls: HlsConstructor }).Hls
    const video = document.createElement('video')
    video.muted = true
    document.body.append(video)
    const hls = new HlsCtor()
    const timer = window.setTimeout(() => reject(new Error('manifest timeout')), 20000)
    hls.on(HlsCtor.Events.MANIFEST_PARSED, (_event, data) => {
      window.clearTimeout(timer)
      resolve(data.levels.map((level) => ({ height: level.height, bitrate: level.bitrate })))
      hls.destroy()
    })
    hls.on(HlsCtor.Events.ERROR, (_event, data) => {
      if (data.fatal) {
        window.clearTimeout(timer)
        reject(new Error(data.details))
      }
    })
    hls.loadSource(url)
    hls.attachMedia(video)
  }), mediaUrl)

  expect(levels.map((level) => level.height).sort((a, b) => a - b)).toEqual([360, 720])

  const master = await page.request.get(mediaUrl)
  expect(master.headers()['content-type']).toContain('application/vnd.apple.mpegurl')
  const playlist = await master.text()
  expect(playlist).toContain('360p/index.m3u8')
  expect(playlist).toContain('720p/index.m3u8')

  const renditionUrl = new URL('360p/index.m3u8', mediaUrl).href
  const rendition = await (await page.request.get(renditionUrl)).text()
  const segment = rendition.split('\n').find((line) => line.trim().endsWith('.ts'))
  expect(segment).toBeTruthy()
  const segmentResponse = await page.request.get(new URL(segment!.trim(), renditionUrl).href)
  expect(segmentResponse.headers()['content-type']).toContain('video/mp2t')

  const thumbUrl = new URL('../thumb/default.jpg', mediaUrl).href
  const thumb = await page.request.get(thumbUrl)
  expect(thumb.headers()['content-type']).toContain('image/jpeg')
})
