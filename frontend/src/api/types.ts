import type { components } from './schema'

export type Session = components['schemas']['Session']
export type Project = components['schemas']['Project']
export type Video = components['schemas']['Video']
export type VideoStatus = components['schemas']['VideoStatus']
export type UploadTicket = components['schemas']['UploadTicket']
export type LoginRequest = components['schemas']['LoginRequest']
export type CreateProjectRequest = components['schemas']['CreateProjectRequest']
export type CreateVideoRequest = components['schemas']['CreateVideoRequest']
export type ErrorResponse = components['schemas']['ErrorResponse']
export type ProjectList = components['schemas']['ProjectList']
export type VideoList = components['schemas']['VideoList']

export function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
function nonempty(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}
export function isIsoDate(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?(?:Z|[+-]\d\d:\d\d)$/.test(value)
    && Number.isFinite(Date.parse(value))
}
export function isSession(value: unknown): value is Session {
  return isRecord(value) && nonempty(value.accessToken) && !/[^\x21-\x7e]/.test(value.accessToken)
    && isIsoDate(value.expiresAt)
    && isRecord(value.user) && nonempty(value.user.userId) && nonempty(value.user.name)
    && typeof value.user.email === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.user.email)
}
export function isProject(value: unknown): value is Project {
  return isRecord(value) && nonempty(value.projectId) && nonempty(value.name)
    && typeof value.description === 'string' && Number.isInteger(value.videoCount)
    && (value.videoCount as number) >= 0 && isIsoDate(value.createdAt)
}
const statuses: readonly VideoStatus[] = [
  'UPLOADING', 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED_VALIDATION', 'FAILED_TRANSCODE', 'FAILED_STORAGE',
]
export function isVideo(value: unknown): value is Video {
  if (!isRecord(value) || !nonempty(value.videoId) || !nonempty(value.projectId)
    || !nonempty(value.filename) || !nonempty(value.contentType)
    || !Number.isInteger(value.sizeBytes) || (value.sizeBytes as number) < 1
    || (value.sizeBytes as number) > 209715200
    || !statuses.includes(value.status as VideoStatus)
    || !isIsoDate(value.createdAt) || !isIsoDate(value.updatedAt)) return false
  if (value.errorCode !== undefined && !nonempty(value.errorCode)) return false
  if (value.durationSeconds !== undefined && (typeof value.durationSeconds !== 'number'
    || !Number.isFinite(value.durationSeconds) || value.durationSeconds < 0)) return false
  if (value.assets !== undefined) {
    if (!isRecord(value.assets) || !nonempty(value.assets.hls)) return false
    for (const asset of Object.values(value.assets)) {
      if (typeof asset !== 'string' || !asset.startsWith('/media/')) return false
    }
  }
  return true
}
export function isUploadTicket(value: unknown): value is UploadTicket {
  return isRecord(value) && isVideo(value.video) && nonempty(value.uploadUrl)
    && isIsoDate(value.expiresAt) && isRecord(value.headers)
    && Object.values(value.headers).every((header) => typeof header === 'string')
}
