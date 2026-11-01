import { useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { ArrowRight, FolderOpen, UploadCloud, Layers3 } from 'lucide-react'
import { api, ApiError } from '../api/client'
import { useAuth } from '../components/Auth'
import { Brand } from '../components/Brand'
import { useNotice } from '../components/Notice'

export function Login() {
  const { session, expired, signIn } = useAuth()
  const notify = useNotice()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  if (session) return <Navigate to="/projects" replace />
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setBusy(true); setError('')
    try {
      signIn(await api.login({ email: String(form.get('email')).trim(), password: String(form.get('password')) }))
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : 'Sign in failed. Please try again.'
      setError(message)
      if (!(reason instanceof ApiError) || reason.status !== 401) notify(message)
    } finally { setBusy(false) }
  }
  return <main className="login-page">
    <div className="login-brand"><Brand /></div>
    <div className="login-content">
      <section className="login-intro"><span className="eyebrow">YOUR VIDEO WORKSPACE</span>
        <h1>Good videos deserve<br />a simple workflow.</h1>
        <p>One place to organize your projects, upload your videos, and get ready to share.</p>
        <ul className="login-features">
          <li><FolderOpen /><div><strong>A place for every project</strong><span>Keep your content organized from the start.</span></div></li>
          <li><UploadCloud /><div><strong>From file to the cloud</strong><span>Direct uploads, with progress you can follow.</span></div></li>
          <li><Layers3 /><div><strong>Know where things stand</strong><span>A clear view of every video's status.</span></div></li>
        </ul>
      </section>
      <section className="login-form card"><span className="eyebrow">WELCOME BACK</span>
        <h2>Sign in to your workspace</h2><p className="muted">Your projects are right where you left them.</p>
        {expired && <p className="inline-error" role="alert">Your session has expired. Please sign in again.</p>}
        <form onSubmit={submit}>
          <label>Email address<input name="email" type="email" placeholder="you@company.com" autoComplete="username" required disabled={busy} /></label>
          <label>Password<input name="password" type="password" placeholder="Enter your password" autoComplete="current-password" required disabled={busy} /></label>
          {error && <p className="inline-error" role="alert">{error}</p>}
          <button className="button button-outline login-submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}<ArrowRight size={18} /></button>
        </form>
        <p className="small muted">Accounts are provided by your workspace administrator.</p>
        <details className="demo-account"><summary>Trying the local demo?</summary>
          <p>Email: <code>demo@transvidcloud.test</code><br />Password: <code>DemoPass123!</code></p>
          <span className="small muted">Demo credentials work only with the local mock service.</span>
        </details>
      </section>
    </div>
    <footer className="login-footer">TransVidCloud <span>Simple by design. Built for video.</span></footer>
  </main>
}
