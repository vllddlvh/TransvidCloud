import type { components } from './schema'

export type AuthResponse = components['schemas']['AuthResponse']
export type RegisterRequest = components['schemas']['RegisterRequest']
export type LoginRequest = components['schemas']['LoginRequest']
export type CreateProjectRequest = components['schemas']['CreateProjectRequest']
export type UpdateProjectRequest = components['schemas']['UpdateProjectRequest']
export type ProjectResponse = components['schemas']['ProjectResponse']
export type ProjectList = components['schemas']['ProjectListResponse']
export type CreateApiKeyRequest = components['schemas']['CreateApiKeyRequest']
export type ApiKeyMetadataResponse = components['schemas']['ApiKeyMetadataResponse']
export type ApiKeyCreatedResponse = components['schemas']['ApiKeyCreatedResponse']
export type ApiKeyListResponse = components['schemas']['ApiKeyListResponse']
export type InitVideoRequest = components['schemas']['InitVideoRequest']
export type InitVideoResponse = components['schemas']['InitVideoResponse']
export type VideoResponse = components['schemas']['VideoResponse']
export type VideoStatus = components['schemas']['VideoStatus']
export type VideoList = components['schemas']['VideoPageResponse']
export type ErrorResponse = components['schemas']['ErrorResponse'] | { error: { code: string; message: string } }

export interface UserSummary {
  userId: string
  email: string
  name: string
  fullName?: string
  createdAt?: string
}

export interface Session {
  accessToken: string
  expiresAt: string
  user: UserSummary
}

export interface Project {
  projectId: string
  ownerId?: string
  name: string
  description: string
  webhookUrl?: string
  status?: 'ACTIVE' | 'DELETED'
  videoCount: number
  createdAt: string
  updatedAt?: string
}

export interface ApiKey {
  keyHash: string
  projectId: string
  name: string
  keyPrefix: string
  createdAt: string
}

export interface CreatedApiKey extends ApiKey {
  rawKey: string
}

export interface Video {
  videoId: string
  projectId: string
  filename: string
  title?: string
  inputKey?: string
  sizeBytes: number
  contentType: string
  status: VideoStatus
  attemptCount?: number
  createdAt: string
  updatedAt: string
  startedAt?: string
  completedAt?: string
  errorCode?: string
  durationSeconds?: number
  assets?: {
    hls: string
    hls360?: string
    hls720?: string
    thumbnail?: string
  }
}

export interface VideoPage {
  items: Video[]
  nextCursor?: string
  hasMore: boolean
}

export interface UploadTicket {
  video: Video
  uploadUrl: string
  expiresAt: string
  headers: Record<string, string>
}

export interface CreateVideoRequest {
  title?: string
  filename: string
  sizeBytes: number
  contentType: InitVideoRequest['contentType'] | string
}

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

export function normalizeUserSummary(value: unknown): UserSummary | null {
  if (!isRecord(value) || !nonempty(value.userId)) return null
  if (typeof value.email !== 'string' || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.email)) return null
  const name = nonempty(value.name)
    ? value.name.trim()
    : nonempty(value.fullName)
      ? value.user?.toString() ? value.fullName.trim() : value.fullName.trim()
      : value.email.split('@')[0]
  return {
    userId: value.userId,
    email: value.email,
    name,
    ...(nonempty(value.fullName) ? { fullName: value.fullName.trim() } : {}),
    ...(isIsoDate(value.createdAt) ? { createdAt: value.createdAt } : {}),
  }
}

export function isSession(value: unknown): value is Session {
  return isRecord(value) && nonempty(value.accessToken) && !/[^\x21-\x7e]/.test(value.accessToken)
    && isIsoDate(value.expiresAt)
    && isRecord(value.user) && nonempty(value.user.userId) && nonempty(value.user.name)
    && typeof value.user.email === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.user.email)
}
export function normalizeSession(value: unknown): Session | null {
  if (isSession(value)) return value
  if (!isRecord(value) || !nonempty(value.accessToken) || /[^\x21-\x7e]/.test(value.accessToken)) return null
  const user = normalizeUserSummary(value.user)
  if (!user) return null
  const expiresAt = isIsoDate(value.expiresAt)
    ? value.expiresAt
    : typeof value.expiresInSeconds === 'number' && Number.isFinite(value.expiresInSeconds) && value.expiresInSeconds > 0
      ? new Date(Date.now() + value.expiresInSeconds * 1000).toISOString()
      : null
  if (!expiresAt) return null
  return {
    accessToken: value.accessToken,
    expiresAt,
    user: {
      userId: user.userId,
      email: user.email,
      name: user.name,
    },
  }
}

export function isProject(value: unknown): value is Project {
  return isRecord(value) && nonempty(value.projectId) && nonempty(value.name)
    && typeof value.description === 'string' && Number.isInteger(value.videoCount)
    && (value.videoCount as number) >= 0 && isIsoDate(value.createdAt)
}
export function normalizeProject(value: unknown): Project | null {
  if (isProject(value)) return value
  if (!isRecord(value) || !nonempty(value.projectId) || !nonempty(value.name) || !isIsoDate(value.createdAt)) {
    return null
  }
  const description = typeof value.description === 'string'
    ? value.description
    : value.description === undefined || value.description === null
      ? ''
      : null
  if (description === null) return null
  const videoCount = Number.isInteger(value.videoCount) && (value.videoCount as number) >= 0
    ? (value.videoCount as number)
    : value.videoCount === undefined
      ? 0
      : -1
  if (videoCount < 0) return null
  const status = value.status === 'ACTIVE' || value.status === 'DELETED' ? value.status : undefined
  return {
    projectId: value.projectId,
    ...(nonempty(value.ownerId) ? { ownerId: value.ownerId } : {}),
    name: value.name,
    description,
    ...(nonempty(value.webhookUrl) ? { webhookUrl: value.webhookUrl } : {}),
    ...(status ? { status } : {}),
    videoCount,
    createdAt: value.createdAt,
    ...(isIsoDate(value.updatedAt) ? { updatedAt: value.updatedAt } : {}),
  }
}

