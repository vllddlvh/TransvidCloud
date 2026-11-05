# Implementation Plan: F-008 — Truy Vấn Danh Sách (Cursor Pagination), Chi Tiết, Assets & Xóa Video

**Branch**: `008-video-query-lifecycle` | **Date**: 2026-11-01T16:55:00+07:00 | **Spec**: [`specs/008-video-query-lifecycle/spec.md`](spec.md)

**Input**: Feature specification from `specs/008-video-query-lifecycle/spec.md`

---

## Summary

Triển khai trọn vẹn 3 API vòng đời video còn lại theo chuẩn OpenAPI:
1. `GET /api/projects/{projectId}/videos`: Phân trang con trỏ (Cursor Pagination) trên GSI `project-index` bằng DynamoDB `LastEvaluatedKey`, sắp xếp giảm dần theo thời gian tạo mới nhất lên đầu (`ScanIndexForward = false`), hỗ trợ `limit` (1..100) và `cursor` (Base64URL Tamper-proof Cursor), loại bỏ các bản ghi nội bộ `OUTBOX#`.
2. `GET /api/videos/{videoId}`: Tra cứu chi tiết video, trạng thái (`status`), số lần thử (`attemptCount`), mã lỗi (`errorCode`), các mốc thời gian, và tự động ghép tiền tố `TVC_MEDIA_BASE_URL` (mặc định `/media`) vào các đường dẫn `assets` (`masterPlaylistUrl`, `hls360Url`, `hls720Url`, `thumbnailUrl`) khi video ở trạng thái `COMPLETED`.
3. `DELETE /api/videos/{videoId}`: Xóa bản ghi video (Fast Delete), chặn đứng với HTTP 409 Conflict (`INVALID_STATE_TRANSITION` / `RESOURCE_IN_USE`) nếu video đang trong tiến trình xử lý chuyển mã `PROCESSING`.
4. Áp dụng cơ chế bảo mật kép Dual Security (Bearer JWT và `X-API-Key`) trên cả 3 endpoints, bảo đảm cô lập đa người thuê tuyệt đối.

---

## Technical Context

**Language/Version**: Java 21 (LTS), OpenJDK 64-Bit Server VM  
**Primary Dependencies**: Spring Boot 3.4.5, Spring Security 6.4, AWS SDK for Java v2 (DynamoDB Enhanced Client, S3 Client), Jackson Databind, Lombok, MapStruct  
**Storage**: Amazon DynamoDB Single-Table (`tvc-{env}-videos`, GSI `project-index`), Amazon S3 (`tvc-{env}-input`, `tvc-{env}-output`)  
**Testing**: JUnit 5, AssertJ, Mockito, Spring Boot Test (`@WebMvcTest`), Testcontainers LocalStack 3.0 (DynamoDB, S3, SQS), Newman CLI (Postman E2E)  
**Target Platform**: Linux container / AWS ECS Fargate  
**Project Type**: RESTful Web Service API (Backend Microservice)  
**Performance Goals**: Thời gian phản hồi phân trang con trỏ < 100ms; thời gian xóa Fast Delete < 100ms; 0 Table Scan trên DynamoDB  
**Constraints**: Không dùng Offset Pagination; không đệm byte video trong RAM; không xóa video khi đang `PROCESSING`; kiểm tra chéo `projectId` trong Cursor để chống can thiệp đa người thuê  
**Scale/Scope**: Hỗ trợ hàng trăm nghìn video mỗi project; phân trang mượt mà không suy giảm hiệu năng theo độ sâu dữ liệu  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá tuân thủ trong Thiết kế F-008 | Kết quả |
|---|---|:---:|
| **I. Code Quality & Maintainability** | Tách bạch logic Cursor Codec, Video Mapper, Repository Query độc lập, dễ hiểu, tuân thủ DRY, KISS và YAGNI. | **PASS** |
| **II. Architecture & Modularity** | Tôn trọng ranh giới module `video`, tái sử dụng cấu trúc Project/Auth hiện có, không tạo phụ thuộc vòng. | **PASS** |
| **III. Security & Privacy** | Áp dụng Dual Security (JWT + API Key); kiểm tra chéo `projectId` trong cursor để chống tamper giữa các tenant; chặn 401/403. | **PASS** |
| **IV. Correctness & Data Integrity** | Tuân thủ 100% OpenAPI contract tại `contracts/openapi.yaml`; chặn 409 khi video đang `PROCESSING`. | **PASS** |
| **V. Testing & Verification** | Xây dựng đầy đủ Unit test, WebMvc slice test, LocalStack Integration Test (`VideoQueryLifecycleIT.java`) và Postman Newman E2E. | **PASS** |
| **VI. Error Handling & Observability** | Chuẩn hóa schema lỗi (`ErrorResponse`), ném `AppException` với các mã chuẩn `INVALID_CURSOR`, `RESOURCE_NOT_FOUND`, `INVALID_STATE_TRANSITION`. | **PASS** |
| **VII. Documentation & Technical Decisions** | Cập nhật đầy đủ `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, `plan.md` và `feature-evidence.md`. | **PASS** |
| **VIII. Dependency & Engineering Discipline** | Tận dụng thư viện Java 21 và Jackson hiện có; không thêm thư viện ngoài không cần thiết. | **PASS** |
| **IX. Human Oversight & Governance** | Đã làm rõ và thống nhất 2 quyết định kiến trúc quan trọng (Fast Delete và Tamper-proof Cursor) với người dùng qua `/speckit-clarify`. | **PASS** |

---

## Project Structure

### Documentation (this feature)

```text
specs/008-video-query-lifecycle/
├── spec.md              # Đặc tả yêu cầu và các tiêu chí nghiệm thu (US1 -> US4)
├── checklists/
│   └── requirements.md  # Bảng kiểm tra chất lượng đặc tả (PASS 16/16)
├── plan.md              # Kế hoạch kiến trúc và lộ trình thực thi (Tệp này)
├── research.md          # Nghiên cứu kỹ thuật Cursor Pagination, Assets prefix, Fast Delete
├── data-model.md        # Thiết kế DTOs (VideoCursor, CursorCodec, VideoPageResponse, VideoAssets)
├── quickstart.md        # Kịch bản kiểm thử nhanh cURL
└── contracts/
    └── video-query-lifecycle-api.yaml # OpenAPI Contract trích xuất cho F-008
