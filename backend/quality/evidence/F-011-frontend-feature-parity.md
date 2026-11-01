# Feature Verification Evidence — F-011

- Feature: `F-011 — Đồng bộ Đầy đủ Tính năng Frontend theo Đặc tả Backend (F-003 -> F-008)`
- Spec: [`specs/011-frontend-feature-parity/spec.md`](../../specs/011-frontend-feature-parity/spec.md)
- Plan: [`specs/011-frontend-feature-parity/plan.md`](../../specs/011-frontend-feature-parity/plan.md)
- Tasks: [`specs/011-frontend-feature-parity/tasks.md`](../../specs/011-frontend-feature-parity/tasks.md)
- Branch: `phuong-integration`
- Date: `2026-11-01T22:15:00+07:00`

## Acceptance criteria mapping

| Criterion ID | Mô tả tóm tắt | Evidence / test | Result | Reference |
|---|---|---|---|---|
| `US1-AC1` | Trình phát `HlsPlayer` hiển thị thanh chọn chất lượng phát (`Auto (ABR)`, `720p HD`, `360p SD`) và chuyển đổi mượt mà sang `/hls/720p/index.m3u8` hoặc `/hls/360p/index.m3u8` giữ nguyên vị trí phát | `HlsPlayer.test.tsx (switches between Auto (ABR), 720p HD, and 360p SD renditions)`, `real-stack.spec.ts` | `PASS` | `frontend/src/player/HlsPlayer.tsx`, `frontend/src/player/HlsPlayer.test.tsx`, `frontend/e2e/real-stack.spec.ts` |
| `US1-AC2` | Trang `VideoDetail` hiển thị đầy đủ cả 4 tài nguyên đầu ra (`Master HLS (ABR)`, `720p HD HLS`, `360p SD HLS`, `Thumbnail JPEG`) kèm nút Copy riêng lẻ và siêu dữ liệu vòng đời (`title`, `filename`, `attemptCount`, `createdAt`, `startedAt`, `completedAt`, `updatedAt`) | `App.test.tsx (shows all 4 transcoded VideoAssets and deletes a video from VideoDetail)`, `real-stack.spec.ts` | `PASS` | `frontend/src/pages/VideoDetail.tsx`, `frontend/src/App.test.tsx`, `frontend/e2e/real-stack.spec.ts` |
| `US2-AC1` | Hộp thoại `ApiKeysModal` liệt kê danh sách API Keys của Project (`GET /api/projects/{projectId}/api-keys`), hiển thị `name`, `keyPrefix` (`tvc_live_xxxxxxxx...`), `createdAt` và bộ đếm `x / 5 keys` | `client.test.ts`, `App.test.tsx`, `real-stack.spec.ts` | `PASS` | `frontend/src/components/ApiKeysModal.tsx`, `frontend/src/App.test.tsx`, `frontend/e2e/real-stack.spec.ts` |
| `US2-AC2` | Tạo API Key mới (`POST /api/projects/{projectId}/api-keys`, `name` 1–100 ký tự), hiển thị khóa thô `rawKey` duy nhất 1 lần trong banner cảnh báo kèm nút Copy và khóa nút tạo khi đạt giới hạn 5 keys | `client.test.ts`, `App.test.tsx (manages Developer API keys: create with one-time rawKey display and revoke)`, `real-stack.spec.ts` | `PASS` | `frontend/src/components/ApiKeysModal.tsx`, `frontend/src/App.test.tsx`, `frontend/e2e/real-stack.spec.ts` |
| `US2-AC3` | Thu hồi API Key (`DELETE /api/projects/{projectId}/api-keys/{keyHash}` -> `204 No Content`) và cập nhật tức thì danh sách khóa đang hoạt động | `client.test.ts`, `App.test.tsx`, `real-stack.spec.ts` | `PASS` | `frontend/src/components/ApiKeysModal.tsx`, `frontend/src/App.test.tsx`, `frontend/e2e/real-stack.spec.ts` |
| `US3-AC1` | Xem chi tiết Project (`GET /api/projects/{projectId}`) và chỉnh sửa thông tin Project (`PATCH /api/projects/{projectId}` cho `name`, `description`, `webhookUrl`) ngay trên giao diện | `client.test.ts`, `App.test.tsx (edits project metadata and deletes an empty project)`, `real-stack.spec.ts` | `PASS` | `frontend/src/pages/Projects.tsx`, `frontend/src/pages/Videos.tsx`, `frontend/src/App.test.tsx` |
| `US3-AC2` | Xóa mềm Project (`DELETE /api/projects/{projectId}` -> `204 No Content`), điều hướng về `/projects` hoặc hiển thị thông báo lỗi khi Backend chặn `409 Conflict` (`RESOURCE_IN_USE`) | `client.test.ts`, `App.test.tsx` | `PASS` | `frontend/src/pages/Videos.tsx`, `frontend/src/App.test.tsx` |
| `US4-AC1` | Hộp thoại `UploadDialog` cho phép nhập tiêu đề hiển thị `title` (1–200 ký tự, tự động gợi ý từ tên file) và truyền `title` vào `POST /api/projects/{projectId}/videos` | `UploadDialog.test.tsx (sends custom video title when preparing upload)`, `real-stack.spec.ts` | `PASS` | `frontend/src/components/UploadDialog.tsx`, `frontend/src/components/UploadDialog.test.tsx`, `frontend/e2e/real-stack.spec.ts` |
| `US4-AC2` | Xóa Video (`DELETE /api/videos/{videoId}` -> `204 No Content`) từ trang `VideoDetail`, khóa nút xóa khi `video.status === 'PROCESSING'` và hỗ trợ tải thêm trang (`Load more videos`) với `listVideosPage` | `client.test.ts`, `App.test.tsx`, `real-stack.spec.ts` | `PASS` | `frontend/src/pages/VideoDetail.tsx`, `frontend/src/pages/Videos.tsx`, `frontend/e2e/real-stack.spec.ts` |

