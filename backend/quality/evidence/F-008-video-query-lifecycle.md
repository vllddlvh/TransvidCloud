# Feature Verification Evidence — F-008

- Feature: `F-008 — Truy Vấn Danh Sách (Cursor Pagination), Chi Tiết, Assets & Xóa Video (Epic A2/A3)`
- Spec: [`specs/008-video-query-lifecycle/spec.md`](../../specs/008-video-query-lifecycle/spec.md)
- Plan: [`specs/008-video-query-lifecycle/plan.md`](../../specs/008-video-query-lifecycle/plan.md)
- Tasks: [`specs/008-video-query-lifecycle/tasks.md`](../../specs/008-video-query-lifecycle/tasks.md)
- Branch: `phuong-backend`
- Date: `2026-11-01T17:15:00+07:00`

## Acceptance criteria mapping

| Criterion ID | Mô tả tóm tắt | Evidence / test | Result | Reference |
|---|---|---|---|---|
| `US1-AC1` | Phân trang con trỏ (Cursor Pagination) trên GSI `project-index` theo thứ tự `createdAt` giảm dần (mới nhất lên đầu), giới hạn `limit` (mặc định 20, 1..100) | `VideoServiceTest.testListVideosByProject_Success_FirstPage`, `VideoQueryLifecycleIT.testCursorPagination_EndToEnd`, `Newman E2E Item 08` | `PASS` | `VideoServiceTest.java`, `VideoQueryLifecycleIT.java`, `F-008-video-query-lifecycle.postman_collection.json` |
| `US1-AC2` | Trả về cấu trúc phân trang chuẩn `VideoPageResponse` gồm `items`, `nextCursor` (Base64URL) và `hasMore = true` khi còn bản ghi | `VideoControllerTest.testListVideosByProject_Success_Returns200`, `VideoQueryLifecycleIT.testCursorPagination_EndToEnd`, `Newman E2E Item 08` | `PASS` | `VideoControllerTest.java`, `VideoQueryLifecycleIT.java`, `F-008-video-query-lifecycle.postman_collection.json` |
| `US1-AC3` | Trang cuối cùng trả về `hasMore = false` và `nextCursor = null` | `VideoServiceTest.testListVideosByProject_Success_NextAndLastPage`, `VideoQueryLifecycleIT.testCursorPagination_EndToEnd`, `Newman E2E Item 09` | `PASS` | `VideoServiceTest.java`, `VideoQueryLifecycleIT.java`, `F-008-video-query-lifecycle.postman_collection.json` |
| `US1-AC4` | Cursor an toàn Tamper-proof: Chặn đứng HTTP 400 `INVALID_CURSOR` khi client can thiệp, làm hỏng chuỗi hoặc tráo đổi `projectId` khác | `CursorCodecTest.decode_TamperedProjectId_ThrowsInvalidCursor`, `VideoServiceTest.testListVideosByProject_Throws400_WhenCursorProjectTampered`, `VideoControllerTest.testListVideosByProject_InvalidCursor_Returns400`, `Newman E2E Item 11` | `PASS` | `CursorCodecTest.java`, `VideoServiceTest.java`, `VideoControllerTest.java`, `F-008-video-query-lifecycle.postman_collection.json` |
| `US1-AC5` | Lọc sạch các bản ghi nội bộ Transactional Outbox (`OUTBOX#...`), người dùng chỉ nhận được bản ghi video thật sự | `VideoRepository.findVideosByProjectWithPagination`, `VideoQueryLifecycleIT.testCursorPagination_EndToEnd` | `PASS` | `VideoRepository.java`, `VideoQueryLifecycleIT.java` |
| `US2-AC1` | Tra cứu chi tiết video qua `GET /api/videos/{videoId}`, trả về đầy đủ metadata vòng đời video (`videoId`, `projectId`, `title`, `status`, `sizeBytes`, `contentType`, `attemptCount`, `errorCode`, `createdAt`, `updatedAt`, `startedAt`, `completedAt`) | `VideoServiceTest.testGetVideoDetails_Success_CompletedWithAssets`, `VideoControllerTest.testGetVideoDetails_Success_Returns200WithAssets`, `VideoQueryLifecycleIT.testGetVideoDetails_CompletedAndProcessing`, `Newman E2E Item 13` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java`, `VideoQueryLifecycleIT.java`, `F-008-video-query-lifecycle.postman_collection.json` |
| `US2-AC2` | Tự động ghép tiền tố `TVC_MEDIA_BASE_URL` (mặc định `/media`) vào các đường dẫn `assets` (`masterPlaylistUrl`, `hls360Url`, `hls720Url`, `thumbnailUrl`) khi video ở trạng thái `COMPLETED` | `VideoMapper.mapAssets`, `VideoServiceTest.testGetVideoDetails_Success_CompletedWithAssets`, `VideoQueryLifecycleIT.testGetVideoDetails_CompletedAndProcessing` | `PASS` | `VideoMapper.java`, `VideoServiceTest.java`, `VideoQueryLifecycleIT.java` |
| `US2-AC3` | Trường `assets = null` khi video ở trạng thái chưa hoàn tất (`UPLOADING`, `PENDING`, `PROCESSING`, `FAILED_*`) | `VideoServiceTest.testGetVideoDetails_Success_ProcessingNullAssets`, `VideoQueryLifecycleIT.testGetVideoDetails_CompletedAndProcessing`, `Newman E2E Item 13` | `PASS` | `VideoServiceTest.java`, `VideoQueryLifecycleIT.java`, `F-008-video-query-lifecycle.postman_collection.json` |
| `US2-AC4` | Từ chối HTTP 404 Not Found (`RESOURCE_NOT_FOUND`) khi tra cứu videoId không tồn tại hoặc cố ý truyền khóa nội bộ `OUTBOX#` | `VideoServiceTest.testGetVideoDetails_Throws404_WhenNotFound`, `VideoControllerTest.testGetVideoDetails_NotFound_Returns404`, `Newman E2E Item 16` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java`, `F-008-video-query-lifecycle.postman_collection.json` |
| `US3-AC1` | Xóa video qua `DELETE /api/videos/{videoId}` trả về HTTP 204 No Content và dọn dẹp bản ghi OutboxItem tương ứng nếu có | `VideoServiceTest.testDeleteVideo_Success_WhenCompleted`, `VideoControllerTest.testDeleteVideo_Success_Returns204`, `VideoQueryLifecycleIT.testDeleteVideo_FastDeleteAndConflictGuard`, `Newman E2E Item 15` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java`, `VideoQueryLifecycleIT.java`, `F-008-video-query-lifecycle.postman_collection.json` |
| `US3-AC2` | Cơ chế Fast Delete: Xóa bản ghi DynamoDB tức thì để video biến mất khỏi API List & Detail với độ trễ < 100ms | `VideoQueryLifecycleIT.testDeleteVideo_FastDeleteAndConflictGuard`, `Newman E2E Item 15 & 16` | `PASS` | `VideoQueryLifecycleIT.java`, `F-008-video-query-lifecycle.postman_collection.json` |
| `US3-AC3` | Conflict Guard: Chặn đứng yêu cầu xóa video với HTTP 409 Conflict (`INVALID_STATE_TRANSITION`) khi video đang ở trạng thái `PROCESSING` | `VideoServiceTest.testDeleteVideo_Throws409_WhenProcessing`, `VideoControllerTest.testDeleteVideo_ProcessingConflict_Returns409`, `VideoQueryLifecycleIT.testDeleteVideo_FastDeleteAndConflictGuard` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java`, `VideoQueryLifecycleIT.java` |
| `US4-AC1` | Phân quyền Dual Security: Bảo vệ cả 3 endpoints qua Bearer JWT (chủ sở hữu Project) và `X-API-Key` (thuộc đúng Project chứa video) | `VideoServiceTest.testDualSecurity`, `VideoControllerTest.testDualSecurity`, `VideoQueryLifecycleIT.testDualSecurity_JwtAndApiKeyIsolation`, `Newman E2E Item 10 & 14` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java`, `VideoQueryLifecycleIT.java`, `F-008-video-query-lifecycle.postman_collection.json` |
| `US4-AC2` | Chặn truy cập hoặc xóa trái phép từ Project / User khác với HTTP 403 Forbidden (`ACCESS_DENIED`) | `VideoServiceTest.testListVideosByProject_Throws403_WhenJwtNotOwner`, `VideoServiceTest.testGetVideoDetails_Throws403_WhenApiKeyMismatch`, `VideoServiceTest.testDeleteVideo_Throws403_WhenJwtNotOwner`, `VideoQueryLifecycleIT.testDualSecurity_JwtAndApiKeyIsolation`, `Newman E2E Item 17` | `PASS` | `VideoServiceTest.java`, `VideoQueryLifecycleIT.java`, `F-008-video-query-lifecycle.postman_collection.json` |
| `US4-AC3` | Chặn yêu cầu không gửi thông tin xác thực với HTTP 401 Unauthorized (`UNAUTHENTICATED`) | `VideoControllerTest.testGetVideoDetails_Unauthenticated_Returns401`, `Newman E2E Item 18` | `PASS` | `VideoControllerTest.java`, `F-008-video-query-lifecycle.postman_collection.json` |

## Commands executed

| Command | Exit code | Result | Test summary | Notes |
|---|---:|---|---|---|
| `./scripts/check-setup.sh` | `0` | `PASS` | 10 required files OK | Kiểm tra cấu trúc Harness và engineering guidelines |
| `./mvnw -B test -Dtest="CursorCodecTest"` | `0` | `PASS` | 6 runs, 0 failures | Kiểm tra mã hóa Base64URL không padding, giải mã JSON và tamper guard |
| `./mvnw -B test -Dtest="VideoServiceTest"` | `0` | `PASS` | 33 runs, 0 failures | Kiểm tra nghiệp vụ phân trang con trỏ, chi tiết assets và xóa video |
| `./mvnw -B test -Dtest="VideoControllerTest"` | `0` | `PASS` | 26 runs, 0 failures | WebMvc slice test 3 endpoints mới kèm validation và dual security |
| `./mvnw -B test -Dtest="VideoQueryLifecycleIT"` | `0` | `PASS` | 4 runs, 0 failures | Real Verification Testcontainers LocalStack DynamoDB + S3: phân trang 5 video, assets /media URL, fast delete 204, conflict guard 409, dual security |
| `npx newman run backend/postman/F-008-video-query-lifecycle.postman_collection.json` | `0` | `PASS` | 18 requests, 27 assertions, 0 failures | Kiểm thử E2E độc lập qua Newman trên hệ thống thực tế |
| `./scripts/verify.sh` (`./mvnw -B clean verify`) | `0` | `PASS` | 100% tests PASS (109 tests cũ + tests mới F-008), 0 failures | Đạt Zero Regression tuyệt đối trên toàn bộ test suite dự án |

## Verification Layers

- **Static Analysis / Structure**: `scripts/check-setup.sh` (PASS).
- **Unit / Web Slice Tests**: `CursorCodecTest`, `VideoServiceTest`, `VideoControllerTest` (PASS 65/65 tests).
- **Integration Tests (Real Verification)**: `VideoQueryLifecycleIT` tương tác trực tiếp với LocalStack 3.0 container:
  - Khởi tạo 5 video -> Phân trang con trỏ `limit=2` các trang 1, 2, 3 -> Lọc sạch bản ghi `OUTBOX#`.
  - Tra cứu chi tiết video `COMPLETED` -> Tự động ghép tiền tố `/media` cho `masterPlaylistUrl`, `hls360Url`, `hls720Url`, `thumbnailUrl`.
  - Tra cứu video `PROCESSING` -> Trả về `assets = null`.
  - Thử xóa video `PROCESSING` -> Bị chặn đứng với HTTP 409 Conflict (`INVALID_STATE_TRANSITION`).
  - Xóa video `COMPLETED` -> Xóa thành công 204 No Content và tra cứu lại ném 404 Not Found.
  - Kiểm tra cô lập đa người thuê tuyệt đối cho cả Bearer JWT và X-API-Key trên 3 endpoints.