export function isApiKey(value: unknown): value is ApiKey {
  return isRecord(value) && nonempty(value.keyHash) && nonempty(value.projectId)
    && nonempty(value.name) && nonempty(value.keyPrefix) && isIsoDate(value.createdAt)
}
export function normalizeApiKey(value: unknown): ApiKey | null {
  if (!isApiKey(value)) return null
  return {
    keyHash: value.keyHash,
    projectId: value.projectId,
    name: value.name,
    keyPrefix: value.keyPrefix,
    createdAt: value.createdAt,
  }
}
export function normalizeCreatedApiKey(value: unknown): CreatedApiKey | null {
  const base = normalizeApiKey(value)
  if (!base || !isRecord(value) || !nonempty(value.rawKey)) return null
  return {
    ...base,
    rawKey: value.rawKey,
  }
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
export function normalizeVideo(value: unknown): Video | null {
  if (isVideo(value)) return value
  if (!isRecord(value) || !nonempty(value.videoId) || !nonempty(value.projectId)
    || !nonempty(value.contentType)
    || !Number.isInteger(value.sizeBytes) || (value.sizeBytes as number) < 1
    || (value.sizeBytes as number) > 209715200
    || !statuses.includes(value.status as VideoStatus)
    || !isIsoDate(value.createdAt) || !isIsoDate(value.updatedAt)) return null
  const filename = nonempty(value.filename) ? value.filename : nonempty(value.title) ? value.title : null
  if (!filename) return null
  if (value.errorCode !== undefined && value.errorCode !== null && !nonempty(value.errorCode)) return null
  if (value.durationSeconds !== undefined && value.durationSeconds !== null
    && (typeof value.durationSeconds !== 'number' || !Number.isFinite(value.durationSeconds) || value.durationSeconds < 0)) {
    return null
  }
  let assets: Video['assets']
  if (value.assets !== undefined && value.assets !== null) {
    if (!isRecord(value.assets)) return null
    const hls = nonempty(value.assets.hls)
      ? value.assets.hls
      : nonempty(value.assets.masterPlaylistUrl)
        ? value.assets.masterPlaylistUrl
        : null
    if (!hls || !hls.startsWith('/media/')) return null
    const hls360 = nonempty(value.assets.hls360) ? value.assets.hls360 : nonempty(value.assets.hls360Url) ? value.assets.hls360Url : undefined
    const hls720 = nonempty(value.assets.hls720) ? value.assets.hls720 : nonempty(value.assets.hls720Url) ? value.assets.hls720Url : undefined
    const thumbnail = nonempty(value.assets.thumbnail) ? value.assets.thumbnail : nonempty(value.assets.thumbnailUrl) ? value.assets.thumbnailUrl : undefined
    for (const url of [hls360, hls720, thumbnail]) {
      if (url !== undefined && !url.startsWith('/media/')) return null
    }
    assets = {
      hls,
      ...(hls360 ? { hls360 } : {}),
      ...(hls720 ? { hls720 } : {}),
      ...(thumbnail ? { thumbnail } : {}),
    }
  }
  return {
    videoId: value.videoId,
    projectId: value.projectId,
    filename,
    ...(nonempty(value.title) ? { title: value.title } : {}),
    ...(nonempty(value.inputKey) ? { inputKey: value.inputKey } : {}),
    sizeBytes: value.sizeBytes as number,
    contentType: value.contentType,
    status: value.status as VideoStatus,
    ...(typeof value.attemptCount === 'number' ? { attemptCount: value.attemptCount } : {}),
    createdAt: value.createdAt,
    updatedAt: value.updatedAt,
    ...(isIsoDate(value.startedAt) ? { startedAt: value.startedAt } : {}),
    ...(isIsoDate(value.completedAt) ? { completedAt: value.completedAt } : {}),
    ...(nonempty(value.errorCode) ? { errorCode: value.errorCode } : {}),
    ...(typeof value.durationSeconds === 'number' ? { durationSeconds: value.durationSeconds } : {}),
    ...(assets ? { assets } : {}),
  }
}

export function isUploadTicket(value: unknown): value is UploadTicket {
  return isRecord(value) && isVideo(value.video) && nonempty(value.uploadUrl)
    && isIsoDate(value.expiresAt) && isRecord(value.headers)
    && Object.values(value.headers).every((header) => typeof header === 'string')
}
export function normalizeUploadTicket(value: unknown): UploadTicket | null {
  if (isUploadTicket(value)) return value
  if (!isRecord(value)) return null
  const video = normalizeVideo(value.video)
  if (!video) return null
  if (isRecord(value.upload)) {
    const upload = value.upload
    if (!nonempty(upload.presignedUrl) || !isRecord(upload.requiredHeaders)) return null
    if (!Object.values(upload.requiredHeaders).every((header) => typeof header === 'string')) return null
    const expiresAt = isIsoDate(upload.expiresAt)
      ? upload.expiresAt
      : typeof upload.expiresInSeconds === 'number' && Number.isFinite(upload.expiresInSeconds) && upload.expiresInSeconds > 0
        ? new Date(Date.now() + upload.expiresInSeconds * 1000).toISOString()
        : null
    if (!expiresAt) return null
    return {
      video,
      uploadUrl: upload.presignedUrl,
      expiresAt,
      headers: upload.requiredHeaders as Record<string, string>,
    }
  }
  return null
}
