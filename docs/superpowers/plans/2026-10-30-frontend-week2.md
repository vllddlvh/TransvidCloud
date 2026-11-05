# Frontend Week 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the mock-only upload-to-HLS-playback workflow with automatic state transitions, resilient polling, live video UI, copy actions, embed playback, and full Week 2 verification.

**Architecture:** Extend the existing MSW mock and `useResource` hook, then share one `HlsPlayer` between detail and embed pages. Keep API paths relative and media outside the SaaS OpenAPI contract; preserve all existing Week 1 behavior and uncommitted user work.

**Tech Stack:** React 19, TypeScript strict, Vite 7, Vitest/jsdom, Testing Library, MSW 2, hls.js, Playwright Chromium, FFmpeg-generated HLS fixtures.

**Spec:** `docs/superpowers/specs/2026-10-30-frontend-week2-design.md`

## Global Constraints

- Run package commands from `frontend/`; Node must be 20.19 or newer.
- Keep `VITE_API_MODE=mock|real` behind the existing API interface; UI code must not branch on mock mode.
- Use relative `/api/...` and `/media/...` URLs and existing CSS tokens.
- Poll every 3000 ms, never overlap requests, preserve old data during refreshes, and stop on terminal states/unmount/hidden documents.
- Use `VITE_MOCK_PROCESS=auto|freeze|fail`; auto is the development default.
- Do not add `/media/*` to `docs/contracts/openapi.yaml` and do not invent a batch-status endpoint.
- Do not add retry-job controls for transcode/storage failures.
- Embed never autoplays and never renders application navigation/header/sidebar.
- Do not commit changes: the user has not requested commits and the working tree already contains extensive uncommitted work.

---

### Task 1: Mock video state machine and completed assets

**Files:**
- Modify: `frontend/src/mocks/fixtures.ts`
- Modify: `frontend/src/mocks/handlers.ts`
- Test: `frontend/src/api/contract.test.ts`

**Interfaces:**
- Produces: `completeVideoAssets(projectId: string, videoId: string): NonNullable<Video['assets']>` for consistent fixture/completion URLs.
- Consumes: existing `Video`, `MockState`, `persist()`, `videoById()`, and `page()` helpers.

- [ ] **Step 1: Add failing auto/freeze/fail transition tests**

Use fake system time around the existing confirmed-upload setup. Assert the exact sequence and asset pattern:

```ts
it('auto-completes pending videos without regressing terminal states', async () => {
  vi.useFakeTimers()
  try {
    vi.setSystemTime(new Date('2026-01-12T09:00:00.000Z'))
    const login = await call('/auth/login', 'POST', { email: 'demo@transvidcloud.test', password: 'DemoPass123!' })
    token = (await login.json()).accessToken
    vi.setSystemTime(new Date('2026-01-12T09:00:05.000Z'))
    expect((await (await call('/videos/demo-pending')).json()).status).toBe('PROCESSING')
    vi.setSystemTime(new Date('2026-01-12T09:00:15.000Z'))
    const completed = await (await call('/videos/demo-pending')).json()
    expect(completed.status).toBe('COMPLETED')
    expect(completed.durationSeconds).toBe(10)
    for (const key of ['hls', 'hls360', 'hls720', 'thumbnail']) {
      expect(completed.assets[key]).toMatch(/^\/media\/demo-project\/demo-pending\//)
    }
    validate('Video', completed)
    expect((await (await call('/videos/demo-completed')).json()).status).toBe('COMPLETED')
  } finally {
    vi.useRealTimers()
  }
})
```

For freeze/fail, call `vi.stubEnv('VITE_MOCK_PROCESS', mode)` followed by `server.resetHandlers(...createHandlers())`, authenticate, advance the same timestamps, and assert pending remains pending in freeze while processing becomes `FAILED_TRANSCODE` with `TRANSCODE_FAILED` in fail. Call `vi.unstubAllEnvs()` and `vi.useRealTimers()` in `finally`.

- [ ] **Step 2: Run the tests and confirm the missing behavior fails**

Run: `npm test -- src/api/contract.test.ts`
Expected: new cases fail because confirmed videos remain `PENDING` and completed fixtures lack thumbnails.

