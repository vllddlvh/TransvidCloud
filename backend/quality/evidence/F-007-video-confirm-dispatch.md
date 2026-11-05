# Feature Verification Evidence — F-007

- Feature: `F-007 — Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job (Epic A3/A5)`
- Spec: [`specs/007-video-confirm-dispatch/spec.md`](../../specs/007-video-confirm-dispatch/spec.md)
- Plan: [`specs/007-video-confirm-dispatch/plan.md`](../../specs/007-video-confirm-dispatch/plan.md)
- Tasks: [`specs/007-video-confirm-dispatch/tasks.md`](../../specs/007-video-confirm-dispatch/tasks.md)
- Branch: `phuong-backend`
- Date: `2026-11-01T16:25:00+07:00`

## Acceptance criteria mapping

| Criterion ID | Mô tả tóm tắt | Evidence / test | Result | Reference |
|---|---|---|---|---|
| `US1-AC1` | Xác nhận video hợp lệ lần đầu trả về 202 Accepted, chuyển status sang `PENDING`, ghi OutboxItem và điều phối SQS Job | `VideoServiceTest.testConfirmVideoUpload_Success_FirstCallReturns202`, `VideoControllerTest.testConfirmVideo_Success_Returns202`, `VideoConfirmDispatchIT.testConfirmVideo_FullLifecycle_RealVerification`, `Newman E2E Item 10` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java`, `VideoConfirmDispatchIT.java`, `F-007-video-confirm-dispatch.postman_collection.json` |
| `US1-AC2` | Phát hành SQS message vào hàng đợi `tvc-{env}-jobs` tuân thủ nghiêm ngặt schema JSON v1 (`schemaVersion: 1`, `jobId`, `videoId`, `projectId`, `inputKey`, `requestedAt`) | `OutboxPublisherServiceTest.testPublishOutboxItem_Success`, `VideoConfirmDispatchIT.testConfirmVideo_FullLifecycle_RealVerification` | `PASS` | `OutboxPublisherServiceTest.java`, `VideoConfirmDispatchIT.java` |
| `US1-AC3` | Nguyên tử hóa bằng DynamoDB `TransactWriteItems`: Cập nhật `VideoItem` và lưu `OutboxItem` trong cùng 1 transaction duy nhất | `VideoServiceTest.testConfirmVideoUpload_Success_FirstCallReturns202`, `VideoConfirmDispatchIT.testConfirmVideo_FullLifecycle_RealVerification` | `PASS` | `VideoRepository.java`, `VideoConfirmDispatchIT.java` |
| `US2-AC1` | Tính Idempotent: Gọi confirm lặp lại khi video đã `PENDING`, `PROCESSING` hoặc `COMPLETED` trả về 200 OK ngay (`newlyConfirmed = false`) mà không gửi trùng SQS message | `VideoServiceTest.testConfirmVideoUpload_Idempotent_SecondCallReturns200`, `VideoControllerTest.testConfirmVideo_Idempotent_Returns200`, `VideoConfirmDispatchIT.testConfirmVideo_Idempotent_SecondCallNoDuplicateSqs`, `Newman E2E Item 11` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java`, `VideoConfirmDispatchIT.java`, `F-007-video-confirm-dispatch.postman_collection.json` |
| `US2-AC2` | Cơ chế Outbox Relay Scheduler (`@Scheduled`) định kỳ quét GSI `project-index` (`OUTBOX#PENDING`) để phát bù các sự kiện tồn đọng mà không Table Scan | `OutboxRelaySchedulerTest.testRelayPendingOutboxEvents_Success`, `VideoConfirmDispatchIT.testOutboxRelay_CompensatePendingEvents` | `PASS` | `OutboxRelayScheduler.java`, `OutboxRepository.java`, `VideoConfirmDispatchIT.java` |
| `US2-AC3` | Định danh `jobId` và `requestedAt` bất biến qua các lần gọi lại để downstream worker (Go) chống xử lý trùng (Idempotency Key) | `VideoValidationHelperTest.testGenerateJobId_FormatAndEntropy`, `VideoConfirmDispatchIT.testConfirmVideo_FullLifecycle_RealVerification` | `PASS` | `VideoValidationHelper.java`, `VideoConfirmDispatchIT.java` |
| `US3-AC1` | Kiểm tra đối tượng S3 qua `HeadObject`; nếu không tồn tại hoặc file 0 byte thì từ chối HTTP 422 Unprocessable Entity (`S3_OBJECT_NOT_FOUND`) | `VideoServiceTest.testConfirmVideoUpload_Throws422_WhenS3ObjectNotFound`, `VideoServiceTest.testConfirmVideoUpload_Throws422_WhenS3ObjectZeroBytes`, `VideoConfirmDispatchIT.testConfirmVideo_ErrorCases`, `Newman E2E Item 07` | `PASS` | `VideoServiceTest.java`, `VideoConfirmDispatchIT.java`, `F-007-video-confirm-dispatch.postman_collection.json` |
| `US3-AC2` | Chấp nhận dung lượng linh hoạt `0 < contentLength <= 200MB`, cập nhật `sizeBytes` thực tế vào DynamoDB; nếu > 200MB từ chối HTTP 413 Payload Too Large (`UPLOAD_LIMIT_EXCEEDED`) | `VideoServiceTest.testConfirmVideoUpload_Throws413_WhenS3ObjectExceedsMaxBytes`, `VideoServiceTest.testConfirmVideoUpload_Success_FirstCallReturns202`, `VideoConfirmDispatchIT.testConfirmVideo_FullLifecycle_RealVerification` | `PASS` | `VideoServiceTest.java`, `VideoConfirmDispatchIT.java` |
| `US4-AC1` | Hỗ trợ xác thực kép Dual Security: Bearer JWT (chủ sở hữu Project) và `X-API-Key` (thuộc đúng Project chứa video) | `VideoServiceTest.testConfirmVideoUpload_Success_ApiKey`, `VideoConfirmDispatchIT.testConfirmVideo_WithApiKeyAuthentication`, `Newman E2E Item 10 & 14` | `PASS` | `VideoServiceTest.java`, `VideoConfirmDispatchIT.java`, `F-007-video-confirm-dispatch.postman_collection.json` |
| `US4-AC2` | Chặn người dùng hoặc API Key của Project khác gọi xác nhận video với HTTP 403 Forbidden (`ACCESS_DENIED`) | `VideoServiceTest.testConfirmVideoUpload_Throws403_WhenJwtUserNotOwner`, `VideoServiceTest.testConfirmVideoUpload_Throws403_WhenApiKeyDifferentProject`, `VideoConfirmDispatchIT.testConfirmVideo_ErrorCases`, `Newman E2E Item 09` | `PASS` | `VideoServiceTest.java`, `VideoConfirmDispatchIT.java`, `F-007-video-confirm-dispatch.postman_collection.json` |
| `US4-AC3` | Chặn xác nhận khi `videoId` không tồn tại với HTTP 404 Not Found (`RESOURCE_NOT_FOUND`) | `VideoServiceTest.testConfirmVideoUpload_Throws404_WhenVideoNotFound`, `VideoControllerTest.testConfirmVideo_ResourceNotFound_Returns404`, `VideoConfirmDispatchIT.testConfirmVideo_ErrorCases`, `Newman E2E Item 15` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java`, `VideoConfirmDispatchIT.java`, `F-007-video-confirm-dispatch.postman_collection.json` |
| `US4-AC4` | Chặn xác nhận khi video ở trạng thái lỗi kết thúc `FAILED_*` với HTTP 409 Conflict (`INVALID_STATE_TRANSITION`) | `VideoServiceTest.testConfirmVideoUpload_Throws409_WhenVideoInFailedStatus`, `VideoControllerTest.testConfirmVideo_InvalidState_Returns409` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java` |
| `US4-AC5` | Chặn yêu cầu không có thông tin xác thực với HTTP 401 Unauthorized (`UNAUTHENTICATED`) | `VideoControllerTest.testConfirmVideo_Unauthenticated_Returns401`, `Newman E2E Item 16` | `PASS` | `VideoControllerTest.java`, `F-007-video-confirm-dispatch.postman_collection.json` |

