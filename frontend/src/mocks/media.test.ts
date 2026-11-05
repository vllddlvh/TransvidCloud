// @vitest-environment node
import { afterAll, afterEach, beforeAll, beforeEach, expect, it, vi } from 'vitest'
import { setupServer } from 'msw/node'
import { createHandlers } from './handlers'
import { mediaResponse } from './media'

const origin = 'http://localhost:5173'
const root = '/media/demo-project/demo-completed'
const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => {
  const storage = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
  })
  server.resetHandlers(...createHandlers())
})
afterEach(() => vi.unstubAllGlobals())
afterAll(() => server.close())

function requiredMedia(path: string): Response {
  const response = mediaResponse(path)
  expect(response, path).toBeDefined()
  if (!response) throw new Error(`Missing media response for ${path}`)
  return response
}

it.each([
  [`${root}/hls/master.m3u8`, 'application/vnd.apple.mpegurl', '#EXTM3U'],
  [`${root}/hls/360p/index.m3u8`, 'application/vnd.apple.mpegurl', '#EXT-X-TARGETDURATION:2'],
  [`${root}/thumb/default.jpg`, 'image/jpeg', null],
])('serves %s', async (path, contentType, textMarker) => {
  const response = mediaResponse(path)
  expect(response?.headers.get('Content-Type')).toContain(contentType)
  if (textMarker) expect(await response?.text()).toContain(textMarker)
})

it('names both renditions in the master playlist', async () => {
  const master = await requiredMedia(`${root}/hls/master.m3u8`).text()
  expect(master).toContain('360p/index.m3u8')
  expect(master).toContain('720p/index.m3u8')
})

it.each([
  ['360p', 'segment-360p-'],
  ['720p', 'segment-720p-'],
] as const)('names five two-second segments in the %s variant', async (rendition, prefix) => {
  const response = requiredMedia(`${root}/hls/${rendition}/index.m3u8`)
  expect(response.headers.get('Content-Type')).toContain('application/vnd.apple.mpegurl')
  const playlist = await response.text()
  expect(playlist).toContain('#EXT-X-TARGETDURATION:2')
  expect(playlist.match(/#EXTINF:2\.000000,/g)).toHaveLength(5)
  expect(playlist.split('\n').filter((line) => line.endsWith('.ts'))).toEqual([
    `${prefix}000.ts`,
    `${prefix}001.ts`,
    `${prefix}002.ts`,
    `${prefix}003.ts`,
    `${prefix}004.ts`,
  ])
})

it.each(['360p', '720p'] as const)('serves all non-empty %s MPEG transport segments', async (rendition) => {
  for (let index = 0; index < 5; index += 1) {
    const path = `${root}/hls/${rendition}/segment-${rendition}-${String(index).padStart(3, '0')}.ts`
    const response = requiredMedia(path)
    expect(response.headers.get('Content-Type')).toContain('video/mp2t')
    const bytes = new Uint8Array(await response.arrayBuffer())
    expect(bytes.length).toBeGreaterThan(0)
    expect(bytes[0]).toBe(0x47)
  }
})

it('serves real JPEG bytes', async () => {
  const bytes = new Uint8Array(await requiredMedia(`${root}/thumb/default.jpg`).arrayBuffer())
  expect(bytes.length).toBeGreaterThan(2)
  expect([...bytes.slice(0, 2)]).toEqual([0xff, 0xd8])
})

it('returns independently consumable responses', async () => {
  const first = requiredMedia(`${root}/hls/master.m3u8`)
  const second = requiredMedia(`${root}/hls/master.m3u8`)
  expect(first).not.toBe(second)
  expect(await first.text()).toBe(await second.text())
})

it.each([
  '/media/demo-project/demo-completed/hls/unknown.m3u8',
  '/media/demo-project/demo-completed/hls/360p/unknown.ts',
  '/media/demo-project/demo-completed/hls/../master.m3u8',
  '/media/demo-project/demo-completed/hls/360p/..%2F..%2Fmaster.m3u8',
  '/media/demo-project/demo-completed/thumb/other.jpg',
  '/etc/passwd',
])('rejects unknown or traversal path %s', (path) => {
  expect(mediaResponse(path)).toBeUndefined()
})

it('serves a known segment through MSW using only the URL pathname', async () => {
  const response = await fetch(`${origin}${root}/hls/360p/segment-360p-000.ts?ignored=true`)
  expect(response.status).toBe(200)
  expect(response.headers.get('Content-Type')).toContain('video/mp2t')
  expect((await response.arrayBuffer()).byteLength).toBeGreaterThan(0)
})

it('returns 404 for unsupported media paths through MSW', async () => {
  const response = await fetch(`${origin}${root}/hls/unknown.m3u8`)
  expect(response.status).toBe(404)
})