- [ ] **Step 3: Implement centralized assets and elapsed-time transitions**

Add to `fixtures.ts`:

```ts
export function completeVideoAssets(projectId: string, videoId: string): NonNullable<Video['assets']> {
  const root = `/media/${projectId}/${videoId}`
  return {
    hls: `${root}/hls/master.m3u8`,
    hls360: `${root}/hls/360p/index.m3u8`,
    hls720: `${root}/hls/720p/index.m3u8`,
    thumbnail: `${root}/thumb/default.jpg`,
  }
}
```

Use it for `demo-completed`. In `createHandlers()`, add `advanceProcessing(now = Date.now())` that loops only pending/processing videos, uses `Date.parse(video.updatedAt)`, updates `updatedAt` on transition, assigns duration/assets on completion, assigns the failure error on fail mode, and persists only when something changed. Call it before both video list and video detail responses. Do not call it from confirm/create/upload.

- [ ] **Step 4: Run mock and existing API regression tests**

Run: `npm test -- src/api/contract.test.ts src/api/client.test.ts src/api/transport.test.ts src/api/upload.test.ts`
Expected: all selected tests pass; terminal fixture statuses remain unchanged.

---

### Task 2: FFmpeg HLS/JPEG fixtures and media handlers

**Files:**
- Create: `frontend/src/mocks/media/master.m3u8`
- Create: `frontend/src/mocks/media/360p.m3u8`
- Create: `frontend/src/mocks/media/720p.m3u8`
- Create: `frontend/src/mocks/media/segment-*.ts`
- Create: `frontend/src/mocks/media/default.jpg`
- Create: `frontend/src/mocks/media.ts`
- Modify: `frontend/src/mocks/handlers.ts`
- Test: `frontend/src/mocks/media.test.ts`

**Interfaces:**
- Produces: `mediaResponse(pathname: string): Response | undefined`, accepting only the four Week 2 asset suffixes and returning the correct body/content type.
- Consumes: Task 1 asset paths, playlist `?raw` imports, and binary fixture `?inline` data-URI imports decoded with `atob`.

- [ ] **Step 1: Generate a real compact fixture**

Create a temporary output directory, then run `/opt/homebrew/bin/ffmpeg` with a ten-second `testsrc2` input, a `split` filter, 640x360 and 1280x720 outputs, H264 CRF 38, five-frame-per-second video, `-hls_time 2`, `-hls_playlist_type vod`, and `-var_stream_map "v:0,name:360p v:1,name:720p"`. Set `-hls_segment_filename` to `<temporary-directory>/%v/segment-%03d.ts` and the output to `<temporary-directory>/%v/index.m3u8`; generate `default.jpg` from one frame of the 360p output. Keep committed media at or below 100 KB if achievable without invalid segments; otherwise keep the smallest valid fixture and document the actual size. Copy the master and variant playlists into the fixture paths listed above and ensure every variant names five relative segments.

- [ ] **Step 2: Write failing media contract tests**

```ts
it.each([
  ['/media/demo-project/demo-completed/hls/master.m3u8', 'application/vnd.apple.mpegurl', '#EXTM3U'],
  ['/media/demo-project/demo-completed/hls/360p/index.m3u8', 'application/vnd.apple.mpegurl', '#EXT-X-TARGETDURATION:2'],
  ['/media/demo-project/demo-completed/thumb/default.jpg', 'image/jpeg', null],
])('serves %s', async (path, contentType, textMarker) => {
  const response = mediaResponse(path)
  expect(response?.headers.get('Content-Type')).toContain(contentType)
  if (textMarker) expect(await response?.text()).toContain(textMarker)
})
```

Also assert the master names both variants, each variant names five segments, segment responses are non-empty `video/mp2t`, JPEG starts with `0xff 0xd8`, and unknown/traversal paths return `undefined`.

- [ ] **Step 3: Run tests and confirm media is unavailable**

Run: `npm test -- src/mocks/media.test.ts`
Expected: FAIL because `media.ts` and its exports do not exist.

- [ ] **Step 4: Implement explicit media resolution and MSW routes**

