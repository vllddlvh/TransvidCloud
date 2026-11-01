import { lazy, Suspense, useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { CircleAlert, FileVideo } from 'lucide-react'
import { api } from '../api/client'
import { useResource } from '../components/useResource'
import { ErrorCard, useNotice } from '../components/Notice'
import { copyText } from '../components/clipboard'
import { VideoStatus, formatBytes, formatDate } from '../components/VideoStatus'
import { failureGuidance, isVideoActive } from '../components/video'

const HlsPlayer = lazy(() => import('../player/HlsPlayer').then((module) => ({ default: module.HlsPlayer })))

export function VideoDetail() {
  const { id = '' } = useParams()
  const notify = useNotice()
  const load = useCallback(() => api.getVideo(id), [id])
  const { data: video, loading, error, reload } = useResource(load, isVideoActive)
  if (loading) return <p role="status">Loading video…</p>
  if (error && !video) return <ErrorCard message={error} retry={reload} />
  if (!video) return null
  const failed = video.status.startsWith('FAILED_')
  const copy = async (value: string, message: string) => {
    try {
      await copyText(value)
      notify(message)
    } catch (failure) {
      notify(failure instanceof Error ? failure.message : 'Copy failed.')
    }
  }
  const copyStreamingLink = () => video.assets
    && copy(new URL(video.assets.hls, window.location.origin).href, 'Streaming link copied.')
  const copyEmbed = () => copy(
    `<iframe src="${window.location.origin}/embed?video=${encodeURIComponent(video.videoId)}&project=${encodeURIComponent(video.projectId)}" width="640" height="360" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen frameborder="0"></iframe>`,
    'Embed code copied.',
  )
  return <>
    <div className="breadcrumb"><Link to="/projects">Projects</Link><span>/</span><Link to={`/projects/${encodeURIComponent(video.projectId)}/videos`}>Videos</Link><span>/</span>Video details</div>
    <div className="page-heading"><div><h1>{video.filename}</h1><p className="muted">Video details and upload status.</p></div><VideoStatus status={video.status} /></div>
    {error && <div className="status-unknown card" role="alert"><span>{error}</span><button className="button" onClick={reload}>Reload</button></div>}
    <div className="detail-grid">
      {failed
        ? <section className="player-placeholder failure-panel card" role="alert">
          <CircleAlert size={32} aria-hidden="true" />
          <h2>Processing failed</h2>
          <p className="inline-error">Error code: {video.errorCode ?? 'UNKNOWN'}</p>
          <p className="muted">{failureGuidance(video)}</p>
        </section>
        : video.status === 'COMPLETED'
          ? video.assets
            ? <div className="player-column">
              <Suspense fallback={<p className="player-placeholder card" role="status">Loading player…</p>}>
                <HlsPlayer assets={video.assets} autoplay />
              </Suspense>
              <section className="card video-actions">
                <h2>Share</h2>
                <p className="muted">Copy the streaming link or embed this video anywhere.</p>
                <div className="action-row">
                  <button className="button" onClick={() => void copyStreamingLink()}>Copy streaming link</button>
                  <button className="button" onClick={() => void copyEmbed()}>Copy embed code</button>
                </div>
              </section>
            </div>
            : <section className="player-placeholder card"><FileVideo size={32} aria-hidden="true" /><h2>Video ready</h2><p className="muted">Playback assets are unavailable.</p></section>
          : <section className="player-placeholder card"><FileVideo size={32} aria-hidden="true" /><h2>{video.status === 'PROCESSING' ? 'Processing your video' : 'Waiting to process'}</h2><p className="muted">This page updates automatically when the status changes.</p></section>}
      <section className="card detail-info"><h2>File information</h2><dl><dt>File name</dt><dd>{video.filename}</dd><dt>Size</dt><dd>{formatBytes(video.sizeBytes)}</dd><dt>Format</dt><dd>{video.contentType}</dd><dt>Added</dt><dd>{formatDate(video.createdAt)}</dd><dt>Video ID</dt><dd className="small">{video.videoId}</dd></dl>
        {video.assets?.hls && <><h3>HLS asset</h3><code className="asset-path">{video.assets.hls}</code></>}
      </section>
    </div>
  </>
}
