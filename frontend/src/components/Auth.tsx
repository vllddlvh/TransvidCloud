import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { clearSession, readSession, saveSession, SESSION_KEY } from '../api/session'
import { isSession, type Session } from '../api/types'

const AuthContext = createContext<{
  session: Session | null
  expired: boolean
  signIn: (session: Session) => void
  signOut: () => void
} | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  // Read the stored expiry before readSession() deletes it, so a reload can still explain the sign-in.
  const [expired, setExpired] = useState(() => {
    try {
      const stored: unknown = JSON.parse(localStorage.getItem(SESSION_KEY) ?? 'null')
      return isSession(stored) && Date.parse(stored.expiresAt) <= Date.now()
    } catch { return false }
  })
  const [session, setSession] = useState(readSession)
  useEffect(() => {
    const sync = () => setSession(readSession())
    const expire = () => { setExpired(true); setSession(null) }
    const storage = (event: StorageEvent) => { if (event.key === SESSION_KEY || event.key === null) sync() }
    window.addEventListener('tvc:session', sync)
    window.addEventListener('tvc:session-expired', expire)
    window.addEventListener('storage', storage)
    return () => {
      window.removeEventListener('tvc:session', sync)
      window.removeEventListener('tvc:session-expired', expire)
      window.removeEventListener('storage', storage)
    }
  }, [])
  useEffect(() => {
    if (!session) return
    const timer = window.setTimeout(() => {
      clearSession()
      setExpired(true)
      setSession(null)
    }, Math.max(0, Date.parse(session.expiresAt) - Date.now()))
    return () => window.clearTimeout(timer)
  }, [session])
  return <AuthContext.Provider value={{ session, expired,
    signIn: (value) => { saveSession(value); setSession(value); setExpired(false) },
    signOut: () => { clearSession(); setSession(null); setExpired(false) },
  }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('AuthProvider is required')
  return context
}

export function RequireAuth() {
  return useAuth().session ? <Outlet /> : <Navigate to="/login" replace />
}
