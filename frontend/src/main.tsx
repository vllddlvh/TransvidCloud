import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { startMockRuntime } from '@app/mock-runtime'
import App from './App'
import { putFile } from './api/transport'
import { resolveApiMode } from './runtime/mode'
import { unregisterMockWorkers } from './runtime/serviceWorker'
import './styles.css'

const root = createRoot(document.getElementById('root')!)

async function start() {
  const mode = resolveApiMode(import.meta.env.VITE_API_MODE, import.meta.env.PROD)
  let uploadTransport = putFile
  if (mode === 'mock') {
    uploadTransport = await startMockRuntime()
  } else {
    await unregisterMockWorkers('serviceWorker' in navigator ? navigator.serviceWorker : undefined)
  }
  root.render(<StrictMode><BrowserRouter><App uploadTransport={uploadTransport} /></BrowserRouter></StrictMode>)
}

start().catch((error: Error) => {
  root.render(<main className="empty-state"><h1>Unable to start your workspace</h1><p role="alert">{error.message}</p><button className="button" onClick={() => window.location.reload()}>Try again</button></main>)
})
