# Feature Verification Evidence — F-006

- Feature: `F-006 — Khởi tạo Upload Video & Cấp S3 Presigned PUT URL (Epic A3)`
- Spec: [`specs/006-video-upload-init/spec.md`](../../specs/006-video-upload-init/spec.md)
- Plan: [`specs/006-video-upload-init/plan.md`](../../specs/006-video-upload-init/plan.md)
- Tasks: [`specs/006-video-upload-init/tasks.md`](../../specs/006-video-upload-init/tasks.md)
- Branch: `phuong-backend`
- Date: `2026-11-01T00:10:00+07:00`

## Acceptance criteria mapping

| Criterion ID | Mô tả tóm tắt | Evidence / test | Result | Reference |
|---|---|---|---|---|
| `US1-AC1` | Tạo video hợp lệ qua JWT trả về 201 Created, `status = UPLOADING`, `attemptCount = 0`, cấp S3 Presigned URL 15 phút kèm `Content-Type` & `Content-Length` | `VideoServiceTest.testInitVideoUpload_Success_JwtUser`, `VideoControllerTest.testInitVideoUpload_Success_Returns201`, `VideoUploadInitIT` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java`, `VideoUploadInitIT.java` |
| `US1-AC2` | Presigned URL trỏ đúng bucket `tvc-{env}-input` với cấu trúc `{projectId}/{videoId}/source.{ext}` và chữ ký SigV4 hợp lệ | `VideoUploadInitIT.testVideoUploadInit_FullLifecycle_RealVerification` | `PASS` | `VideoUploadInitIT.java` |
| `US1-AC3` | Trường bắt buộc bị thiếu (title rỗng, sizeBytes <= 0) trả về 400 Bad Request (`VALIDATION_ERROR`) | `VideoControllerTest.testInitVideoUpload_ValidationError_Returns400` | `PASS` | `VideoControllerTest.java` |
| `US2-AC1` | Gửi request kèm `X-API-Key` hợp lệ tạo video thành công trả về 201 Created và S3 Presigned URL | `VideoServiceTest.testInitVideoUpload_Success_ApiKey`, `VideoUploadInitIT` | `PASS` | `VideoServiceTest.java`, `VideoUploadInitIT.java` |
| `US2-AC2` | Gửi `X-API-Key` của Project A vào Project B bị chặn 403 Forbidden (`ACCESS_DENIED`) | `VideoServiceTest.testInitVideoUpload_Throws403_WhenApiKeyCrossTenant`, `VideoUploadInitIT` | `PASS` | `VideoServiceTest.java`, `VideoUploadInitIT.java` |
| `US2-AC3` | Gửi `X-API-Key` không tồn tại, sai định dạng hoặc bị thu hồi bị chặn 401 Unauthorized (`UNAUTHENTICATED`) | `VideoUploadInitIT`, `DualSecurityAuthIT` | `PASS` | `VideoUploadInitIT.java`, `DualSecurityAuthIT.java` |
| `US3-AC1` | Dung lượng `sizeBytes > 200MB` (209715201 bytes) bị từ chối với HTTP 413 Payload Too Large (`UPLOAD_LIMIT_EXCEEDED`) | `VideoValidationHelperTest.testValidateSizeBytes`, `VideoServiceTest.testInitVideoUpload_Throws413_WhenSizeExceeds200MB`, `VideoControllerTest.testInitVideoUpload_PayloadTooLarge_Returns413`, `VideoUploadInitIT` | `PASS` | `VideoValidationHelperTest.java`, `VideoServiceTest.java`, `VideoControllerTest.java`, `VideoUploadInitIT.java` |
| `US3-AC2` | Dung lượng `sizeBytes` đúng bằng ngưỡng biên 200MB (209715200 bytes) được chấp nhận thành công | `VideoValidationHelperTest.testValidateSizeBytes` | `PASS` | `VideoValidationHelperTest.java` |
| `US4-AC1` | MIME type `contentType` không thuộc danh mục cho phép bị từ chối HTTP 422 Unprocessable Entity (`UNSUPPORTED_MEDIA_TYPE`) | `VideoServiceTest.testInitVideoUpload_Throws422_WhenContentTypeUnsupported`, `VideoControllerTest.testInitVideoUpload_UnsupportedMediaType_Returns422`, `VideoUploadInitIT` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java`, `VideoUploadInitIT.java` |
| `US4-AC2` | Tệp tin `filename` không có đuôi hoặc đuôi không hợp lệ bị từ chối HTTP 422 Unprocessable Entity (`UNSUPPORTED_MEDIA_TYPE`) | `VideoValidationHelperTest.testIsExtensionAllowed`, `VideoServiceTest.testInitVideoUpload_Throws422_WhenExtensionUnsupported`, `VideoUploadInitIT` | `PASS` | `VideoValidationHelperTest.java`, `VideoServiceTest.java`, `VideoUploadInitIT.java` |
| `US4-AC3` | Đuôi tệp viết hoa (`.MP4`, `.MOV`) hoặc nhiều dấu chấm được trích xuất và chuẩn hóa về chữ thường lowercase (`source.mp4`) | `VideoValidationHelperTest.testExtractExtension`, `VideoServiceTest.testInitVideoUpload_Success_JwtUser`, `VideoUploadInitIT` | `PASS` | `VideoValidationHelperTest.java`, `VideoServiceTest.java`, `VideoUploadInitIT.java` |
| `US5-AC1` | Người dùng web không sở hữu Project bị chặn HTTP 403 Forbidden (`ACCESS_DENIED`) | `VideoServiceTest.testInitVideoUpload_Throws403_WhenJwtUserNotOwner`, `VideoControllerTest.testInitVideoUpload_AccessDenied_Returns403`, `VideoUploadInitIT` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java`, `VideoUploadInitIT.java` |
| `US5-AC2` | Khởi tạo video trên Project không tồn tại hoặc đã bị xóa mềm (`DELETED`) bị từ chối HTTP 404 Not Found (`RESOURCE_NOT_FOUND`) | `VideoServiceTest.testInitVideoUpload_Throws404_WhenProjectNotFoundOrDeleted`, `VideoControllerTest.testInitVideoUpload_ProjectNotFound_Returns404`, `VideoUploadInitIT` | `PASS` | `VideoServiceTest.java`, `VideoControllerTest.java`, `VideoUploadInitIT.java` |
| `US5-AC3` | Yêu cầu không có thông tin xác thực bị từ chối HTTP 401 Unauthorized (`UNAUTHENTICATED`) | `VideoUploadInitIT`, `SecurityAuthFilterTest` | `PASS` | `VideoUploadInitIT.java`, `SecurityAuthFilterTest.java` |

## Commands executed

| Command | Exit code | Result | Test summary | Notes |
|---|---:|---|---|---|
| `./scripts/check-setup.sh` | `0` | `PASS` | 10 required files OK | Kiểm tra cấu trúc Harness |
| `./mvnw -B test -Dtest="VideoValidationHelperTest,VideoServiceTest"` | `0` | `PASS` | 13 runs, 0 failures | Unit test tiện ích thẩm định, đuôi lowercase, ranh giới 200MB, MIME, mock service |
| `./mvnw -B test -Dtest="VideoControllerTest"` | `0` | `PASS` | 6 runs, 0 failures | WebMvc slice test endpoint POST /api/projects/{projectId}/videos |
| `./mvnw -B test -Dtest="VideoUploadInitIT"` | `0` | `PASS` | 1 suite, 0 failures | Real Verification LocalStack Testcontainers S3 + DynamoDB: PUT byte thật lên S3 qua Presigned URL và verify HeadObject |
| `./mvnw -B test` | `0` | `PASS` | 100 runs, 0 failures | Toàn bộ 100 Unit & Slice tests pass 100% |
| `./scripts/verify.sh` (`./mvnw -B clean verify`) | `0` | `PASS` | 109 runs (100 unit/slice + 9 IT), 0 failures | Toàn bộ test suite tích lũy từ F-001 đến F-006 đạt Zero Regression tuyệt đối |
| `npx newman run backend/postman/F-006-video-upload-init.postman_collection.json` | `0` | `PASS` | 16 requests, 31 assertions, 0 failures | Bộ kiểm thử E2E Postman Collection qua Newman pass 100% |

## Verification Layers

- **Static Analysis / Structure**: `scripts/check-setup.sh` (PASS).
- **Unit / Web Slice Tests**: `VideoValidationHelperTest`, `VideoServiceTest`, `VideoControllerTest` (PASS, 19 tests mới cho module `video`).
- **Integration Tests (Real Verification)**: `VideoUploadInitIT` giao tiếp trực tiếp với LocalStack 3.0 S3 + DynamoDB (PASS, kiểm thử trọn vẹn việc cấp Presigned PUT URL, dùng HttpClient thực hiện HTTP PUT gửi byte dữ liệu mẫu thật lên LocalStack S3 qua URL được cấp và xác minh bằng S3 `HeadObject` đúng `Content-Length` và `Content-Type`, đồng thời kiểm tra bản ghi DynamoDB đúng `status = UPLOADING` và `attemptCount = 0`).
- **Zero Regression**: Toàn bộ 89 tests cũ của F-001 -> F-005 cùng 20 tests mới của F-006 (tổng cộng 109 automated tests) đều đạt PASS 100% trong lệnh verify chính thức `./scripts/verify.sh`.
- **Manual Quickstart / Postman**: Bộ sưu tập Postman collection 16 requests hoàn chỉnh tại `backend/postman/F-006-video-upload-init.postman_collection.json`, đã chạy kiểm thử thực tế qua Newman đạt PASS 100% (16/16 requests, 31/31 assertions).

## Review

- Reviewer: User (Phương) & AI Pair Programmer
- Status: `verified` (Sẵn sàng nghiệm thu)
- Findings: Hoàn thành 100% (35/35 tasks). Tuân thủ triệt để Hiến pháp: không trung chuyển luồng byte video qua Backend; S3 Presigned URL ràng buộc SigV4 chặt chẽ (`Content-Type`, `Content-Length`); kiểm soát dung lượng tối đa 200MB (HTTP 413) và MIME type cho phép (HTTP 422); bảo vệ cô lập quyền sở hữu Project (HTTP 403 / 404).

## Remaining risks / gaps

- Không có rủi ro kỹ thuật hay khoảng trống. Toàn bộ 109 automated tests pass 100%.