Import fixture contents once in `media.ts`; return new `Response` instances so bodies can be consumed independently. Do not read arbitrary filesystem paths. Add `http.get('*/media/*', ...)` before any bypass behavior, extracting only `new URL(request.url).pathname` and passing it to `mediaResponse`. Return 404 for unsupported paths.

- [ ] **Step 5: Run media and contract tests**

Run: `npm test -- src/mocks/media.test.ts src/api/contract.test.ts`
Expected: all media bytes, MIME types, playlists, and asset paths pass.

---

### Task 3: Generic non-overlapping visibility-aware polling

**Files:**
- Modify: `frontend/src/components/useResource.ts`
- Test: `frontend/src/components/useResource.test.tsx`

**Interfaces:**
- Produces: `useResource<T>(load: () => Promise<T>, shouldPoll?: (data: T) => boolean)` returning `{ data, loading, error, reload }` with its existing field types.
- Consumes: existing `useNotice()` and page-provided `useCallback` loaders.

- [ ] **Step 1: Write fake-timer hook tests**

Render a harness around `useResource`. Cover:

```ts
expect(load).toHaveBeenCalledTimes(1)
await act(() => vi.advanceTimersByTimeAsync(3000))
expect(load).toHaveBeenCalledTimes(2)
```

Add distinct cases proving: terminal data schedules nothing; a pending promise receives no overlapping tick; data remains rendered during refresh; unmount cancels; hidden documents stop and visible documents resume; one/two errors retain data without an error card; the third sets `Status unknown. Reload to try again.`; reload and later success reset failures. Mock `document.visibilityState` with configurable properties and dispatch `visibilitychange`.

- [ ] **Step 2: Run hook tests and confirm failure**

Run: `npm test -- src/components/useResource.test.tsx`
Expected: FAIL because the current hook only loads on `load`/`revision` changes and clears data on every load.

- [ ] **Step 3: Implement polling with one effect and per-effect cancellation**

Keep initial and polling execution in the same effect. Use an `active` flag, one timeout handle, and an asynchronous `execute(initial: boolean)` that schedules the next timeout only after settlement and only when visible and `shouldPoll(latestData)` is true. Initial load resets state; background success updates data without loading; background errors increment a local consecutive-failure counter and expose status-unknown only at three. `reload` increments the existing revision and resets polling through effect cleanup. Do not notify once per background failure.

- [ ] **Step 4: Run hook and existing component tests**

Run: `npm test -- src/components/useResource.test.tsx src/components/UploadDialog.test.tsx src/App.test.tsx`
Expected: polling cases pass and existing resource consumers retain their initial-load behavior.

---

### Task 4: Live list/detail status UI and failure guidance

**Files:**
- Create: `frontend/src/components/video.ts`
- Modify: `frontend/src/components/VideoStatus.tsx`
- Modify: `frontend/src/pages/Videos.tsx`
- Modify: `frontend/src/pages/VideoDetail.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/components/video.test.ts`

**Interfaces:**
- Produces: `hasActiveVideo(videos: Video[]): boolean`, `isVideoActive(video: Video): boolean`, `formatDuration(seconds?: number): string`, and `failureGuidance(video: Video): string`.
- Consumes: Task 3 `useResource(load, shouldPoll)` and Task 1 thumbnail/duration assets.

- [ ] **Step 1: Write failing helper tests**

Assert active only for `PENDING`/`PROCESSING`, list polling when any item is active, `undefined` duration as an em dash, zero as `0:00`, 65 as `1:05`, duration/resolution validation messages, and automatic-retry guidance for transcode/storage failures.

- [ ] **Step 2: Run helper tests and implement them**

Run: `npm test -- src/components/video.test.ts`
Expected: initial import failure, then PASS after adding the pure helpers without UI dependencies.

- [ ] **Step 3: Wire polling and live list rendering**

Pass `({ videos }) => hasActiveVideo(videos)` to the list resource and `isVideoActive` to detail. Replace the generic row icon with an `<img>` only when `assets.thumbnail` exists; otherwise retain the accessible icon fallback. Display duration in the existing metadata area, remove the Week 2 footnote, and retain immediate `reload()` after confirmation. Add `status-spin` animation for both uploading and processing badges with `prefers-reduced-motion` disablement.