```

### Source Code Touched

```text
backend/src/
├── main/java/com/transvidcloud/backend/
│   ├── common/
│   │   └── exception/ErrorCode.java            # Bổ sung mã lỗi INVALID_CURSOR (400)
│   └── video/
│       ├── controller/VideoController.java     # Thêm GET list, GET detail, DELETE endpoints
│       ├── dto/
│       │   ├── VideoCursor.java                # DTO con trỏ phân trang {projectId, videoId, createdAt}
│       │   ├── CursorCodec.java                # Utility mã hóa / giải mã Base64URL an toàn
│       │   ├── VideoAssets.java                # DTO các link HLS và thumbnail
│       │   ├── VideoPageResponse.java          # DTO phản hồi phân trang {items, nextCursor, hasMore}
│       │   └── VideoResponse.java              # Bổ sung trường assets, startedAt, completedAt
│       ├── mapper/VideoMapper.java             # Logic ghép TVC_MEDIA_BASE_URL vào assets
│       ├── repository/
│       │   ├── VideoRepository.java            # Thêm findVideosByProjectWithPagination, deleteVideoWithOutbox
│       │   └── PagedVideoResult.java           # Container kết quả trang và LastEvaluatedKey
│       └── service/VideoService.java           # Nghiệp vụ listVideos, getVideoDetails, deleteVideo
└── test/java/com/transvidcloud/backend/
    └── video/
        ├── controller/VideoControllerTest.java # WebMvc slice test cho cả 3 endpoints
        ├── dto/CursorCodecTest.java            # Unit test mã hóa, giải mã, chống giả mạo cursor
        ├── service/VideoServiceTest.java       # Unit test nghiệp vụ list, assets mapper, delete guard
        └── repository/VideoQueryLifecycleIT.java # Real Verification LocalStack DynamoDB + S3
```

---

## Execution Plan

### Phase 0: Research (Hoàn thành)
- Nghiên cứu cơ chế Cursor Pagination trên DynamoDB GSI `project-index` bằng `DynamoDbClient` query SDK.
- Nghiên cứu thuật toán mã hóa Base64URL Tamper-proof Cursor.
- Nghiên cứu cơ chế ghép `TVC_MEDIA_BASE_URL` cho assets khi video `COMPLETED`.
- Nghiên cứu cơ chế Fast Delete và Conflict Guard (`PROCESSING` ➔ 409).
- *Output*: [`research.md`](research.md).

### Phase 1: Design Artifacts (Hoàn thành)
- Hoàn thiện mô hình dữ liệu DTO và Repository interfaces trong [`data-model.md`](data-model.md).
- Trích xuất OpenAPI contract trong [`contracts/video-query-lifecycle-api.yaml`](contracts/video-query-lifecycle-api.yaml).
- Soạn thảo kịch bản kiểm thử cURL trong [`quickstart.md`](quickstart.md).

### Phase 2: Implementation & Verification (Tiếp theo - qua `/speckit-tasks`)
- Triển khai `ErrorCode.INVALID_CURSOR` và `CursorCodec`.
- Mở rộng `VideoRepository` với query phân trang và xóa video.
- Cập nhật `VideoMapper` hỗ trợ ghép tiền tố media URL cho `assets`.
- Cập nhật `VideoService` và `VideoController` triển khai 3 endpoints.
- Xây dựng bộ Unit tests (`CursorCodecTest`, `VideoServiceTest`, `VideoControllerTest`).
- Xây dựng bộ Integration tests (`VideoQueryLifecycleIT`) trên Testcontainers LocalStack.
- Xây dựng bộ Postman Collection E2E và chạy với Newman CLI.
- Xác nhận Zero Regression với `./scripts/verify.sh` và lập hồ sơ Feature Evidence.
