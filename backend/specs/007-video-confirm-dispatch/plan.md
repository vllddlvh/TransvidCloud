# Implementation Plan: Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job (F-007)

**Branch**: `phuong-backend` | **Date**: 2026-11-01 | **Spec**: [specs/007-video-confirm-dispatch/spec.md](spec.md)

**Input**: Feature specification từ `/specs/007-video-confirm-dispatch/spec.md`

## Summary

Triển khai tính năng **Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job (Epic A3/A5 — F-007)** cho TransvidCloud Backend với mẫu thiết kế **Transactional Outbox Event** và bảo đảm **Idempotency** từ đầu đến cuối:

1. **Thẩm định Đối tượng S3 qua `HeadObject` (`S3Client`)**:
   - Kiểm tra tệp tại bucket `tvc-{env}-input` với khóa `inputKey` của video.
   - Nếu đối tượng chưa tồn tại (`NoSuchKeyException` / 404): ném `AppException(ErrorCode.S3_OBJECT_NOT_FOUND)` (HTTP 422).
   - Nếu dung lượng thực tế `contentLength <= 0`: ném `AppException(ErrorCode.UPLOAD_SIZE_MISMATCH)` (HTTP 422).
   - Nếu `contentLength > 209715200` (200 MB): ném `AppException(ErrorCode.UPLOAD_LIMIT_EXCEEDED)` (HTTP 413).
   - Chấp nhận linh hoạt mọi dung lượng hợp lệ `0 < contentLength <= 200 MB` và đồng bộ lại `sizeBytes` của video theo `contentLength` thực tế trên S3.
2. **Giao dịch Nguyên tử `TransactWriteItems` & Transactional Outbox trên Bảng `tvc-{env}-videos`**:
   - Cập nhật `VideoItem` (`status`: `UPLOADING -> PENDING`, `sizeBytes = contentLength`, `updatedAt = now`) với `ConditionExpression: attribute_exists(videoId) AND #st = :uploading`.
   - Đồng thời ghi bản ghi `OutboxItem` trên cùng bảng `tvc-{env}-videos` (Single-Table design: PK `videoId = "OUTBOX#" + videoId`, GSI `project-index` PK `projectId = "OUTBOX#PENDING"`, SK `createdAt = now`) với `ConditionExpression: attribute_not_exists(videoId)`.
   - Bản ghi `OutboxItem` lưu trữ cố định `jobId` (`job_` + 12 hex CSPRNG) và `requestedAt` trong chuỗi JSON `payload` chuẩn `schemaVersion: 1` (`contracts/sqs-job-message.schema.json`), giúp Worker có đầy đủ thông tin idempotency để chống xử lý trùng.
3. **Cơ chế Phát hành Kép (Immediate Dispatch + `@Scheduled` Outbox Relay)**:
   - **Fast-path**: Ngay sau khi giao dịch `TransactWriteItems` thành công, `OutboxPublisherService` gọi `SqsAsyncClient.sendMessage` đẩy thông điệp vào hàng đợi `tvc-{env}-jobs` và cập nhật `OutboxItem` sang `OUTBOX#PUBLISHED` (`outboxStatus = PUBLISHED`).
   - **Outbox Relay (`OutboxRelayScheduler`)**: Tiến trình nền `@Scheduled` định kỳ truy vấn GSI `project-index` với khóa phân vùng `projectId = "OUTBOX#PENDING"` (sử dụng `Query` FIFO theo `createdAt`, tuyệt đối không dùng `Scan`) để tự động phát bù các sự kiện bị kẹt do lỗi mạng hoặc SQS timeout.
4. **Idempotency & Xử lý Tranh chấp Đồng thời**:
   - Lần xác nhận thành công đầu tiên: phản hồi **HTTP 202 Accepted** kèm `VideoResponse`.
   - Khi gọi lặp cho video đã ở `PENDING`, `PROCESSING` hoặc `COMPLETED` (hoặc khi request đồng thời gặp `TransactionCanceledException`): phản hồi **HTTP 200 OK** kèm `VideoResponse` hiện tại mà không tạo Outbox mới hay gửi trùng message SQS (nếu Outbox còn `PENDING` thì kích hoạt thử gửi bù với cùng `jobId` ban đầu).
   - Khi video ở trạng thái lỗi (`FAILED_*`): từ chối với **HTTP 409 Conflict** (`INVALID_STATE_TRANSITION`).
5. **REST API Controller & Phân quyền Dual Security**:
   - Endpoint `POST /api/videos/{videoId}/confirm` trên `VideoController` hỗ trợ đồng thời Bearer JWT (kiểm tra `ownerId` của Project) và `X-API-Key` (kiểm tra `projectId` của API Key).
