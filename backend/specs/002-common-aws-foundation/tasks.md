# Tasks: Nền tảng Common, AWS Clients, CORS & Xử lý lỗi chuẩn (F-002)

**Input**: Design documents from `specs/002-common-aws-foundation/` (`plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/error-response.contract.md`, `quickstart.md`)  
**Prerequisites**: `plan.md`, `spec.md`, `data-model.md`, `contracts/error-response.contract.md`  
**Branch**: `002-common-aws-foundation`  
**Tests Required**: Cả **Unit Test** và **Integration Test (với Testcontainers LocalStack)** theo yêu cầu của dự án.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Khởi tạo cấu trúc các thư mục gói mã nguồn và cấu hình nạp properties trong backend.

- [X] T001 Khởi tạo cấu trúc các package trong `backend/src/main/java/com/transvidcloud/backend/common/` (`config`, `dto`, `exception`) và `backend/src/test/java/com/transvidcloud/backend/common/` (`config`, `exception`, `aws`) theo implementation plan
- [X] T002 [P] Cập nhật cấu hình nạp properties trong `backend/src/main/java/com/transvidcloud/backend/TransvidCloudBackendApplication.java` với annotation `@EnableConfigurationProperties(TvcProperties.class)`

---

## Phase 2: Foundational (Core Entities & Exceptions)

**Purpose**: Các DTO, Enum mã lỗi và lớp ngoại lệ nền tảng bắt buộc phải hoàn thành trước khi triển khai các User Story.

**⚠️ CRITICAL**: Không thể triển khai các User Story nếu thiếu các thành phần cơ sở này.

- [X] T003 [P] Tạo record DTO bất biến `ErrorResponse` trong `backend/src/main/java/com/transvidcloud/backend/common/dto/ErrorResponse.java` gồm các trường: `code` (String, required), `message` (String, required), `timestamp` (Instant, required), `details` (Map<String, Object>, optional) khớp 100% với `contracts/openapi.yaml`
- [X] T004 [P] Định nghĩa enum `ErrorCode` trong `backend/src/main/java/com/transvidcloud/backend/common/exception/ErrorCode.java` với 11 mã chuẩn: `VALIDATION_ERROR` (400), `MALFORMED_JSON` (400), `TYPE_MISMATCH` (400), `INVALID_PARAMETER` (400), `UNAUTHENTICATED` (401), `UNAUTHORIZED` (403), `NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405), `CONFLICT` (409), `UPLOAD_LIMIT_EXCEEDED` (413), `UNSUPPORTED_MEDIA_TYPE` (422), `INTERNAL_SERVER_ERROR` (500)
- [X] T005 Tạo ngoại lệ nghiệp vụ nền tảng `AppException` trong `backend/src/main/java/com/transvidcloud/backend/common/exception/AppException.java` nhận `ErrorCode`, message tùy chỉnh và map `details`

**Checkpoint**: Nền tảng Core DTO & Exception sẵn sàng — có thể bắt đầu triển khai các User Story.

---

## Phase 3: User Story 1 - Phản hồi lỗi chuẩn hóa & thân thiện cho Client (Priority: P1) 🎯 MVP

**Goal**: Xử lý tập trung mọi ngoại lệ và trả về phản hồi lỗi đúng hợp đồng `contracts/openapi.yaml` (`{ code, message, timestamp, details }`), tuyệt đối ẩn stack trace và thông tin nhạy cảm.

**Independent Test**: Gửi các request cố tình vi phạm validation, sai cú pháp JSON, ném `AppException` và unhandled exception, xác nhận mã HTTP cùng payload phản hồi.

### Tests cho User Story 1 ⚠️
> **LƯU Ý: Viết các ca kiểm thử trước, bảo đảm fail trước khi hoàn thiện implementation.**

- [X] T006 [P] [US1] Viết web-slice unit test `GlobalExceptionHandlerTest` trong `backend/src/test/java/com/transvidcloud/backend/common/exception/GlobalExceptionHandlerTest.java` kiểm tra bắt lỗi 400 (`VALIDATION_ERROR` kèm chi tiết trường), 400 (`MALFORMED_JSON`), 405 (`METHOD_NOT_ALLOWED`), các mã của `AppException` (401, 403, 404, 409, 413, 422), và 500 (`INTERNAL_SERVER_ERROR` không lộ stack trace)

### Implementation cho User Story 1

- [X] T007 [US1] Triển khai `@RestControllerAdvice` trong `backend/src/main/java/com/transvidcloud/backend/common/exception/GlobalExceptionHandler.java` bắt và xử lý `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `HttpRequestMethodNotSupportedException`, `AppException`, và `Exception` chung

**Checkpoint**: User Story 1 hoàn thành độc lập và kiểm chứng thành công cơ chế xử lý lỗi chuẩn.

---

## Phase 4: User Story 2 - Khởi tạo và kết nối hạ tầng dịch vụ AWS SDK v2 (Priority: P1)

**Goal**: Khởi tạo và đăng ký các bean AWS SDK v2 (`DynamoDbEnhancedClient`, `S3Client`, `S3Presigner`, `SqsAsyncClient`) hỗ trợ LocalStack (`endpointOverride`, `forcePathStyle`) và AWS Cloud thật.

