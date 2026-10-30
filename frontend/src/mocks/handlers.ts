import { delay, http, HttpResponse, type HttpResponseResolver, type PathParams } from 'msw'
import {
  isRecord, isIsoDate, isSession, isProject, isVideo,
  type Session, type Project, type Video, type UploadTicket, type ErrorResponse,
} from '../api/types'
import { completeVideoAssets, createFixtures, DEMO_USER } from './fixtures'
import { mediaResponse } from './media'

const STORE_KEY = 'tvc.mock.v1'
const MAX_BYTES = 209715200
const UPLOAD_TTL = 15 * 60_000
const MIME_TYPES: Record<string, string> = {
  mp4: 'video/mp4', mov: 'video/quicktime', mkv: 'video/x-matroska', webm: 'video/webm',
}
type UploadGrant = { videoId: string; token: string; expiresAt: string; uploaded: boolean }
type MockState = {
  version: 1; projects: Project[]; videos: Video[]; sessions: Session[]; uploads: UploadGrant[]
}
class MockRequestError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) { super(message) }
}
function fail(status: number, code: string, message: string): never {
  throw new MockRequestError(status, code, message)
}
function loadState(): MockState {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(STORE_KEY) ?? 'null')
    if (isRecord(value) && value.version === 1
      && Array.isArray(value.projects) && value.projects.every(isProject)
      && Array.isArray(value.videos) && value.videos.every(isVideo)
      && Array.isArray(value.sessions) && value.sessions.every(isSession)
      && Array.isArray(value.uploads) && value.uploads.every((grant: unknown) =>
        isRecord(grant) && typeof grant.videoId === 'string' && typeof grant.token === 'string'
        && grant.token.length > 0 && isIsoDate(grant.expiresAt) && typeof grant.uploaded === 'boolean')) {
      return value as MockState
    }
  } catch { /* Unavailable/corrupt storage starts a fresh demo. */ }
  return { version: 1, ...createFixtures(), sessions: [], uploads: [] }
}
async function readBody(request: Request, fields: readonly string[]): Promise<Record<string, unknown>> {
  let body: unknown
  try { body = await request.json() } catch { fail(400, 'INVALID_JSON', 'A valid JSON request body is required.') }
  if (!isRecord(body)) fail(400, 'INVALID_REQUEST', 'The request body must be an object.')
  if (Object.keys(body).some((key) => !fields.includes(key)) || fields.some((key) => !(key in body))) {
    fail(422, 'VALIDATION_ERROR', 'The request contains missing or unexpected fields.')
  }
  return body
}
function base64Url(value: unknown): string {
  return btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
}

