import { StrictMode } from 'react'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Video } from '../api/types'
import { HlsPlayer } from './HlsPlayer'

const hlsMock = vi.hoisted(() => ({
  isSupported: vi.fn(),
  instances: [] as object[],
  loadSource: vi.fn(),
  attachMedia: vi.fn(),
  on: vi.fn(),
  destroy: vi.fn(),
}))

vi.mock('hls.js', () => {
  class MockHls {
    static isSupported = hlsMock.isSupported
    static Events = { MANIFEST_PARSED: 'hlsManifestParsed', ERROR: 'hlsError' }
    constructor() { hlsMock.instances.push(this) }
    loadSource(...args: unknown[]) { return hlsMock.loadSource(...args) }
    attachMedia(...args: unknown[]) { return hlsMock.attachMedia(...args) }
    on(...args: unknown[]) { return hlsMock.on(...args) }
    destroy() { return hlsMock.destroy() }
  }
  return { default: MockHls, ErrorTypes: { NETWORK_ERROR: 'networkError', MEDIA_ERROR: 'mediaError' } }
})

const assets: NonNullable<Video['assets']> = {
  hls: '/media/demo-project/demo-completed/hls/master.m3u8',
  hls360: '/media/demo-project/demo-completed/hls/360p/index.m3u8',
  hls720: '/media/demo-project/demo-completed/hls/720p/index.m3u8',
  thumbnail: '/media/demo-project/demo-completed/thumb/default.jpg',
}
type HlsHandler = (event: string, data: { fatal: boolean; type: string }) => void

function handler(eventName: string, callIndex = 0): HlsHandler {
  const calls = hlsMock.on.mock.calls.filter(([name]) => name === eventName)
  const call = calls[callIndex]
  if (!call) throw new Error(`Missing ${eventName} handler`)
  return call[1] as HlsHandler
}

beforeEach(() => {
  vi.clearAllMocks()
  hlsMock.instances.length = 0
  hlsMock.isSupported.mockReturnValue(true)
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {})
})

describe('HlsPlayer', () => {
  it('loads and attaches the master playlist before attempting autoplay', () => {
    render(<HlsPlayer assets={assets} autoplay />)
    const video = screen.getByLabelText('Video player')
    expect(video).toHaveAttribute('poster', assets.thumbnail)
    expect(hlsMock.loadSource).toHaveBeenCalledWith(assets.hls)
    expect(hlsMock.attachMedia).toHaveBeenCalledWith(video)
    expect(HTMLMediaElement.prototype.play).not.toHaveBeenCalled()

    act(() => { handler('hlsManifestParsed')('hlsManifestParsed', { fatal: false, type: '' }) })

    expect(video).toHaveAttribute('data-manifest-state', 'parsed')
    expect(HTMLMediaElement.prototype.play).toHaveBeenCalledTimes(1)
  })

  it('does not autoplay when the option is disabled', () => {
    render(<HlsPlayer assets={assets} />)
    act(() => { handler('hlsManifestParsed')('hlsManifestParsed', { fatal: false, type: '' }) })
    expect(screen.getByLabelText('Video player')).toHaveAttribute('data-manifest-state', 'parsed')
    expect(HTMLMediaElement.prototype.play).not.toHaveBeenCalled()
  })

  it('uses native HLS when hls.js is unsupported', () => {
    hlsMock.isSupported.mockReturnValue(false)
    vi.spyOn(HTMLMediaElement.prototype, 'canPlayType').mockReturnValue('probably')
    render(<HlsPlayer assets={assets} autoplay />)
    const video = screen.getByLabelText('Video player')
    expect(video).toHaveAttribute('src', assets.hls)
    expect(hlsMock.loadSource).not.toHaveBeenCalled()
    fireEvent.loadedMetadata(video)
    expect(video).toHaveAttribute('data-manifest-state', 'parsed')
    expect(HTMLMediaElement.prototype.play).toHaveBeenCalledTimes(1)
  })

  it('shows fatal errors and reinitializes on retry', () => {
    render(<HlsPlayer assets={assets} />)
    act(() => { handler('hlsError')('hlsError', { fatal: true, type: 'networkError' }) })
    expect(screen.getByRole('alert')).toHaveTextContent('Playback failed. Check your connection and try again.')
    expect(hlsMock.destroy).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(hlsMock.instances).toHaveLength(2)
  })

  it('reports unsupported browsers without creating an hls instance', () => {
    hlsMock.isSupported.mockReturnValue(false)
    vi.spyOn(HTMLMediaElement.prototype, 'canPlayType').mockReturnValue('')
    render(<HlsPlayer assets={assets} />)
    expect(screen.getByRole('alert')).toHaveTextContent('HLS playback is not supported in this browser.')
    expect(hlsMock.instances).toHaveLength(0)
  })

  it('keeps the poster after a StrictMode effect remount', () => {
    render(<StrictMode><HlsPlayer assets={assets} /></StrictMode>)
    expect(screen.getByLabelText('Video player')).toHaveAttribute('poster', assets.thumbnail)
  })

  it('destroys the previous instance and clears media on source changes and unmount', () => {
    const first = render(<HlsPlayer assets={assets} />)
    const video = screen.getByLabelText('Video player')
    const nextAssets = { ...assets, hls: '/media/demo-project/next/hls/master.m3u8' }
    first.rerender(<HlsPlayer assets={nextAssets} />)
    expect(hlsMock.destroy).toHaveBeenCalledTimes(1)
    expect(hlsMock.instances).toHaveLength(2)
    first.unmount()
    expect(hlsMock.destroy).toHaveBeenCalledTimes(2)
    expect(video).not.toHaveAttribute('src')
    expect(video).not.toHaveAttribute('poster')
  })
})
