# Frontend Week 3 — Integration Readiness, Hosting, and Freeze Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver every Week 3 frontend deliverable that does not require the absent backend or CloudFront, prove real worker HLS output playback through nginx, and record the remaining contract/infrastructure gaps without inventing decisions.

**Architecture:** Keep one UI behind the existing `mock|real` interface. Move runtime selection into small testable modules; exclude mock code and the MSW worker from production builds at build time; lazy-load `HlsPlayer` so `hls.js` is a separate chunk; host both Vite dev and the static mock build through nginx on `:8000`; make Playwright origin-agnostic; parse S3 XML upload failures without leaking raw markup; verify worker-generated HLS through LocalStack + nginx with hls.js.

**Tech Stack:** React 19, TypeScript 5.9 strict, Vite 7, React Router 7, Vitest 4 + Testing Library + jsdom, Playwright Chromium, MSW 2, hls.js 1.7.4, nginx 1.29.1, LocalStack 3.8.1, Go worker + FFmpeg.

**Spec:** `docs/docs-frontend/work/Week3.md`

## Global Constraints

- Do **not** modify `docs/contracts/openapi.yaml`, `s3-key-convention.md`, `sqs-job-message.schema.json`, or backend/worker source. Only status/evidence notes in `docs/contracts/frontend-week2-gaps.md` and frontend docs.
- Do **not** invent public-embed, batch-status, or backend approval decisions. Record them as open with the owner named in the Week 3 spec.
- Do **not** commit, amend, or tag. Record the current HEAD as the freeze candidate in the Week 3 results.
- Keep the mock build and fixtures for the demo. Production build must contain no MSW worker and no mock fixtures.
- Keep all runtime URLs relative (`/api/*`, `/media/*`). No environment-specific origins in app code.
- Baseline evidence before this plan: 127/127 unit tests, 15/15 Chromium e2e, production build warns about a ~879 kB main chunk and currently defaults to mock.
- Commands run from `frontend/` unless a path says otherwise.
- Checkpoint = run the listed tests; no git commit.

---

## File Structure

**Create**
- `frontend/src/runtime/mode.ts` — pure API-mode resolution.
- `frontend/src/runtime/serviceWorker.ts` — stale MSW unregister logic.
- `frontend/src/runtime/mock.ts` — dynamic mock runtime loader (dev/mock builds).
- `frontend/src/runtime/mock.disabled.ts` — production stub that fails closed.
- `frontend/src/runtime/mode.test.ts`
- `frontend/src/runtime/serviceWorker.test.ts`
- `frontend/src/assets/favicon.svg` — moved from `frontend/public/favicon.svg`.
- `frontend/.env.production` — `VITE_API_MODE=real`.
- `frontend/.env.mock` — `VITE_API_MODE=mock`.
- `frontend/scripts/check-production-bundle.mjs` — build assertions.
- `frontend/scripts/serve-nginx.sh` — host nginx runner for dev/static/prod.
- `frontend/e2e/support.ts` — origin helpers and auxiliary-server gate.
- `frontend/e2e/hosting.spec.ts` — static-hosting smoke.
- `frontend/e2e/real-media.spec.ts` — worker-output playback through nginx.
- `frontend/public` keeps only `mockServiceWorker.js`.
- `local/nginx/static.conf` — Compose static profile config.
- `worker/scripts/local-media.sh` — seed a LocalStack job and run the real worker binary.

**Modify**
- `frontend/src/main.tsx` — use runtime modules.
- `frontend/src/api/transport.ts` — S3 XML/empty error parsing.
- `frontend/src/api/transport.test.ts`
- `frontend/src/pages/VideoDetail.tsx`, `frontend/src/pages/Embed.tsx` — lazy player + Suspense.
- `frontend/index.html` — favicon from `src/assets`.
- `frontend/vite.config.ts` — mode-aware build, alias, publicDir, manifest.
- `frontend/tsconfig.json` — path alias for `@app/mock-runtime`.
- `frontend/package.json` — build scripts.
- `frontend/.gitignore` — `dist-mock/`, `.nginx/`.
- `frontend/playwright.config.ts`, `frontend/e2e/week2.spec.ts`, `frontend/e2e/recovery.spec.ts`, `frontend/e2e/confirm-retry.spec.ts`.
- `docker-compose.yml` — `edge-static` profile.
- `frontend/README.md`, `local/nginx/README.md`, `worker/README.md`.
- `docs/contracts/frontend-week2-gaps.md`, `docs/docs-frontend/5. API Integration.md`, `docs/docs-frontend/work/Week3.md`, `docs/docs-frontend/work/Week2.md`.

---

### Task 1: Runtime mode and stale mock-worker cleanup

**Files:**
- Create: `frontend/src/runtime/mode.ts`, `frontend/src/runtime/mode.test.ts`, `frontend/src/runtime/serviceWorker.ts`, `frontend/src/runtime/serviceWorker.test.ts`, `frontend/src/runtime/mock.ts`, `frontend/src/runtime/mock.disabled.ts`
- Modify: `frontend/src/main.tsx`, `frontend/tsconfig.json`, `frontend/vite.config.ts`

**Interfaces:**
- Produces: `resolveApiMode(configured: string | undefined, isProduction: boolean): 'mock' | 'real'`
- Produces: `unregisterMockWorkers(source?: { getRegistrations(): Promise<readonly RegistrationLike[]> }): Promise<number>`
- Produces: `startMockRuntime(): Promise<typeof putFile>` from both `mock.ts` and `mock.disabled.ts`
- Consumes: existing `putFile` and `mockPutFile` signatures.

- [ ] **Step 1: Write the failing mode tests**

Create `frontend/src/runtime/mode.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { resolveApiMode } from './mode'

describe('resolveApiMode', () => {
  it('defaults to mock for development and real for production', () => {
    expect(resolveApiMode(undefined, false)).toBe('mock')
    expect(resolveApiMode(undefined, true)).toBe('real')
  })

  it('honors an explicit mode', () => {
    expect(resolveApiMode('mock', true)).toBe('mock')
    expect(resolveApiMode('real', false)).toBe('real')
  })

  it('rejects unknown values instead of silently guessing', () => {
    expect(() => resolveApiMode('staging', false)).toThrow('VITE_API_MODE must be mock or real.')
  })
})
```

- [ ] **Step 2: Run the mode test and confirm it fails**

Run: `npx vitest run src/runtime/mode.test.ts`
Expected: FAIL because `./mode` does not exist.

- [ ] **Step 3: Implement mode resolution**

Create `frontend/src/runtime/mode.ts`:

