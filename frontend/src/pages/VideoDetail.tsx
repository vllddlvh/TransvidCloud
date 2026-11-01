import { lazy, Suspense, useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { CircleAlert, FileVideo, Trash2 } from 'lucide-react'
import { api } from '../api/client'
import { useResource } from '../components/useResource'
import { Modal } from '../components/Modal'
import { ErrorCard, useNotice } from '../components/Notice'
import { copyText } from '../components/clipboard'
import { VideoStatus, formatBytes, formatDate } from '../components/VideoStatus'
import { failureGuidance, isVideoActive } from '../components/video'

const HlsPlayer = lazy(() => import('../player/HlsPlayer').then((module) => ({ default: module.HlsPlayer })))

export function VideoDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const notify = useNotice()
  const load = useCallback(() => api.getVideo(id), [id])
  const { data: video, loading, error, reload } = useResource(load, isVideoActive)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState('')

  if (loading) return <p role="status">Loading video…</p>
  if (error && !video) return <ErrorCard message={error} retry={reload} />
  if (!video) return null

  const failed = video.status.startsWith('FAILED_')
  const processing = video.status === 'PROCESSING'
  const displayTitle = video.title || video.filename

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
  const copyAssetUrl = (path: string, label: string) =>
    copy(new URL(path, window.location.origin).href, `${label} link copied.`)

  async function handleDelete() {
    if (!video || deleting) return
    setDeleting(true)
    setDeleteError('')
    try {
      await api.deleteVideo(video.videoId)
      notify('Video deleted.')
      navigate(`/projects/${encodeURIComponent(video.projectId)}/videos`)
    } catch (reason) {
      setDeleteError(reason instanceof Error ? reason.message : 'Unable to delete video.')
      setDeleting(false)
    }
  }

  return <>
    <div className="breadcrumb"><Link to="/projects">Projects</Link><span>/</span><Link to={`/projects/${encodeURIComponent(video.projectId)}/videos`}>Videos</Link><span>/</span>Video details</div>
    <div className="page-heading">
      <div><h1>{displayTitle}</h1><p className="muted">Video details and upload status.</p></div>
      <div className="heading-actions">
        <VideoStatus status={video.status} />
        <button
          className="button button-outline button-danger"
          type="button"
          disabled={processing}
          title={processing ? 'Cannot delete video while it is actively processing' : 'Delete video'}
          onClick={() => { setConfirmDelete(true); setDeleteError('') }}
        >
          <Trash2 size={16} />Delete video
        </button>
      </div>
    </div>
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
      <section className="card detail-info">
        <h2>File information</h2>
        <dl>
          {video.title && video.title !== video.filename && <><dt>Title</dt><dd>{video.title}</dd></>}
          <dt>File name</dt><dd>{video.filename}</dd>
          <dt>Size</dt><dd>{formatBytes(video.sizeBytes)}</dd>
          <dt>Format</dt><dd>{video.contentType}</dd>
          {typeof video.attemptCount === 'number' && <><dt>Attempts</dt><dd>{video.attemptCount}</dd></>}
          <dt>Added</dt><dd>{formatDate(video.createdAt)}</dd>
          {video.startedAt && <><dt>Started</dt><dd>{formatDate(video.startedAt)}</dd></>}
          {video.completedAt && <><dt>Completed</dt><dd>{formatDate(video.completedAt)}</dd></>}
          <dt>Updated</dt><dd>{formatDate(video.updatedAt)}</dd>
          <dt>Video ID</dt><dd className="small">{video.videoId}</dd>
        </dl>
        {video.assets && <>
          <h3>HLS asset</h3>
          <div className="asset-list" aria-label="Transcoded assets">
            <div className="asset-item">
              <div><strong>Master HLS (ABR)</strong><code className="asset-path">{video.assets.hls}</code></div>
              <button className="button button-outline" type="button" onClick={() => void copyAssetUrl(video.assets!.hls, 'Master HLS')}>Copy</button>
            </div>
            {video.assets.hls720 && (
              <div className="asset-item">
                <div><strong>720p HD HLS</strong><code className="asset-path">{video.assets.hls720}</code></div>
                <button className="button button-outline" type="button" onClick={() => void copyAssetUrl(video.assets!.hls720!, '720p HLS')}>Copy</button>
              </div>
            )}
            {video.assets.hls360 && (
              <div className="asset-item">
                <div><strong>360p SD HLS</strong><code className="asset-path">{video.assets.hls360}</code></div>
                <button className="button button-outline" type="button" onClick={() => void copyAssetUrl(video.assets!.hls360!, '360p HLS')}>Copy</button>
              </div>
            )}
            {video.assets.thumbnail && (
              <div className="asset-item">
                <div><strong>Thumbnail JPEG</strong><code className="asset-path">{video.assets.thumbnail}</code></div>
                <button className="button button-outline" type="button" onClick={() => void copyAssetUrl(video.assets!.thumbnail!, 'Thumbnail')}>Copy</button>
              </div>
            )}
          </div>
        </>}
      </section>
    </div>
    {confirmDelete && (
      <Modal title="Delete video" onClose={() => { if (!deleting) setConfirmDelete(false) }}>
        <p className="muted">Are you sure you want to delete <strong>{displayTitle}</strong>? This action cannot be undone.</p>
        {deleteError && <p className="inline-error" role="alert">{deleteError}</p>}
        <div className="modal-actions">
          <button className="button" type="button" onClick={() => setConfirmDelete(false)} disabled={deleting}>Cancel</button>
          <button className="button button-outline button-danger" type="button" onClick={() => void handleDelete()} disabled={deleting}>
            {deleting ? 'Deleting…' : 'Confirm delete'}
          </button>
        </div>
      </Modal>
    )}
  </>
}
