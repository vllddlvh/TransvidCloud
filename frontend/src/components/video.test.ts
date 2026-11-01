import { describe, expect, it } from 'vitest'
import type { Video } from '../api/types'
import { failureGuidance, formatDuration, hasActiveVideo, isVideoActive } from './video'

function video(status: Video['status'], errorCode?: string): Video {
  return {
    videoId: 'video-1', projectId: 'project-1', filename: 'clip.mp4',
    sizeBytes: 1024, contentType: 'video/mp4', status,
    createdAt: '2026-01-12T09:00:00.000Z', updatedAt: '2026-01-12T09:00:00.000Z',
    ...(errorCode ? { errorCode } : {}),
  }
}

describe('video helpers', () => {
  it('identifies only pending and processing videos as active', () => {
    expect(isVideoActive(video('PENDING'))).toBe(true)
    expect(isVideoActive(video('PROCESSING'))).toBe(true)
    for (const status of ['UPLOADING', 'COMPLETED', 'FAILED_VALIDATION', 'FAILED_TRANSCODE', 'FAILED_STORAGE'] as const) {
      expect(isVideoActive(video(status))).toBe(false)
    }
  })

  it('polling the list when any video is active', () => {
    expect(hasActiveVideo([video('COMPLETED'), video('PROCESSING')])).toBe(true)
    expect(hasActiveVideo([video('COMPLETED'), video('FAILED_TRANSCODE')])).toBe(false)
    expect(hasActiveVideo([])).toBe(false)
  })

  it('formats missing, zero, and minute durations', () => {
    expect(formatDuration(undefined)).toBe('—')
    expect(formatDuration(0)).toBe('0:00')
    expect(formatDuration(65)).toBe('1:05')
    expect(formatDuration(600)).toBe('10:00')
  })

  it('maps known validation errors to actionable guidance', () => {
    expect(failureGuidance(video('FAILED_VALIDATION', 'DURATION_EXCEEDED'))).toContain('5-minute')
    expect(failureGuidance(video('FAILED_VALIDATION', 'HEIGHT_EXCEEDED'))).toContain('1080p')
    expect(failureGuidance(video('FAILED_VALIDATION', 'CODEC_UNSUPPORTED'))).toContain('codec')
    expect(failureGuidance(video('FAILED_VALIDATION', 'NOT_A_VIDEO'))).toContain('valid video')
  })

  it('explains automatic retry for transcode and storage failures', () => {
    expect(failureGuidance(video('FAILED_TRANSCODE', 'TRANSCODE_FAILED'))).toContain('retry processing automatically')
    expect(failureGuidance(video('FAILED_STORAGE', 'STORAGE_WRITE_FAILED'))).toContain('retry processing automatically')
  })

  it('falls back to stable guidance for unknown errors', () => {
    expect(failureGuidance(video('FAILED_TRANSCODE'))).toContain('upload the video again')
    expect(failureGuidance(video('COMPLETED'))).toBe('')
  })
})