```ts
export type ApiMode = 'mock' | 'real'

export function resolveApiMode(configured: string | undefined, isProduction: boolean): ApiMode {
  const value = configured || (isProduction ? 'real' : 'mock')
  if (value !== 'mock' && value !== 'real') {
    throw new Error('VITE_API_MODE must be mock or real.')
  }
  return value
}
```

- [ ] **Step 4: Write the failing service-worker tests**

Create `frontend/src/runtime/serviceWorker.test.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'
import { isMockWorkerRegistration, unregisterMockWorkers } from './serviceWorker'

function registration(scriptURL: string | null, unregister = vi.fn().mockResolvedValue(true)) {
  return {
    active: scriptURL ? { scriptURL } : null,
    waiting: null,
    installing: null,
    unregister,
  }
}

describe('stale mock service worker cleanup', () => {
  it('detects the mock worker in any lifecycle slot', () => {
    const active = registration('http://localhost:8000/mockServiceWorker.js')
    const waiting = { ...registration(null), waiting: { scriptURL: 'http://localhost:8000/mockServiceWorker.js' } }
    expect(isMockWorkerRegistration(active)).toBe(true)
    expect(isMockWorkerRegistration(waiting)).toBe(true)
    expect(isMockWorkerRegistration(registration('http://localhost:8000/sw.js'))).toBe(false)
  })

  it('unregisters only mock workers and reports the count', async () => {
    const mock = registration('http://localhost:8000/mockServiceWorker.js')
    const other = registration('http://localhost:8000/app-worker.js')
    const count = await unregisterMockWorkers({ getRegistrations: async () => [mock, other] })
    expect(count).toBe(1)
    expect(mock.unregister).toHaveBeenCalledOnce()
    expect(other.unregister).not.toHaveBeenCalled()
  })

  it('does nothing when service workers are unavailable', async () => {
    await expect(unregisterMockWorkers(undefined)).resolves.toBe(0)
  })
})
```

- [ ] **Step 5: Run the service-worker test and confirm it fails**

Run: `npx vitest run src/runtime/serviceWorker.test.ts`
Expected: FAIL because `./serviceWorker` does not exist.

- [ ] **Step 6: Implement stale-worker cleanup**

Create `frontend/src/runtime/serviceWorker.ts`:

```ts
const MOCK_WORKER_PATH = '/mockServiceWorker.js'

type WorkerLike = Pick<ServiceWorker, 'scriptURL'>

export type RegistrationLike = {
  active: WorkerLike | null
  waiting: WorkerLike | null
  installing: WorkerLike | null
  unregister: () => Promise<boolean>
}

type RegistrationSource = {
  getRegistrations: () => Promise<readonly RegistrationLike[]>
}

export function isMockWorkerRegistration(registration: RegistrationLike): boolean {
  return [registration.active, registration.waiting, registration.installing]
    .some((worker) => worker !== null && new URL(worker.scriptURL).pathname === MOCK_WORKER_PATH)
}

export async function unregisterMockWorkers(source?: RegistrationSource): Promise<number> {
  if (!source) return 0
  const registrations = await source.getRegistrations()
  const stale = registrations.filter(isMockWorkerRegistration)
  await Promise.all(stale.map((registration) => registration.unregister()))
  return stale.length
}
```

- [ ] **Step 7: Add the mock runtime loader and the production stub**

Create `frontend/src/runtime/mock.ts`:

```ts
import type { putFile } from '../api/transport'

export async function startMockRuntime(): Promise<typeof putFile> {
  const [{ startMocking }, { mockPutFile }] = await Promise.all([
    import('../mocks/browser'),
    import('../mocks/upload'),
  ])
  await startMocking()
  return mockPutFile
}
```

Create `frontend/src/runtime/mock.disabled.ts`:

```ts
import type { putFile } from '../api/transport'

export async function startMockRuntime(): Promise<typeof putFile> {
  throw new Error('Mock runtime is not available in this build.')
}
```

- [ ] **Step 8: Rewire `main.tsx` through the runtime modules**

Replace the top of `frontend/src/main.tsx` with:

```tsx
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
```

Keep the existing `start().catch(...)` block unchanged.

- [ ] **Step 9: Configure the alias in TypeScript and Vite**

In `frontend/tsconfig.json` add:

```json
"baseUrl": ".",
"paths": { "@app/mock-runtime": ["./src/runtime/mock.ts"] },
```

In `frontend/vite.config.ts` add:

```ts
import { fileURLToPath, URL } from 'node:url'

// inside defineConfig...
resolve: {
  alias: {
    '@app/mock-runtime': fileURLToPath(new URL(
      mode === 'production' ? './src/runtime/mock.disabled.ts' : './src/runtime/mock.ts',
      import.meta.url,
    )),
  },
},
```

`defineConfig` must take the callback form: `defineConfig(({ mode }) => ({ ... }))`. Keep the existing test block unchanged.

- [ ] **Step 10: Run the focused and full unit suites**

Run: `npx vitest run src/runtime`
Expected: 6 tests pass.

Run: `npm test`
Expected: 133 tests pass (127 baseline + 6).

- [ ] **Step 11: Checkpoint**

Run: `npx tsc -b`
Expected: exit 0. Do not commit.

---

### Task 2: Production build isolation and bundle verification

**Files:**
- Create: `frontend/.env.production`, `frontend/.env.mock`, `frontend/scripts/check-production-bundle.mjs`
- Move: `frontend/public/favicon.svg` → `frontend/src/assets/favicon.svg`
- Modify: `frontend/index.html`, `frontend/vite.config.ts`, `frontend/package.json`, `frontend/.gitignore`

**Interfaces:**
- Produces: `npm run build` = tsc + production build + bundle assertions, output `dist/`, `VITE_API_MODE=real`.
- Produces: `npm run build:mock` = tsc + mock build, output `dist-mock/`, includes `mockServiceWorker.js`.
- Consumes: Task 1 alias and runtime modules.

- [ ] **Step 1: Add build-mode env files**

Create `frontend/.env.production`:

```
VITE_API_MODE=real
```

Create `frontend/.env.mock`:

```
VITE_API_MODE=mock
```

- [ ] **Step 2: Move the favicon out of `public/`**

Run:

```bash
mkdir -p frontend/src/assets
mv frontend/public/favicon.svg frontend/src/assets/favicon.svg
```

Edit `frontend/index.html`:

```html
<link rel="icon" type="image/svg+xml" href="/src/assets/favicon.svg" />
```

After this, `frontend/public/` contains only `mockServiceWorker.js`.

- [ ] **Step 3: Make Vite mode-aware and fail closed**

Replace `frontend/vite.config.ts` with:

```ts
import { fileURLToPath, URL } from 'node:url'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode, command }) => {
  const mockBuild = mode === 'mock'
  const productionBuild = command === 'build' && !mockBuild
  const env = loadEnv(mode, process.cwd(), '')
  const configuredMode = process.env.VITE_API_MODE ?? env.VITE_API_MODE
  if (productionBuild && configuredMode !== 'real') {
    throw new Error('Production builds must set VITE_API_MODE=real. Use npm run build:mock for the demo build.')
  }
  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@app/mock-runtime': fileURLToPath(new URL(
          productionBuild ? './src/runtime/mock.disabled.ts' : './src/runtime/mock.ts',
          import.meta.url,
        )),
      },
    },
    publicDir: productionBuild ? false : 'public',
    assetsInclude: ['**/src/mocks/media/segment-*.ts'],
    build: {
      outDir: mockBuild ? 'dist-mock' : 'dist',
      manifest: true,
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      restoreMocks: true,
      environmentOptions: { jsdom: { url: 'http://localhost:5173' } },
    },
  }
})
```

- [ ] **Step 4: Add the production bundle checker**

Create `frontend/scripts/check-production-bundle.mjs`:

```js
import { existsSync } from 'node:fs'
import { readFile, readdir, stat } from 'node:fs/promises'
import { join } from 'node:path'

const dist = join(process.cwd(), 'dist')
const manifestPath = join(dist, '.vite', 'manifest.json')
if (!existsSync(manifestPath)) throw new Error('missing dist/.vite/manifest.json; run npm run build first')

const manifest = JSON.parse(await readFile(manifestPath, 'utf8'))
const entries = Object.entries(manifest)
const entry = manifest['index.html']
if (!entry) throw new Error('manifest has no index.html entry')
if (existsSync(join(dist, 'mockServiceWorker.js'))) throw new Error('production build contains mockServiceWorker.js')
if (entries.some(([key]) => /mocks\//.test(key))) throw new Error('production build references src/mocks')

const assetsDir = join(dist, 'assets')
for (const file of (await readdir(assetsDir)).filter((name) => name.endsWith('.js'))) {
  const code = await readFile(join(assetsDir, file), 'utf8')
  if (code.includes('/media/demo-project/')) throw new Error(`fixture URL leaked into ${file}`)
  if (code.includes('setupWorker')) throw new Error(`MSW runtime leaked into ${file}`)
}

const mainSize = (await stat(join(dist, entry.file))).size
if (mainSize > 700_000) throw new Error(`main chunk is ${mainSize} bytes; expected code splitting`)

const hlsEntry = entries
  .map(([, value]) => value)
  .find((value) => value.isDynamicEntry && /hlsplayer/i.test(value.file))
if (!hlsEntry) throw new Error('HlsPlayer is not a dynamic entry')
const hlsSize = (await stat(join(dist, hlsEntry.file))).size
if (hlsSize < 200_000) throw new Error(`HlsPlayer chunk is only ${hlsSize} bytes; hls.js may not be split`)

console.log(`production bundle OK: main ${mainSize} B, HlsPlayer ${hlsSize} B`)
```

- [ ] **Step 5: Wire scripts and ignore build output**

In `frontend/package.json`:

```json
"build": "tsc -b && vite build && node scripts/check-production-bundle.mjs",
"build:mock": "tsc -b && vite build --mode mock",
```

In `frontend/.gitignore` append:

```
dist-mock/
.nginx/
```

- [ ] **Step 6: Build production and inspect**

Run: `npm run build`
Expected: exit 0, `production bundle OK: main <number> B, HlsPlayer <number> B`, no `mockServiceWorker.js` in `dist/`, `.vite/manifest.json` present, favicon hashed under `dist/assets/`.

Run: `find dist -name 'mockServiceWorker.js' -o -name '*.m3u8' -o -name 'segment-*'`
Expected: no output.

- [ ] **Step 7: Build the mock demo bundle**

Run: `npm run build:mock`
Expected: exit 0, `dist-mock/mockServiceWorker.js` exists, `dist-mock/index.html` exists.

- [ ] **Step 8: Prove the production guard fails closed**

Run: `VITE_API_MODE=mock npx vite build`
Expected: nonzero exit with `Production builds must set VITE_API_MODE=real.`

- [ ] **Step 9: Run tests and checkpoint**

Run: `npm test`
Expected: pass. Do not commit.

---

### Task 3: Lazy-load the HLS player

**Files:**
- Modify: `frontend/src/pages/VideoDetail.tsx`, `frontend/src/pages/Embed.tsx`

**Interfaces:**
- Consumes: named export `HlsPlayer` from `../player/HlsPlayer`.
- Produces: `HlsPlayer` becomes a dynamic import chunk; the player pages render a visible loading state during the import.

- [ ] **Step 1: Lazy-load in the detail page**

In `frontend/src/pages/VideoDetail.tsx`, replace the static import with:

```tsx
import { lazy, Suspense, useCallback } from 'react'
// ...
const HlsPlayer = lazy(() => import('../player/HlsPlayer').then((module) => ({ default: module.HlsPlayer })))
```

Replace the completed-player branch with:

```tsx
<Suspense fallback={<p className="player-placeholder card" role="status">Loading player…</p>}>
  <HlsPlayer assets={video.assets} autoplay />
</Suspense>
```

- [ ] **Step 2: Lazy-load in the embed page**

In `frontend/src/pages/Embed.tsx`, use the same lazy import and wrap the player:

```tsx
<Suspense fallback={<p className="embed-status" role="status">Loading video…</p>}>
  <HlsPlayer assets={video.assets} />
</Suspense>
```

- [ ] **Step 3: Build and verify the split**

Run: `npm run build`
Expected: `check-production-bundle.mjs` passes and reports a main chunk below 700 kB plus an HlsPlayer dynamic entry above 200 kB.

- [ ] **Step 4: Run focused tests**

Run: `npx vitest run src/player src/App.test.tsx`
Expected: all pass.

- [ ] **Step 5: Checkpoint**

Run: `npx tsc -b`
Expected: exit 0. Do not commit.

---

### Task 4: Safe S3 XML/empty upload errors

**Files:**
- Modify: `frontend/src/api/transport.ts`, `frontend/src/api/transport.test.ts`

**Interfaces:**
- Produces: `UploadError` keeps `message`/`status`, never exposes raw XML/HTML, and maps known S3 codes to actionable copy.
- Consumes: existing `putFile` contract.

- [ ] **Step 1: Add failing transport tests**

Append to `frontend/src/api/transport.test.ts`:

```ts
it('parses an S3 signature error without printing raw XML', async () => {
  const pending = putFile(options)
  const xhr = TestXHR.last
  xhr.status = 403
  xhr.responseText = '<?xml version="1.0"?><Error><Code>SignatureDoesNotMatch</Code><Message>bad signature</Message></Error>'
  xhr.dispatchEvent(new Event('load'))
  await expect(pending).rejects.toThrow('The upload link is not valid for this file. Retry to request a new link.')
  await expect(pending).rejects.not.toThrow(/</)
})

it('maps an oversized S3 rejection to storage copy', async () => {
  const pending = putFile(options)
  const xhr = TestXHR.last
  xhr.status = 400
  xhr.responseText = '<Error><Code>EntityTooLarge</Code></Error>'
  xhr.dispatchEvent(new Event('load'))
  await expect(pending).rejects.toThrow('This file is larger than storage accepts.')
})

it('keeps an empty or HTML error response generic and safe', async () => {
  const pending = putFile(options)
  const xhr = TestXHR.last
  xhr.status = 500
  xhr.responseText = '<html><body>Internal error</body></html>'
  xhr.dispatchEvent(new Event('load'))
  await expect(pending).rejects.toThrow('The storage upload failed. Please try again.')
  await expect(pending).rejects.not.toThrow(/html/i)
})
```

Add `responseText = ''` to `TestXHR`:

```ts
responseText = ''
```

- [ ] **Step 2: Run the new tests and confirm they fail**

Run: `npx vitest run src/api/transport.test.ts`
Expected: the three new tests fail on the old generic messages.

- [ ] **Step 3: Implement safe S3 error parsing**

In `frontend/src/api/transport.ts` add above `putFile`:

```ts
function storageError(xhr: XMLHttpRequest): UploadError {
  const body = typeof xhr.responseText === 'string' ? xhr.responseText : ''
  const code = /<Code>([^<]+)<\/Code>/.exec(body)?.[1]?.trim()
  if (xhr.status === 403) {
    if (code === 'SignatureDoesNotMatch' || code === 'InvalidAccessKeyId') {
      return new UploadError('The upload link is not valid for this file. Retry to request a new link.', xhr.status)
    }
    return new UploadError('The upload link expired or was rejected. Retry to request a new link.', xhr.status)
  }
  if (code === 'EntityTooLarge') {
    return new UploadError('This file is larger than storage accepts.', xhr.status)
  }
  return new UploadError('The storage upload failed. Please try again.', xhr.status)
}
```

Replace the `load` failure branch with:

```ts
fail(storageError(xhr))
```

- [ ] **Step 4: Run transport tests**

Run: `npx vitest run src/api/transport.test.ts`
Expected: 10 tests pass (7 existing + 3 new).

- [ ] **Step 5: Run the full unit suite**

Run: `npm test`
Expected: 136 tests pass.

- [ ] **Step 6: Checkpoint**

Do not commit.

---

### Task 5: Origin-agnostic Playwright suite

**Files:**
- Create: `frontend/e2e/support.ts`, `frontend/e2e/hosting.spec.ts`
- Modify: `frontend/playwright.config.ts`, `frontend/e2e/week2.spec.ts`, `frontend/e2e/recovery.spec.ts`, `frontend/e2e/confirm-retry.spec.ts`

**Interfaces:**
- Produces: `primaryOrigin`, `auxiliaryOrigins`, `hasLocalAuxiliaryServers` from `e2e/support.ts`.
- Produces: `E2E_BASE_URL` disables all four managed web servers.
- Consumes: Task 2 mock build for `hosting.spec.ts`.

- [ ] **Step 1: Add the origin helpers**

Create `frontend/e2e/support.ts`:

```ts
export const primaryOrigin = process.env.E2E_BASE_URL ?? 'http://localhost:5181'

export const auxiliaryOrigins = {
  real: process.env.E2E_REAL_ORIGIN ?? 'http://localhost:5182',
  putFailure: process.env.E2E_PUT_FAILURE_ORIGIN ?? 'http://localhost:5183',
  processFailure: process.env.E2E_PROCESS_FAILURE_ORIGIN ?? 'http://localhost:5184',
}

export const hasLocalAuxiliaryServers = process.env.E2E_BASE_URL === undefined
```

- [ ] **Step 2: Make the Playwright config origin-configurable**

Replace `frontend/playwright.config.ts` with:

```ts
import { defineConfig, devices } from '@playwright/test'

const externalBaseURL = process.env.E2E_BASE_URL

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: 'list',
  use: { baseURL: externalBaseURL ?? 'http://localhost:5181', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: externalBaseURL ? undefined : [
    { command: 'npm run dev -- --port 5181', url: 'http://localhost:5181', env: { VITE_API_MODE: 'mock', VITE_MOCK_PROCESS: 'auto', VITE_MOCK_UPLOAD_FAILURE_ONCE: 'false' } },
    { command: 'npm run dev -- --port 5182', url: 'http://localhost:5182', env: { VITE_API_MODE: 'real' } },
    { command: 'npm run dev -- --port 5183', url: 'http://localhost:5183', env: { VITE_API_MODE: 'mock', VITE_MOCK_UPLOAD_FAILURE_ONCE: 'true' } },
    { command: 'npm run dev -- --port 5184', url: 'http://localhost:5184', env: { VITE_API_MODE: 'mock', VITE_MOCK_PROCESS: 'fail' } },
  ],
})
```

- [ ] **Step 3: Update Week 2 tests**

In `frontend/e2e/week2.spec.ts`:

```ts
import { auxiliaryOrigins, hasLocalAuxiliaryServers, primaryOrigin } from './support'

async function login(page: Page, origin = primaryOrigin) { /* body unchanged */ }
```

- In the clipboard test, replace `${ORIGIN}` expectations with the live origin:

```ts
const origin = await page.evaluate(() => location.origin)
expect(await page.evaluate(() => navigator.clipboard.readText()))
  .toBe(`${origin}/media/demo-project/demo-completed/hls/master.m3u8`)
// ...
.toBe(`<iframe src="${origin}/embed?video=demo-completed" width="640" height="360" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen frameborder="0"></iframe>`)
```

- At the start of the fail-mode test:

```ts
test('fail mode surfaces TRANSCODE_FAILED with automatic-retry guidance', async ({ page }) => {
  test.skip(!hasLocalAuxiliaryServers, 'Requires the local transcode-failure server')
  await login(page, auxiliaryOrigins.processFailure)
  await page.goto(`${auxiliaryOrigins.processFailure}/projects/demo-project/videos`)
  // rest unchanged
})
```

Remove the `ORIGIN` and `FAIL_ORIGIN` constants.

- [ ] **Step 4: Update recovery tests**

In `frontend/e2e/recovery.spec.ts` import `auxiliaryOrigins`, `hasLocalAuxiliaryServers`, `primaryOrigin`.

- Failed-PUT test: skip when `!hasLocalAuxiliaryServers`; use `auxiliaryOrigins.putFailure`.
- Real-mode test: skip when `!hasLocalAuxiliaryServers`; use `auxiliaryOrigins.real`.
- Navigation and expired-session tests: use `primaryOrigin`.

