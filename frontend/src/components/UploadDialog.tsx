import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { Check, FileVideo, UploadCloud } from 'lucide-react'
import { api } from '../api/client'
import type { UploadTicket } from '../api/types'
import { putFile, UploadError } from '../api/transport'
import { validateVideoFile, videoContentType } from '../api/upload'
import { Modal } from './Modal'
import { formatBytes } from './VideoStatus'

export const UploadTransportContext = createContext(putFile)
type Phase = 'idle' | 'preparing' | 'uploading' | 'confirming' | 'failed'

export function UploadDialog({ projectId, onComplete, onClose }: { projectId: string; onComplete: () => void; onClose: () => void }) {
  const upload = useContext(UploadTransportContext)
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [validation, setValidation] = useState('')
  const [error, setError] = useState('')
  const [phase, setPhase] = useState<Phase>('idle')
  const [progress, setProgress] = useState(0)
  const ticket = useRef<UploadTicket | null>(null)
  const transferred = useRef(false)
  const controller = useRef<AbortController | null>(null)
  const active = useRef(true)
  useEffect(() => {
    active.current = true
    return () => { active.current = false; controller.current?.abort() }
  }, [])
  const busy = ['preparing', 'uploading', 'confirming'].includes(phase)
  const close = () => {
    // Confirmation is idempotent and the list reload reveals its real result, so waiting for it is never a trap.
    active.current = false
    controller.current?.abort()
    onClose()
  }
  function select(selected: File | undefined) {
    setFile(selected ?? null)
    setTitle(selected ? selected.name : '')
    setValidation(selected ? validateVideoFile(selected) ?? '' : '')
    setError(''); setPhase('idle'); setProgress(0)
    ticket.current = null; transferred.current = false
  }
  async function start() {
    if (!file || validation || busy) return
    const trimmedTitle = title.trim()
    if (!trimmedTitle || trimmedTitle.length > 200) {
      setError('Enter a video title between 1 and 200 characters.')
      return
    }
    const abort = new AbortController()
    controller.current = abort
    setError('')
    try {
      if (!ticket.current || (!transferred.current && Date.parse(ticket.current.expiresAt) <= Date.now())) {
        setPhase('preparing')
        ticket.current = await api.createVideo(projectId, {
          filename: file.name,
          sizeBytes: file.size,
          contentType: videoContentType(file),
          ...(trimmedTitle !== file.name ? { title: trimmedTitle } : {}),
        })
        transferred.current = false
      }
      if (!active.current || abort.signal.aborted) return
      if (!transferred.current) {
        setPhase('uploading'); setProgress(0)
        await upload({ url: ticket.current.uploadUrl, headers: ticket.current.headers, file, signal: abort.signal, onProgress: setProgress })
        transferred.current = true
      }
      if (!active.current || abort.signal.aborted) return
      setPhase('confirming')
      await api.confirmVideo(ticket.current.video.videoId)
      if (active.current) onComplete()
    } catch (reason) {
      if (!active.current || abort.signal.aborted) return
      if (reason instanceof UploadError && reason.status === 403) ticket.current = null
      setError((reason as Error).message)
      setPhase('failed')
    }
  }
  const step = phase === 'preparing' ? 1 : phase === 'uploading' ? 2 : phase === 'confirming' || transferred.current ? 3 : 0
  return <Modal title="Upload a video" onClose={close}>
    <p className="muted">Add a video to this project. We'll take it from here.</p>
    <label className={`upload-dropzone ${busy ? 'upload-disabled' : ''}`}
      onDragOver={(event) => event.preventDefault()}
      onDrop={(event) => { event.preventDefault(); if (!busy) select(event.dataTransfer.files[0]) }}>
      <UploadCloud size={32} /><strong>{file ? 'Choose a different video' : 'Choose a video or drop it here'}</strong>
      <span className="small muted">MP4, MOV, MKV, WEBM · Up to 200 MB</span>
      <input aria-label="Video file" type="file" accept=".mp4,.mov,.mkv,.webm" onChange={(event) => select(event.target.files?.[0])} disabled={busy} />
    </label>
    {file && <>
      <div className="selected-file"><FileVideo /><div><strong>{file.name}</strong><span className="small muted">{formatBytes(file.size)}</span></div></div>
      <label>
        Video title
        <input
          name="title"
          placeholder="Enter a title for this video"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          maxLength={200}
          required
          disabled={busy}
        />
      </label>
    </>}
    {validation && <p className="inline-error" role="alert">{validation}</p>}
    <ol className="upload-steps">{['Prepare upload', 'Upload file', 'Confirm video'].map((label, index) => <li key={label} className={step === index + 1 ? 'current' : ''}><span>{step > index + 1 ? <Check size={14} /> : index + 1}</span>{label}</li>)}</ol>
    {(busy || phase === 'failed') && <div className="upload-progress" aria-live="polite">
      <div><span>{phase === 'preparing' ? 'Preparing a secure upload…' : phase === 'confirming' ? 'Confirming your video…' : phase === 'failed' ? 'Upload paused' : 'Uploading your video…'}</span><strong>{progress}%</strong></div>
      <progress aria-label="Upload progress" max={100} value={progress} />
    </div>}
    {error && <p className="inline-error" role="alert">{error}</p>}
    <p className="small muted">Your file uploads directly to storage. Keep this window open until confirmation.</p>
    <div className="modal-actions"><button className="button" onClick={close}>{phase === 'confirming' ? 'Close' : busy ? 'Cancel upload' : 'Cancel'}</button>
      <button className="button button-outline" disabled={!file || !!validation || busy || !title.trim()} onClick={start}>{phase === 'failed' ? 'Retry upload' : busy ? 'Uploading…' : 'Start upload'}</button>
    </div>
  </Modal>
}
