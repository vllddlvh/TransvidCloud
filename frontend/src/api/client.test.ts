import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { api, ApiError } from './client'
import { clearSession, readSession, saveSession, SESSION_KEY } from './session'
import { createHandlers } from '../mocks/handlers'

const server = setupServer()
const credentials = { email: 'demo@transvidcloud.test', password: 'DemoPass123!' }
const uploadInput = { filename: 'sample.mp4', sizeBytes: 4, contentType: 'video/mp4' }
const session = {
  accessToken: 'test-token', expiresAt: '2099-01-01T00:00:00.000Z',
  user: { userId: 'demo-user', email: credentials.email, name: 'Demo user' },
}
let fetchSpy: ReturnType<typeof vi.fn<typeof fetch>>

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => {
  localStorage.clear()
  server.resetHandlers(...createHandlers())
  // Production always uses relative paths; only the Node fetch adapter resolves them.
  const interceptedFetch = globalThis.fetch
  fetchSpy = vi.fn<typeof fetch>((input, init) => interceptedFetch(
    typeof input === 'string' && input.startsWith('/') ? new URL(input, 'http://localhost:5173') : input, init,
  ))
  vi.stubGlobal('fetch', fetchSpy)
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.restoreAllMocks() })
afterAll(() => server.close())

async function login() {
  const result = await api.login(credentials)
  saveSession(result)
  return result
}
async function put(url: string, body = new Uint8Array([1, 2, 3, 4]), contentType = 'video/mp4') {
  return fetch(url, { method: 'PUT', headers: { 'Content-Type': contentType }, body })
}

describe('session storage', () => {
  it('survives reload and emits only save/clear notifications', async () => {
    const listener = vi.fn()
    window.addEventListener('tvc:session', listener)
    try {
      saveSession(session)
      expect(listener).toHaveBeenCalledTimes(1)
      vi.resetModules()
      const reloaded = await import('./session')
      expect(reloaded.readSession()).toEqual(session)
      expect(listener).toHaveBeenCalledTimes(1)
      clearSession()
      expect(listener).toHaveBeenCalledTimes(2)
      expect(localStorage.getItem(SESSION_KEY)).toBeNull()
    } finally { window.removeEventListener('tvc:session', listener) }
  })

  it.each([
    'bad json', 'null', '{}',
    JSON.stringify({ ...session, expiresAt: '2020-01-01T00:00:00.000Z' }),
    JSON.stringify({ ...session, expiresAt: 'not-a-date' }),
    JSON.stringify({ ...session, accessToken: '' }),
    JSON.stringify({ ...session, accessToken: 'token\nwith-newline' }),
    JSON.stringify({ ...session, user: { email: credentials.email } }),
  ])('silently removes malformed or expired storage: %s', (value) => {
    const listener = vi.fn()
    window.addEventListener('tvc:session', listener)
    try {
      localStorage.setItem(SESSION_KEY, value)
      expect(readSession()).toBeNull()
      expect(localStorage.getItem(SESSION_KEY)).toBeNull()
      expect(listener).not.toHaveBeenCalled()
    } finally { window.removeEventListener('tvc:session', listener) }
  })

  it('does not crash when localStorage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked') })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('blocked') })
    expect(readSession()).toBeNull()
    expect(() => saveSession(session)).not.toThrow()
    expect(() => clearSession()).not.toThrow()
  })
})

