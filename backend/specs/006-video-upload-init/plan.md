# Implementation Plan: Khởi tạo Upload Video & Cấp S3 Presigned PUT URL

**Branch**: `phuong-backend` | **Date**: 2026-10-31 | **Spec**: [specs/006-video-upload-init/spec.md](spec.md)

**Input**: Feature specification từ `/specs/006-video-upload-init/spec.md`

## Summary

Triển khai tính năng Khởi tạo Upload Video & Cấp S3 Presigned PUT URL (Epic A3) cho TransvidCloud Backend, bao gồm:
1. **Tầng lưu trữ DynamoDB (`VideoItem`)**:
   - Ánh xạ bảng `videos` (`tvc-{env}-videos`) sử dụng AWS SDK v2 `DynamoDbEnhancedClient`.
   - Partition Key: `videoId` (String, dạng `vid_` + 12 hex ngẫu nhiên).
   - GSI `project-index`: Partition Key `projectId` (String), Sort Key `createdAt` (String), Projection `ALL`.
   - Thuộc tính khởi tạo: `title`, `sizeBytes`, `contentType`, `status = UPLOADING`, `attemptCount = 0`, `inputKey`, `assets = null`, `errorCode = null`.
2. **Logic Thẩm định & Chuẩn hóa Đường dẫn S3**:
   - Kiểm tra dung lượng: `sizeBytes > 209715200` (200MB) -> Ném `AppException(ErrorCode.UPLOAD_LIMIT_EXCEEDED)` (HTTP 413).
   - Kiểm tra định dạng: MIME type thuộc danh mục cho phép và `filename` có đuôi hợp lệ (`mp4`, `mov`, `mkv`, `webm`) -> nếu sai ném `AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE)` (HTTP 422).
   - Trích xuất phần mở rộng sau dấu chấm cuối cùng, chuẩn hóa về chữ thường (lowercase) để cấu thành `inputKey = {projectId}/{videoId}/source.{ext}` theo `contracts/s3-key-convention.md`.
3. **Ký S3 Presigned PUT URL**:
   - Sử dụng `S3Presigner` ký Presigned PUT URL có thời hạn 15 phút (900 giây) trỏ tới bucket tiếp nhận `tvc-{env}-input`.
   - Ký kèm ràng buộc `Content-Type` và `Content-Length` để S3 từ chối mọi yêu cầu PUT sai kích thước hoặc sai loại tệp.
4. **Phân quyền & Tích hợp Dual Security**:
   - Hỗ trợ cả `BearerAuth` (JWT) và `ApiKeyAuth` (`X-API-Key`).
   - Kiểm tra quyền sở hữu `ownerId` khi dùng JWT, kiểm tra `projectId` hợp lệ khi dùng `X-API-Key`, chặn 403 Forbidden nếu truy cập chéo tenant, chặn 404 Not Found nếu Project không tồn tại hoặc đã `DELETED`.
5. **REST API Controller**:
   - `POST /api/projects/{projectId}/videos`: Khởi tạo video và cấp presigned URL, trả về `ApiResponse<InitVideoResponse>` (HTTP 201 Created).
6. **Chiến lược Kiểm chứng Thực tế (Real Verification)**:
   - Unit Tests: `VideoValidationTest`, `VideoServiceTest`.
   - WebMvc Slice Tests: `VideoControllerTest`.
   - Integration Test (Testcontainers LocalStack S3 + DynamoDB): `VideoUploadInitIT` (gọi API -> lấy Presigned URL -> PUT file mẫu lên S3 -> kiểm tra S3 HeadObject và DynamoDB).
   - E2E Test Suite: Postman Collection độc lập `backend/postman/F-006-video-upload-init.postman_collection.json`.
   - Zero Regression: Lệnh `./scripts/verify.sh` đạt 100% test pass trên toàn bộ test suite tích lũy (89 test cũ + test mới).

---

## Technical Context

