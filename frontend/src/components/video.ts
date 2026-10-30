import type { Video } from '../api/types'

export function isVideoActive(video: Video): boolean {
  return video.status === 'PENDING' || video.status === 'PROCESSING'
}

export function hasActiveVideo(videos: Video[]): boolean {
  return videos.some(isVideoActive)
}

export function formatDuration(seconds?: number): string {
  if (seconds === undefined) return '—'
  const wholeSeconds = Math.floor(seconds)
  return `${Math.floor(wholeSeconds / 60)}:${String(wholeSeconds % 60).padStart(2, '0')}`
}

const errorGuidance: Record<string, string> = {
  DURATION_EXCEEDED: 'This video exceeds the 5-minute limit. Upload a shorter clip.',
  HEIGHT_EXCEEDED: 'This video exceeds the 1080p resolution limit. Upload a video up to 1080p.',
  CODEC_UNSUPPORTED: 'This video uses an unsupported codec. Re-encode it with a common codec and upload it again.',
  NOT_A_VIDEO: 'This file is not a valid video. Choose a playable video file.',
}

export function failureGuidance(video: Video): string {
  if (!video.status.startsWith('FAILED_')) return ''
  if (video.errorCode && errorGuidance[video.errorCode]) return errorGuidance[video.errorCode]
  if (video.status === 'FAILED_TRANSCODE' || video.status === 'FAILED_STORAGE') {
    return 'Processing failed. The system will retry processing automatically. If it keeps failing, upload the video again.'
  }
  return 'Processing failed. Try uploading the file again.'
}