describe('client and mock authentication', () => {
  it('logs in without reading a stale token and uses relative endpoints', async () => {
    saveSession(session)
    const storageRead = vi.spyOn(Storage.prototype, 'getItem')
    const result = await api.login(credentials)
    expect(result.user.email).toBe(credentials.email)
    expect(result.accessToken.split('.')).toHaveLength(3)
    expect(Date.parse(result.expiresAt) - Date.now()).toBeGreaterThan(29 * 60_000)
    expect(storageRead.mock.calls.some(([key]) => key === SESSION_KEY)).toBe(false)
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/auth/login')
    expect(new Headers(fetchSpy.mock.calls[0][1]?.headers).has('Authorization')).toBe(false)
  })

  it('rejects wrong credentials without expiring an existing session', async () => {
    saveSession(session)
    const listener = vi.fn()
    window.addEventListener('tvc:session-expired', listener)
    try {
      await expect(api.login({ ...credentials, password: 'wrong' })).rejects.toMatchObject({ status: 401, code: 'INVALID_CREDENTIALS' })
      expect(readSession()).toEqual(session)
      expect(listener).not.toHaveBeenCalled()
    } finally { window.removeEventListener('tvc:session-expired', listener) }
  })

  it('clears the session and signals expiration on a protected 401', async () => {
    saveSession(session)
    const listener = vi.fn()
    window.addEventListener('tvc:session-expired', listener)
    try {
      await expect(api.listProjects()).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' })
      expect(readSession()).toBeNull()
      expect(listener).toHaveBeenCalledTimes(1)
    } finally { window.removeEventListener('tvc:session-expired', listener) }
  })

  it('expires issued tokens using fractional mock-session minutes', async () => {
    vi.stubEnv('VITE_MOCK_SESSION_MINUTES', '0.01')
    const issued = await login()
    const expiry = Date.parse(issued.expiresAt)
    expect(expiry - Date.now()).toBeLessThanOrEqual(600)
    vi.spyOn(Date, 'now').mockReturnValue(expiry + 1)
    // Send the expired token directly, rather than relying only on frontend expiry.
    const response = await fetch('/api/projects', { headers: { Authorization: `Bearer ${issued.accessToken}` } })
    expect(response.status).toBe(401)
    expect(readSession()).toBeNull()
  })

  it('rejects forged JWT-looking tokens even with a future exp', async () => {
    const issued = await login()
    const parts = issued.accessToken.split('.')
    parts[2] = 'forged-signature'
    saveSession({ ...issued, accessToken: parts.join('.') })
    await expect(api.listProjects()).rejects.toMatchObject({ status: 401 })
  })

  it('persists mock records and issued sessions across handler recreation', async () => {
    await login()
    const project = await api.createProject({ name: 'My project', description: 'Reload test' })
    server.resetHandlers(...createHandlers())
    expect((await api.listProjects()).find((item) => item.projectId === project.projectId)).toEqual(project)
    expect(Object.values(localStorage).join(' ')).not.toContain(credentials.password)
    localStorage.clear()
    server.resetHandlers(...createHandlers())
    await login()
    expect(await api.listProjects()).toHaveLength(3)
  })
})