**Language/Version**: Java 21 LTS  
**Primary Dependencies**: Spring Boot 3.4.5, Spring Security 6, Spring Cloud AWS 3.3.0, AWS SDK for Java v2 (`DynamoDbEnhancedClient`, `S3Presigner`), MapStruct, Lombok  
**Storage**: Amazon DynamoDB (`tvc-{env}-videos` với GSI `project-index`), Amazon S3 (`tvc-{env}-input`), LocalStack 3.0 / Testcontainers  
**Testing**: JUnit 5, Mockito, Spring WebMvcTest (`@WebMvcTest`), Testcontainers LocalStack 3.0 (`*IT.java`), Postman/Newman E2E  
**Target Platform**: Linux Server / Docker Container  
**Project Type**: Web REST API Service (Spring Boot)  
**Performance Goals**: Thời gian khởi tạo video và ký S3 Presigned URL < 500ms; không chiếm dụng bộ nhớ RAM server cho dữ liệu video; zero-table scan trên DynamoDB  
**Constraints**: 
* Tuyệt đối KHÔNG trung chuyển byte video qua Backend; client tải trực tiếp lên S3 bucket input thông qua S3 Presigned PUT URL.
* CSDL duy nhất là DynamoDB (tuân thủ ADR 0001, cấm JPA/SQL, cấm Scan).
* Tuân thủ triệt để `contracts/upload-limits.md` (giới hạn 200MB, 15 phút presign, 4 định dạng container).
* Ràng buộc hợp đồng API tại `contracts/openapi.yaml`.
**Scale/Scope**: 1 REST endpoint `POST /api/projects/{projectId}/videos`, 1 package tính năng `video/`, 1 integration test suite với Testcontainers S3 + DynamoDB.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc / Gate | Tiêu chuẩn đánh giá | Kết quả | Ghi chú bằng chứng |
|---|---|---|---|
| **I. Code Quality & Maintainability** | Code rõ ràng, YAGNI, KISS, DTO bất biến (`record`), tách biệt domain qua MapStruct | **PASS** | Tách tầng: `VideoController -> VideoService -> VideoRepository / S3Presigner`, DTO dùng Java record |
| **II. Architecture & Modularity** | Tôn trọng ranh giới module, không proxy video qua Backend, tách biệt storage & compute | **PASS** | Backend chỉ điều phối metadata và cấp URL ký sẵn; client tải trực tiếp lên S3 bucket input |
| **III. Security & Privacy** | Presigned URL giới hạn thời gian (15p), ký kèm Content-Length/Type, cô lập đa người thuê | **PASS** | SigV4 ký kèm headers; chặn 403 nếu truy cập chéo project hoặc không phải owner; che giấu inputKey nội bộ |
| **IV. Correctness & Data Integrity** | Thẩm định chặt chẽ giới hạn 200MB (413), định dạng (422), trạng thái ban đầu UPLOADING, attemptCount=0 | **PASS** | Kiểm tra ranh giới dung lượng và MIME type; trạng thái tuân thủ `contracts/video-state-machine.md` |
| **V. Testing & Verification** | Real Verification: Unit Test + WebMvcTest + Integration Test Testcontainers S3/DynamoDB + Postman | **PASS** | Kế hoạch `VideoUploadInitIT` thực hiện PUT byte thật lên S3 qua Presigned URL được cấp |
| **VI. Error Handling & Observability** | Chuẩn hóa `ApiResponse`, mã lỗi `UPLOAD_LIMIT_EXCEEDED`, `UNSUPPORTED_MEDIA_TYPE` | **PASS** | Tái sử dụng `GlobalExceptionHandler` và các mã lỗi sẵn có trong `ErrorCode.java` |
| **VII. Single Source of Truth** | Khớp hoàn toàn contract `contracts/openapi.yaml`, `s3-key-convention.md`, `upload-limits.md` | **PASS** | Trích xuất contract cục bộ tại `contracts/video-upload-init-api.yaml`, schema khớp 100% |
| **VIII. Dependency Discipline** | Không bổ sung dependency bên ngoài không cần thiết | **PASS** | Sử dụng toàn bộ thư viện AWS SDK v2, Spring Boot 3.4.5 sẵn có trong `pom.xml` |
| **IX. Human Oversight** | Mọi quyết định kỹ thuật đã được làm rõ và phê chuẩn qua `/speckit-clarify` | **PASS** | Đã làm rõ cho phép trùng title trong cùng Project và chuẩn hóa lowercase đuôi tệp tin |

