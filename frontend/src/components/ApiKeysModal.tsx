import { useCallback, useState, type FormEvent } from 'react'
import { KeyRound, Plus, Trash2 } from 'lucide-react'
import { api } from '../api/client'
import type { CreatedApiKey } from '../api/types'
import { Modal } from './Modal'
import { useNotice } from './Notice'
import { copyText } from './clipboard'
import { useResource } from './useResource'
import { formatDate } from './VideoStatus'

const MAX_KEYS = 5

export function ApiKeysModal({ projectId, projectName, onClose }: { projectId: string; projectName?: string; onClose: () => void }) {
  const load = useCallback(() => api.listApiKeys(projectId), [projectId])
  const { data: keys, loading, error, reload } = useResource(load)
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [revokingHash, setRevokingHash] = useState<string | null>(null)
  const [formError, setFormError] = useState('')
  const [justCreated, setJustCreated] = useState<CreatedApiKey | null>(null)
  const notify = useNotice()

  const activeCount = keys?.length ?? 0
  const limitReached = activeCount >= MAX_KEYS

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setFormError('Enter an API key name.')
      return
    }
    if (limitReached || busy) return
    setBusy(true)
    setFormError('')
    try {
      const created = await api.createApiKey(projectId, { name: trimmed })
      setJustCreated(created)
      setName('')
      reload()
      notify('API key created. Copy the secret key now.')
    } catch (reason) {
      setFormError(reason instanceof Error ? reason.message : 'Unable to create API key.')
    } finally {
      setBusy(false)
    }
  }

  async function handleRevoke(keyHash: string, keyName: string) {
    if (revokingHash) return
    setRevokingHash(keyHash)
    setFormError('')
    try {
      await api.revokeApiKey(projectId, keyHash)
      if (justCreated?.keyHash === keyHash) setJustCreated(null)
      reload()
      notify(`Revoked API key "${keyName}".`)
    } catch (reason) {
      setFormError(reason instanceof Error ? reason.message : 'Unable to revoke API key.')
    } finally {
      setRevokingHash(null)
    }
  }

  async function copyRawKey(rawKey: string) {
    try {
      await copyText(rawKey)
      notify('API key copied to clipboard.')
    } catch (reason) {
      notify(reason instanceof Error ? reason.message : 'Copy failed.')
    }
  }

  return (
    <Modal title="Developer API keys" onClose={() => { if (!busy && !revokingHash) onClose() }}>
      <p className="muted">
        Use an <code>X-API-Key</code> header to upload and manage videos programmatically for <strong>{projectName || 'this project'}</strong> ({activeCount} / {MAX_KEYS} keys).
      </p>

      {justCreated && (
        <div className="rawkey-banner" role="region" aria-label="Newly created API key">
          <strong>Copy your API key now — it will only be shown once</strong>
          <span className="small muted">Key name: {justCreated.name}</span>
          <code className="rawkey-value">{justCreated.rawKey}</code>
          <div className="action-row">
            <button className="button" type="button" onClick={() => void copyRawKey(justCreated.rawKey)}>
              Copy API key
            </button>
            <button className="button button-outline" type="button" onClick={() => setJustCreated(null)}>
              Done
            </button>
          </div>
        </div>
      )}

      <form onSubmit={handleCreate}>
        <label>
          New API key name
          <input
            name="apiKeyName"
            placeholder="e.g. Backend Production Key"
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={100}
            disabled={busy || limitReached}
          />
        </label>
        {limitReached && (
          <p className="small muted">Maximum of {MAX_KEYS} active API keys reached for this project. Revoke an unused key to create a new one.</p>
        )}
        {formError && <p className="inline-error" role="alert">{formError}</p>}
        <div className="modal-actions">
          <button className="button button-outline" type="submit" disabled={busy || limitReached || !name.trim()}>
            <Plus size={16} />{busy ? 'Creating…' : 'Create API key'}
          </button>
        </div>
      </form>

      <div className="apikey-list" aria-label="Active API keys">
        {loading && <p role="status" className="small muted">Loading API keys…</p>}
        {error && <p className="inline-error" role="alert">{error}</p>}
        {!loading && keys && keys.length === 0 && (
          <p className="small muted">No API keys created for this project yet.</p>
        )}
        {keys?.map((key) => (
          <div className="apikey-row" key={key.keyHash}>
            <div>
              <strong><KeyRound size={14} aria-hidden="true" /> {key.name}</strong>
              <code className="asset-path">{key.keyPrefix}</code>
              <span className="small muted">Created {formatDate(key.createdAt)}</span>
            </div>
            <button
              className="button button-outline button-danger"
              type="button"
              disabled={revokingHash === key.keyHash}
              onClick={() => void handleRevoke(key.keyHash, key.name)}
            >
              <Trash2 size={14} />{revokingHash === key.keyHash ? 'Revoking…' : 'Revoke'}
            </button>
          </div>
        ))}
      </div>

      <div className="modal-actions">
        <button className="button" type="button" onClick={onClose} disabled={busy || Boolean(revokingHash)}>
          Close
        </button>
      </div>
    </Modal>
  )
}