## Commands executed

| Command | Exit code | Result | Test summary | Notes |
|---|---:|---|---|---|
| `cd frontend && npm run api:check` | `0` | `PASS` | OpenAPI schema in sync | Xác nhận `frontend/src/api/schema.d.ts` khớp 100% với `/contracts/openapi.yaml` |
| `cd frontend && npm test` | `0` | `PASS` | 13 test files passed, 142 tests passed, 0 failures | Bao gồm Unit tests (`HlsPlayer.test.tsx`, `UploadDialog.test.tsx`, `client.test.ts`), Contract tests (`contract.test.ts`) và UI Integration tests (`App.test.tsx`) |
| `cd frontend && npm run build` | `0` | `PASS` | `tsc -b && vite build && check-production-bundle.mjs` OK | Bundle production sạch MSW (`main 322298 B, HlsPlayer 597976 B`) |
| `E2E_REAL_STACK=1 E2E_BASE_URL=http://localhost:8000 npx playwright test e2e/real-stack.spec.ts` | `0` | `PASS` | 1 passed (14.2s) trên Chromium thật + Docker Compose + LocalStack | Kiểm chứng toàn luồng: Register -> Create/Edit Project -> Create/Revoke API Key -> Upload với custom `title` -> Worker Transcode -> Chuyển đổi `720p HD` / `360p SD` / `Auto (ABR)` & 4 `VideoAssets` -> Embed -> `FAILED_VALIDATION` -> Delete Video |

## Verification Layers

- **Static Analysis & Contract Check**: `npm run api:check`, `tsc -b` và `src/api/contract.test.ts` (PASS).
- **Unit & Component Tests**: `HlsPlayer.test.tsx` (8 tests), `UploadDialog.test.tsx` (4 tests), `client.test.ts` (49 tests) (PASS).
- **UI Integration Tests (MSW)**: `App.test.tsx` (8 tests bao phủ luồng API Keys, Edit/Delete Project, 4 VideoAssets & Delete Video) (PASS).
- **System / Real-Stack E2E Tests**: `frontend/e2e/real-stack.spec.ts` chạy trên trình duyệt thật kết nối qua Nginx Edge (`:8000`) tới Spring Boot Backend (`:8080`), Go FFmpeg Worker và LocalStack S3/SQS/DynamoDB (`:4566`) (PASS).

## Review

- Reviewer: User (Phương) & AI Pair Programmer
- Status: `verified` (Đã kiểm chứng toàn diện cả 16/16 endpoints Backend trên Frontend)
- Findings:
  - Người dùng đã có thể chủ động chuyển đổi giữa `Auto (ABR)`, `720p HD` và `360p SD` ngay dưới trình phát HLS và sao chép từng đường dẫn `VideoAssets` (`master.m3u8`, `720p/index.m3u8`, `360p/index.m3u8`, `thumb/default.jpg`).
  - Toàn bộ các tính năng quản trị Project (`PATCH`, `DELETE`), Developer API Keys (`GET`, `POST` hiển thị `rawKey` 1 lần, `DELETE`), tiêu đề video `title` khi upload và xóa video (`DELETE /api/videos/{videoId}`) đã hoạt động đồng bộ với Backend.

## Remaining risks / gaps

- Không còn khoảng trống tính năng nào giữa Frontend và các đặc tả Backend P0 (`F-003` -> `F-008`).
