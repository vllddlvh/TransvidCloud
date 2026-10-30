import { useCallback, useState } from 'react'
import { Link, useOutletContext, useParams } from 'react-router-dom'
import { ArrowUpRight, FileVideo, UploadCloud } from 'lucide-react'
import { api } from '../api/client'
import { ErrorCard, useNotice } from '../components/Notice'
import { useResource } from '../components/useResource'
import { VideoStatus, formatBytes, formatDate } from '../components/VideoStatus'
import { hasActiveVideo, formatDuration } from '../components/video'
import { UploadDialog } from '../components/UploadDialog'

export function Videos() {
  const { id = '' } = useParams()
  const { search } = useOutletContext<{ search: string }>()
  const load = useCallback(async () => {
    const [projects, videos] = await Promise.all([api.listProjects(), api.listVideos(id)])
    return { project: projects.find((project) => project.projectId === id), videos }
  }, [id])
  const { data, loading, error, reload } = useResource(load, ({ videos }) => hasActiveVideo(videos))
  const [uploading, setUploading] = useState(false)
  const [filter, setFilter] = useState('All videos')
  const notify = useNotice()
  const videos = data?.videos.filter((video) => video.filename.toLowerCase().includes(search.toLowerCase()) && (
    filter === 'All videos' || (filter === 'Ready' ? video.status === 'COMPLETED' : filter === 'Needs attention' ? video.status.startsWith('FAILED_') : ['UPLOADING', 'PENDING', 'PROCESSING'].includes(video.status))
  )) ?? []
  return <>
    <div className="breadcrumb"><Link to="/projects">Projects</Link><span>/</span>{data?.project?.name ?? 'Project videos'}</div>
    <div className="page-heading"><div><h1>{data?.project?.name ?? 'Project videos'}</h1><p className="muted">{data?.project?.description || 'Every video, from first upload to the final cut.'}</p></div>
      <button className="button" onClick={() => setUploading(true)} disabled={!data?.project}><UploadCloud size={18} />Upload video</button>
    </div>
    <div className="video-toolbar"><div className="filter-group" aria-label="Filter videos">{['All videos', 'In progress', 'Ready', 'Needs attention'].map((name) => <button className={`filter-pill ${filter === name ? 'selected' : ''}`} aria-pressed={filter === name} key={name} onClick={() => setFilter(name)}>{name}</button>)}</div><span className="small muted">{data?.videos.length ?? 0} videos</span></div>
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
          <div><h3>{video.filename}</h3><span className="small muted">{formatBytes(video.sizeBytes)} · {video.contentType} · {formatDuration(video.durationSeconds)}</span></div>
        </div>
        <VideoStatus status={video.status} /><span className="video-date small muted">{formatDate(video.createdAt)}</span><ArrowUpRight size={18} />
      </Link>)}
    </div> : <div className="empty-state card"><FileVideo size={32} /><h2>{search || filter !== 'All videos' ? 'No matching videos' : 'Your first video belongs here'}</h2><p className="muted">{search || filter !== 'All videos' ? 'Try another search or filter.' : 'Upload a video to get this project moving.'}</p></div>)}
    <p className="page-footnote small muted">Confirmed uploads update automatically while processing.</p>
    {uploading && <UploadDialog projectId={id} onClose={() => { setUploading(false); reload() }} onComplete={() => { setUploading(false); reload(); notify('Video uploaded and confirmed. Status: Pending.') }} />}
  </>
}