- [ ] **Step 4: Wire detail status-unknown and failure branches**

Keep initial errors on `ErrorCard`. For status-unknown with retained video data, render the old detail plus an alert/reload action. Render `errorCode` and `failureGuidance(video)` for every failed branch; do not render a retry-job button. Retain the existing completed-status panel until Task 5 replaces it with `HlsPlayer`.

- [ ] **Step 5: Run unit/build checks**

Run: `npm test -- src/components/video.test.ts src/App.test.tsx && npm run build`
Expected: helpers/tests and strict TypeScript build pass.

---

### Task 5: Shared HLS player

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/src/player/HlsPlayer.tsx`
- Create: `frontend/src/player/HlsPlayer.test.tsx`
- Modify: `frontend/src/pages/VideoDetail.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Produces: `HlsPlayer({ assets, autoplay?: boolean }: { assets: NonNullable<Video['assets']>; autoplay?: boolean })`.
- Consumes: Task 1 assets and hls.js `MANIFEST_PARSED`, `ERROR`, `destroy`, `loadSource`, `attachMedia`, and native HLS fallback.

- [ ] **Step 1: Install hls.js**

Run: `npm install hls.js`
Expected: dependency and lockfile update without removing existing dependencies.

- [ ] **Step 2: Write failing mocked-player tests**

Mock the hls.js module and capture instances/handlers. Verify call order (`loadSource`, `attachMedia`), no play before manifest, play after manifest only when `autoplay` is true, native `video.src` fallback, poster, fatal error UI, retry reinitialization, source changes, and exact cleanup (`destroy`, emptied `src`, `load()`). Set `data-manifest-state="parsed"` only after `MANIFEST_PARSED`.

- [ ] **Step 3: Run tests and confirm the component is missing**

Run: `npm test -- src/player/HlsPlayer.test.tsx`
Expected: FAIL because `HlsPlayer.tsx` does not exist.

- [ ] **Step 4: Implement the player lifecycle**

Use a `useEffect` keyed by master URL/autoplay/retry revision and a `useRef<HTMLVideoElement>`. Catch synchronous play promises and autoplay rejection without treating policy rejection as fatal. Fatal hls.js network/media errors set the error card; retry cleans up and recreates the pipeline. Unsupported browsers show a compact unsupported message. Render native controls in a 16:9 container with optional poster and token-based fallback.

- [ ] **Step 5: Render completed detail playback and verify**

Replace only the completed detail placeholder with `<HlsPlayer assets={video.assets} autoplay />`; retain status placeholders for incomplete states. Run `npm test -- src/player/HlsPlayer.test.tsx src/App.test.tsx && npm run build`.
Expected: player unit cases, route regressions, and build pass.

---

### Task 6: Clipboard actions and public embed

