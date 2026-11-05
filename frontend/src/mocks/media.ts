import masterPlaylist from './media/master.m3u8?raw'
import playlist360 from './media/360p.m3u8?raw'
import playlist720 from './media/720p.m3u8?raw'
import thumbnail from './media/default.jpg?inline'
import segment360000 from './media/segment-360p-000.ts?inline'
import segment360001 from './media/segment-360p-001.ts?inline'
import segment360002 from './media/segment-360p-002.ts?inline'
import segment360003 from './media/segment-360p-003.ts?inline'
import segment360004 from './media/segment-360p-004.ts?inline'
import segment720000 from './media/segment-720p-000.ts?inline'
import segment720001 from './media/segment-720p-001.ts?inline'
import segment720002 from './media/segment-720p-002.ts?inline'
import segment720003 from './media/segment-720p-003.ts?inline'
import segment720004 from './media/segment-720p-004.ts?inline'

type MediaFixture = {
  body: string | Uint8Array<ArrayBuffer>
  contentType: 'application/vnd.apple.mpegurl' | 'video/mp2t' | 'image/jpeg'
}

function decodeDataUri(dataUri: string): Uint8Array<ArrayBuffer> {
  const binary = atob(dataUri.slice(dataUri.indexOf(',') + 1))
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index)
  return bytes
}

const fixtures = new Map<string, MediaFixture>([
  ['hls/master.m3u8', { body: masterPlaylist, contentType: 'application/vnd.apple.mpegurl' }],
  ['hls/360p/index.m3u8', { body: playlist360, contentType: 'application/vnd.apple.mpegurl' }],
  ['hls/720p/index.m3u8', { body: playlist720, contentType: 'application/vnd.apple.mpegurl' }],
  ['hls/360p/segment-360p-000.ts', { body: decodeDataUri(segment360000), contentType: 'video/mp2t' }],
  ['hls/360p/segment-360p-001.ts', { body: decodeDataUri(segment360001), contentType: 'video/mp2t' }],
  ['hls/360p/segment-360p-002.ts', { body: decodeDataUri(segment360002), contentType: 'video/mp2t' }],
  ['hls/360p/segment-360p-003.ts', { body: decodeDataUri(segment360003), contentType: 'video/mp2t' }],
  ['hls/360p/segment-360p-004.ts', { body: decodeDataUri(segment360004), contentType: 'video/mp2t' }],
  ['hls/720p/segment-720p-000.ts', { body: decodeDataUri(segment720000), contentType: 'video/mp2t' }],
  ['hls/720p/segment-720p-001.ts', { body: decodeDataUri(segment720001), contentType: 'video/mp2t' }],
  ['hls/720p/segment-720p-002.ts', { body: decodeDataUri(segment720002), contentType: 'video/mp2t' }],
  ['hls/720p/segment-720p-003.ts', { body: decodeDataUri(segment720003), contentType: 'video/mp2t' }],
  ['hls/720p/segment-720p-004.ts', { body: decodeDataUri(segment720004), contentType: 'video/mp2t' }],
  ['thumb/default.jpg', { body: decodeDataUri(thumbnail), contentType: 'image/jpeg' }],
])

const mediaPath = /^\/media\/[A-Za-z0-9_-]+\/[A-Za-z0-9_-]+\/(.+)$/

export function mediaResponse(pathname: string): Response | undefined {
  const match = mediaPath.exec(pathname)
  if (!match) return undefined
  const fixture = fixtures.get(match[1])
  if (!fixture) return undefined
  return new Response(fixture.body, { headers: { 'Content-Type': fixture.contentType } })
}