- [ ] **Step 5: Update the confirm-retry test**

In `frontend/e2e/confirm-retry.spec.ts`:

```ts
import { auxiliaryOrigins, hasLocalAuxiliaryServers } from './support'

test('failed confirmation retries only confirm, without duplicating the video or PUT', async ({ page }) => {
  test.skip(!hasLocalAuxiliaryServers, 'Requires the local real-mode server')
  await page.goto(`${auxiliaryOrigins.real}/login`)
  // replace the remaining hard-coded 5182 origin if any
})
```

- [ ] **Step 6: Add the static-hosting smoke**

Create `frontend/e2e/hosting.spec.ts`:

```ts
import { expect, test } from '@playwright/test'

const hosted = process.env.E2E_HOSTED === '1'

test.skip(!hosted, 'Set E2E_HOSTED=1 and E2E_BASE_URL to the nginx origin')

test('hosted build serves the SPA, registers the mock worker at root scope, and plays HLS media', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Sign in to your workspace' })).toBeVisible()
  await page.getByLabel('Email address').fill('demo@transvidcloud.test')
  await page.getByLabel('Password', { exact: true }).fill('DemoPass123!')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Projects', exact: true })).toBeVisible()
  await page.goto('/videos/demo-completed')
  await expect(page.locator('[data-manifest-state="parsed"]')).toBeVisible({ timeout: 15000 })
  const workers = await page.evaluate(async () => (await navigator.serviceWorker.getRegistrations())
    .map((registration) => new URL(registration.active?.scriptURL ?? '', location.origin).pathname))
  expect(workers).toContain('/mockServiceWorker.js')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})
```

- [ ] **Step 7: Run the full local suite (no external base URL)**

Run: `npx playwright test`
Expected: 16 tests pass (15 existing + hosting skipped). This proves the refactor did not break the managed-server setup.

- [ ] **Step 8: Checkpoint**

Do not commit.

---

### Task 6: nginx hosting for dev, static, and production builds

**Files:**
- Create: `frontend/scripts/serve-nginx.sh`, `local/nginx/static.conf`
- Modify: `docker-compose.yml`

**Interfaces:**
- Produces: `bash scripts/serve-nginx.sh dev|static|prod` on `PORT` (default 8000), runtime under `frontend/.nginx/`.
- Produces: Compose profile `static` with `edge-static`.
- Consumes: `dist-mock` from Task 2, vite dev server on 5173.

- [ ] **Step 1: Add the host nginx runner**

Create `frontend/scripts/serve-nginx.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-static}"
PORT="${PORT:-8000}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.nginx"

case "$MODE" in
  dev) APP_ROOT="" ;;
  static) APP_ROOT="$ROOT_DIR/dist-mock" ;;
  prod) APP_ROOT="$ROOT_DIR/dist" ;;
  *) echo "usage: $0 dev|static|prod" >&2; exit 2 ;;
esac

if [ "$MODE" != "dev" ] && [ ! -f "$APP_ROOT/index.html" ]; then
  echo "Missing $APP_ROOT/index.html. Run npm run build:mock (static) or npm run build (prod)." >&2
  exit 2
fi

mkdir -p "$RUN_DIR/logs"
cat > "$RUN_DIR/mime.types" <<'EOF'
types {
  text/html html;
  text/css css;
  application/javascript js mjs;
  application/json json;
  image/svg+xml svg;
  image/jpeg jpg jpeg;
  image/png png;
  video/mp2t ts;
  application/vnd.apple.mpegurl m3u8;
  font/woff2 woff2;
  application/octet-stream bin;
}
EOF

if [ "$MODE" = "dev" ]; then
  WEB_LOCATION='proxy_pass http://127.0.0.1:5173;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;'
else
  WEB_LOCATION="root $APP_ROOT;
        try_files \$uri /index.html;"
fi

cat > "$RUN_DIR/nginx.conf" <<EOF
worker_processes 1;
pid $RUN_DIR/nginx.pid;
error_log $RUN_DIR/logs/error.log info;

events { worker_connections 512; }

http {
  include $RUN_DIR/mime.types;
  default_type application/octet-stream;
  access_log $RUN_DIR/logs/access.log;
  sendfile on;

  server {
    listen $PORT;
    server_name localhost;

    location /api/ {
      proxy_pass http://127.0.0.1:8080;
      proxy_set_header Host \$host;
    }

    location /media/ {
      rewrite ^/media/(.*)\$ /tvc-local-output/\$1 break;
      proxy_pass http://127.0.0.1:4566;
      proxy_set_header Host \$host;
    }

    location / {
      $WEB_LOCATION
    }
  }
}
EOF

echo "nginx $MODE on http://localhost:$PORT"
exec nginx -c "$RUN_DIR/nginx.conf" -p "$RUN_DIR" -g 'daemon off;'
```

Make it executable: `chmod +x frontend/scripts/serve-nginx.sh`.

- [ ] **Step 2: Add the Compose static profile**

Create `local/nginx/static.conf`:

```nginx
server {
    listen 80;

    location /api/ {
        set $backend http://backend:8080;
        proxy_pass $backend;
        proxy_set_header Host $host;
    }

    location /media/ {
        set $s3 http://localstack:4566;
        rewrite ^/media/(.*)$ /tvc-local-output/$1 break;
        proxy_pass $s3;
    }

    location / {
        root /usr/share/nginx/html;
        try_files $uri /index.html;
    }
}
```

Add to `docker-compose.yml`:

```yaml
  edge-static:
    image: nginx:alpine
    profiles: [static]
    ports:
      - "8000:80"
    volumes:
      - ./local/nginx/static.conf:/etc/nginx/conf.d/default.conf:ro
      - ./frontend/dist-mock:/usr/share/nginx/html:ro
```

- [ ] **Step 3: Prove static hosting on the host**

Run: `npm run build:mock`
Run in background: `bash scripts/serve-nginx.sh static`
Run: `curl -fsS http://localhost:8000/ | grep -q 'TransVidCloud'`
Run: `curl -fsS http://localhost:8000/mockServiceWorker.js | grep -q 'ServiceWorker'`
Run: `curl -fsS http://localhost:8000/videos/demo-completed | grep -q 'TransVidCloud'`
Expected: all exit 0.

- [ ] **Step 4: Run the hosted e2e suite against the static build**

Run:

```bash
E2E_BASE_URL=http://localhost:8000 E2E_HOSTED=1 npx playwright test
```

Expected: 12 tests pass, 4 auxiliary-server tests skipped (failed PUT, real without backend, confirm retry, transcode failure). This proves SPA fallback, SW root scope, `/media` fixtures, polling, upload, and player through nginx.

- [ ] **Step 5: Prove the dev proxy**