**Independent Test**: Chạy `AwsConfigUnitTest` kiểm tra logic builder và `AwsClientsIT` với Testcontainers LocalStack kiểm tra thao tác đọc/ghi thật trên DynamoDB, S3, SQS.

### Tests cho User Story 2 ⚠️

- [X] T008 [P] [US2] Viết unit test `AwsConfigUnitTest` trong `backend/src/test/java/com/transvidcloud/backend/common/aws/AwsConfigUnitTest.java` kiểm tra cấu hình builder khi có và không có endpoint tùy biến
- [X] T009 [P] [US2] Viết integration test `AwsClientsIT` trong `backend/src/test/java/com/transvidcloud/backend/common/aws/AwsClientsIT.java` sử dụng Testcontainers LocalStack (`localstack/localstack:3.0`) kiểm tra: tạo bảng DynamoDB & đọc/ghi item qua `DynamoDbEnhancedClient`; tạo bucket S3 & put/get object qua `S3Client`; ký PUT URL qua `S3Presigner` và upload thử; tạo queue & gửi/nhận message qua `SqsAsyncClient`

### Implementation cho User Story 2

- [X] T010 [US2] Triển khai lớp cấu hình `@Configuration` `AwsConfig` trong `backend/src/main/java/com/transvidcloud/backend/common/config/AwsConfig.java` định nghĩa các bean `DynamoDbClient`, `DynamoDbEnhancedClient`, `S3Client`, `S3Presigner`, và `SqsAsyncClient` tương thích linh hoạt giữa LocalStack và AWS Cloud

**Checkpoint**: User Story 2 hoàn thành độc lập và kiểm chứng thành công kết nối tới hạ tầng AWS SDK v2.

---

## Phase 5: User Story 3 - Truy cập API an toàn xuyên miền từ Web Frontend (Priority: P2)

**Goal**: Cấu hình CORS toàn cục cho phép Frontend gửi request và preflight OPTIONS an toàn trên base path `/api/**`.

**Independent Test**: Gửi request preflight `OPTIONS` và request có header `Origin` hợp lệ/không hợp lệ, kiểm tra các header CORS phản hồi.

### Tests cho User Story 3 ⚠️

- [X] T011 [P] [US3] Viết unit test `CorsConfigTest` trong `backend/src/test/java/com/transvidcloud/backend/common/config/CorsConfigTest.java` kiểm tra preflight `OPTIONS` và các header CORS cho allowed origin và từ chối unauthorized origin

### Implementation cho User Story 3

- [X] T012 [US3] Triển khai bean `CorsConfigurationSource` và `WebMvcConfigurer` trong `backend/src/main/java/com/transvidcloud/backend/common/config/CorsConfig.java` cấu hình `allowedOrigins`, `allowedMethods` (`GET, POST, PATCH, DELETE, OPTIONS`), `allowedHeaders` (`Authorization, Content-Type, X-API-Key, Accept, Origin`), `allowCredentials(true)` và `maxAge(3600)`
- [X] T013 [US3] Cập nhật cấu hình bảo mật trong `backend/src/main/java/com/transvidcloud/backend/common/config/SecurityConfig.java` tích hợp `.cors(cors -> cors.configurationSource(corsConfigurationSource))` vào `SecurityFilterChain`

**Checkpoint**: User Story 3 hoàn thành độc lập và kiểm chứng thành công chính sách CORS.

---

## Phase 6: User Story 4 - Quản lý cấu hình tập trung và thẩm định an toàn khi khởi động (Priority: P2)

**Goal**: Quản lý tập trung các biến cấu hình qua `TvcProperties` với validation tự động khi boot (Fail-fast).

**Independent Test**: Nạp cấu hình hợp lệ và các bộ cấu hình thiếu/sai ràng buộc (secret < 32 bytes, TTL <= 0, max-bytes <= 0) bằng `ApplicationContextRunner`.

### Tests cho User Story 4 ⚠️

- [X] T014 [P] [US4] Viết unit test `TvcPropertiesValidationTest` trong `backend/src/test/java/com/transvidcloud/backend/common/config/TvcPropertiesValidationTest.java` kiểm tra nạp cấu hình hợp lệ và xác nhận ném ngoại lệ khi vi phạm các ràng buộc validation

### Implementation cho User Story 4

- [X] T015 [US4] Triển khai lớp cấu hình `@ConfigurationProperties(prefix = "tvc")` kèm `@Validated` `TvcProperties` trong `backend/src/main/java/com/transvidcloud/backend/common/config/TvcProperties.java` với các POJO con: `AwsProperties` (`s3.inputBucket` `@NotBlank`, `s3.outputBucket` `@NotBlank`, `s3.presignTtlMinutes` `@Min(1)`), `UploadProperties` (`maxBytes` `@Min(1)`, `allowedContentTypes` `@NotEmpty`), `SecurityProperties` (`jwt.secret` `@NotBlank` `@Size(min=32, message="JWT secret must be at least 32 bytes long for HMAC-SHA256")`, `jwt.expirationSeconds` `@Min(60)`, `cors.allowedOrigins` `@NotEmpty`), và `MediaProperties` (`baseUrl` `@NotBlank`)