**Files:**
- Create: `frontend/src/components/clipboard.ts`
- Create: `frontend/src/components/clipboard.test.ts`
- Create: `frontend/src/pages/Embed.tsx`
- Modify: `frontend/src/pages/VideoDetail.tsx`
- Modify: `frontend/src/api/client.ts`
- Modify: `frontend/src/mocks/handlers.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Produces: `copyText(value: string): Promise<void>`, `api.getPublicVideo(videoId: string): Promise<Video>`, and `Embed()`.
- Consumes: Task 3 polling, Task 5 `HlsPlayer`, and existing notice/error components.

- [ ] **Step 1: Write failing clipboard and public-route tests**

Test Clipboard API success and textarea/`execCommand` fallback. Replace App placeholder assertions with a missing-ID compact unavailable state and no navigation. Add client tests proving public detail does not attach bearer credentials or dispatch session-expiry on 401.

- [ ] **Step 2: Run tests and confirm missing public behavior**

Run: `npm test -- src/components/clipboard.test.ts src/api/client.test.ts src/App.test.tsx`
Expected: FAIL on missing exports and stale placeholder behavior.

- [ ] **Step 3: Implement clipboard and public detail request**

`copyText` uses `navigator.clipboard.writeText` when available, otherwise appends a readonly textarea, selects it, calls `document.execCommand('copy')`, removes it in `finally`, and throws if copying fails. Add `getPublicVideo` through `request(..., { public: true })` without changing `getVideo`.

- [ ] **Step 4: Implement completed-only mock embed reads and Embed page**

Register public detail handling ahead of protected detail: without authorization, return a completed video; return 404 for missing/incomplete videos; preserve existing authorized behavior for all states. `Embed` reads `useSearchParams().get('video')`, uses `api.getPublicVideo`, polls only active videos, renders `HlsPlayer` with no autoplay for completed assets, and otherwise shows `Video is not available yet.` in a compact chrome-free main element.

- [ ] **Step 5: Add completed-detail copy actions**

Use the exact iframe string:

```tsx
const embed = `<iframe src="${window.location.origin}/embed?video=${video.videoId}" width="640" height="360" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen frameborder="0"></iframe>`
```

Copy the absolute master URL and embed only for completed videos, then notify `Streaming link copied.` or `Embed code copied.` through `useNotice`.

- [ ] **Step 6: Run route, clipboard, API, and build checks**

Run: `npm test -- src/components/clipboard.test.ts src/api/client.test.ts src/App.test.tsx src/player/HlsPlayer.test.tsx && npm run build`
Expected: all selected tests and strict build pass.

---

### Task 7: Browser acceptance, contract gaps, and Week 2 evidence

**Files:**
- Modify: `frontend/playwright.config.ts`
- Modify: `frontend/e2e/week1.spec.ts`
- Create: `frontend/e2e/week2.spec.ts`
- Modify: `frontend/.env.example`
- Modify: `frontend/README.md`
- Modify: `docs/docs-frontend/5. API Integration.md`
- Create: `docs/contracts/frontend-week2-gaps.md`
- Modify: `docs/docs-frontend/work/Week2.md`

**Interfaces:**
- Consumes: all previous public UI behavior and `VITE_MOCK_PROCESS` modes.
- Produces: reproducible Week 2 verification evidence and explicit Week 9 integration gaps.

- [ ] **Step 1: Update browser configuration and stale Week 1 assertions**

Use auto mode on the primary mock server, retain real/upload-failure servers, and add a fail-mode mock server on port 5184. Replace player-placeholder assertions with current detail/embed behavior without weakening existing upload/mobile checks.

- [ ] **Step 2: Add Week 2 Playwright scenarios**

Write independent tests for:

```ts
await expect(row.getByText('Completed', { exact: true })).toBeVisible({ timeout: 35000 })
await row.click()
await expect(page.locator('[data-manifest-state="parsed"]')).toBeVisible({ timeout: 15000 })
```

Cover upload-to-completion, master manifest parsing, absolute clipboard contents, exact iframe contents, chrome-free embed with no autoplay, missing embed ID, and fail-mode `TRANSCODE_FAILED` guidance. Use condition-based waits, grant clipboard permissions where needed, and never use fixed sleeps.

- [ ] **Step 3: Run complete automated verification**

Run: `npm test && npm run build && npm run api:check && npm run test:e2e`
Expected: all unit/contract tests, strict build, OpenAPI generated-type check, and all old/new Chromium scenarios pass.

- [ ] **Step 4: Exercise desktop and mobile UI manually**

Start the mock dev server on 5173. At 1440px and 390px verify upload through completed playback, thumbnail/duration layout, copy actions, embed, fatal/status-unknown recovery, no horizontal overflow, and no unexpected console errors. Check network requests for three-second non-overlapping polling and no polling while hidden.

- [ ] **Step 5: Document assumptions, gaps, and actual evidence**

Record the installed hls.js version, 3000 ms polling, relative media resolution, shared mock media, generated fixture choice/size, iframe attributes, public mock-only completed detail, error-code mapping, and clipboard/autoplay limitations with `giả định — chờ Phương xác nhận`. Record full-list polling and real embed authorization as Week 9 gaps. Fill Week 2 result counts from actual command output; explicitly list any check not completed rather than claiming success.

- [ ] **Step 6: Review the final diff without committing**

Run: `git status --short && git diff -- frontend docs/docs-frontend docs/contracts/frontend-week2-gaps.md docs/superpowers`
Expected: only intended Week 2 files plus pre-existing user work are present; no user changes were overwritten or committed.