Stop the static nginx process. Start `npm run dev` (5173) and `bash scripts/serve-nginx.sh dev` (8000). Run:

```bash
E2E_BASE_URL=http://localhost:8000 E2E_HOSTED=1 npx playwright test
```

Expected: same 12 pass / 4 skipped. If the service worker does not register through the proxy, inspect `frontend/.nginx/logs/error.log` and fix the proxy WebSocket/headers, not the app code.

- [ ] **Step 6: Stop nginx and Vite, then checkpoint**

Do not commit.

---

### Task 7: Real worker HLS playback evidence

**Files:**
- Create: `worker/scripts/local-media.sh`, `frontend/e2e/real-media.spec.ts`
- Modify: `worker/README.md` (one run section)

**Interfaces:**
- Produces: LocalStack objects under `prj-e2e/vid-worker-sample/`; DynamoDB item `vid-worker-sample` reaches `COMPLETED`.
- Produces: `E2E_REAL_MEDIA_PATH` gates the real-media spec.
- Consumes: `worker/bin/worker`, LocalStack on 4566, host nginx `prod` mode on 8000, `dist/` from Task 2.

- [ ] **Step 1: Add the worker media seed script**

Create `worker/scripts/local-media.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
PROJECT_ID="prj-e2e"
VIDEO_ID="vid-worker-sample"
KEY="$PROJECT_ID/$VIDEO_ID/source.mp4"
QUEUE="$ENDPOINT/000000000000/tvc-local-jobs"
WORKER_BIN="${WORKER_BIN:-$ROOT/worker/bin/worker}"

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=ap-southeast-1

aws --endpoint-url "$ENDPOINT" s3api head-bucket --bucket tvc-local-input >/dev/null
aws --endpoint-url "$ENDPOINT" s3 cp "$ROOT/worker/testdata/sample-10s-720p.mp4" "s3://tvc-local-input/$KEY" --only-show-errors

NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
aws --endpoint-url "$ENDPOINT" dynamodb put-item --table-name tvc-local-videos --item "{
  \"videoId\":{\"S\":\"$VIDEO_ID\"},
  \"projectId\":{\"S\":\"$PROJECT_ID\"},
  \"status\":{\"S\":\"PENDING\"},
  \"createdAt\":{\"S\":\"$NOW\"},
  \"updatedAt\":{\"S\":\"$NOW\"}
}"

JOB_ID="job-e2e-$(date +%s)"
aws --endpoint-url "$ENDPOINT" sqs send-message --queue-url "$QUEUE" --message-body "{
  \"schemaVersion\":1,
  \"jobId\":\"$JOB_ID\",
  \"videoId\":\"$VIDEO_ID\",
  \"projectId\":\"$PROJECT_ID\",
  \"inputKey\":\"$KEY\",
  \"requestedAt\":\"$NOW\"
}" >/dev/null

if [ ! -x "$WORKER_BIN" ]; then
  (cd "$ROOT/worker" && go build -o bin/worker ./cmd/worker)
fi

TVC_INPUT_BUCKET=tvc-local-input \
TVC_OUTPUT_BUCKET=tvc-local-output \
TVC_DDB_VIDEOS=tvc-local-videos \
TVC_JOBS_QUEUE_URL="$QUEUE" \
AWS_ENDPOINT_URL="$ENDPOINT" \
AWS_REGION=ap-southeast-1 \
AWS_ACCESS_KEY_ID=test \
AWS_SECRET_ACCESS_KEY=test \
TVC_TMP_DIR="$(mktemp -d)" \
  "$WORKER_BIN" >"$ROOT/frontend/.nginx/worker-media.log" 2>&1 &
WORKER_PID=$!
trap 'kill "$WORKER_PID" 2>/dev/null || true' EXIT

STATUS=""
for _ in $(seq 1 60); do
  STATUS="$(aws --endpoint-url "$ENDPOINT" dynamodb get-item --table-name tvc-local-videos \
    --key "{\"videoId\":{\"S\":\"$VIDEO_ID\"}}" --query 'Item.status.S' --output text)"
  case "$STATUS" in
    COMPLETED) break ;;
    FAILED_*) echo "worker failed: $STATUS" >&2; exit 1 ;;
  esac
  sleep 2
done

if [ "$STATUS" != "COMPLETED" ]; then
  echo "timed out waiting for COMPLETED (last status: ${STATUS:-none})" >&2
  exit 1
fi

for suffix in hls/master.m3u8 hls/360p/index.m3u8 hls/720p/index.m3u8 thumb/default.jpg; do
  aws --endpoint-url "$ENDPOINT" s3api head-object \
    --bucket tvc-local-output --key "$PROJECT_ID/$VIDEO_ID/$suffix" >/dev/null
done

echo "COMPLETED: http://localhost:8000/media/$PROJECT_ID/$VIDEO_ID/hls/master.m3u8"
```

Make it executable: `chmod +x worker/scripts/local-media.sh`.

- [ ] **Step 2: Add the real-media Playwright spec**

Create `frontend/e2e/real-media.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { join } from 'node:path'

const mediaPath = process.env.E2E_REAL_MEDIA_PATH

test.skip(!mediaPath, 'Set E2E_REAL_MEDIA_PATH and E2E_BASE_URL to a served nginx origin')

test('plays worker-generated HLS with the expected renditions and storage content types', async ({ page }) => {
  await page.goto('/login')
  const origin = new URL(page.url()).origin
  const mediaUrl = new URL(mediaPath!, origin).href

  await page.addScriptTag({ path: join(process.cwd(), 'node_modules/hls.js/dist/hls.min.js') })
  const levels = await page.evaluate((url) => new Promise<{ height: number; bitrate: number }[]>((resolve, reject) => {
    const HlsCtor = (window as unknown as { Hls: new () => {
      loadSource: (source: string) => void
      attachMedia: (media: HTMLMediaElement) => void
      destroy: () => void
      on: (event: string, handler: (event: string, data: { levels: { height: number; bitrate: number }[]; fatal: boolean; details: string }) => void) => void
    } }).Hls as unknown as {
      new (): {
        loadSource: (source: string) => void
        attachMedia: (media: HTMLMediaElement) => void
        destroy: () => void
        on: (event: string, handler: (event: string, data: { levels: { height: number; bitrate: number }[]; fatal: boolean; details: string }) => void) => void
      }
      Events: { MANIFEST_PARSED: string; ERROR: string }
    }
    const video = document.createElement('video')
    video.muted = true
    document.body.append(video)
    const hls = new HlsCtor()
    const timer = window.setTimeout(() => reject(new Error('manifest timeout')), 20000)
    hls.on(HlsCtor.Events.MANIFEST_PARSED, (_event, data) => {
      window.clearTimeout(timer)
      resolve(data.levels.map((level) => ({ height: level.height, bitrate: level.bitrate })))
      hls.destroy()
    })
    hls.on(HlsCtor.Events.ERROR, (_event, data) => {
      if (data.fatal) {
        window.clearTimeout(timer)
        reject(new Error(data.details))
      }
    })
    hls.loadSource(url)
    hls.attachMedia(video)
  }), mediaUrl)

  expect(levels.map((level) => level.height).sort((a, b) => a - b)).toEqual([360, 720])

  const master = await page.request.get(mediaUrl)
  expect(master.headers()['content-type']).toContain('application/vnd.apple.mpegurl')
  const playlist = await master.text()
  expect(playlist).toContain('hls/360p/index.m3u8')
  expect(playlist).toContain('hls/720p/index.m3u8')

  const rendition = await (await page.request.get(new URL(mediaPath!.replace('master.m3u8', '360p/index.m3u8'), origin).href)).text()
  const segment = rendition.split('\n').find((line) => line.trim().endsWith('.ts'))
  expect(segment).toBeTruthy()
  const segmentResponse = await page.request.get(new URL(segment!.trim(), new URL(mediaPath!.replace('master.m3u8', '360p/index.m3u8'), origin).href).href)
  expect(segmentResponse.headers()['content-type']).toContain('video/mp2t')

  const thumb = await page.request.get(new URL(mediaPath!.replace('hls/master.m3u8', 'thumb/default.jpg'), origin).href)
  expect(thumb.headers()['content-type']).toContain('image/jpeg')
})
```

