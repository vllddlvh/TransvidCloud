import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { putFile } from './api/transport'
import './styles.css'

const root = createRoot(document.getElementById('root')!)

async function start() {
  const mode = import.meta.env.VITE_API_MODE ?? 'mock'
  let uploadTransport = putFile
  if (mode === 'mock') {
    const [{ startMocking }, { mockPutFile }] = await Promise.all([import('./mocks/browser'), import('./mocks/upload')])
    await startMocking()
    uploadTransport = mockPutFile
  } else if (mode === 'real') {
    if ('serviceWorker' in navigator) {
      const registrations = await navigator.serviceWorker.getRegistrations()
      await Promise.all(registrations.filter((registration) =>
        [registration.active, registration.waiting, registration.installing].some((worker) => worker && new URL(worker.scriptURL).pathname === '/mockServiceWorker.js')
      ).map((registration) => registration.unregister()))
    }
  } else {
    throw new Error('VITE_API_MODE must be mock or real.')
  }
  root.render(<StrictMode><BrowserRouter><App uploadTransport={uploadTransport} /></BrowserRouter></StrictMode>)
}

start().catch((error: Error) => {
  root.render(<main className="empty-state"><h1>Unable to start your workspace</h1><p role="alert">{error.message}</p><button className="button" onClick={() => window.location.reload()}>Try again</button></main>)
})
