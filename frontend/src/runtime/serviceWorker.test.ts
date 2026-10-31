import { describe, expect, it, vi } from 'vitest'
import { isMockWorkerRegistration, unregisterMockWorkers } from './serviceWorker'

function registration(scriptURL: string | null, unregister = vi.fn().mockResolvedValue(true)) {
  return {
    active: scriptURL ? { scriptURL } : null,
    waiting: null,
    installing: null,
    unregister,
  }
}

describe('stale mock service worker cleanup', () => {
  it('detects the mock worker in any lifecycle slot', () => {
    const active = registration('http://localhost:8000/mockServiceWorker.js')
    const waiting = { ...registration(null), waiting: { scriptURL: 'http://localhost:8000/mockServiceWorker.js' } }
    expect(isMockWorkerRegistration(active)).toBe(true)
    expect(isMockWorkerRegistration(waiting)).toBe(true)
    expect(isMockWorkerRegistration(registration('http://localhost:8000/sw.js'))).toBe(false)
  })

  it('unregisters only mock workers and reports the count', async () => {
    const mock = registration('http://localhost:8000/mockServiceWorker.js')
    const other = registration('http://localhost:8000/app-worker.js')
    const count = await unregisterMockWorkers({ getRegistrations: async () => [mock, other] })
    expect(count).toBe(1)
    expect(mock.unregister).toHaveBeenCalledOnce()
    expect(other.unregister).not.toHaveBeenCalled()
  })

  it('does nothing when service workers are unavailable', async () => {
    await expect(unregisterMockWorkers(undefined)).resolves.toBe(0)
  })
})
