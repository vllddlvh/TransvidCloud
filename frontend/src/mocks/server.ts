import { setupServer } from 'msw/node'
import { createHandlers } from './handlers'

export const server = setupServer(...createHandlers())
// Before each test: clear localStorage, then server.resetHandlers(...createHandlers()).