- **Zero Regression**: Toàn bộ test suite dự án đều PASS 100% khi chạy `./scripts/verify.sh`.
- **E2E Postman Collection**: Collection 18 kịch bản tại `backend/postman/F-008-video-query-lifecycle.postman_collection.json`, chạy tự động qua Newman CLI với 27/27 assertions đạt PASS 100%.

## Review

- Reviewer: User (Phương) & AI Pair Programmer
- Status: `verified` (Đã kiểm chứng toàn diện, sẵn sàng bàn giao)
- Findings:
  - Cursor phân trang mã hóa Base64URL không padding, bảo mật chống tráo đổi `projectId` giữa các tenant (Tamper-proof guard).
  - Tra cứu GSI `project-index` sắp xếp giảm dần `createdAt` đạt thời gian phản hồi < 100ms.
  - Video Assets tự động chuẩn hóa Base URL, hỗ trợ linh hoạt cấu hình biến môi trường `TVC_MEDIA_BASE_URL`.
  - Cơ chế Fast Delete phản hồi 204 No Content tức thì, đồng thời Conflict Guard ngăn chặn xung đột với tiến trình chuyển mã dở dang `PROCESSING`.

## Remaining risks / gaps

- Không có rủi ro kỹ thuật hay khoảng trống nào còn lại.
