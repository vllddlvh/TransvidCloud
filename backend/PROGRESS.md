# Project Progress — Snapshot hiện tại

> File này là snapshot trạng thái mới nhất, không phải nhật ký lịch sử. Cập nhật/thay thế khi có thay đổi đáng kể hoặc kết thúc session. Không ghi lại toàn bộ prompt và tool calls.

## Cập nhật

- Thời điểm: `2026-11-01T17:23:00+07:00`
- Branch: `phuong-backend`

## Trạng thái tổng quan

- Current focus: `Sẵn sàng cho Feature tiếp theo (F-009: Quan sát hệ thống, Bảo mật Log & Bộ kiểm thử Tích hợp / Contract)`
- Feature vừa hoàn thành: `F-008: Truy vấn Danh sách (Cursor Pagination), Chi tiết, Assets & Xóa Video (Epic A2/A3)` (completed)
- Feature spec F-008: [`specs/008-video-query-lifecycle/spec.md`](specs/008-video-query-lifecycle/spec.md)
- Evidence F-008: [`quality/evidence/F-008-video-query-lifecycle.md`](quality/evidence/F-008-video-query-lifecycle.md)
- Status: `ready-for-next-feature`

## Kết quả đã được kiểm chứng (F-008)

- **Hoàn thành 100% 34/34 Tasks của F-008**:
  - `Phase 1 & 2 (Setup & Foundational)`: Mã lỗi `INVALID_CURSOR`, cấu hình `tvc.media.base-url`, các DTOs (`VideoCursor`, `VideoAssets`, `VideoPageResponse`, `PagedVideoResult`), tiện ích `CursorCodec` (mã hóa Base64URL không padding, thẩm định an ninh chống tráo đổi projectId).
  - `Phase 3 (User Story 1 - MVP)`: Endpoint `GET /api/projects/{projectId}/videos` phân trang con trỏ trên GSI `project-index`, sắp xếp giảm dần `createdAt`, lọc sạch bản ghi `OUTBOX#`.
  - `Phase 4 (User Story 2)`: Endpoint `GET /api/videos/{videoId}` tra cứu chi tiết, tự động ghép tiền tố `TVC_MEDIA_BASE_URL` (mặc định `/media`) vào các URL `assets` (`masterPlaylistUrl`, `hls360Url`, `hls720Url`, `thumbnailUrl`) khi video `COMPLETED`, và trả về `assets = null` khi chưa xong.
  - `Phase 5 (User Story 3)`: Endpoint `DELETE /api/videos/{videoId}` Fast Delete trả về 204 No Content và Conflict Guard chặn đứng với HTTP 409 Conflict (`INVALID_STATE_TRANSITION`) khi video đang `PROCESSING`.
  - `Phase 6 (User Story 4)`: Dual Security bảo vệ toàn bộ 3 endpoints bằng Bearer JWT và `X-API-Key`, cô lập tuyệt đối dữ liệu giữa các tenant (401/403).
  - `Phase 7 (Polish & Real Verification)`:
    - Testcontainers LocalStack Integration Test (`VideoQueryLifecycleIT`): 4/4 runs PASS 100%.
    - Postman E2E Collection (`F-008-video-query-lifecycle.postman_collection.json`): 18 requests, 27 assertions PASS 100% qua Newman CLI.
    - Zero Regression: `./scripts/verify.sh` (`./mvnw -B clean verify`) PASS 100% trên toàn bộ test suite dự án (0 failures, 0 errors).
    - Hồ sơ Feature Evidence được lập đầy đủ tại `quality/evidence/F-008-video-query-lifecycle.md`.
    - `/speckit-converge`: Đạt kết quả `CONVERGED` (0 findings, 0 gaps).

## Việc còn lại

- Khởi động Feature tiếp theo theo kế hoạch: `F-009: Quan sát hệ thống, Bảo mật Log & Bộ kiểm thử Tích hợp / Contract (Epic A5/A8/C5)`.

## Blocker / Quyết định cần con người

- Không có blocker. Tất cả tests pass, codebase sạch, không có tiến trình treo.

## Verification gần nhất

| Lệnh | Kết quả | Thời điểm | Evidence |
|---|---|---|---|
| `./scripts/verify.sh` (`./mvnw -B clean verify`) | `PASS` (100% test suite pass, 0 failures, 0 errors) | `2026-11-01T17:18:54+07:00` | Zero Regression test suite pass toàn diện |
| `npx newman run backend/postman/F-008-video-query-lifecycle.postman_collection.json` | `PASS` (18 requests, 27 assertions, 0 failures) | `2026-11-01T17:16:21+07:00` | E2E Postman collection pass 100% |
| `./mvnw -B test -Dtest="VideoQueryLifecycleIT"` | `PASS` (4 runs, 0 failures, 0 errors) | `2026-11-01T17:13:41+07:00` | Testcontainers LocalStack DynamoDB + S3 IT |
| `/speckit-converge` | `CONVERGED` (0 findings) | `2026-11-01T17:21:40+07:00` | Codebase khớp 100% spec, plan, tasks |

## Hành động tiếp theo

- Bắt đầu chu trình SDD cho Feature tiếp theo (F-009): `/speckit-specify F-009`.