describe('projects and videos', () => {
  beforeEach(async () => { await login() })

  it('seeds three projects and all seven video states', async () => {
    vi.stubEnv('VITE_MOCK_PROCESS', 'freeze')
    server.resetHandlers(...createHandlers())
    const projects = await api.listProjects()
    expect(projects.map((project) => project.name)).toEqual(['Getting started', 'Product stories', 'Team library'])
    expect(projects[0]).toMatchObject({ projectId: 'demo-project', videoCount: 7 })
    const videos = await api.listVideos('demo-project')
    expect(videos.map((video) => video.status)).toEqual([
      'UPLOADING', 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED_VALIDATION', 'FAILED_TRANSCODE', 'FAILED_STORAGE',
    ])
    for (const video of videos) expect(await api.getVideo(video.videoId)).toEqual(video)
    expect(videos[3].assets?.hls).toMatch(/^\/media\/demo-project\/[^/]+\/hls\/master\.m3u8$/)
    expect(videos.filter((video) => video.status.startsWith('FAILED_')).every((video) => video.errorCode)).toBe(true)
  })

  it('follows opaque cursors for both list methods and keeps requests relative', async () => {
    const projects = await api.listProjects()
    const videos = await api.listVideos('demo-project')
    server.use(
      http.get('*/api/projects', ({ request }) => HttpResponse.json(new URL(request.url).searchParams.has('cursor')
        ? { items: projects.slice(1) } : { items: projects.slice(0, 1), nextCursor: 'page+/=' })),
      http.get('*/api/projects/:projectId/videos', ({ request }) => HttpResponse.json(new URL(request.url).searchParams.has('cursor')
        ? { items: videos.slice(1) } : { items: videos.slice(0, 1), nextCursor: 'video+/=' })),
    )
    expect(await api.listProjects()).toEqual(projects)
    expect(await api.listVideos('demo-project')).toEqual(videos)
    const paths = fetchSpy.mock.calls.map(([path]) => path)
    expect(paths).toContain('/api/projects?cursor=page%2B%2F%3D')
    expect(paths).toContain('/api/projects/demo-project/videos?cursor=video%2B%2F%3D')
    expect(paths.every((path) => typeof path === 'string' && path.startsWith('/api/'))).toBe(true)
  })

  it('rejects cursor cycles instead of looping indefinitely', async () => {
    server.use(http.get('*/api/projects', () => HttpResponse.json({ items: [], nextCursor: 'repeat' })))
    await expect(api.listProjects()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })

  it('creates a project and returns missing-resource errors', async () => {
    const project = await api.createProject({ name: 'New project', description: '' })
    expect(project).toMatchObject({ name: 'New project', description: '', videoCount: 0 })
    expect(await api.listVideos(project.projectId)).toEqual([])
    await expect(api.getVideo('missing')).rejects.toMatchObject({ status: 404, code: 'NOT_FOUND' })
    await expect(api.listVideos('missing')).rejects.toMatchObject({ status: 404 })
    await expect(api.createVideo('missing', uploadInput)).rejects.toMatchObject({ status: 404 })
    await expect(api.confirmVideo('missing')).rejects.toMatchObject({ status: 404 })
  })

  it.each([
    [{ ...uploadInput, sizeBytes: 209715201 }, 413, 'UPLOAD_TOO_LARGE'],
    [{ ...uploadInput, sizeBytes: 0 }, 422, 'VALIDATION_ERROR'],
    [{ ...uploadInput, sizeBytes: 1.5 }, 422, 'VALIDATION_ERROR'],
    [{ ...uploadInput, filename: 'sample.exe' }, 422, 'UNSUPPORTED_FORMAT'],
    [{ ...uploadInput, filename: 'mp4' }, 422, 'UNSUPPORTED_FORMAT'],
    [{ ...uploadInput, contentType: 'video/webm' }, 422, 'UNSUPPORTED_FORMAT'],
    [{ ...uploadInput, filename: '../sample.mp4' }, 422, 'VALIDATION_ERROR'],
  ] as const)('validates upload declarations %#', async (input, status, code) => {
    await expect(api.createVideo('demo-project', input)).rejects.toMatchObject({ status, code })
  })

  it.each([
    ['clip.mp4', 'video/mp4'], ['clip.MOV', 'video/quicktime'],
    ['clip.mkv', 'video/x-matroska'], ['clip.webm', 'video/webm'],
  ])('accepts the size boundary and supported pair %s', async (filename, contentType) => {
    const ticket = await api.createVideo('demo-project', { filename, contentType, sizeBytes: 209715200 })
    expect(ticket.video.status).toBe('UPLOADING')
  })

  it('rejects malformed JSON and invalid project/request fields', async () => {
    const auth = { Authorization: `Bearer ${readSession()!.accessToken}`, 'Content-Type': 'application/json' }
    expect((await fetch('/api/projects', { method: 'POST', headers: auth, body: '{' })).status).toBe(400)
    expect((await fetch('/api/projects', { method: 'POST', headers: auth, body: '[]' })).status).toBe(400)
    expect((await fetch('/api/projects', { method: 'POST', headers: auth, body: JSON.stringify({ name: 'x', description: '', ownerId: 'other' }) })).status).toBe(422)
    await expect(api.createProject({ name: '   ', description: '' })).rejects.toMatchObject({ status: 422 })
    await expect(api.createProject({ name: 'x'.repeat(101), description: '' })).rejects.toMatchObject({ status: 422 })
  })

  it('supports getMe, project detail/update/delete, API keys lifecycle, video delete, and paged video queries', async () => {
    vi.stubEnv('VITE_MOCK_PROCESS', 'freeze')
    server.resetHandlers(...createHandlers())

    const me = await api.getMe()
    expect(me).toMatchObject({ email: credentials.email, name: 'Demo user' })

    const project = await api.createProject({ name: 'Managed project', description: 'Initial', webhookUrl: 'https://example.com/hook' })
    expect(await api.getProject(project.projectId)).toMatchObject({
      projectId: project.projectId,
      name: 'Managed project',
      webhookUrl: 'https://example.com/hook',
    })

    const updated = await api.updateProject(project.projectId, {
      name: 'Renamed project',
      description: 'Updated description',
      webhookUrl: null,
    })
    expect(updated.name).toBe('Renamed project')
    expect(updated.description).toBe('Updated description')
    expect(updated.webhookUrl).toBeUndefined()

    // API Keys lifecycle (up to 5 keys per project)
    expect(await api.listApiKeys(project.projectId)).toEqual([])
    const createdKeys = []
    for (let i = 1; i <= 5; i += 1) {
      const key = await api.createApiKey(project.projectId, { name: `Key ${i}` })
      expect(key.rawKey).toMatch(/^tvc_live_[0-9a-f]{32}$/)
      expect(key.keyPrefix).toMatch(/^tvc_live_[0-9a-f]{8}\.\.\.$/)
      createdKeys.push(key)
    }
    await expect(api.createApiKey(project.projectId, { name: 'Key 6' })).rejects.toMatchObject({
      status: 400,
      code: 'INVALID_KEY',
    })
    expect(await api.listApiKeys(project.projectId)).toHaveLength(5)
    await api.revokeApiKey(project.projectId, createdKeys[0].keyHash)
    expect(await api.listApiKeys(project.projectId)).toHaveLength(4)

    // Delete project guard when active video exists
    await expect(api.deleteProject('demo-project')).rejects.toMatchObject({
      status: 409,
      code: 'RESOURCE_IN_USE',
    })
    await api.deleteProject(project.projectId)
    await expect(api.getProject(project.projectId)).rejects.toMatchObject({ status: 404 })

    // Paged video query and delete video guard when PROCESSING
    const firstPage = await api.listVideosPage('demo-project', { limit: 2 })
    expect(firstPage.items).toHaveLength(2)
    expect(firstPage.hasMore).toBe(true)
    expect(firstPage.nextCursor).toBeDefined()

    await expect(api.deleteVideo('demo-processing')).rejects.toMatchObject({
      status: 409,
      code: 'RESOURCE_IN_USE',
    })
    await api.deleteVideo('demo-completed')
    await expect(api.getVideo('demo-completed')).rejects.toMatchObject({ status: 404 })
  })
})

describe('public embed reads', () => {
  it('reads completed detail without bearer credentials or session expiry', async () => {
    saveSession(session)
    const listener = vi.fn()
    window.addEventListener('tvc:session-expired', listener)
    try {
      const video = await api.getPublicVideo('demo-completed')
      expect(video).toMatchObject({ videoId: 'demo-completed', status: 'COMPLETED' })
      const call = fetchSpy.mock.calls.find(([path]) => path === '/api/videos/demo-completed')
      expect(new Headers(call?.[1]?.headers).has('Authorization')).toBe(false)
      expect(readSession()).toEqual(session)
      expect(listener).not.toHaveBeenCalled()
    } finally { window.removeEventListener('tvc:session-expired', listener) }
  })

  it.each(['demo-processing', 'missing'])('treats %s as unavailable', async (videoId) => {
    await expect(api.getPublicVideo(videoId)).rejects.toMatchObject({ status: 404, code: 'NOT_FOUND' })
  })

  it('does not expire the session when a public read returns 401', async () => {
    saveSession(session)
    const listener = vi.fn()
    window.addEventListener('tvc:session-expired', listener)
    server.use(http.get('*/api/videos/:videoId', () =>
      HttpResponse.json({ error: { code: 'UNAUTHORIZED', message: 'Public read denied.' } }, { status: 401 })))
    try {
      await expect(api.getPublicVideo('demo-completed')).rejects.toMatchObject({ status: 401 })
      expect(readSession()).toEqual(session)
      expect(listener).not.toHaveBeenCalled()
    } finally { window.removeEventListener('tvc:session-expired', listener) }
  })
})

describe('presign → PUT → confirm', () => {
  beforeEach(async () => { await login() })

  it('requires a complete PUT, persists it across reload, and confirms idempotently', async () => {
    const ticket = await api.createVideo('demo-project', uploadInput)
    expect(ticket.video.status).toBe('UPLOADING')
    expect(ticket.uploadUrl).toMatch(/^\/__mock-upload\//)
    expect(ticket.headers).toEqual({ 'Content-Type': 'video/mp4' })
    expect(Date.parse(ticket.expiresAt) - Date.now()).toBeGreaterThan(14 * 60_000)
    await expect(api.confirmVideo(ticket.video.videoId)).rejects.toMatchObject({ status: 409, code: 'UPLOAD_NOT_COMPLETE' })
    const start = Date.now()
    expect((await put(ticket.uploadUrl)).status).toBe(200)
    expect(Date.now() - start).toBeGreaterThanOrEqual(1100)
    server.resetHandlers(...createHandlers())
    const pending = await api.confirmVideo(ticket.video.videoId)
    expect(pending.status).toBe('PENDING')
    expect(await api.confirmVideo(ticket.video.videoId)).toEqual(pending)
    expect(await api.getVideo(ticket.video.videoId)).toEqual(pending)
    expect((await api.listProjects())[0].videoCount).toBe(8)
    expect(fetchSpy.mock.calls.filter(([, init]) => init?.method === 'PUT').every(([, init]) => !new Headers(init?.headers).has('Authorization'))).toBe(true)
  })

  it('does not regress processing or terminal fixtures on repeat confirm', async () => {
    const videos = await api.listVideos('demo-project')
    for (const video of videos.slice(2)) {
      await expect(api.confirmVideo(video.videoId)).rejects.toMatchObject({ status: 409, code: 'VIDEO_NOT_CONFIRMABLE' })
      expect(await api.getVideo(video.videoId)).toEqual(video)
    }
  })

  it('rejects absent, wrong, and expired upload capabilities', async () => {
    const ticket = await api.createVideo('demo-project', uploadInput)
    const noToken = ticket.uploadUrl.split('?')[0]
    expect((await put(noToken)).status).toBe(403)
    const wrong = await put(`${noToken}?token=forged`)
    expect(wrong.status).toBe(403)
    expect(await wrong.json()).toMatchObject({ error: { code: 'INVALID_UPLOAD_TOKEN' } })
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse(ticket.expiresAt) + 1)
    const expired = await put(ticket.uploadUrl)
    expect(expired.status).toBe(403)
    expect(await expired.json()).toMatchObject({ error: { code: 'UPLOAD_URL_EXPIRED' } })
  })

  it('validates actual PUT bytes and content type before permitting confirm', async () => {
    const ticket = await api.createVideo('demo-project', uploadInput)
    const wrongSize = await put(ticket.uploadUrl, new Uint8Array([1, 2]))
    expect(wrongSize.status).toBe(422)
    expect(await wrongSize.json()).toMatchObject({ error: { code: 'UPLOAD_SIZE_MISMATCH' } })
    const wrongType = await put(ticket.uploadUrl, new Uint8Array([1, 2, 3, 4]), 'video/webm')
    expect(wrongType.status).toBe(422)
    expect(await wrongType.json()).toMatchObject({ error: { code: 'UPLOAD_TYPE_MISMATCH' } })
    await expect(api.confirmVideo(ticket.video.videoId)).rejects.toMatchObject({ status: 409 })
  })

  it('fails the first valid PUT once, then retries the same ticket successfully', async () => {
    vi.stubEnv('VITE_MOCK_UPLOAD_FAILURE_ONCE', 'true')
    server.resetHandlers(...createHandlers())
    const ticket = await api.createVideo('demo-project', uploadInput)
    // Invalid attempts must not consume the one-shot failure.
    expect((await put(ticket.uploadUrl, new Uint8Array([1]))).status).toBe(422)
    await expect(put(ticket.uploadUrl)).rejects.toThrow()
    await expect(api.confirmVideo(ticket.video.videoId)).rejects.toMatchObject({ status: 409 })
    expect((await put(ticket.uploadUrl)).status).toBe(200)
    expect((await api.confirmVideo(ticket.video.videoId)).status).toBe('PENDING')
  })
})

describe('client error normalization', () => {
  it.each([
    [503, '<html>unavailable</html>', 'SERVER_ERROR'],
    [401, '<html>unauthorized</html>', 'UNAUTHORIZED'],
    [403, 'forbidden', 'HTTP_ERROR'],
    [200, '<html>vite fallback</html>', 'INVALID_RESPONSE'],
    [200, '{}', 'INVALID_RESPONSE'],
    [200, '{"items":[{}]}', 'INVALID_RESPONSE'],
  ])('normalizes HTTP %s with an unusable body', async (status, body, code) => {
    saveSession(session)
    server.use(http.get('*/api/projects', () => new HttpResponse(body, { status })))
    await expect(api.listProjects()).rejects.toBeInstanceOf(ApiError)
    await expect(api.listProjects()).rejects.toMatchObject({ status, code })
    if (status === 401) expect(readSession()).toBeNull()
  })

  it('normalizes offline failures without clearing the session or retrying writes', async () => {
    saveSession(session)
    server.use(http.post('*/api/projects', () => HttpResponse.error()))
    await expect(api.createProject({ name: 'Offline', description: '' })).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
    expect(readSession()).toEqual(session)
    expect(fetchSpy).toHaveBeenCalledTimes(1)
  })
})