---

## Project Structure

### Documentation (this feature)

```text
specs/006-video-upload-init/
├── spec.md              # Yêu cầu nghiệp vụ & Acceptance Criteria (/speckit-specify, /speckit-clarify)
├── plan.md              # Kế hoạch triển khai & kiến trúc kỹ thuật (/speckit-plan)
├── research.md          # Nghiên cứu kỹ thuật: S3Presigner SigV4, upload limits, extension lowercase
├── data-model.md        # Mô hình dữ liệu DynamoDB VideoItem, DTOs, State Machine & Mapping
├── quickstart.md        # Hướng dẫn xác minh tự động và kịch bản cURL thủ công
├── contracts/
│   └── video-upload-init-api.yaml # Contract OpenAPI trích xuất cho endpoint tạo video
└── checklists/
    └── requirements.md  # Checklist chất lượng đặc tả
```

### Source Code (repository layout)

```text
backend/src/main/java/com/transvidcloud/backend/
├── TransvidCloudBackendApplication.java
├── video/
│   ├── controller/
│   │   └── VideoController.java           # REST Controller: POST /api/projects/{projectId}/videos
│   ├── dto/
│   │   ├── InitVideoRequest.java          # Request DTO: title, filename, sizeBytes, contentType (record)
│   │   ├── PresignedUploadInfo.java       # DTO: presignedUrl, method, expiresInSeconds, requiredHeaders (record)
│   │   ├── VideoResponse.java             # DTO: videoId, projectId, status, sizeBytes, assets, etc. (record)
│   │   └── InitVideoResponse.java         # DTO phản hồi: video, upload (record)
│   ├── entity/
│   │   ├── VideoItem.java                 # DynamoDB Enhanced Client Entity (@DynamoDbBean, PK videoId, GSI project-index)
│   │   └── VideoStatus.java               # Enum trạng thái video: UPLOADING, PENDING, PROCESSING, COMPLETED, FAILED_*
│   ├── mapper/
│   │   └── VideoMapper.java               # MapStruct interface: VideoItem <-> VideoResponse
│   ├── repository/
│   │   └── VideoRepository.java           # Tương tác DynamoDB bảng tvc-{env}-videos (save, findByVideoId)
│   └── service/
│       └── VideoService.java              # Nghiệp vụ: validate 200MB/MIME, sinh videoId, ký S3Presigner PUT URL, lưu DynamoDB
├── apikey/                                # Hoàn thành từ Feature F-005 (Dual Security)
├── project/                               # Hoàn thành từ Feature F-004
├── auth/                                  # Hoàn thành từ Feature F-003
└── common/                                # Hoàn thành từ Feature F-001/F-002
```

### Test Suite Structure

```text
backend/src/test/java/com/transvidcloud/backend/
├── video/
│   ├── service/
│   │   └── VideoServiceTest.java          # Unit test Mockito: logic validate 200MB, 422 MIME, quyền sở hữu, tạo video thành công
│   ├── controller/
│   │   └── VideoControllerTest.java       # WebMvc slice test: serialization, Bean Validation, HTTP 201/400/413/422/403
│   └── repository/
│       └── VideoUploadInitIT.java         # Integration test (Testcontainers S3 + DynamoDB): gọi API, PUT file mẫu thật lên S3 Presigned URL, verify S3 + DynamoDB
└── postman/
    └── F-006-video-upload-init.postman_collection.json # Bộ test E2E Postman Collection độc lập
```

---

## Complexity Tracking

*Không có vi phạm Constitution Check. Thiết kế duy trì tính đơn giản (KISS/YAGNI), tái sử dụng hoàn toàn hạ tầng sẵn có và tuân thủ tuyệt đối các chuẩn mực kỹ thuật của dự án.*
