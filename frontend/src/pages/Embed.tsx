import { lazy, Suspense, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { readSession } from '../api/session'
import type { Video } from '../api/types'
import { useResource } from '../components/useResource'
import { isVideoActive } from '../components/video'

const HlsPlayer = lazy(() => import('../player/HlsPlayer').then((module) => ({ default: module.HlsPlayer })))

const UNAVAILABLE = 'Video is not available yet.'

export function Embed() {
  const [params] = useSearchParams()
  const videoId = params.get('video') ?? ''
  const projectId = params.get('project') ?? ''
  const load = useCallback(async (): Promise<Video | null> => {
    if (!videoId) return null
    try {
      return await api.getPublicVideo(videoId)
    } catch {
      if (readSession()) {
        try { return await api.getVideo(videoId) } catch { /* Fall through to direct CDN check */ }
      }
      if (projectId && /^[\w-]+$/.test(projectId) && /^[\w-]+$/.test(videoId)) {
        const root = `/media/${projectId}/${videoId}`
        try {
          const probe = await fetch(`${root}/hls/master.m3u8`, { method: 'HEAD' })
          if (probe.ok) {
            const now = new Date().toISOString()
            return {
              videoId,
              projectId,
              filename: videoId,
              sizeBytes: 1,
              contentType: 'video/mp4',
              status: 'COMPLETED',
              createdAt: now,
              updatedAt: now,
              assets: {
                hls: `${root}/hls/master.m3u8`,
                hls360: `${root}/hls/360p/index.m3u8`,
                hls720: `${root}/hls/720p/index.m3u8`,
                thumbnail: `${root}/thumb/default.jpg`,
              },
            }
          }
        } catch { /* Unavailable */ }
      }
      return null
    }
  }, [videoId, projectId])
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