## Commands executed

| Command | Exit code | Result | Test summary | Notes |
|---|---:|---|---|---|
| `./scripts/check-setup.sh` | `0` | `PASS` | 10 required files OK | Kiểm tra cấu trúc Harness và engineering guidelines |
| `./mvnw -B test -Dtest="VideoValidationHelperTest"` | `0` | `PASS` | 6 runs, 0 failures | Kiểm tra format `job_` + 12 hex CSPRNG và entropy |
| `./mvnw -B test -Dtest="OutboxPublisherServiceTest"` | `0` | `PASS` | 3 runs, 0 failures | Kiểm tra gửi SQS và cập nhật trạng thái OutboxItem |
| `./mvnw -B test -Dtest="OutboxRelaySchedulerTest"` | `0` | `PASS` | 3 runs, 0 failures | Kiểm tra Outbox Relay quét GSI phát bù FIFO |
| `./mvnw -B test -Dtest="VideoServiceTest"` | `0` | `PASS` | 20 runs, 0 failures | Kiểm tra toàn diện confirmVideoUpload: S3 check, 202/200, 403, 404, 409, 413, 422 |
| `./mvnw -B test -Dtest="VideoControllerTest"` | `0` | `PASS` | 14 runs, 0 failures | WebMvc slice test endpoint POST /api/videos/{videoId}/confirm và POST /api/projects/{projectId}/videos |
| `./mvnw -B test -Dtest="VideoConfirmDispatchIT"` | `0` | `PASS` | 5 runs, 0 failures | Real Verification Testcontainers LocalStack S3 + DynamoDB + SQS: vòng đời thực tế, SQS schema v1, idempotent không duplicate, outbox relay phát bù, dual security auth |
| `./scripts/verify.sh` (`./mvnw -B clean verify`) | `0` | `PASS` | 100% tests PASS (109 tests cũ + tests mới), 0 failures | Đạt Zero Regression tuyệt đối trên toàn bộ test suite dự án |
| `npx newman run backend/postman/F-007-video-confirm-dispatch.postman_collection.json` | `0` | `PASS` | 16 requests, 29 assertions, 0 failures | Kiểm thử E2E độc lập qua Newman trên LocalStack thực tế |