**Checkpoint**: Toàn bộ 4 User Story đã được triển khai hoàn chỉnh và có bộ kiểm thử độc lập cho từng phần.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Kiểm chứng toàn diện, tài liệu hóa bằng chứng kiểm thử và hoàn thiện feature.

- [X] T016 Chạy kiểm thử toàn bộ các ca Unit Test bằng lệnh `./mvnw -B test` trong `backend/` và xác nhận 100% test pass
- [X] T017 Chạy kiểm thử tích hợp bằng lệnh `./mvnw -B test -Dtest="*IT"` trong `backend/` (sử dụng Testcontainers LocalStack) và xác nhận 100% test pass
- [X] T018 [P] Thực hiện kiểm chứng thực nghiệm đối soát các kịch bản cURL theo hướng dẫn tại `backend/specs/002-common-aws-foundation/quickstart.md`
- [X] T019 Tạo bản ghi bằng chứng kiểm chứng tính năng tại `backend/quality/evidence/F-002-common-aws-foundation.md` ghi nhận kết quả thực thi kiểm thử và exit code theo template
- [X] T020 Cập nhật trạng thái feature `F-002` trong `backend/backlog/features.md` sang `verified` và cập nhật snapshot tiến độ tại `backend/PROGRESS.md`

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Phase 1: Setup
    │
    ▼
Phase 2: Foundational (T003, T004, T005)
    │
    ├──────────────────────────┬──────────────────────────┐
    ▼                          ▼                          ▼
Phase 3: US1 (Error/P1)    Phase 4: US2 (AWS/P1)     Phase 6: US4 (Props/P2)
    │                          │                          │
    └──────────────────────────┴──────────────────────────┘
                               │
                               ▼
                           Phase 5: US3 (CORS/P2) [depends on US4 for allowedOrigins]
                               │
                               ▼
                           Phase 7: Polish & Verification (T016 -> T020)
```

### User Story Dependencies

- **User Story 1 (P1 - Error Handling)**: Phụ thuộc vào Phase 2 (Foundational: `ErrorResponse`, `ErrorCode`, `AppException`). Độc lập với AWS clients.
- **User Story 2 (P1 - AWS Clients)**: Phụ thuộc vào Phase 2 và `TvcProperties` để lấy cấu hình endpoint/bucket/queue.
- **User Story 3 (P2 - CORS)**: Phụ thuộc vào `TvcProperties` (`security.cors.allowedOrigins`) và `SecurityConfig`.
- **User Story 4 (P2 - Configuration)**: Có thể triển khai song song hoặc ngay sau Phase 1 & 2.

### Parallel Opportunities

- **Trong Phase 2**: `T003` (`ErrorResponse`) và `T004` (`ErrorCode`) có thể chạy song song.
- **Trong Phase 3 (US1)**: `T006` (`GlobalExceptionHandlerTest`) được viết trước và có thể chạy độc lập.
- **Trong Phase 4 (US2)**: `T008` (`AwsConfigUnitTest`) và `T009` (`AwsClientsIT`) có thể phát triển song song trước khi hoàn thiện `T010`.
- **Giữa các User Story**: US1 (Xử lý lỗi) và US2 (AWS Clients) hoàn toàn có thể chạy song song bởi hai developer khác nhau.

---

## Parallel Example: User Story 2 (AWS Clients)

```bash
# Phát triển các ca kiểm thử cho User Story 2 song song:
Task: "T008 [P] [US2] Viết unit test AwsConfigUnitTest trong backend/src/test/java/com/transvidcloud/backend/common/aws/AwsConfigUnitTest.java"
Task: "T009 [P] [US2] Viết integration test AwsClientsIT trong backend/src/test/java/com/transvidcloud/backend/common/aws/AwsClientsIT.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 - Error Handling)
1. Hoàn thành **Phase 1: Setup** (`T001`, `T002`).
2. Hoàn thành **Phase 2: Foundational** (`T003`, `T004`, `T005`).
3. Hoàn thành **Phase 3: User Story 1** (`T006`, `T007`).
4. **Kiểm tra độc lập**: Chạy `GlobalExceptionHandlerTest` chứng minh hệ thống đã xử lý lỗi chuẩn theo hợp đồng OpenAPI (MVP).

### Incremental Delivery
1. Tiếp tục với **Phase 4: User Story 2** (`T008`, `T009`, `T010`) -> Kiểm chứng kết nối AWS SDK v2 với Testcontainers LocalStack.
2. Tiếp tục với **Phase 6: User Story 4** (`T014`, `T015`) -> Kiểm chứng thẩm định cấu hình Fail-fast.
3. Tiếp tục với **Phase 5: User Story 3** (`T011`, `T012`, `T013`) -> Kiểm chứng chính sách CORS.
4. Hoàn tất **Phase 7: Polish & Verification** (`T016` -> `T020`) -> Tạo Feature Evidence và bàn giao.