/** Each factory owns its state. Clear localStorage before constructing isolated test handlers. */
export function createHandlers() {
  const state = loadState()
  let failUploadOnce = import.meta.env.VITE_MOCK_UPLOAD_FAILURE_ONCE === 'true'
  function persist(): void {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(state)) } catch { /* In-memory demo still works. */ }
  }
  function advanceProcessing(now = Date.now()): void {
    const mode = import.meta.env.VITE_MOCK_PROCESS ?? 'auto'
    if (mode === 'freeze') return
    let changed = false
    for (const video of state.videos) {
      const elapsed = now - Date.parse(video.updatedAt)
      if (video.status === 'PENDING' && elapsed >= 5_000) {
        video.status = 'PROCESSING'
        video.updatedAt = new Date(now).toISOString()
        changed = true
      } else if (video.status === 'PROCESSING' && elapsed >= 10_000) {
        video.status = mode === 'fail' ? 'FAILED_TRANSCODE' : 'COMPLETED'
        video.updatedAt = new Date(now).toISOString()
        if (video.status === 'COMPLETED') {
          video.durationSeconds = 10
          video.assets = completeVideoAssets(video.projectId, video.videoId)
        } else {
          video.errorCode = 'TRANSCODE_FAILED'
        }
        changed = true
      }
    }
    if (changed) persist()
  }
  function authorize(request: Request): void {
    const match = /^Bearer (\S+)$/.exec(request.headers.get('Authorization') ?? '')
    // Unsigned mock tokens must match the locally issued registry, not merely decode.
    const session = match && state.sessions.find((item) => item.accessToken === match[1])
    if (!session || Date.parse(session.expiresAt) <= Date.now()) fail(401, 'UNAUTHORIZED', 'Please sign in again.')
    if (session.user.userId !== DEMO_USER.userId) fail(403, 'FORBIDDEN', 'This demo account cannot access these resources.')
  }
  function route(handler: (request: Request, params: PathParams) => Response | Promise<Response>, protectedRoute = true): HttpResponseResolver {
    return async ({ request, params }) => {
      try {
        if (protectedRoute) authorize(request)
        return await handler(request, params)
      } catch (error) {
        if (!(error instanceof MockRequestError)) throw error
        return HttpResponse.json<ErrorResponse>({ error: { code: error.code, message: error.message } }, { status: error.status })
      }
    }
  }
  function projectById(id: unknown): Project {
    const project = state.projects.find((item) => item.projectId === id)
    if (!project) fail(404, 'NOT_FOUND', 'Project not found.')
    return project
  }
  function videoById(id: unknown): Video {
    const video = state.videos.find((item) => item.videoId === id)
    if (!video) fail(404, 'NOT_FOUND', 'Video not found.')
    return video
  }
  function page<T extends Project | Video>(request: Request, items: T[]) {
    const cursor = new URL(request.url).searchParams.get('cursor')
    let start = 0
    if (cursor !== null) {
      const index = items.findIndex((item) => base64Url('projectId' in item && !('videoId' in item) ? item.projectId : (item as Video).videoId) === cursor)
      if (index < 0) fail(422, 'INVALID_CURSOR', 'The pagination cursor is not valid for this collection.')
      start = index + 1
    }
    const result = items.slice(start, start + 50)
    const last = result.at(-1)
    return HttpResponse.json({
      items: result,
      ...(start + result.length < items.length && last
        ? { nextCursor: base64Url('videoId' in last ? last.videoId : last.projectId) } : {}),
    })
  }

  return [
    http.get('*/media/*', ({ request }) =>
      mediaResponse(new URL(request.url).pathname) ?? new HttpResponse(null, { status: 404 })),
    http.post('*/api/auth/login', route(async (request) => {
      const body = await readBody(request, ['email', 'password'])
      if (typeof body.email !== 'string' || typeof body.password !== 'string'
        || body.email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email.trim())
        || !body.password.length || body.password.length > 128) fail(422, 'VALIDATION_ERROR', 'Enter a valid email and password.')
      if (body.email.trim().toLowerCase() !== DEMO_USER.email || body.password !== 'DemoPass123!') {
        fail(401, 'INVALID_CREDENTIALS', 'Email or password is incorrect.')
      }
      const configuredMinutes = Number(import.meta.env.VITE_MOCK_SESSION_MINUTES ?? 30)
      const minutes = Number.isFinite(configuredMinutes) && configuredMinutes > 0 ? configuredMinutes : 30
      const expiresAt = new Date(Date.now() + minutes * 60_000).toISOString()
      const accessToken = [
        base64Url({ alg: 'MOCK', typ: 'JWT' }),
        base64Url({ sub: DEMO_USER.userId, exp: Math.ceil(Date.parse(expiresAt) / 1000), iat: Math.floor(Date.now() / 1000), mock: true, jti: crypto.randomUUID() }),
        crypto.randomUUID().replace(/-/g, ''),
      ].join('.')
      const session: Session = { accessToken, expiresAt, user: { ...DEMO_USER } }
      state.sessions = state.sessions.filter((item) => Date.parse(item.expiresAt) > Date.now())
      state.sessions.push(session)
      persist()
      return HttpResponse.json(session)
    }, false)),
    http.get('*/api/projects', route((request) => page(request, state.projects))),
    http.post('*/api/projects', route(async (request) => {
      const body = await readBody(request, ['name', 'description'])
      if (typeof body.name !== 'string' || !body.name.trim() || body.name.length > 100
        || typeof body.description !== 'string' || body.description.length > 1000) {
        fail(422, 'VALIDATION_ERROR', 'Project name must be 1–100 characters and description at most 1000.')
      }
      const project: Project = {
        projectId: crypto.randomUUID(), name: body.name.trim(), description: body.description,
        videoCount: 0, createdAt: new Date(Date.now()).toISOString(),
      }
      state.projects.push(project)
      persist()
      return HttpResponse.json(project, { status: 201 })
    })),
    http.get('*/api/projects/:projectId/videos', route((request, params) => {
      const project = projectById(params.projectId)
      advanceProcessing()
      return page(request, state.videos.filter((item) => item.projectId === project.projectId))
    })),
    http.post('*/api/projects/:projectId/videos', route(async (request, params) => {
      const project = projectById(params.projectId)
      const body = await readBody(request, ['filename', 'sizeBytes', 'contentType'])
      if (typeof body.filename !== 'string' || !body.filename.trim() || body.filename.length > 255
        || /[/\\\x00-\x1f]/.test(body.filename) || typeof body.contentType !== 'string'
        || typeof body.sizeBytes !== 'number' || !Number.isSafeInteger(body.sizeBytes) || body.sizeBytes <= 0) {
        fail(422, 'VALIDATION_ERROR', 'Provide a filename, a positive integer sizeBytes and contentType.')
      }
      if (body.sizeBytes > MAX_BYTES) fail(413, 'UPLOAD_TOO_LARGE', 'Maximum upload size is 200 MB (209715200 bytes).')
      const extension = /\.([^.]+)$/.exec(body.filename)?.[1].toLowerCase()
      if (!extension || !Object.hasOwn(MIME_TYPES, extension) || MIME_TYPES[extension] !== body.contentType) {
        fail(422, 'UNSUPPORTED_FORMAT', 'Use matching MP4, MOV, MKV or WebM extension and video MIME type.')
      }
      const now = new Date(Date.now()).toISOString()
      const video: Video = {
        videoId: crypto.randomUUID(), projectId: project.projectId,
        filename: body.filename, sizeBytes: body.sizeBytes, contentType: body.contentType,
        status: 'UPLOADING', createdAt: now, updatedAt: now,
      }
      const grant: UploadGrant = { videoId: video.videoId, token: crypto.randomUUID(), expiresAt: new Date(Date.now() + UPLOAD_TTL).toISOString(), uploaded: false }
      state.videos.push(video)
      state.uploads.push(grant)
      project.videoCount += 1
      persist()
      return HttpResponse.json<UploadTicket>({
        video, uploadUrl: `/__mock-upload/${video.videoId}?token=${encodeURIComponent(grant.token)}`,
        expiresAt: grant.expiresAt, headers: { 'Content-Type': video.contentType },
      }, { status: 201 })
    })),
    http.get('*/api/videos/:videoId', route((request, params) => {
      if (request.headers.has('Authorization')) {
        authorize(request)
        advanceProcessing()
        return HttpResponse.json(videoById(params.videoId))
      }
      const video = state.videos.find((item) => item.videoId === params.videoId)
      if (!video || video.status !== 'COMPLETED') fail(404, 'NOT_FOUND', 'Video is not available.')
      return HttpResponse.json(video)
    }, false)),
    http.post('*/api/videos/:videoId/confirm', route(async (request, params) => {
      const video = videoById(params.videoId)
      if ((await request.text()).trim()) fail(400, 'INVALID_REQUEST', 'Confirm does not accept a request body.')
      if (video.status === 'PENDING') return HttpResponse.json(video, { status: 202 })
      if (video.status !== 'UPLOADING') fail(409, 'VIDEO_NOT_CONFIRMABLE', 'This video cannot return to PENDING.')
      if (!state.uploads.some((grant) => grant.videoId === video.videoId && grant.uploaded)) {
        fail(409, 'UPLOAD_NOT_COMPLETE', 'Upload the complete file before confirming.')
      }
      video.status = 'PENDING'
      video.updatedAt = new Date(Date.now()).toISOString()
      persist()
      return HttpResponse.json(video, { status: 202 })
    })),
    // Local mock capability transport, deliberately outside /api and bearer auth.
    http.put('*/__mock-upload/:videoId', route(async (request, params) => {
      const video = videoById(params.videoId)
      const grant = state.uploads.find((item) => item.videoId === video.videoId)
      if (!grant || new URL(request.url).searchParams.get('token') !== grant.token) {
        fail(403, 'INVALID_UPLOAD_TOKEN', 'The upload capability is missing or invalid.')
      }
      if (Date.parse(grant.expiresAt) <= Date.now()) fail(403, 'UPLOAD_URL_EXPIRED', 'The upload URL has expired.')
      if (video.status !== 'UPLOADING') fail(409, 'VIDEO_NOT_CONFIRMABLE', 'The upload has already been confirmed.')
      if (request.headers.get('Content-Type') !== video.contentType) fail(422, 'UPLOAD_TYPE_MISMATCH', 'PUT Content-Type must match the upload declaration.')
      const size = (await request.arrayBuffer()).byteLength
      if (size > MAX_BYTES) fail(413, 'UPLOAD_TOO_LARGE', 'The uploaded body exceeds 200 MB.')
      if (size !== video.sizeBytes) fail(422, 'UPLOAD_SIZE_MISMATCH', 'PUT body length must match the upload declaration.')
      // Reserve the failure before awaiting so concurrent valid PUTs cannot both consume it.
      const shouldFail = failUploadOnce
      failUploadOnce = false
      await delay(1200)
      if (shouldFail) return HttpResponse.error()
      if (request.signal.aborted) return HttpResponse.error()
      if (Date.parse(grant.expiresAt) <= Date.now()) fail(403, 'UPLOAD_URL_EXPIRED', 'The upload URL has expired.')
      grant.uploaded = true
      persist()
      return new HttpResponse(null, { status: 200 })
    }, false)),
  ]
}