## Verification Layers

- **Static Analysis / Structure**: `scripts/check-setup.sh` (PASS).
- **Unit / Web Slice Tests**: `VideoValidationHelperTest`, `OutboxPublisherServiceTest`, `OutboxRelaySchedulerTest`, `VideoServiceTest`, `VideoControllerTest` (PASS 46/46 tests mới liên quan đến video confirm và outbox dispatch).
- **Integration Tests (Real Verification)**: `VideoConfirmDispatchIT` tương tác trực tiếp với LocalStack 3.0 container:
  - Khởi tạo video -> Upload byte thật lên S3 qua Presigned PUT URL.
  - Gọi xác nhận upload thành công (202 Accepted).
  - Xác minh DynamoDB `VideoItem` chuyển sang `PENDING` và cập nhật `sizeBytes` thực tế.
  - Xác minh DynamoDB `OutboxItem` được tạo và cập nhật sang `OUTBOX#PUBLISHED`.
  - Xác minh SQS queue nhận được message `SqsJobMessage` chuẩn schema v1 (`requestedAt`, `jobId`, `inputKey`, `projectId`, `videoId`).
  - Xác minh Idempotency: Lần gọi thứ hai trả về 200 OK và không gửi thêm message SQS nào.
  - Xác minh Outbox Relay phát bù các sự kiện `OUTBOX#PENDING` thành công.
  - Xác minh phân quyền kép (JWT + API Key) và các mã lỗi 403, 404, 422.
- **Zero Regression**: Toàn bộ 109 tests có sẵn từ F-001 -> F-006 kết hợp cùng các test mới của F-007 đều PASS 100% khi chạy `./scripts/verify.sh`.
- **E2E Postman Collection**: Collection 16 kịch bản tại `backend/postman/F-007-video-confirm-dispatch.postman_collection.json`, chạy tự động qua Newman CLI với 29/29 assertions đạt PASS 100%.

## Review

- Reviewer: User (Phương) & AI Pair Programmer
- Status: `verified` (Đã kiểm chứng toàn diện, sẵn sàng bàn giao)
- Findings:
  - Áp dụng mẫu Transactional Outbox Event theo mô hình Single-Table trên bảng `tvc-{env}-videos`, không cần tạo thêm bảng mới, không vi phạm ranh giới hệ thống của hợp đồng hợp đồng tích hợp (`/contracts`).
  - Outbox Relay quét GSI `project-index` (`OUTBOX#PENDING`) có điều kiện, bảo đảm 0 Table Scan.
  - Cung cấp `jobId` ngẫu nhiên CSPRNG cố định (`job_` + 12 hex) cùng `requestedAt` để Worker downstream (Go) chống trùng lặp xử lý (idempotency key).
  - Không trung chuyển byte video qua Backend; kiểm tra S3 bằng `HeadObject` nhanh chóng, tiết kiệm băng thông và tài nguyên.

## Remaining risks / gaps

- Không có rủi ro kỹ thuật hay khoảng trống nào còn lại.
- Tất cả các verification gates đều đạt PASS 100%.
