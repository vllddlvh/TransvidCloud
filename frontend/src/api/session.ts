import { isSession, type Session } from './types'

export const SESSION_KEY = 'tvc.session'

function removeStoredSession(): void {
  try { localStorage.removeItem(SESSION_KEY) } catch { /* Storage may be blocked. */ }
}
function notify(): void {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event('tvc:session'))
}

export function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (raw === null) return null
    const session: unknown = JSON.parse(raw)
    if (isSession(session) && Date.parse(session.expiresAt) > Date.now()) return session
  } catch { /* Malformed JSON/unavailable storage is an unauthenticated state. */ }
  // Read is used during React rendering: never dispatch recursively from here.
  removeStoredSession()
  return null
}

export function saveSession(session: Session): void {
  if (!isSession(session) || Date.parse(session.expiresAt) <= Date.now()) {
    clearSession()
    return
  }
  try { localStorage.setItem(SESSION_KEY, JSON.stringify(session)) } catch { /* No persistence when blocked. */ }
  notify()
}

export function clearSession(): void {
  removeStoredSession()
  notify()
}
