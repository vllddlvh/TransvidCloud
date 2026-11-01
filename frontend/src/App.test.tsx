import { beforeEach, describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from './App'

beforeEach(() => localStorage.clear())

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
