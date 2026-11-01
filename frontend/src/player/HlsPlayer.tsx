import { useEffect, useRef, useState } from 'react'
import Hls from 'hls.js'
import type { Video } from '../api/types'

type Assets = NonNullable<Video['assets']>
export type PlaybackQuality = 'auto' | '720p' | '360p'
const PLAYBACK_ERROR = 'Playback failed. Check your connection and try again.'
const UNSUPPORTED_ERROR = 'HLS playback is not supported in this browser.'

export function HlsPlayer({ assets, autoplay = false }: { assets: Assets; autoplay?: boolean }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [quality, setQuality] = useState<PlaybackQuality>('auto')
  const [manifestState, setManifestState] = useState<'pending' | 'parsed'>('pending')
  const [error, setError] = useState('')
  const [retryRevision, setRetryRevision] = useState(0)

  const activeQuality: PlaybackQuality =
    quality === '720p' && assets.hls720
      ? '720p'
      : quality === '360p' && assets.hls360
        ? '360p'
        : 'auto'

  const sourceUrl =
    activeQuality === '720p' && assets.hls720
      ? assets.hls720
      : activeQuality === '360p' && assets.hls360
        ? assets.hls360
        : assets.hls

  const hasMultiQuality = Boolean(assets.hls720 || assets.hls360)

  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    let hls: Hls | null = null
    let disposed = false
    const resumeTime = video.currentTime > 0 ? video.currentTime : 0
    // React owns the poster prop, but StrictMode's cleanup/re-run removes it on the second pass.
    if (assets.thumbnail) video.poster = assets.thumbnail
    setManifestState('pending')
    setError('')

    const play = () => {
      if (resumeTime > 0) {
        try { video.currentTime = resumeTime } catch { /* Ignore seek error before buffer ready */ }
      }
      if (autoplay) void video.play().catch(() => { /* Native controls remain available. */ })
    }
    const parsed = () => {
      if (disposed) return
      setManifestState('parsed')
      play()
    }
    const failed = (message: string) => {
      if (disposed) return
      setManifestState('pending')
      setError(message)
    }

    if (Hls.isSupported()) {
      hls = new Hls()
      hls.on(Hls.Events.MANIFEST_PARSED, parsed)
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!data.fatal) return
        failed(PLAYBACK_ERROR)
        hls?.destroy()
        hls = null
      })
      hls.loadSource(sourceUrl)
      hls.attachMedia(video)
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = sourceUrl
      video.addEventListener('loadedmetadata', parsed, { once: true })
    } else {
      failed(UNSUPPORTED_ERROR)
    }

    return () => {
      disposed = true
      video.removeEventListener('loadedmetadata', parsed)
      hls?.destroy()
      hls = null
      video.pause()
      video.removeAttribute('src')
      video.removeAttribute('poster')
      video.load()
    }
  }, [sourceUrl, assets.thumbnail, autoplay, retryRevision])

  return <div className="player-shell">
    <video
      ref={videoRef}
      className="hls-player"
      controls
      playsInline
      preload="metadata"
      poster={assets.thumbnail}
      data-manifest-state={manifestState}
      data-quality={activeQuality}
      aria-label="Video player"
    />
    {hasMultiQuality && (
      <div className="quality-selector" role="group" aria-label="Playback quality">
        <span className="quality-selector-label">Quality:</span>
        <div className="quality-options">
          <button
            type="button"
            className={`filter-pill ${activeQuality === 'auto' ? 'selected' : ''}`}
            aria-pressed={activeQuality === 'auto'}
            onClick={() => setQuality('auto')}
          >
            Auto (ABR)
          </button>
          {assets.hls720 && (
            <button
              type="button"
              className={`filter-pill ${activeQuality === '720p' ? 'selected' : ''}`}
              aria-pressed={activeQuality === '720p'}
              onClick={() => setQuality('720p')}
            >
              720p HD
            </button>
          )}
          {assets.hls360 && (
            <button
              type="button"
              className={`filter-pill ${activeQuality === '360p' ? 'selected' : ''}`}
              aria-pressed={activeQuality === '360p'}
              onClick={() => setQuality('360p')}
            >
              360p SD
            </button>
          )}
        </div>
      </div>
    )}
    {error && <div className="player-error card" role="alert">
      <p>{error}</p>
      <button className="button" onClick={() => setRetryRevision((value) => value + 1)}>Try again</button>
    </div>}
  </div>
}
