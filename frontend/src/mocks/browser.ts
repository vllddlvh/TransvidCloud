import { setupWorker } from 'msw/browser'
import { createHandlers } from './handlers'

let startup: Promise<void> | undefined

export async function startMocking(): Promise<void> {
  if (!startup) {
    const worker = setupWorker(...createHandlers())
    startup = worker.start({
      serviceWorker: { url: '/mockServiceWorker.js' },
      onUnhandledRequest: 'bypass',
    }).then(() => undefined).catch((error: unknown) => {
      startup = undefined
      throw error
    })
  }
  await startup
}
