// @vitest-environment node
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import Ajv, { type AnySchema } from 'ajv'
import addFormats from 'ajv-formats'
import { parse } from 'yaml'
import { afterAll, afterEach, beforeAll, beforeEach, expect, it, vi } from 'vitest'
import { setupServer } from 'msw/node'
import { createHandlers } from '../mocks/handlers'
import { createFixtures } from '../mocks/fixtures'
import {
  normalizeSession,
  normalizeProject,
  normalizeVideo,
  normalizeUploadTicket,
} from './types'

const contract = parse(readFileSync(fileURLToPath(new URL('../../../docs/contracts/openapi.yaml', import.meta.url)), 'utf8'))
// OpenAPI 3.0 nullable is NOT a JSON Schema type union. Convert explicitly,
// and remove documentation-only keywords; retain all validation constraints.
function jsonSchema(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(jsonSchema)
  if (value === null || typeof value !== 'object') return value
  const input = value as Record<string, unknown>
  const output: Record<string, unknown> = {}
  for (const [key, child] of Object.entries(input)) {
    if (key === 'nullable' || key === 'example' || key === 'discriminator' || key.startsWith('x-')) continue
    output[key] = key === '$ref' && typeof child === 'string'
      ? child.replace('#/components/schemas/', '#/$defs/') : jsonSchema(child)
  }
  if (input.nullable === true) return { anyOf: [output, { type: 'null' }] }
  return output
}
const ajv = new Ajv({ allErrors: true, strict: false })
addFormats(ajv)
function validate(name: string, value: unknown) {
  expect(contract.components?.schemas?.[name], `Missing schema ${name}`).toBeDefined()
  const check = ajv.compile({
    $defs: jsonSchema(contract.components.schemas),
    $ref: `#/$defs/${name}`,
  } as AnySchema)
  expect(check(value), JSON.stringify(check.errors)).toBe(true)
}
const server = setupServer()
const origin = 'http://localhost:5173'
let token: string
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => {
  const storage = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
  })
  server.resetHandlers(...createHandlers())
  token = ''
})
afterEach(() => vi.unstubAllGlobals())
afterAll(() => server.close())
async function call(path: string, method = 'GET', body?: unknown) {
  return fetch(`${origin}/api${path}`, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

it('defines every official Backend SSOT endpoint with resolvable path parameters and schemas', () => {
  for (const path of ['/auth/register', '/auth/login', '/projects', '/projects/{projectId}/api-keys', '/projects/{projectId}/videos', '/videos/{videoId}/confirm', '/videos/{videoId}']) {
    expect(contract.paths[path]).toBeDefined()
    for (const placeholder of path.matchAll(/\{(\w+)\}/g)) {
      const parameters = contract.paths[path].parameters ?? []
      const resolved = parameters.map((parameter: { $ref?: string }) => parameter.$ref
        ? contract.components.parameters[parameter.$ref.split('/').pop()!] : parameter)
      expect(resolved).toContainEqual(expect.objectContaining({ name: placeholder[1], in: 'path', required: true }))
    }
  }
  for (const name of [
    'AuthResponse', 'RegisterRequest', 'LoginRequest', 'UserSummary',
    'CreateProjectRequest', 'ProjectResponse', 'ProjectListResponse',
    'InitVideoRequest', 'InitVideoResponse', 'PresignedUploadInfo',
    'VideoResponse', 'VideoPageResponse', 'VideoAssets', 'ErrorResponse',
  ]) {
    expect(contract.components?.schemas?.[name], `Missing ${name}`).toBeDefined()
  }
})

it('validates Backend SSOT wire payloads against OpenAPI and normalizes them for Frontend UI', () => {
  const authResponse = {
    accessToken: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.sig',
    tokenType: 'Bearer',
    expiresInSeconds: 86400,
    user: {
      userId: 'usr_8b31a2',
      email: 'user@example.com',
      fullName: 'Nguyen Van A',
      createdAt: '2026-10-31T08:00:00Z',
    },
  }
  validate('AuthResponse', authResponse)
  const session = normalizeSession(authResponse)
  expect(session).not.toBeNull()
  expect(session?.user.name).toBe('Nguyen Van A')

  const projectResponse = {
    projectId: 'prj_1c05d9',
    ownerId: 'usr_8b31a2',
    name: 'E-Learning Course Videos',
    description: 'Kho video bài giảng học kỳ 1',
    createdAt: '2026-10-31T08:05:00Z',
    updatedAt: '2026-10-31T08:05:00Z',
  }
  validate('ProjectResponse', projectResponse)
  expect(normalizeProject(projectResponse)).toMatchObject({
    projectId: 'prj_1c05d9',
    name: 'E-Learning Course Videos',
    description: 'Kho video bài giảng học kỳ 1',
    videoCount: 0,
  })

  const initVideoRequest = {
    title: 'lecture-01.mp4',
    filename: 'lecture-01.mp4',
    sizeBytes: 52428800,
    contentType: 'video/mp4',
  }
  validate('InitVideoRequest', initVideoRequest)

  const initVideoResponse = {
    video: {
      videoId: 'vid_4a77e1',
      projectId: 'prj_1c05d9',
      title: 'lecture-01.mp4',
      status: 'UPLOADING',
      sizeBytes: 52428800,
      contentType: 'video/mp4',
      attemptCount: 0,
      createdAt: '2026-10-31T09:15:00Z',
      updatedAt: '2026-10-31T09:15:00Z',
    },
    upload: {
      presignedUrl: 'http://localhost:4566/tvc-local-input/prj_1c05d9/vid_4a77e1/source.mp4?X-Amz-Signature=abc',
      method: 'PUT',
      expiresInSeconds: 900,
      requiredHeaders: {
        'Content-Type': 'video/mp4',
        'Content-Length': '52428800',
      },
    },
  }
  validate('InitVideoResponse', initVideoResponse)
  const ticket = normalizeUploadTicket(initVideoResponse)
  expect(ticket).not.toBeNull()
  expect(ticket?.uploadUrl).toContain('http://localhost:4566/tvc-local-input/')
  expect(ticket?.video.filename).toBe('lecture-01.mp4')

  const completedVideoResponse = {
    videoId: 'vid_4a77e1',
    projectId: 'prj_1c05d9',
    title: 'lecture-01.mp4',
    status: 'COMPLETED',
    sizeBytes: 52428800,
    contentType: 'video/mp4',
    attemptCount: 1,
    assets: {
      masterPlaylistUrl: '/media/prj_1c05d9/vid_4a77e1/hls/master.m3u8',
      hls360Url: '/media/prj_1c05d9/vid_4a77e1/hls/360p/index.m3u8',
      hls720Url: '/media/prj_1c05d9/vid_4a77e1/hls/720p/index.m3u8',
      thumbnailUrl: '/media/prj_1c05d9/vid_4a77e1/thumb/default.jpg',
    },
    createdAt: '2026-10-31T09:15:00Z',
    updatedAt: '2026-10-31T09:16:02Z',
    startedAt: '2026-10-31T09:15:05Z',
    completedAt: '2026-10-31T09:16:02Z',
  }
  validate('VideoResponse', completedVideoResponse)
  const completedVideo = normalizeVideo(completedVideoResponse)
  expect(completedVideo?.assets).toEqual({
    hls: '/media/prj_1c05d9/vid_4a77e1/hls/master.m3u8',
    hls360: '/media/prj_1c05d9/vid_4a77e1/hls/360p/index.m3u8',
    hls720: '/media/prj_1c05d9/vid_4a77e1/hls/720p/index.m3u8',
    thumbnail: '/media/prj_1c05d9/vid_4a77e1/thumb/default.jpg',
  })

  const errorResponse = {
    code: 'UPLOAD_LIMIT_EXCEEDED',
    message: 'Dung lượng tệp vượt quá giới hạn 200MB',
    timestamp: '2026-10-31T09:20:00Z',
  }
  validate('ErrorResponse', errorResponse)
})

it('validates every seeded project and every video state in mock fixtures', () => {
  const fixtures = createFixtures()
  expect(fixtures.projects).toHaveLength(3)
  expect(fixtures.videos).toHaveLength(7)
  fixtures.projects.forEach((project) => expect(normalizeProject(project)).not.toBeNull())
  fixtures.videos.forEach((video) => expect(normalizeVideo(video)).not.toBeNull())
  expect(new Set(fixtures.videos.map((video) => video.status)).size).toBe(7)
})

it('auto-completes pending videos without regressing terminal states', async () => {
  vi.useFakeTimers()
  try {
    vi.setSystemTime(new Date('2026-01-12T09:00:00.000Z'))
    const login = await call('/auth/login', 'POST', { email: 'demo@transvidcloud.test', password: 'DemoPass123!' })
    token = (await login.json()).accessToken
    vi.setSystemTime(new Date('2026-01-12T09:00:05.000Z'))
    expect((await (await call('/videos/demo-pending')).json()).status).toBe('PROCESSING')
    vi.setSystemTime(new Date('2026-01-12T09:00:15.000Z'))
    const completed = await (await call('/videos/demo-pending')).json()
    expect(completed.status).toBe('COMPLETED')
    expect(completed.durationSeconds).toBe(10)
    for (const key of ['hls', 'hls360', 'hls720', 'thumbnail']) {
      expect(completed.assets[key]).toMatch(/^\/media\/demo-project\/demo-pending\//)
    }
    expect(normalizeVideo(completed)).not.toBeNull()
    expect((await (await call('/videos/demo-completed')).json()).status).toBe('COMPLETED')
  } finally {
    vi.useRealTimers()
  }
})

it('leaves pending videos unchanged when mock processing is frozen', async () => {
  vi.useFakeTimers()
  try {
    vi.stubEnv('VITE_MOCK_PROCESS', 'freeze')
    server.resetHandlers(...createHandlers())
    vi.setSystemTime(new Date('2026-01-12T09:00:00.000Z'))
    const login = await call('/auth/login', 'POST', { email: 'demo@transvidcloud.test', password: 'DemoPass123!' })
    token = (await login.json()).accessToken
    vi.setSystemTime(new Date('2026-01-12T09:00:05.000Z'))
    expect((await (await call('/videos/demo-pending')).json()).status).toBe('PENDING')
    vi.setSystemTime(new Date('2026-01-12T09:00:15.000Z'))
    expect((await (await call('/videos/demo-pending')).json()).status).toBe('PENDING')
  } finally {
    vi.unstubAllEnvs()
    vi.useRealTimers()
  }
})

it('fails processing videos with a transcode error in fail mode', async () => {
  vi.useFakeTimers()
  try {
    vi.stubEnv('VITE_MOCK_PROCESS', 'fail')
    server.resetHandlers(...createHandlers())
    vi.setSystemTime(new Date('2026-01-12T09:00:00.000Z'))
    const login = await call('/auth/login', 'POST', { email: 'demo@transvidcloud.test', password: 'DemoPass123!' })
    token = (await login.json()).accessToken
    vi.setSystemTime(new Date('2026-01-12T09:00:05.000Z'))
    expect((await (await call('/videos/demo-pending')).json()).status).toBe('PROCESSING')
    vi.setSystemTime(new Date('2026-01-12T09:00:15.000Z'))
    const failed = await (await call('/videos/demo-processing')).json()
    expect(failed.status).toBe('FAILED_TRANSCODE')
    expect(failed.errorCode).toBe('TRANSCODE_FAILED')
    expect(normalizeVideo(failed)).not.toBeNull()
  } finally {
    vi.unstubAllEnvs()
    vi.useRealTimers()
  }
})