- [ ] **Step 3: Rebuild the worker binary and seed LocalStack**

Run: `cd worker && go build -o bin/worker ./cmd/worker && cd ..`
Run: `bash worker/scripts/local-media.sh`
Expected: `COMPLETED: http://localhost:8000/media/prj-e2e/vid-worker-sample/hls/master.m3u8`; four `head-object` calls succeed.

- [ ] **Step 4: Serve the production build and play the real output**

Run: `npm run build`
Run in background: `bash scripts/serve-nginx.sh prod`
Verify direct responses:

```bash
curl -fsSI http://localhost:8000/media/prj-e2e/vid-worker-sample/hls/master.m3u8 | grep -i 'content-type: application/vnd.apple.mpegurl'
curl -fsSI http://localhost:8000/media/prj-e2e/vid-worker-sample/thumb/default.jpg | grep -i 'content-type: image/jpeg'
```

Run:

```bash
E2E_BASE_URL=http://localhost:8000 \
E2E_REAL_MEDIA_PATH=/media/prj-e2e/vid-worker-sample/hls/master.m3u8 \
npx playwright test e2e/real-media.spec.ts
```

Expected: 1 test passes; levels are exactly 360p and 720p; S3 content types survive nginx.

- [ ] **Step 5: Stop nginx and the worker, then checkpoint**

Do not commit. Record the worker log path and the observed status/assets for Week 3 results.

---

### Task 8: Contract and documentation reconciliation

**Files:**
- Modify: `docs/contracts/frontend-week2-gaps.md`, `docs/docs-frontend/5. API Integration.md`, `docs/docs-frontend/work/Week3.md`, `docs/docs-frontend/work/Week2.md`, `frontend/README.md`, `local/nginx/README.md`, `worker/README.md`

**Interfaces:**
- Produces: explicit open/verified status per gap and exact evidence commands/results.
- Consumes: results from Tasks 1–7.

- [ ] **Step 1: Update the gap file**

In `docs/contracts/frontend-week2-gaps.md`:

- Add a header line: `Cập nhật Week 3 (2026-10-31): trạng thái dưới đây chỉ ghi nhận bằng chứng; mục nào chưa có phê duyệt của Phương/Lưu/Nguyên vẫn giữ nhãn "chờ xác nhận".`
- Gap 1 (embed): keep **open**. Add: "Chưa có endpoint/presigned URL công khai được duyệt. Frontend giữ mock; demo thật phải chờ Phương chốt. Không thêm endpoint vào OpenAPI."
- Gap 2 (batch polling): keep **open**. Add: "Chưa có batch-status endpoint. Frontend giữ re-list 3 giây; chấp nhận cho quy mô demo nếu Phương xác nhận, chưa coi là đã chốt."
- Gap 3 (media): mark **verified locally for worker output** with: worker `sample-10s-720p.mp4` → LocalStack → nginx `:8000/media/*` → hls.js 1.7.4 parsed master with 360p/720p; `Content-Type` checks passed. Keep **CloudFront/cache/Safari pending** under Lưu/Nguyên.
- Gap 4 (errorCode): mark **worker-side confirmed** with: `NOT_A_VIDEO`, `CODEC_UNSUPPORTED`, `DURATION_EXCEEDED`, `HEIGHT_EXCEEDED` exist in `worker/internal/validate`; system failures use `FAILED_STORAGE`/`FAILED_TRANSCODE` codes (`S3_GET_FAILED`, `S3_PUT_FAILED`, `LOCAL_FS_FAILED`, FFmpeg codes) that the UI intentionally does not enumerate. Keep **backend envelope/approval pending**.

- [ ] **Step 2: Add Week 3 reconciliation to the API integration doc**

Append a new `## 9. Week 3 — đối chiếu hợp nhất (2026-10-31)` section to `docs/docs-frontend/5. API Integration.md` with a table:

| Hạng mục | Kết quả Week 3 | Bằng chứng | Còn mở |
|---|---|---|---|
| Chế độ real/mock | real tắt MSW, xoá đăng ký cũ, build production không chứa mock | unit runtime + build script + hosted e2e | backend thật |
| Build production | `dist/` real-only, `dist-mock/` cho demo, hls.js tách chunk | `npm run build` log | CloudFront deploy |
| nginx :8000 | dev proxy + static + prod đều phục vụ app; SW scope `/`; `/media/*` proxy | hosted e2e | backend 8080, CloudFront |
| Upload S3 XML | 403/`SignatureDoesNotMatch`, 400/`EntityTooLarge`, HTML/empty đều an toàn | transport tests | CORS/presigned thật |
| Worker HLS | output thật 360p/720p, `Content-Type` đúng, hls.js parse | real-media e2e | CloudFront cache |
| Embed | vẫn mock/demo-only | gap #1 | contract Phương |
| Polling list | vẫn re-list 3 giây | gap #2 | batch contract |
| ErrorCode | 4 mã validation khớp worker | worker code | envelope backend |

Do not check any "chờ Phương xác nhận" box.

- [ ] **Step 3: Update Week 2's deferred list**

In `docs/docs-frontend/work/Week2.md`, below the "Chưa kiểm chứng — dời tuần 9" list, add a line per item with the Week 3 disposition (verified locally / still blocked by CloudFront/backend) and link to Week 3 results. Do not delete the original list.

