import { useState, type FormEvent } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import { ArrowUpRight, FolderOpen, Plus, Video, ArrowRight } from 'lucide-react'
import { api } from '../api/client'
import { Modal } from '../components/Modal'
import { ErrorCard, useNotice } from '../components/Notice'
import { useResource } from '../components/useResource'
import { formatDate } from '../components/VideoStatus'

export function Projects() {
  const { data: projects, loading, error, reload } = useResource(api.listProjects)
  const { search } = useOutletContext<{ search: string }>()
  const [creating, setCreating] = useState(false)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  const notify = useNotice()
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const name = String(form.get('name')).trim()
    if (!name) { setFormError('Enter a project name.'); return }
    setBusy(true); setFormError('')
    try {
      await api.createProject({ name, description: String(form.get('description')).trim() })
      setCreating(false); reload(); notify('Project created. Ready for your first video.')
    } catch (reason) { setFormError((reason as Error).message) }
    finally { setBusy(false) }
  }
  const filtered = projects?.filter((project) => `${project.name} ${project.description}`.toLowerCase().includes(search.toLowerCase())) ?? []
  return <>
    <div className="breadcrumb">Workspace <span>/</span> Projects</div>
    <div className="page-heading"><div><h1>Projects</h1><p className="muted">A home for every idea. Organize your videos into projects.</p></div>
      <button className="button" onClick={() => { setCreating(true); setFormError('') }}><Plus size={18} />Create project</button>
    </div>
    <section className="welcome-panel card"><div className="welcome-icon"><FolderOpen size={28} /></div>
      <div><span className="eyebrow">MAKE ROOM FOR YOUR NEXT IDEA</span><h2>Great things start with a project.</h2><p>Bring your videos together, keep track of uploads, and take it from there.</p></div>
      <span className="welcome-arrow" aria-hidden="true"><ArrowUpRight size={28} /></span>
    </section>
    <div className="section-heading"><h2>All projects {projects && <span className="count">{projects.length}</span>}</h2><span className="small muted">Your library, at a glance</span></div>
    {loading && <div className="loading-state" role="status">Loading projects…</div>}
    {error && <ErrorCard message={error} retry={reload} />}
    {!loading && !error && <div className="project-grid">
      {filtered.map((project) => <Link className="project-card card" to={`/projects/${encodeURIComponent(project.projectId)}/videos`} key={project.projectId}>
        <div className="project-card-top"><span className="folder-icon"><FolderOpen size={24} /></span><ArrowUpRight className="card-arrow" size={18} /></div>
        <h3>{project.name}</h3><p className="project-description">{project.description || 'A fresh space for your next video.'}</p>
        <div className="project-meta"><span><Video size={15} />{project.videoCount} {project.videoCount === 1 ? 'video' : 'videos'}</span><span>{formatDate(project.createdAt)}</span></div>
      </Link>)}
      {!search && <button className="new-project-card card" onClick={() => { setCreating(true); setFormError('') }}><span className="new-project-icon"><Plus /></span><strong>Create a new project</strong><span className="small muted">Give your next idea a space of its own</span><ArrowRight size={18} /></button>}
    </div>}
    {!loading && !error && filtered.length === 0 && <p className="empty-message">{search ? 'No projects match your search.' : 'No projects yet. Create your first project to get started.'}</p>}
    <div className="page-footnote"><span className="small muted">A little structure. A lot of possibility.</span></div>
    {creating && <Modal title="Create a project" onClose={() => { if (!busy) setCreating(false) }}>
      <p className="muted">Bring related videos together in one place.</p><form onSubmit={create}>
        <label>Project name<input name="name" placeholder="e.g. Product launch" maxLength={100} required disabled={busy} /></label>
        <label>Description <span className="muted">(optional)</span><textarea name="description" placeholder="What is this project about?" maxLength={1000} rows={3} disabled={busy} /></label>
        {formError && <p className="inline-error" role="alert">{formError}</p>}
        <div className="modal-actions"><button className="button" type="button" onClick={() => setCreating(false)} disabled={busy}>Cancel</button><button className="button button-outline" disabled={busy}>{busy ? 'Creating…' : 'Create project'}</button></div>
      </form>
    </Modal>}
  </>
}
