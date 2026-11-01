import { useCallback, useState, type FormEvent } from 'react'
import { Link, useNavigate, useOutletContext, useParams } from 'react-router-dom'
import { ArrowUpRight, FileVideo, KeyRound, Pencil, Trash2, UploadCloud } from 'lucide-react'
import { api } from '../api/client'
import { ApiKeysModal } from '../components/ApiKeysModal'
import { Modal } from '../components/Modal'
import { ErrorCard, useNotice } from '../components/Notice'
import { useResource } from '../components/useResource'
import { VideoStatus, formatBytes, formatDate } from '../components/VideoStatus'
import { hasActiveVideo, formatDuration } from '../components/video'
import { UploadDialog } from '../components/UploadDialog'

export function Videos() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { search } = useOutletContext<{ search: string }>()
  const [extraVideos, setExtraVideos] = useState<Awaited<ReturnType<typeof api.listVideos>>>([])
  const [cursorState, setCursorState] = useState<{ nextCursor: string | null; hasMore: boolean } | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const load = useCallback(async () => {
    const [project, page] = await Promise.all([api.getProject(id), api.listVideosPage(id)])
    setExtraVideos([])
    setCursorState({ nextCursor: page.nextCursor ?? null, hasMore: page.hasMore })
    return { project, videos: page.items, nextCursor: page.nextCursor ?? null, hasMore: page.hasMore }
  }, [id])
  const { data, loading, error, reload } = useResource(load, ({ videos }) => hasActiveVideo(videos))
  const [uploading, setUploading] = useState(false)
  const [managingKeys, setManagingKeys] = useState(false)
  const [editing, setEditing] = useState(false)
  const [deletingProject, setDeletingProject] = useState(false)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')
  const [filter, setFilter] = useState('All videos')
  const notify = useNotice()

  const allVideos = data
    ? [
        ...data.videos,
        ...extraVideos.filter((extra) => !data.videos.some((v) => v.videoId === extra.videoId)),
      ]
    : []
  const hasMore = cursorState ? cursorState.hasMore : Boolean(data?.hasMore)
  const nextCursor = cursorState ? cursorState.nextCursor : (data?.nextCursor ?? null)

  async function loadMoreVideos() {
    if (!nextCursor || loadingMore) return
    setLoadingMore(true)
    try {
      const page = await api.listVideosPage(id, { cursor: nextCursor })
      setExtraVideos((prev) => [...prev, ...page.items])
      setCursorState({ nextCursor: page.nextCursor ?? null, hasMore: page.hasMore })
    } catch (reason) {
      notify(reason instanceof Error ? reason.message : 'Unable to load more videos.')
    } finally {
      setLoadingMore(false)
    }
  }

  const videos = allVideos.filter((video) => {
    const label = `${video.title ?? ''} ${video.filename}`.toLowerCase()
    return label.includes(search.toLowerCase()) && (
      filter === 'All videos'
        || (filter === 'Ready'
          ? video.status === 'COMPLETED'
          : filter === 'Needs attention'
            ? video.status.startsWith('FAILED_')
            : ['UPLOADING', 'PENDING', 'PROCESSING'].includes(video.status))
    )
  }) ?? []

  async function saveProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!data?.project || busy) return
    const form = new FormData(event.currentTarget)
    const name = String(form.get('name') ?? '').trim()
    const description = String(form.get('description') ?? '').trim()
    const webhookUrl = String(form.get('webhookUrl') ?? '').trim()
    if (!name) {
      setActionError('Enter a project name.')
      return
    }
    setBusy(true)
    setActionError('')
    try {
      await api.updateProject(data.project.projectId, {
        name,
        description,
        webhookUrl: webhookUrl || null,
      })
      setEditing(false)
      reload()
      notify('Project updated.')
    } catch (reason) {
      setActionError(reason instanceof Error ? reason.message : 'Unable to update project.')
    } finally {
      setBusy(false)
    }
  }

  async function confirmDeleteProject() {
    if (!data?.project || busy) return
    setBusy(true)
    setActionError('')
    try {
      await api.deleteProject(data.project.projectId)
      notify('Project deleted.')
      navigate('/projects')
    } catch (reason) {
      setActionError(reason instanceof Error ? reason.message : 'Unable to delete project.')
      setBusy(false)
    }
  }

  return <>
    <div className="breadcrumb"><Link to="/projects">Projects</Link><span>/</span>{data?.project?.name ?? 'Project videos'}</div>
    <div className="page-heading">
      <div>
        <h1>{data?.project?.name ?? 'Project videos'}</h1>
        <p className="muted">{data?.project?.description || 'Every video, from first upload to the final cut.'}</p>
        {data?.project?.webhookUrl && (
          <p className="small muted">Webhook: <code>{data.project.webhookUrl}</code></p>
        )}
      </div>
      <div className="heading-actions">
        <button className="button button-outline" type="button" onClick={() => setManagingKeys(true)} disabled={!data?.project}>
          <KeyRound size={16} />API keys
        </button>
        <button className="button button-outline" type="button" onClick={() => { setEditing(true); setActionError('') }} disabled={!data?.project}>
          <Pencil size={16} />Edit project
        </button>
        <button className="button button-outline button-danger" type="button" onClick={() => { setDeletingProject(true); setActionError('') }} disabled={!data?.project}>
          <Trash2 size={16} />Delete project
        </button>
        <button className="button" type="button" onClick={() => setUploading(true)} disabled={!data?.project}>
          <UploadCloud size={18} />Upload video
        </button>
      </div>
    </div>
    <div className="video-toolbar"><div className="filter-group" aria-label="Filter videos">{['All videos', 'In progress', 'Ready', 'Needs attention'].map((name) => <button className={`filter-pill ${filter === name ? 'selected' : ''}`} aria-pressed={filter === name} key={name} onClick={() => setFilter(name)}>{name}</button>)}</div><span className="small muted">{allVideos.length} videos</span></div>
    {loading && <div className="loading-state" role="status">Loading videos…</div>}
    {error && !data && <ErrorCard message={error} retry={reload} />}
    {error && data && <div className="status-unknown card" role="alert"><span>{error}</span><button className="button" onClick={reload}>Reload</button></div>}
    {!loading && (data || !error) && (videos.length ? <div className="video-list">
      <div className="video-list-heading"><span>VIDEO</span><span>STATUS</span><span>ADDED</span><span /></div>
      {videos.map((video) => <Link className="video-row card" key={video.videoId} to={`/videos/${encodeURIComponent(video.videoId)}`}>
        <div className="video-name">
          {video.assets?.thumbnail
            ? <span className="video-thumbnail"><img src={video.assets.thumbnail} alt="" /></span>
            : <span className="video-thumbnail"><FileVideo aria-hidden="true" /></span>}
          <div><h3>{video.title || video.filename}</h3><span className="small muted">{formatBytes(video.sizeBytes)} · {video.contentType} · {formatDuration(video.durationSeconds)}</span></div>
        </div>
        <VideoStatus status={video.status} /><span className="video-date small muted">{formatDate(video.createdAt)}</span><ArrowUpRight size={18} />
      </Link>)}
    </div> : <div className="empty-state card"><FileVideo size={32} /><h2>{search || filter !== 'All videos' ? 'No matching videos' : 'Your first video belongs here'}</h2><p className="muted">{search || filter !== 'All videos' ? 'Try another search or filter.' : 'Upload a video to get this project moving.'}</p></div>)}
    {hasMore && nextCursor && (
      <div className="modal-actions">
        <button className="button button-outline" type="button" onClick={() => void loadMoreVideos()} disabled={loadingMore}>
          {loadingMore ? 'Loading more…' : 'Load more videos'}
        </button>
      </div>
    )}
    <p className="page-footnote small muted">Confirmed uploads update automatically while processing.</p>
    {uploading && <UploadDialog projectId={id} onClose={() => { setUploading(false); reload() }} onComplete={() => { setUploading(false); reload(); notify('Video uploaded and confirmed. Status: Pending.') }} />}
    {managingKeys && data?.project && (
      <ApiKeysModal
        projectId={data.project.projectId}
        projectName={data.project.name}
        onClose={() => setManagingKeys(false)}
      />
    )}
    {editing && data?.project && (
      <Modal title="Edit project" onClose={() => { if (!busy) setEditing(false) }}>
        <p className="muted">Update your project details and optional webhook notification endpoint.</p>
        <form onSubmit={saveProject}>
          <label>
            Project name
            <input name="name" defaultValue={data.project.name} maxLength={120} required disabled={busy} />
          </label>
          <label>
            Description <span className="muted">(optional)</span>
            <textarea name="description" defaultValue={data.project.description} maxLength={500} rows={3} disabled={busy} />
          </label>
          <label>
            Webhook URL <span className="muted">(optional)</span>
            <input name="webhookUrl" type="url" defaultValue={data.project.webhookUrl ?? ''} placeholder="https://example.com/webhooks/transvid" disabled={busy} />
          </label>
          {actionError && <p className="inline-error" role="alert">{actionError}</p>}
          <div className="modal-actions">
            <button className="button" type="button" onClick={() => setEditing(false)} disabled={busy}>Cancel</button>
            <button className="button button-outline" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save changes'}</button>
          </div>
        </form>
      </Modal>
    )}
    {deletingProject && data?.project && (
      <Modal title="Delete project" onClose={() => { if (!busy) setDeletingProject(false) }}>
        <p className="muted">
          Are you sure you want to delete <strong>{data.project.name}</strong>? Projects with videos that are still uploading or processing cannot be deleted.
        </p>
        {actionError && <p className="inline-error" role="alert">{actionError}</p>}
        <div className="modal-actions">
          <button className="button" type="button" onClick={() => setDeletingProject(false)} disabled={busy}>Cancel</button>
          <button className="button button-outline button-danger" type="button" onClick={() => void confirmDeleteProject()} disabled={busy}>
            {busy ? 'Deleting…' : 'Confirm delete project'}
          </button>
        </div>
      </Modal>
    )}
  </>
}