- [ ] **Step 4: Update the frontend README**

Add sections:

- `## Build production và demo` with `npm run build` (real-only, `dist/`), `npm run build:mock` (`dist-mock/`), the bundle checker, and the favicon/mock-worker isolation rule.
- `## Chạy qua nginx :8000` with `bash scripts/serve-nginx.sh dev|static|prod`, required build, and the e2e commands from Tasks 5–7.
- `## E2E theo BASE_URL` with `E2E_BASE_URL`, `E2E_HOSTED=1`, `E2E_REAL_MEDIA_PATH`, and the note that auxiliary servers are skipped for external origins.
- `## Bằng chứng worker HLS` with the `worker/scripts/local-media.sh` command and the four verified content types.

- [ ] **Step 5: Update the nginx and worker READMEs**

`local/nginx/README.md`: document the three routes (unchanged), the new `static` Compose profile and `edge-static`, and the host script as the no-Docker alternative.

`worker/README.md`: add a "Kiểm chứng output với frontend" section that runs `bash scripts/local-media.sh`, requires LocalStack buckets initialized, and points at the real-media e2e.

- [ ] **Step 6: Fill Week 3 results**

Replace the empty "Kết quả — kiểm chứng" section in `docs/docs-frontend/work/Week3.md` with:

- Date and HEAD hash.
- Unit test count, build output (main/HlsPlayer sizes), e2e counts for managed servers, static hosting, dev proxy, and real media.
- The four content types observed.
- Blocked items: W3.4–W3.7 real API merge because `backend/` has no code; embed/batch contract approval; CloudFront domain/cache; real presigned CORS.
- Freeze candidate statement: HEAD hash and "không tag/commit theo yêu cầu; tuần 10 chỉ sửa lỗi chặn nộp".
- Checkboxes: check W3.1 (evidence recorded, not approvals), W3.2, W3.3, W3.6 (local worker output), W3.8 (build/hosting/local verification; CloudFront handoff documented), W3.9 (BASE_URL portability), W3.10 (rehearsal recorded). Leave W3.4, W3.5, W3.7 unchecked with the blocker noted. W3.5's XML handling is done, but the real backend PUT is not, so keep it unchecked with a sub-bullet.

- [ ] **Step 7: Checkpoint**

Run: `git diff --stat`
Expected: only frontend code, tests, scripts, docs, compose, and worker script/README; no `openapi.yaml`, no backend source. Do not commit.

---

### Task 9: Mock demo rehearsal and freeze record

**Files:**
- Modify: `docs/docs-frontend/work/Week3.md` (rehearsal section)

**Interfaces:**
- Consumes: static/dev hosting from Task 6 and the full mock fixtures.
- Produces: a dated rehearsal record and a freeze-candidate hash.

- [ ] **Step 1: Rehearse the mock demo through nginx**

Run `npm run build:mock`, start `bash scripts/serve-nginx.sh static`, then:

```bash
E2E_BASE_URL=http://localhost:8000 E2E_HOSTED=1 npx playwright test
```

Record: date/time, command, 12 passed / 4 skipped, no console errors from the player, and the URL used.

- [ ] **Step 2: Rehearse the failure/fallback story**

Run the managed suite once (`npx playwright test`) to exercise the three failure profiles (real without backend, failed PUT retry, transcode failure) and the embed path. Record the same.

- [ ] **Step 3: Record the freeze candidate**

Run: `git rev-parse HEAD`.
Write in Week 3 results: candidate hash, date, "mock giữ nguyên cho demo", "không tag/commit theo yêu cầu người dùng", and the cut list for week 10 (only blockers). Do not create a tag.

- [ ] **Step 4: Checkpoint**

Do not commit.

---

### Task 10: Full verification matrix

**Files:**
- Modify: `docs/docs-frontend/work/Week3.md` only if a command fails and the results need correction.

- [ ] **Step 1: Contract types**

Run: `npm run api:check`
Expected: exit 0 (no generated-type drift).

- [ ] **Step 2: Unit tests**

Run: `npm test`
Expected: 136/136 pass.

- [ ] **Step 3: Production build**

Run: `npm run build`
Expected: bundle checker passes; no mock worker/fixtures.

- [ ] **Step 4: Mock build**

Run: `npm run build:mock`
Expected: `dist-mock` contains the worker.

- [ ] **Step 5: Managed-server e2e**

Run: `npx playwright test`
Expected: 15 pass, 1 skipped (hosting).

- [ ] **Step 6: Static hosted e2e**

Build mock, start static nginx, run `E2E_BASE_URL=http://localhost:8000 E2E_HOSTED=1 npx playwright test`.
Expected: 12 pass, 4 skipped.

- [ ] **Step 7: Dev proxy e2e**

Start Vite + dev nginx, rerun the same command.
Expected: 12 pass, 4 skipped.

- [ ] **Step 8: Real worker media**

Run the seed script, start prod nginx, run `E2E_REAL_MEDIA_PATH=... npx playwright test e2e/real-media.spec.ts`.
Expected: 1 pass, 360p/720p, correct content types.

- [ ] **Step 9: Diff review**

Run: `git status --short` and `git diff --stat`.
Expected: no backend/contract schema edits; no commit/tag. Report any unexpected file.

---

## Self-Review

**Spec coverage**
- W3.1 gap contract: Task 8 records evidence and leaves approvals open (correct, because Phương/Lưu/Nguyên have not responded).
- W3.2 nginx dev/static + BASE_URL e2e: Tasks 5, 6.
- W3.3 real-mode readiness: Tasks 1, 2, 5; stale SW verified by hosted e2e.
- W3.4 partial backend merge: blocked (no backend code); recorded in Task 8.
- W3.5 real upload: blocked at the API layer; S3 XML handling delivered, recorded.
- W3.6 status/player: worker-local real HLS delivered and verified; backend polling remains blocked.
- W3.7 real e2e/embed: blocked by backend and embed contract; mock e2e retained.
- W3.8 production build + code split + hosting: Tasks 2, 3, 6; CloudFront deploy is Lưu's step.
- W3.9 BASE_URL portability: Task 5.
- W3.10 rehearsal/freeze: Task 9; no tag per user constraint.

**Placeholder scan:** Every code step contains the actual code/command. No TBD/TODO placeholders.

**Type consistency:** `resolveApiMode`, `unregisterMockWorkers`, `startMockRuntime`, `primaryOrigin`, `auxiliaryOrigins`, and `hasLocalAuxiliaryServers` are defined once and consumed with the same names. `E2E_REAL_MEDIA_PATH`, `E2E_HOSTED`, and `E2E_BASE_URL` are consistent across spec, README, and commands.
