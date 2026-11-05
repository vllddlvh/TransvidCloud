import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { NoticeProvider } from './Notice'
import { useResource } from './useResource'

type Data = { value: string; active: boolean; version?: number }
let visibility: DocumentVisibilityState

function Harness({ load, shouldPoll }: { load: (() => Promise<Data>) | ReturnType<typeof vi.fn>; shouldPoll?: (data: Data) => boolean }) {
  const { data, loading, error, reload } = useResource(load as () => Promise<Data>, shouldPoll)
  return <div>
    <span data-testid="data">{data ? `${data.value}:${data.version ?? 1}` : 'none'}</span>
    <span data-testid="loading">{loading ? 'loading' : 'idle'}</span>
    <span data-testid="error">{error}</span>
    <button onClick={reload}>Reload</button>
  </div>
}

function renderHarness(load: ReturnType<typeof vi.fn>, shouldPoll?: (data: Data) => boolean) {
  return render(<NoticeProvider><Harness load={load} shouldPoll={shouldPoll} /></NoticeProvider>)
}

async function settle() {
  await act(async () => { await Promise.resolve() })
}

async function advance(milliseconds: number) {
  await act(async () => { await vi.advanceTimersByTimeAsync(milliseconds) })
}

beforeEach(() => {
  vi.useFakeTimers()
  visibility = 'visible'
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility })
})

afterEach(() => {
  vi.useRealTimers()
  delete (document as { visibilityState?: DocumentVisibilityState }).visibilityState
})

describe('useResource polling', () => {
  it('loads once initially and waits while data is terminal', async () => {
    const load = vi.fn().mockResolvedValue({ value: 'ready', active: false })
    renderHarness(load, (data) => data.active)
    await settle()
    expect(screen.getByTestId('data')).toHaveTextContent('ready:1')
    await advance(3000)
    expect(load).toHaveBeenCalledTimes(1)
  })

  it('polls active data every three seconds and stops after a terminal response', async () => {
    const load = vi.fn()
      .mockResolvedValueOnce({ value: 'pending', active: true })
      .mockResolvedValueOnce({ value: 'ready', active: false })
    renderHarness(load, (data) => data.active)
    await settle()
    await advance(3000)
    expect(load).toHaveBeenCalledTimes(2)
    expect(screen.getByTestId('data')).toHaveTextContent('ready:1')
    await advance(6000)
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('never overlaps requests and preserves current data while refreshing', async () => {
    let finishRefresh!: (data: Data) => void
    const refresh = new Promise<Data>((resolve) => { finishRefresh = resolve })
    const load = vi.fn()
      .mockResolvedValueOnce({ value: 'pending', active: true })
      .mockReturnValueOnce(refresh)
    renderHarness(load, (data) => data.active)
    await settle()
    await advance(3000)
    expect(load).toHaveBeenCalledTimes(2)
    expect(screen.getByTestId('data')).toHaveTextContent('pending:1')
    expect(screen.getByTestId('loading')).toHaveTextContent('idle')
    await advance(9000)
    expect(load).toHaveBeenCalledTimes(2)
    await act(async () => { finishRefresh({ value: 'ready', active: false }) })
    expect(screen.getByTestId('data')).toHaveTextContent('ready:1')
  })

  it('pauses while hidden and refreshes when visible again', async () => {
    const load = vi.fn().mockResolvedValue({ value: 'pending', active: true })
    renderHarness(load, (data) => data.active)
    await settle()
    visibility = 'hidden'
    act(() => { document.dispatchEvent(new Event('visibilitychange')) })
    await advance(6000)
    expect(load).toHaveBeenCalledTimes(1)
    visibility = 'visible'
    act(() => { document.dispatchEvent(new Event('visibilitychange')) })
    await advance(0)
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('retains data through two failures and reports status unknown on the third', async () => {
    const load = vi.fn()
      .mockResolvedValueOnce({ value: 'pending', active: true })
      .mockRejectedValueOnce(new Error('offline one'))
      .mockRejectedValueOnce(new Error('offline two'))
      .mockRejectedValueOnce(new Error('offline three'))
    renderHarness(load, (data) => data.active)
    await settle()
    await advance(3000)
    expect(screen.getByTestId('data')).toHaveTextContent('pending:1')
    expect(screen.getByTestId('error')).toBeEmptyDOMElement()
    await advance(3000)
    expect(screen.getByTestId('data')).toHaveTextContent('pending:1')
    expect(screen.getByTestId('error')).toBeEmptyDOMElement()
    await advance(3000)
    expect(screen.getByTestId('data')).toHaveTextContent('pending:1')
    expect(screen.getByTestId('error')).toHaveTextContent('Status unknown. Reload to try again.')
    await advance(6000)
    expect(load).toHaveBeenCalledTimes(4)
  })

  it('reloads and clears status unknown after it is shown', async () => {
    const load = vi.fn()
      .mockResolvedValueOnce({ value: 'pending', active: true })
      .mockRejectedValueOnce(new Error('offline one'))
      .mockRejectedValueOnce(new Error('offline two'))
      .mockRejectedValueOnce(new Error('offline three'))
      .mockResolvedValueOnce({ value: 'recovered', active: false })
    renderHarness(load, (data) => data.active)
    await settle()
    await advance(9000)
    expect(screen.getByTestId('error')).toHaveTextContent('Status unknown')
    fireEvent.click(screen.getByRole('button', { name: 'Reload' }))
    await settle()
    expect(screen.getByTestId('data')).toHaveTextContent('recovered:1')
    expect(screen.getByTestId('error')).toBeEmptyDOMElement()
  })

  it('stops scheduled polling on unmount', async () => {
    const load = vi.fn().mockResolvedValue({ value: 'pending', active: true })
    const view = renderHarness(load, (data) => data.active)
    await settle()
    view.unmount()
    await advance(6000)
    expect(load).toHaveBeenCalledTimes(1)
  })
})
