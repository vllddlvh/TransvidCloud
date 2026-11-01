# Project Progress — Snapshot hiện tại

> File này là snapshot trạng thái mới nhất, không phải nhật ký lịch sử. Cập nhật/thay thế khi có thay đổi đáng kể hoặc kết thúc session. Không ghi lại toàn bộ prompt và tool calls.

## Cập nhật

- Thời điểm: `2026-11-01T20:52:00+07:00`
- Branch: `phuong-integration`

## Trạng thái tổng quan

- Current focus: `Tích hợp toàn hệ thống (Backend – Worker – Frontend) trên LocalStack & Đóng gói bàn giao Cloud`
- Feature MVP (F-001 -> F-009): `completed`
- Full-Stack Integration (BE + FE + Worker + Nginx + LocalStack): `verified`
- Tài liệu hướng dẫn Setup & Tự Test: [`../docs/LOCAL_INTEGRATION_AND_TEST_GUIDE.md`](../docs/LOCAL_INTEGRATION_AND_TEST_GUIDE.md)

## Kết quả đạt được trong phiên Tích hợp (Full-Stack Integration)

1. **Dockerization & Hạ tầng Local (`docker-compose.yml`, `backend/Dockerfile`, `local/nginx/default.conf`):**
   - Tạo `backend/Dockerfile` (multi-stage build `eclipse-temurin:21-jdk-alpine` -> `21-jre-alpine`, non-root user, healthcheck `/api/actuator/health`) và `backend/.dockerignore`.
   - Khắc phục cấu hình `docker-compose.yml`, `.env.example`, `application.yml` và `OutboxPublisherService.java` để tương thích đồng thời biến môi trường `TVC_*` và `S3_*`/`SQS_*` (hỗ trợ cả Queue Name và Queue URL).
   - Sửa lỗi phân giải `host.docker.internal` trong `local/nginx/default.conf` trên Linux.
2. **Đồng bộ Backend ↔ Worker:**
   - Cập nhật `VideoMapper.java` hỗ trợ đọc key `"hls"` (cùng `"masterPlaylistUrl"`, `"masterPlaylist"`) từ DynamoDB `assets` map do Worker ghi.
   - Cập nhật `worker/internal/consumer/consumer.go` gửi kèm `AttributeNames: [All]` và fallback `Attempt = 1` để tương thích `ApproximateReceiveCount` trên LocalStack 3.0.
3. **Đồng bộ Backend (SSOT `/contracts/openapi.yaml`) ↔ Frontend & E2E Script:**
   - Đồng bộ `/docs/contracts/openapi.yaml` theo `/contracts/openapi.yaml` của Backend và tái sinh `frontend/src/api/schema.d.ts` (`npm run api:check` PASS).
   - Cập nhật `frontend/src/api/types.ts` và `frontend/src/api/client.ts` chuẩn hóa đầy đủ wire format của Backend (`AuthResponse`, `ProjectResponse`, `InitVideoRequest`, `InitVideoResponse`, `VideoResponse`, `VideoAssets`, `ErrorResponse`).
   - Bổ sung tính năng **Create account (Register)** trên giao diện `Login.tsx`, hỗ trợ `npm run dev:real`, và xử lý trang nhúng `/embed?video=...&project=...` không cần mở public auth trên Backend.
   - Cập nhật `tests/e2e/run.sh` (chế độ `api` và `worker`) khớp 100% với contract Backend.
4. **Tài liệu Hướng dẫn Setup & Tự Test:**
   - Xuất bản tài liệu [`docs/LOCAL_INTEGRATION_AND_TEST_GUIDE.md`](../docs/LOCAL_INTEGRATION_AND_TEST_GUIDE.md) hướng dẫn chi tiết cách dựng hệ thống, tự kiểm thử trên trình duyệt & CLI, và checklist bàn giao cho đội Cloud.

## Blocker / Quyết định cần con người

- Không có blocker. Toàn bộ 7 cổng kiểm định (Quality Gates) đã PASS 100%.

## Verification gần nhất

| Lệnh | Kết quả | Thời điểm | Evidence |
|---|---|---|---|
| `make up` & `make smoke` | `PASS` (4 containers healthy/running, 9/9 smoke checks OK) | `2026-11-01T20:34:47+07:00` | `http://localhost:8080/api/actuator/health` & `:8000` -> `UP` |
| `make e2e` (`bash ./tests/e2e/run.sh api`) | `PASS` (Register -> Project -> S3 PUT -> Confirm -> Worker -> HLS 360p/720p & Thumbnail) | `2026-11-01T20:36:00+07:00` | Hoàn tất E2E API A4 trong 4s |
| `make e2e-worker` (`bash ./tests/e2e/run.sh worker`) | `PASS` (Worker FFmpeg HLS + Thumbnail + DDB + SQS ACK) | `2026-11-01T20:38:49+07:00` | Worker integration E2E đạt 100% |
| `E2E_REAL_STACK=1 E2E_BASE_URL=http://localhost:8000 npx playwright test e2e/real-stack.spec.ts` | `PASS` (1 passed in 8.6s trên Chrome thật) | `2026-11-01T20:48:03+07:00` | UI Register -> Project -> Upload -> HLS Playback -> Embed -> `FAILED_VALIDATION` |
| `cd frontend && npm run api:check && npm test && npm run build` | `PASS` (13 test files, 136 tests pass, production bundle OK) | `2026-11-01T20:29:57+07:00` | Frontend unit/contract tests & build PASS |
| `cd worker && go test ./...` | `PASS` (10/10 Go packages pass) | `2026-11-01T20:39:50+07:00` | Worker unit tests PASS |
| `cd backend && ./scripts/verify.sh` (`./mvnw -B clean verify`) | `PASS` (100% unit tests + 24 Testcontainers IT pass) | `2026-11-01T20:51:10+07:00` | Zero Regression trên Backend |

## Hành động tiếp theo

- Người dùng trải nghiệm/tự test theo hướng dẫn tại `docs/LOCAL_INTEGRATION_AND_TEST_GUIDE.md` và bàn giao cho đội Cloud.
