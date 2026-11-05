import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { CheckCircle2, X } from 'lucide-react'

const NoticeContext = createContext<(message: string) => void>(() => {})

export function NoticeProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState('')
  const notify = useCallback((value: string) => setMessage(value), [])
  return <NoticeContext.Provider value={notify}>
    {children}
    {message && <div className="notice card" role="status">
      <CheckCircle2 aria-hidden="true" size={20} /><span>{message}</span>
      <button className="icon-button" aria-label="Dismiss notification" onClick={() => setMessage('')}><X /></button>
    </div>}
  </NoticeContext.Provider>
}

export const useNotice = () => useContext(NoticeContext)

export function ErrorCard({ message, retry }: { message: string; retry?: () => void }) {
  return <div className="error-card card" role="alert"><p>{message}</p>
    {retry && <button className="button" onClick={retry}>Try again</button>}
  </div>
}