6. **Chiến lược Kiểm chứng Thực tế (Real Verification)**:
   - Unit Tests: `VideoValidationHelperTest`, `VideoServiceTest`, `OutboxPublisherServiceTest`.
   - WebMvc Slice Tests: `VideoControllerTest` (kiểm tra HTTP 202, 200, 401, 403, 404, 409, 413, 422).
   - Integration Test (Testcontainers LocalStack S3 + DynamoDB + SQS): `VideoConfirmDispatchIT`.
   - E2E Postman Collection: `backend/postman/F-007-video-confirm-dispatch.postman_collection.json`.

---

## Technical Context

**Language/Version**: Java 21 LTS  
**Primary Dependencies**: Spring Boot 3.4.5, Spring Security 6, Spring Cloud AWS 3.3.0, AWS SDK for Java v2 (`DynamoDbEnhancedClient`, `S3Client`, `SqsAsyncClient`), Jackson `ObjectMapper`, MapStruct, Lombok  
**Storage**: Amazon DynamoDB (`tvc-{env}-videos` với GSI `project-index` cho cả `VideoItem` và `OutboxItem`), Amazon S3 (`tvc-{env}-input`), Amazon SQS (`tvc-{env}-jobs`)  
**Testing**: JUnit 5, Mockito, Spring WebMvcTest (`@WebMvcTest`), Testcontainers LocalStack 3.0 (`*IT.java`), Postman/Newman E2E  
**Target Platform**: Linux Server / Docker Container  
**Project Type**: Web REST API Service (Spring Boot)  
**Performance Goals**: Thời gian phản hồi `POST /api/videos/{videoId}/confirm` < 500ms; phát hành SQS tức thì ~0ms lag ở điều kiện bình thường; truy vấn Outbox Relay bằng GSI `Query` (0 table scan)  
**Constraints**:
- Không sửa đổi `/contracts`, `/infra`, `/local`, `/worker`, `/frontend`.
- Chỉ sử dụng DynamoDB (AWS SDK v2 Enhanced Client), cấm dùng `Scan`.
- Payload SQS khớp tuyệt đối `contracts/sqs-job-message.schema.json` (`schemaVersion: 1`, `additionalProperties: false`).
- Response HTTP khớp `contracts/openapi.yaml` (trả trực tiếp `VideoResponse` cho 202/200 và `ErrorResponse` cho 4xx/5xx).
**Scale/Scope**: 1 REST endpoint `POST /api/videos/{videoId}/confirm`, mở rộng package `video/` với Transactional Outbox & SQS Dispatcher, 1 integration test suite toàn diện với LocalStack S3 + DynamoDB + SQS.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc / Gate | Tiêu chuẩn đánh giá | Kết quả | Ghi chú bằng chứng |
|---|---|---|---|
| **I. Code Quality & Maintainability** | Code rõ ràng, YAGNI, KISS, DTO bất biến (`record`), tách biệt trách nhiệm | **PASS** | Tách biệt rõ `VideoService`, `OutboxPublisherService`, `OutboxRelayScheduler`, `OutboxRepository` |
| **II. Architecture & Modularity** | Tôn trọng ranh giới module, không sửa `/contracts` hay tạo bảng ngoài quy định | **PASS** | Tận dụng Single-Table pattern trên bảng `tvc-{env}-videos` và GSI `project-index` sẵn có |
| **III. Security & Privacy** | Xác thực kép JWT + API Key, cô lập quyền sở hữu Project, không lộ secret trong log | **PASS** | Kiểm tra `validateProjectAccess` cho mọi yêu cầu confirm; chặn 403 khi gọi chéo Project |
| **IV. Correctness & Data Integrity** | Giao dịch nguyên tử `TransactWriteItems`, chống race condition, idempotency 202/200 | **PASS** | Conditional write `#st = :uploading` + `attribute_not_exists(videoId)` trên Outbox; giữ cố định `jobId` |
| **V. Testing & Verification** | Real Verification: Unit Test + WebMvcTest + Integration Test LocalStack S3/DynamoDB/SQS + Newman | **PASS** | Kế hoạch kiểm thử `VideoConfirmDispatchIT` xác minh trực tiếp trên S3, DynamoDB và SQS |
| **VI. Error Handling & Observability** | Chuẩn hóa `ErrorResponse`, mã lỗi `S3_OBJECT_NOT_FOUND`, `UPLOAD_SIZE_MISMATCH`, `INVALID_STATE_TRANSITION` | **PASS** | Bổ sung đầy đủ enum trong `ErrorCode.java` và log có cấu trúc kèm `videoId`, `projectId`, `jobId` |
| **VII. Single Source of Truth** | Khớp 100% `contracts/openapi.yaml`, `contracts/sqs-job-message.schema.json`, `contracts/video-state-machine.md` | **PASS** | Đối chiếu từng trường JSON và HTTP status code với SSOT |
| **VIII. Dependency Discipline** | Không thêm thư viện ngoài không cần thiết | **PASS** | Sử dụng `S3Client`, `SqsAsyncClient`, `DynamoDbEnhancedClient`, `@Scheduled` sẵn có |
| **IX. Human Oversight** | Mọi quyết định đã được làm rõ qua `/speckit-clarify` | **PASS** | Đã chốt Transactional Outbox pattern và kiểm tra dung lượng S3 linh hoạt (`0 < size <= 200MB`) |

