import { describe, expect, it } from 'vitest'
import { validateVideoFile, videoContentType } from './upload'

const file = (name: string, size: number, type = '') => ({ name, size, type }) as File

describe('upload validation', () => {
  it.each(['mp4', 'mov', 'mkv', 'webm', 'MP4'])('accepts a %s file exactly at the 200 MiB limit', (extension) => {
    expect(validateVideoFile(file(`clip.${extension}`, 209715200))).toBeNull()
  })
  it('rejects one byte over the limit before allocating an upload', () => {
    expect(validateVideoFile(file('clip.mp4', 209715201))).toMatch(/200 MB/)
  })
  it.each(['clip.exe', 'clip.mp4.exe', 'clip', '.mp4'])('rejects unsupported file %s', (name) => {
    expect(validateVideoFile(file(name, 10))).toMatch(/MP4, MOV, MKV, or WEBM/)
  })
  it('rejects an empty file', () => {
    expect(validateVideoFile(file('clip.mp4', 0))).toMatch(/empty/)
  })
  it.each([
    ['clip.mp4', 'video/mp4'], ['clip.mov', 'video/quicktime'],
    ['clip.mkv', 'video/x-matroska'], ['clip.webm', 'video/webm'],
  ])('normalizes MIME for %s when the browser cannot infer it', (name, type) => {
    expect(videoContentType(file(name, 1))).toBe(type)
  })
})
