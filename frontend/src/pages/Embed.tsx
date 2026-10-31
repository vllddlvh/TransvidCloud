import { lazy, Suspense, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { useResource } from '../components/useResource'
import { isVideoActive } from '../components/video'

const HlsPlayer = lazy(() => import('../player/HlsPlayer').then((module) => ({ default: module.HlsPlayer })))

const UNAVAILABLE = 'Video is not available yet.'

export function Embed() {
  const [params] = useSearchParams()
  const videoId = params.get('video') ?? ''
  const load = useCallback(async () => {
    if (!videoId) return null
    try { return await api.getPublicVideo(videoId) } catch { return null }
  }, [videoId])
  const { data: video, loading } = useResource(load, (data) => data !== null && isVideoActive(data))
  return <main className="embed">
    {loading
      ? <p className="embed-status" role="status">Loading video…</p>
      : video?.status === 'COMPLETED' && video.assets
        ? <Suspense fallback={<p className="embed-status" role="status">Loading video…</p>}>
          <HlsPlayer assets={video.assets} />
        </Suspense>
        : <section className="embed-unavailable card" role="alert"><h1>{UNAVAILABLE}</h1></section>}
  </main>
}
