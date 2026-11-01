import { clearSession, readSession } from './session'
import {
  isRecord,
  normalizeSession,
  normalizeProject,
  normalizeVideo,
  normalizeUploadTicket,
  type Session,
  type Project,
  type Video,
  type UploadTicket,
  type LoginRequest,
  type RegisterRequest,
  type CreateProjectRequest,
  type CreateVideoRequest,
} from './types'

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string, message = code) {
    super(message)
    this.name = 'ApiError'
  }
}

type Parser<T> = (value: unknown) => T | null

async function request<T>(
  path: string,
  parse: Parser<T>,
  options: { method?: 'GET' | 'POST' | 'DELETE'; body?: unknown; public?: boolean } = {},
): Promise<T> {
  const headers = new Headers({ Accept: 'application/json' })
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')
  // Login/register must not even read a stale stored token.
  if (!options.public) {
    const session = readSession()
    if (session) headers.set('Authorization', `Bearer ${session.accessToken}`)
  }
  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method: options.method ?? 'GET',
      headers,
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
    const error = isRecord(body)
      ? (isRecord(body.error) ? body.error : body)
      : undefined
    throw new ApiError(
      response.status,
      typeof error?.code === 'string' && error.code ? error.code : fallback,
      typeof error?.message === 'string' && error.message ? error.message : `Request failed (${response.status}).`,
    )
  }
  const parsed = parse(body)
  if (parsed === null) throw new ApiError(response.status, 'INVALID_RESPONSE', 'The server returned an unexpected response.')
  return parsed
}

async function listAll<T>(path: string, parseItem: Parser<T>): Promise<T[]> {
  const items: T[] = []
  const seen = new Set<string>()
  let cursor: string | undefined
  do {
    const page = await request(
      path + (cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''),
      (value): { items: T[]; nextCursor?: string } | null => {
        if (!isRecord(value) || !Array.isArray(value.items)) return null
        if (value.nextCursor !== undefined && value.nextCursor !== null
          && (typeof value.nextCursor !== 'string' || value.nextCursor.length === 0)) {
          return null
        }
        const parsedItems: T[] = []
        for (const raw of value.items) {
          const item = parseItem(raw)
          if (item === null) return null
          parsedItems.push(item)
        }
        return {
          items: parsedItems,
          ...(typeof value.nextCursor === 'string' && value.nextCursor.length > 0 ? { nextCursor: value.nextCursor } : {}),
        }
      },
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
    return request('/auth/login', normalizeSession, { method: 'POST', body: input, public: true })
  },
  register(input: RegisterRequest): Promise<Session> {
    return request('/auth/register', normalizeSession, { method: 'POST', body: input, public: true })
  },
  listProjects(): Promise<Project[]> {
    return listAll('/projects', normalizeProject)
  },
  createProject(input: CreateProjectRequest): Promise<Project> {
    return request('/projects', normalizeProject, { method: 'POST', body: input })
  },
  listVideos(projectId: string): Promise<Video[]> {
    return listAll(`/projects/${encodeURIComponent(projectId)}/videos`, normalizeVideo)
  },
  getVideo(videoId: string): Promise<Video> {
    return request(`/videos/${encodeURIComponent(videoId)}`, normalizeVideo)
  },
  getPublicVideo(videoId: string): Promise<Video> {
    return request(`/videos/${encodeURIComponent(videoId)}`, normalizeVideo, { public: true })
  },
  createVideo(projectId: string, input: CreateVideoRequest): Promise<UploadTicket> {
    const title = (input.title ?? input.filename ?? '').trim() || input.filename
    return request(`/projects/${encodeURIComponent(projectId)}/videos`, normalizeUploadTicket, {
      method: 'POST',
      body: {
        title,
        filename: input.filename,
        sizeBytes: input.sizeBytes,
        contentType: input.contentType,
      },
    })
  },
  confirmVideo(videoId: string): Promise<Video> {
    return request(`/videos/${encodeURIComponent(videoId)}/confirm`, normalizeVideo, { method: 'POST' })
  },
}
