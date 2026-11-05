import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { setupServer } from 'msw/node'
import App from './App'
import { api } from './api/client'
import { saveSession } from './api/session'
import { createHandlers } from './mocks/handlers'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => {
  localStorage.clear()
  server.resetHandlers(...createHandlers())
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  })
  const interceptedFetch = globalThis.fetch
  vi.stubGlobal('fetch', vi.fn<typeof fetch>((input, init) => interceptedFetch(
    typeof input === 'string' && input.startsWith('/') ? new URL(input, 'http://localhost:5173') : input, init,
  )))
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.restoreAllMocks() })
afterAll(() => server.close())

describe('public routes and authentication guard', () => {
  it.each(['/projects', '/projects/demo-project/videos', '/videos/demo-video'])('protects %s without showing the workspace', (path) => {
    render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: 'Sign in to your workspace' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('keeps the embed route public, chrome-free, and honestly unavailable without an id', async () => {
    render(<MemoryRouter initialEntries={['/embed']}><App /></MemoryRouter>)
    expect(await screen.findByText('Video is not available yet.')).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('explains an expired session on a fresh page load', () => {
    localStorage.setItem('tvc.session', JSON.stringify({
      accessToken: 'expired-token', expiresAt: '2020-01-01T00:00:00.000Z',
      user: { userId: 'test-user', name: 'Test user', email: 'test@example.test' },
    }))
    render(<MemoryRouter initialEntries={['/projects']}><App /></MemoryRouter>)
    expect(screen.getByRole('alert')).toHaveTextContent('session has expired')
    expect(localStorage.getItem('tvc.session')).toBeNull()
  })
})

describe('workspace feature parity (F-004, F-005, F-008)', () => {
  beforeEach(async () => {
    vi.stubEnv('VITE_MOCK_PROCESS', 'freeze')
    server.resetHandlers(...createHandlers())
    const session = await api.login({ email: 'demo@transvidcloud.test', password: 'DemoPass123!' })
    saveSession(session)
  })

  it('manages Developer API keys: create with one-time rawKey display and revoke', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/projects/demo-project/videos']}><App /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: /API keys/i }))
    expect(await screen.findByText(/0 \/ 5 keys/i)).toBeInTheDocument()

    await user.type(screen.getByLabelText('New API key name'), 'CI Pipeline Key')
    await user.click(screen.getByRole('button', { name: /Create API key/i }))

    const banner = await screen.findByRole('region', { name: 'Newly created API key' })
    expect(banner).toHaveTextContent('Copy your API key now — it will only be shown once')
    expect(banner.textContent).toMatch(/tvc_live_[0-9a-f]{32}/)

    await user.click(screen.getByRole('button', { name: 'Done' }))
    expect(screen.queryByRole('region', { name: 'Newly created API key' })).not.toBeInTheDocument()
    expect(screen.getByText('CI Pipeline Key')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Revoke/i }))
    await waitFor(() => expect(screen.queryByText('CI Pipeline Key')).not.toBeInTheDocument())
  })

  it('edits project metadata and deletes an empty project', async () => {
    const user = userEvent.setup()
    const created = await api.createProject({ name: 'Temporary Project', description: 'Initial desc' })
    render(<MemoryRouter initialEntries={[`/projects/${created.projectId}/videos`]}><App /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: /Edit project/i }))
    const nameInput = screen.getByLabelText('Project name')
    await user.clear(nameInput)
    await user.type(nameInput, 'Renamed Workspace Project')
    await user.type(screen.getByLabelText(/Webhook URL/i), 'https://example.com/hook')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('heading', { name: 'Renamed Workspace Project' })).toBeInTheDocument()
    expect(screen.getByText('https://example.com/hook')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Delete project/i }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete project' }))

    expect(await screen.findByRole('heading', { name: 'Projects' })).toBeInTheDocument()
  })

  it('renders all 4 transcoded assets on VideoDetail and deletes a completed video', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/videos/demo-completed']}><App /></MemoryRouter>)

    expect(await screen.findByText('Master HLS (ABR)')).toBeInTheDocument()
    expect(screen.getByText('720p HD HLS')).toBeInTheDocument()
    expect(screen.getByText('360p SD HLS')).toBeInTheDocument()
    expect(screen.getByText('Thumbnail JPEG')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Delete video/i }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete' }))

    expect(await screen.findByRole('heading', { name: 'Getting started' })).toBeInTheDocument()
  })
})
