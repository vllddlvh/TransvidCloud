# Frontend Week 2 Design

## Scope

Complete the mock-only F4/F5 workflow defined in `docs/docs-frontend/work/Week2.md`: automatic mock processing, visibility-aware polling, live video lists, HLS playback, streaming-link and iframe copying, a public embed route, failure guidance, and automated/manual verification. Real backend, worker, nginx, CloudFront, and production embed authorization remain Week 9 integration work.

## Architecture

### Shared resource polling

Extend `frontend/src/components/useResource.ts` rather than add page-specific polling. The hook accepts an optional predicate that inspects the latest result and returns whether polling should continue.

- Initial loads retain the current loading/error behavior.
- Polls run three seconds after the preceding request finishes, never concurrently.
- Refreshes preserve existing data and do not show the initial loading state.
- Polling stops for terminal data, unmounting, load-function changes, and hidden documents; visibility restoration schedules a refresh when polling remains applicable.
- One or two consecutive refresh failures preserve data. Three consecutive failures expose a status-unknown error with the existing reload action. A successful request resets the count.
- A 401 continues through the existing session-expired flow, which removes the protected page and therefore stops polling.
- List polling repeats the complete `listVideos` operation. No batch-status endpoint is introduced.

### Mock processing and media

Extend `frontend/src/mocks/handlers.ts` with a processing tick invoked before video list/detail reads.

- `VITE_MOCK_PROCESS=auto` advances `PENDING` to `PROCESSING` after five seconds and `PROCESSING` to `COMPLETED` after ten more seconds.
- `freeze` leaves states unchanged; `fail` advances `PROCESSING` to `FAILED_TRANSCODE` with `TRANSCODE_FAILED`.
- `updatedAt` supplies the mock transition timestamp and is updated on every transition.
- Completion sets duration and `hls`, `hls360`, `hls720`, and `thumbnail` assets under `/media/{projectId}/{videoId}/`.
- Completed and failed states never regress. Retry, queue wait, DLQ, and attempt counts are not simulated.

Add explicit MSW media handlers for known master/variant playlist, segment, and thumbnail paths. Use a one-time FFmpeg-generated two-rendition HLS fixture and JPEG under `frontend/src/mocks/media/`, targeting at most 100 KB. Serve `application/vnd.apple.mpegurl`, `video/mp2t`, and `image/jpeg` respectively. Do not add media routes to the SaaS OpenAPI inventory.

### HLS player

Add `frontend/src/player/HlsPlayer.tsx` as the single player used by detail and embed.

- With hls.js support, load only `assets.hls`, attach the video element, and expose manifest parsing through a testable state attribute.
- On detail, attempt playback after `MANIFEST_PARSED`; browser autoplay rejection leaves native controls available and is not a fatal player error.
- On embed, never autoplay.
- Without hls.js support, use native HLS when `canPlayType` accepts the HLS MIME type.
- Set the thumbnail as poster when present and retain a token-based 16:9 fallback background otherwise.
- Fatal network/media errors show a compact error card and retry action that reinitializes the pipeline.
- Source changes and unmounts destroy hls.js, clear the media source, and remove object references/listeners.

### Pages and actions

Update the video list to poll while any video is pending/processing, animate uploading/processing status icons, and show duration and real thumbnails with a safe fallback. Upload confirmation refreshes the list immediately and polling then advances the new item without another manual reload.

Update video detail to poll one video, render the player only when completed, and map known validation/transcode/storage error codes to concise user guidance. Transcode/storage failures explain that the worker retries automatically; no frontend retry-job action is added.

Completed videos expose:

- Copy streaming link: `new URL(assets.hls, window.location.origin).href`.
- Copy embed: the exact 640x360 iframe format from the Week 2 document.
- Clipboard API with a textarea/`execCommand` fallback and existing notice feedback.

Replace the embed placeholder with a public `Embed` page using `?video=<id>`. It contains only the player or a compact unavailable card, with no application chrome. Add `api.getPublicVideo` using the client's existing public-request option so embed failures do not trigger session expiry. In mock mode only, unauthenticated detail reads return completed videos and treat other videos as unavailable. The real authorization contract remains unchanged and documented as a Week 9 gap.

## Styling

Reuse existing tokens and card/button conventions. Add only player, embed, thumbnail, duration, status-unknown, and spinner styles. Preserve responsive layouts at 1440px and 390px without gradients, shadows, or new brand chrome.

## Testing and verification

- Unit: polling terminal states, non-overlap, preserved data, three consecutive failures, reload, visibility, and unmount; hls.js call sequence, native fallback, autoplay policy, fatal retry, source changes, and cleanup.
- Contract/mock: transition rules and all auto-completed asset URL patterns; media playlists, fixture bytes, and content types.
- Playwright: upload through automatic completion, manifest parsing, both copy actions, chrome-free/non-autoplay embed, unavailable embed, and forced transcode failure.
- Regression: existing Week 1 unit and Chromium scenarios.
- Commands: `npm test`, `npm run build`, `npm run api:check`, and `npm run test:e2e` from `frontend/`.
- Manual browser checks: upload-to-playback, clipboard actions, failure recovery, hidden/visible polling behavior, console/network health, desktop 1440px, and mobile 390px.

Record fixture, hls.js, polling, embed, and error-code assumptions in `docs/docs-frontend/5. API Integration.md`; record batch-status and embed-auth gaps under `docs/contracts/`; update Week 2 results with actual verification evidence.
