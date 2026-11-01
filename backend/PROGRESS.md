# Project Progress — Snapshot hiện tại

> File này là snapshot trạng thái mới nhất, không phải nhật ký lịch sử. Cập nhật/thay thế khi có thay đổi đáng kể hoặc kết thúc session. Không ghi lại toàn bộ prompt và tool calls.

## Cập nhật

- Thời điểm: `2026-11-01T22:15:30+07:00`
- Branch: `phuong-integration`

## Trạng thái tổng quan

- Current focus: `F-011 — Đồng bộ Đầy đủ Tính năng Frontend theo Đặc tả Backend (F-003 -> F-008)`
- Feature MVP Backend (F-001 -> F-009): `completed`
- Feature F-011 (Frontend Feature Parity 16/16 Backend Endpoints): `verified` ([`quality/evidence/F-011-frontend-feature-parity.md`](quality/evidence/F-011-frontend-feature-parity.md))
- Full-Stack Integration (BE + FE + Worker + Nginx + LocalStack): `verified`
- Tài liệu hướng dẫn Setup & Tự Test: [`../docs/LOCAL_INTEGRATION_AND_TEST_GUIDE.md`](../docs/LOCAL_INTEGRATION_AND_TEST_GUIDE.md)

## Kết quả đạt được trong phiên (F-011 & Full-Stack Integration)

1. **US1 — Bộ chọn chất lượng phát HLS (`Auto / 720p HD / 360p SD`) & Danh mục 4 `VideoAssets` (`F-008`):**
   - Nâng cấp `frontend/src/player/HlsPlayer.tsx` bổ sung thanh **Playback quality** (`Auto (ABR)`, `720p HD`, `360p SD`) giữ nguyên thời điểm phát khi chuyển luồng.
   - Nâng cấp `frontend/src/pages/VideoDetail.tsx` hiển thị đầy đủ cả 4 đường dẫn tài nguyên đầu ra (`Master HLS (ABR)`, `720p HD HLS`, `360p SD HLS`, `Thumbnail JPEG`) kèm nút `Copy` riêng cho từng đường dẫn và bảng siêu dữ liệu vòng đời xử lý (`Title`, `File name`, `Attempts`, `Added`, `Started`, `Completed`, `Updated`).
2. **US2 — Quản lý Developer API Keys theo Project (`F-005`):**
   - Xây dựng `frontend/src/components/ApiKeysModal.tsx` hỗ trợ liệt kê danh sách khóa (`x / 5 keys`), tạo khóa mới (`POST /api/projects/{projectId}/api-keys`) hiển thị `rawKey` duy nhất 1 lần kèm nút Copy, và thu hồi khóa (`DELETE /api/projects/{projectId}/api-keys/{keyHash}`).
3. **US3 — Quản trị Vòng đời Project (`F-004`):**
   - Cập nhật `frontend/src/pages/Projects.tsx` và `frontend/src/pages/Videos.tsx` hỗ trợ xem chi tiết Project (`GET /api/projects/{projectId}`), chỉnh sửa `name`/`description`/`webhookUrl` (`PATCH /api/projects/{projectId}`) và xóa mềm Project (`DELETE /api/projects/{projectId}`).
4. **US4 — Nhập Tiêu đề Video (`title`), Xóa Video & Phân trang Cursor (`F-006`, `F-008`):**
   - Cập nhật `frontend/src/components/UploadDialog.tsx` bổ sung ô `Video title` (1–200 ký tự, tự động gợi ý từ tên file).
   - Bổ sung nút **Delete video** (`DELETE /api/videos/{videoId}`, khóa khi `status === 'PROCESSING'`) tại `VideoDetail.tsx` và hỗ trợ `Load more videos` (`listVideosPage`) tại `Videos.tsx`.

## Blocker / Quyết định cần con người

- Không có blocker. Toàn bộ 25/25 tasks của `F-011` và các cổng kiểm định đã PASS 100%.

## Verification gần nhất

| Lệnh | Kết quả | Thời điểm | Evidence |
|---|---|---|---|
| `cd frontend && npm run api:check && npm test && npm run build` | `PASS` (13 test files, 142 tests pass, production bundle OK) | `2026-11-01T22:10:53+07:00` | [`quality/evidence/F-011-frontend-feature-parity.md`](quality/evidence/F-011-frontend-feature-parity.md) |
| `E2E_REAL_STACK=1 E2E_BASE_URL=http://localhost:8000 npx playwright test e2e/real-stack.spec.ts` | `PASS` (1 passed in 14.2s trên Chromium thật + Full Stack) | `2026-11-01T22:14:48+07:00` | Register -> Edit Project -> API Keys -> Upload + Title -> HLS 720p/360p/Auto & 4 Assets -> Embed -> `FAILED_VALIDATION` -> Delete Video |
| `make e2e` & `make e2e-worker` | `PASS` | `2026-11-01T20:38:49+07:00` | Full API & Worker E2E trên LocalStack |
| `cd backend && ./scripts/verify.sh` | `PASS` (100% unit tests + 24 Testcontainers IT pass) | `2026-11-01T20:51:10+07:00` | Zero Regression trên Backend |

## Hành động tiếp theo

- Người dùng trải nghiệm các tính năng mới trên giao diện theo hướng dẫn tại [`docs/LOCAL_INTEGRATION_AND_TEST_GUIDE.md`](../docs/LOCAL_INTEGRATION_AND_TEST_GUIDE.md).
