# Project Progress — Snapshot hiện tại

> File này là snapshot trạng thái mới nhất, không phải nhật ký lịch sử. Cập nhật/thay thế khi có thay đổi đáng kể hoặc kết thúc session. Không ghi lại toàn bộ prompt và tool calls.

## Cập nhật

- Thời điểm: `2026-11-01T00:50:00+07:00`
- Branch: `phuong-backend`

## Trạng thái tổng quan

- Current focus: Chuẩn bị khởi động `F-007: Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job (Epic A3/A5)`
- Feature vừa hoàn thành: `F-006: Khởi tạo Upload Video & Cấp S3 Presigned PUT URL (Epic A3)` (`specs/006-video-upload-init/spec.md`)
- Phase: `Feature Completed & Clean Handoff`
- Status: `completed` (Đã được người dùng chính thức phê duyệt nghiệm thu)

## Kết quả đã được kiểm chứng

- **Mã nguồn tính năng F-006 hoàn thành 100% (35/35 tasks)**:
  - Mã lỗi chuẩn: `ErrorCode.UPLOAD_LIMIT_EXCEEDED` (HTTP 413 khi sizeBytes > 200MB) và `ErrorCode.UNSUPPORTED_MEDIA_TYPE` (HTTP 422 khi sai contentType hoặc đuôi tệp tin).
  - Tiện ích thẩm định & quy ước: `VideoValidationHelper` trích xuất đuôi tệp tin sau dấu chấm cuối cùng chuẩn hóa chữ thường lowercase, sinh định danh CSPRNG `videoId` 16 ký tự (`vid_` + 12 hex ngẫu nhiên), kiểm tra MIME type (`mp4`, `mov`, `mkv`, `webm`) và dung lượng tối đa 200MB.
  - Tầng dữ liệu DynamoDB: `VideoItem` bean trên bảng `tvc-{env}-videos` (PK `videoId`, GSI `project-index` PK `projectId`, SK `createdAt`, `status = UPLOADING`, `attemptCount = 0`, `inputKey`).
  - DTOs & Mappers: `InitVideoRequest`, `PresignedUploadInfo`, `VideoResponse`, `InitVideoResponse` (Java record bất biến) và MapStruct `VideoMapper`.
  - Tầng Repository: `VideoRepository` sử dụng `DynamoDbEnhancedClient` (`save`, `findByVideoId`, `findByProjectId`).
  - Tầng Service & Controller: `VideoService` và `VideoController` xử lý `POST /api/projects/{projectId}/videos`, kiểm tra quyền sở hữu Project qua JWT hoặc `X-API-Key`, chặn 403 gọi chéo tenant, chặn 404 Project không tồn tại hoặc đã `DELETED`, ký S3 Presigned PUT URL 15 phút với ràng buộc `Content-Type` và `Content-Length`.
- **Real Verification & Zero Regression (109/109 automated tests PASS 100%)**:
  - `VideoValidationHelperTest`: 5 tests PASS.
  - `VideoServiceTest`: 8 tests PASS.
  - `VideoControllerTest`: 6 tests PASS.
  - `VideoUploadInitIT`: 1 IT suite PASS (LocalStack Testcontainers S3 + DynamoDB: kiểm thử toàn bộ vòng đời khởi tạo video, cấp Presigned PUT URL, dùng HttpClient thực hiện PUT byte thật lên LocalStack S3 thành công HTTP 200 OK, xác minh S3 `HeadObject` đúng dung lượng và content-type, kiểm tra bản ghi DynamoDB đúng `UPLOADING`).
  - Zero Regression: 100 Surefire unit/slice tests + 9 Failsafe IT suites = 109 tests đạt PASS 100% không một lỗi nào.
- **Postman Collection E2E (16/16 requests PASS 100%)**:
  - `backend/postman/F-006-video-upload-init.postman_collection.json` với 16 kịch bản kiểm thử toàn diện từ setup động (User A, User B, Project A, Project B, API Key A, API Key B) đến kiểm tra PUT trực tiếp lên S3, phân quyền đa người thuê và upload limits. Đã chạy thực tế bằng Newman đạt 16/16 requests PASS, 31/31 assertions PASS.
- **Convergence & Nghiệm thu**:
  - `/speckit-converge` đạt `converged` (0 gaps, 0 findings, 100% artifacts và codebase đồng bộ tuyệt đối).
  - Đã được người dùng chính thức review và phê chuẩn nghiệm thu hoàn thành (`completed`) vào lúc `2026-11-01T00:48:18+07:00`.
- **Hồ sơ Feature Evidence**:
  - `quality/evidence/F-006-video-upload-init.md` ghi nhận đầy đủ bằng chứng kiểm thử thực tế và ánh xạ 14 Acceptance Criteria.

## Việc còn lại

1. Khởi động Feature tiếp theo trong backlog: **`F-007: Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job (Epic A3/A5)`**.

## Blocker / Quyết định cần con người

- Không có blocker. F-006 đã hoàn thành xuất sắc, đóng lại và bàn giao sạch sẽ (Clean Handoff).

## Verification gần nhất

| Lệnh | Kết quả | Thời điểm | Evidence |
|---|---|---|---|
| `./scripts/verify.sh` (`./mvnw -B clean verify`) | `PASS` (109 tests run: 100 unit/slice + 9 IT, 0 failures) | `2026-11-01T00:24:57+07:00` | Zero Regression test suite pass toàn diện 100% |
| `npx newman run backend/postman/F-006-video-upload-init.postman_collection.json` | `PASS` (16 requests, 31 assertions, 0 failures) | `2026-11-01T00:22:44+07:00` | Newman CLI E2E collection pass 100% |
| `./mvnw -B test -Dtest="VideoUploadInitIT"` | `PASS` (1 IT suite, 0 failures) | `2026-11-01T00:08:51+07:00` | Real Verification LocalStack S3 + DynamoDB |
| `./mvnw -B test` | `PASS` (100 unit/slice tests, 0 failures) | `2026-11-01T00:10:03+07:00` | All unit & slice tests pass |
| `./scripts/check-setup.sh` | `PASS` (10 required files OK) | `2026-11-01T00:24:48+07:00` | Harness structure check pass |

## Hành động tiếp theo

- Thực hiện `/speckit-specify F-007` để khởi tạo đặc tả cho feature xác nhận upload và điều phối SQS job.
