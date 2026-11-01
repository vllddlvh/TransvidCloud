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
const ajv = new Ajv({ allErrors: true, strict: true })
addFormats(ajv)
function validate(name: string, value: unknown) {
  expect(contract.components?.schemas?.[name], `Missing draft schema ${name}`).toBeDefined()
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

it('defines every existing endpoint with resolvable path parameters and draft schemas', () => {
  for (const path of ['/auth/register', '/auth/login', '/projects', '/projects/{projectId}/api-keys', '/projects/{projectId}/videos', '/videos/{videoId}/confirm', '/videos/{videoId}']) {
    expect(contract.paths[path]).toBeDefined()
    for (const placeholder of path.matchAll(/\{(\w+)\}/g)) {
      const parameters = contract.paths[path].parameters ?? []
      const resolved = parameters.map((parameter: { $ref?: string }) => parameter.$ref
        ? contract.components.parameters[parameter.$ref.split('/').pop()!] : parameter)
      expect(resolved).toContainEqual(expect.objectContaining({ name: placeholder[1], in: 'path', required: true }))
    }
  }
  expect(contract['x-contract-status']).toBe('DRAFT — proposed, not approved')
  for (const name of ['Session', 'Project', 'Video', 'UploadTicket', 'ErrorResponse', 'LoginRequest', 'CreateProjectRequest', 'CreateVideoRequest', 'ProjectList', 'VideoList']) {
    expect(contract.components?.schemas?.[name], `Missing ${name}`).toBeDefined()
  }
})

it('validates every seeded project and every video state against the draft', () => {
  const fixtures = createFixtures()
  expect(fixtures.projects).toHaveLength(3)
  expect(fixtures.videos).toHaveLength(7)
  fixtures.projects.forEach((project) => validate('Project', project))
  fixtures.videos.forEach((video) => validate('Video', video))
  expect(new Set(fixtures.videos.map((video) => video.status)).size).toBe(7)
})

it('validates actual auth, list, create, detail, presign, confirm, and error wire bodies', async () => {
  const loginBody = { email: 'demo@transvidcloud.test', password: 'DemoPass123!' }
  validate('LoginRequest', loginBody)
  const loginResponse = await call('/auth/login', 'POST', loginBody)
  expect(loginResponse.status).toBe(200)
  const session = await loginResponse.json()
  validate('Session', session)
  token = session.accessToken
  validate('ProjectList', await (await call('/projects')).json())
  validate('VideoList', await (await call('/projects/demo-project/videos')).json())
  const projectBody = { name: 'Contract test', description: '' }
  validate('CreateProjectRequest', projectBody)
  const created = await call('/projects', 'POST', projectBody)
  expect(created.status).toBe(201)
  validate('Project', await created.json())
  const videoBody = { filename: 'contract.mp4', sizeBytes: 3, contentType: 'video/mp4' }
  validate('CreateVideoRequest', videoBody)
  const presign = await call('/projects/demo-project/videos', 'POST', videoBody)
  expect(presign.status).toBe(201)
  const ticket = await presign.json()
  validate('UploadTicket', ticket)
  validate('Video', await (await call(`/videos/${ticket.video.videoId}`)).json())
  const premature = await call(`/videos/${ticket.video.videoId}/confirm`, 'POST')
  expect(premature.status).toBe(409)
  validate('ErrorResponse', await premature.json())
  const uploaded = await fetch(`${origin}${ticket.uploadUrl}`, { method: 'PUT', headers: ticket.headers, body: new Uint8Array([1, 2, 3]) })
  expect(uploaded.status).toBe(200)
  const confirmed = await call(`/videos/${ticket.video.videoId}/confirm`, 'POST')
  expect(confirmed.status).toBe(202)
  validate('Video', await confirmed.json())
  const tooLarge = await call('/projects/demo-project/videos', 'POST', { ...videoBody, sizeBytes: 209715201 })
  expect(tooLarge.status).toBe(413)
  validate('ErrorResponse', await tooLarge.json())
  token = 'not-issued'
  const unauthorized = await call('/projects')
  expect(unauthorized.status).toBe(401)
  validate('ErrorResponse', await unauthorized.json())
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
    validate('Video', completed)
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
    validate('Video', failed)
  } finally {
    vi.unstubAllEnvs()
    vi.useRealTimers()
  }
})
