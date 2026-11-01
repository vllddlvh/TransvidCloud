import { useEffect, useRef, useState } from 'react'
import Hls from 'hls.js'
import type { Video } from '../api/types'

type Assets = NonNullable<Video['assets']>
const PLAYBACK_ERROR = 'Playback failed. Check your connection and try again.'
const UNSUPPORTED_ERROR = 'HLS playback is not supported in this browser.'

export function HlsPlayer({ assets, autoplay = false }: { assets: Assets; autoplay?: boolean }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [manifestState, setManifestState] = useState<'pending' | 'parsed'>('pending')
  const [error, setError] = useState('')
  const [retryRevision, setRetryRevision] = useState(0)

  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    let hls: Hls | null = null
    let disposed = false
    // React owns the poster prop, but StrictMode's cleanup/re-run removes it on the second pass.
    if (assets.thumbnail) video.poster = assets.thumbnail
    setManifestState('pending')
    setError('')

    const play = () => {
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
      hls.loadSource(assets.hls)
      hls.attachMedia(video)
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = assets.hls
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
  }, [assets.hls, assets.thumbnail, autoplay, retryRevision])

  return <div className="player-shell">
    <video
      ref={videoRef}
      className="hls-player"
      controls
      playsInline
      preload="metadata"
      poster={assets.thumbnail}
      data-manifest-state={manifestState}
      aria-label="Video player"
    />
    {error && <div className="player-error card" role="alert">
      <p>{error}</p>
      <button className="button" onClick={() => setRetryRevision((value) => value + 1)}>Try again</button>
    </div>}
  </div>
}
