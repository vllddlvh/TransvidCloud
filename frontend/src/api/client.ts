import { clearSession, readSession } from './session'
import {
  isRecord, isSession, isProject, isVideo, isUploadTicket,
  type Session, type Project, type Video, type UploadTicket,
  type LoginRequest, type CreateProjectRequest, type CreateVideoRequest,
} from './types'

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string, message = code) {
    super(message)
    this.name = 'ApiError'
  }
}

type Guard<T> = (value: unknown) => value is T
async function request<T>(path: string, guard: Guard<T>, options: { method?: 'GET' | 'POST'; body?: unknown; public?: boolean } = {}): Promise<T> {
  const headers = new Headers({ Accept: 'application/json' })
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')
  // Login must not even read a stale stored token.
  if (!options.public) {
    const session = readSession()
    if (session) headers.set('Authorization', `Bearer ${session.accessToken}`)
  }
  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method: options.method ?? 'GET', headers,
      ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}),
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Unable to connect. Check your connection and try again.')
  }
  if (response.status === 401 && !options.public) {
    clearSession()
    if (typeof window !== 'undefined') window.dispatchEvent(new Event('tvc:session-expired'))
  }
  let body: unknown
  try { body = await response.json() } catch { body = undefined }
  if (!response.ok) {
    const fallback = response.status === 401 ? 'UNAUTHORIZED' : response.status >= 500 ? 'SERVER_ERROR' : 'HTTP_ERROR'
    const error = isRecord(body) && isRecord(body.error) ? body.error : undefined
    throw new ApiError(response.status,
      typeof error?.code === 'string' && error.code ? error.code : fallback,
      typeof error?.message === 'string' && error.message ? error.message : `Request failed (${response.status}).`,
    )
  }
  if (!guard(body)) throw new ApiError(response.status, 'INVALID_RESPONSE', 'The server returned an unexpected response.')
  return body
}

async function listAll<T>(path: string, isItem: Guard<T>): Promise<T[]> {
  const items: T[] = []
  const seen = new Set<string>()
  let cursor: string | undefined
  do {
    const page = await request(path + (cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''),
      (value): value is { items: T[]; nextCursor?: string } => isRecord(value)
        && Array.isArray(value.items) && value.items.every(isItem)
        && (value.nextCursor === undefined || (typeof value.nextCursor === 'string' && value.nextCursor.length > 0)),
    )
    items.push(...page.items)
    cursor = page.nextCursor
    if (cursor) {
      if (seen.has(cursor)) throw new ApiError(200, 'INVALID_RESPONSE', 'The server repeated a pagination cursor.')
      seen.add(cursor)
    }
  } while (cursor)
  return items
}

export const api = {
  login(input: LoginRequest): Promise<Session> {
    return request('/auth/login', isSession, { method: 'POST', body: input, public: true })
  },
  listProjects(): Promise<Project[]> { return listAll('/projects', isProject) },
  createProject(input: CreateProjectRequest): Promise<Project> {
    return request('/projects', isProject, { method: 'POST', body: input })
  },
  listVideos(projectId: string): Promise<Video[]> {
    return listAll(`/projects/${encodeURIComponent(projectId)}/videos`, isVideo)
  },
  getVideo(videoId: string): Promise<Video> {
    return request(`/videos/${encodeURIComponent(videoId)}`, isVideo)
  },
  getPublicVideo(videoId: string): Promise<Video> {
    return request(`/videos/${encodeURIComponent(videoId)}`, isVideo, { public: true })
  },
  createVideo(projectId: string, input: CreateVideoRequest): Promise<UploadTicket> {
    return request(`/projects/${encodeURIComponent(projectId)}/videos`, isUploadTicket, { method: 'POST', body: input })
  },
  confirmVideo(videoId: string): Promise<Video> {
    return request(`/videos/${encodeURIComponent(videoId)}/confirm`, isVideo, { method: 'POST' })
  },
}