---

## Project Structure

### Documentation (this feature)

```text
specs/007-video-confirm-dispatch/
├── spec.md                                      # Yêu cầu nghiệp vụ & Clarifications (/speckit-specify, /speckit-clarify)
├── plan.md                                      # Kế hoạch triển khai & kiến trúc kỹ thuật (/speckit-plan)
├── research.md                                  # Nghiên cứu kỹ thuật: Single-Table Outbox, Idempotency, S3 HeadObject
├── data-model.md                                # Mô hình dữ liệu VideoItem, OutboxItem, SqsJobMessage, Sequence Diagram
├── quickstart.md                                # Hướng dẫn chạy kiểm thử tự động và kịch bản cURL/Newman
├── contracts/
│   └── video-confirm-dispatch-api.yaml          # Hợp đồng OpenAPI & SQS Job Message trích xuất cho F-007
└── checklists/
    └── requirements.md                          # Checklist chất lượng đặc tả (16/16 PASS)
```

### Source Code (repository layout)

```text
backend/src/main/java/com/transvidcloud/backend/
├── TransvidCloudBackendApplication.java         # Bật @EnableScheduling hỗ trợ OutboxRelayScheduler
├── common/
│   ├── config/
│   │   └── TvcProperties.java                   # Bổ sung cấu hình OutboxProperties (pollIntervalMs, batchSize, enabled)
│   └── exception/
│       └── ErrorCode.java                       # Bổ sung INVALID_STATE_TRANSITION (409), S3_OBJECT_NOT_FOUND (422), UPLOAD_SIZE_MISMATCH (422)
└── video/
    ├── controller/
    │   └── VideoController.java                 # Bổ sung POST /videos/{videoId}/confirm (trả 202 Accepted hoặc 200 OK)
    ├── dto/
    │   ├── ConfirmVideoResult.java              # Record nội bộ: VideoResponse video, boolean newlyConfirmed
    │   ├── SqsJobMessage.java                   # Record chuẩn schemaVersion=1: jobId, videoId, projectId, inputKey, requestedAt
    │   ├── InitVideoRequest.java                # (Từ F-006)
    │   ├── InitVideoResponse.java               # (Từ F-006)
    │   ├── PresignedUploadInfo.java             # (Từ F-006)
    │   └── VideoResponse.java                   # (Từ F-006)
    ├── entity/
    │   ├── OutboxItem.java                      # @DynamoDbBean trên bảng videos (PK: OUTBOX#{videoId}, GSI PK: OUTBOX#PENDING | OUTBOX#PUBLISHED)
    │   ├── VideoItem.java                       # (Từ F-006)
    │   └── VideoStatus.java                     # (Từ F-006)
    ├── repository/
    │   ├── OutboxRepository.java                # Quản lý OutboxItem: findByVideoId, findPendingOutboxEvents (GSI Query), markAsPublished, recordFailedAttempt
    │   └── VideoRepository.java                 # Bổ sung confirmVideoWithOutboxInTransaction (TransactWriteItems) & lọc khóa OUTBOX#
    └── service/
        ├── OutboxPublisherService.java          # Gửi SqsJobMessage qua SqsAsyncClient và cập nhật trạng thái OutboxItem
        ├── OutboxRelayScheduler.java            # @Scheduled poller quét các bản ghi OUTBOX#PENDING để phát bù bền vững
        ├── VideoValidationHelper.java           # Bổ sung generateJobId() ("job_" + 12 hex CSPRNG)
        └── VideoService.java                    # Bổ sung confirmVideoUpload(videoId, authentication)
```

### Test Suite Structure

```text
backend/src/test/java/com/transvidcloud/backend/
├── video/
│   ├── service/
│   │   ├── VideoValidationHelperTest.java       # Kiểm thử generateJobId() và các hàm tiện ích
│   │   ├── OutboxPublisherServiceTest.java      # Unit test Mockito cho OutboxPublisherService & OutboxRelayScheduler
│   │   └── VideoServiceTest.java                # Unit test Mockito cho confirmVideoUpload (202, 200 idempotent, 403, 404, 409, 413, 422, race condition)
│   ├── controller/
│   │   └── VideoControllerTest.java             # WebMvc slice test cho POST /videos/{videoId}/confirm
│   └── repository/
│       └── VideoConfirmDispatchIT.java          # Integration test (Testcontainers LocalStack S3 + DynamoDB + SQS)
└── postman/
    └── F-007-video-confirm-dispatch.postman_collection.json # Bộ kiểm thử E2E Newman toàn diện
```

---

## Complexity Tracking

*Không có vi phạm Constitution Check. Mẫu thiết kế Single-Table Transactional Outbox giải quyết triệt để bài toán đảm bảo phát hành thông điệp bền vững (At-least-once delivery) và Idempotency mà không cần thêm cơ sở dữ liệu hay bảng mới.*
