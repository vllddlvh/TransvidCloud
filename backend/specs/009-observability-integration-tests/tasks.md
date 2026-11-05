# Tasks: F-009 — Quan Sát Hệ Thống, Bảo Mật Log & Bộ Kiểm Thử Tích Hợp / Contract

**Input**: Design documents from `specs/009-observability-integration-tests/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)  
**Prerequisites**: `spec.md`, `plan.md`, `data-model.md`, `contracts/` đã được phê chuẩn  
**Organization**: Tasks được phân chia theo từng User Story độc lập, đảm bảo khả năng kiểm thử riêng biệt và chuyển giao gia tăng (Incremental Delivery).

---

## Format: `[ID] [P?] [Story] Description with file path`

- **[P]**: Có thể thực hiện song song (khác tệp tin, không phụ thuộc vào task chưa hoàn thành)
- **[Story]**: Đánh dấu User Story mà task phục vụ (`[US1]`, `[US2]`, `[US3]`)
- Luôn chỉ định đường dẫn tệp tin chính xác trong mô tả công việc

---

## Phase 1: Setup (Cấu hình Logging & Khởi tạo)

**Mục tiêu**: Chuẩn bị cấu hình tham số hệ thống phục vụ chuyển đổi định dạng log linh hoạt qua biến môi trường.

- [x] T001 [P] Bổ sung cấu hình biến môi trường `LOG_FORMAT: ${LOG_FORMAT:text}` vào `backend/src/main/resources/application.yml`
- [x] T002 [P] Khởi tạo cấu hình Spring Bean đăng ký Filter `WebFilterConfig` trong `backend/src/main/java/com/transvidcloud/backend/common/config/WebFilterConfig.java`

---

## Phase 2: Foundational (Hạ tầng Ngữ cảnh MDC & Bộ Lọc Correlation ID)

**Mục tiêu**: Xây dựng tiện ích ngữ cảnh `LogContext` và bộ lọc `CorrelationIdFilter` dùng chung cho toàn bộ ứng dụng.

**⚠️ CRITICAL**: Phải hoàn thành Phase 2 trước khi bắt đầu triển khai các User Story.

- [x] T003 [P] Xây dựng lớp tiện ích `LogContext` quản lý các khóa MDC (`correlationId`, `userId`, `projectId`, `videoId`, `jobId`) và các hàm helper (`setCorrelationId`, `setProjectId`, `setVideoId`, `setUserId`, `clear`) trong `backend/src/main/java/com/transvidcloud/backend/common/logging/LogContext.java`
- [x] T004 Xây dựng bộ lọc `CorrelationIdFilter` (`OncePerRequestFilter`) trích xuất header `X-Correlation-ID` / `X-Request-ID` (giới hạn $\le 128$ ký tự an toàn) hoặc tự động sinh mới UUID, gán vào MDC, thiết lập header phản hồi `X-Correlation-ID` và luôn gọi `LogContext.clear()` trong khối `finally` trong `backend/src/main/java/com/transvidcloud/backend/common/logging/CorrelationIdFilter.java`
- [x] T005 [P] Đăng ký `CorrelationIdFilter` vào `WebFilterConfig.java` với độ ưu tiên cao nhất (`Ordered.HIGHEST_PRECEDENCE`) qua `FilterRegistrationBean` trong `backend/src/main/java/com/transvidcloud/backend/common/config/WebFilterConfig.java`
- [x] T006 [P] Viết Unit test cho `CorrelationIdFilterTest` kiểm tra trích xuất header tùy biến, tự động sinh UUID khi thiếu header, làm sạch header chứa ký tự độc hại, và dọn dẹp MDC sau khi request kết thúc trong `backend/src/test/java/com/transvidcloud/backend/common/logging/CorrelationIdFilterTest.java`

**Checkpoint**: Nền tảng `LogContext` và `CorrelationIdFilter` đã sẵn sàng và được kiểm thử độc lập.

---

## Phase 3: User Story 1 - Quan sát Hệ thống qua Structured Logging có Ngữ Cảnh và Truy Vết (Priority: P1) 🎯 MVP

**Mục tiêu**: Cung cấp khả năng truy vết tương quan xuyên suốt cho mọi HTTP request và background task bằng `correlationId`, đồng thời làm giàu ngữ cảnh `userId`, `projectId`, `videoId` vào MDC.

**Independent Test**: Gửi request bất kỳ tới Backend, xác nhận response trả về header `X-Correlation-ID`, log console hiển thị `[corr=<uuid> ...]`, và background scheduler `OutboxRelayScheduler` có log mang `sched-outbox-...`.

- [x] T007 [P] [US1] Bổ sung làm giàu ngữ cảnh `LogContext.setUserId(...)`, `setProjectId(...)`, `setVideoId(...)` trong `VideoService.java` khi thực hiện khởi tạo video, confirm video, tra cứu chi tiết và xóa video trong `backend/src/main/java/com/transvidcloud/backend/video/service/VideoService.java`
- [x] T008 [P] [US1] Bổ sung gán ngữ cảnh `LogContext.setCorrelationId("sched-outbox-" + UUID.randomUUID())` trong `OutboxRelayScheduler.java` trước mỗi chu kỳ quét relay và giải phóng trong `finally` trong `backend/src/main/java/com/transvidcloud/backend/video/service/OutboxRelayScheduler.java`
- [x] T009 [P] [US1] Bổ sung làm giàu ngữ cảnh `LogContext.setProjectId(...)` trong `ProjectService.java` và `ApiKeyService.java` khi thực hiện các thao tác quản lý dự án và API key trong `backend/src/main/java/com/transvidcloud/backend/project/service/ProjectService.java` và `backend/src/main/java/com/transvidcloud/backend/apikey/service/ApiKeyService.java`
- [x] T010 [P] [US1] Viết Unit test trong `backend/src/test/java/com/transvidcloud/backend/video/service/OutboxRelaySchedulerTest.java` kiểm tra `OutboxRelayScheduler` gán và giải phóng `correlationId` đúng chuẩn
- [x] T011 [US1] Cập nhật `GlobalExceptionHandler.java` đảm bảo khi ném `AppException` hoặc exception bất ngờ, `correlationId` trong MDC vẫn được giữ nguyên và log lỗi kèm đầy đủ ngữ cảnh trong `backend/src/main/java/com/transvidcloud/backend/common/exception/GlobalExceptionHandler.java`

**Checkpoint**: User Story 1 hoàn thành — MVP cho phép truy vết và làm giàu ngữ cảnh nhật ký đã hoạt động độc lập.

---

## Phase 4: User Story 2 - Bảo Mật Log, Lọc Bỏ & Masking Dữ Liệu Nhạy Cảm (Priority: P2)

**Mục tiêu**: Che chắn và loại bỏ hoàn toàn các thông tin nhạy cảm (mật khẩu, raw API key, token JWT, AWS credentials, S3 presigned URL signature) khỏi log stream bằng chiến lược Defense-in-Depth.

**Independent Test**: Gọi API đăng ký/đăng nhập, tạo API key, hoặc cấp presigned URL; bắt log stream và kiểm tra 100% không xuất hiện chuỗi mật khẩu bản rõ, không xuất hiện raw key, và URL S3 không chứa `X-Amz-Signature`.

- [x] T012 [P] [US2] Xây dựng Logback Converter `MaskingMessageConverter` (kế thừa `ch.qos.logback.classic.pattern.MessageConverter`) với các regex tối ưu phát hiện và thay thế `password`, `rawApiKey`, `Bearer token`, `aws_secret_access_key`, và query string chữ ký của S3 Presigned URL thành `[PROTECTED]` trong `backend/src/main/java/com/transvidcloud/backend/common/logging/MaskingMessageConverter.java`
- [x] T013 [P] [US2] Rà soát và cập nhật phương thức `toString()` / annotation `@ToString(exclude = ...)` trên các DTO/Entities chứa secret (`RegisterRequest`, `LoginRequest`, `ApiKeyResponse`, `User`, `ApiKey`, `TvcProperties`) để ngăn chặn in bản rõ ra log trong `backend/src/main/java/com/transvidcloud/backend/auth/dto/RegisterRequest.java` và `backend/src/main/java/com/transvidcloud/backend/auth/dto/LoginRequest.java`
- [x] T014 [US2] Tạo cấu hình Logback `backend/src/main/resources/logback-spring.xml` đăng ký `MaskingMessageConverter`, định nghĩa Console Appender với Pattern Layout màu cho `local`/`test` và Single-line JSON Layout cho `docker`/`prod`/`LOG_FORMAT=json` theo đúng hợp đồng trong `backend/specs/009-observability-integration-tests/contracts/structured-log-format.md`
- [x] T015 [P] [US2] Viết Unit test cho `MaskingMessageConverterTest` kiểm tra quét và che giấu đúng các trường hợp mật khẩu JSON, API Key, Bearer token, và S3 Presigned URL signature trong `backend/src/test/java/com/transvidcloud/backend/common/logging/MaskingMessageConverterTest.java`

**Checkpoint**: User Story 2 hoàn thành — cơ chế bảo mật và che chắn nhật ký theo chiều sâu hoạt động hoàn hảo.

---

## Phase 5: User Story 3 - Bộ Kiểm Thử Tích Hợp Toàn Bộ Vòng Đời & Khớp Hợp Đồng (Priority: P3)

**Mục tiêu**: Xây dựng 2 test classes chuyên biệt chạy với Testcontainers LocalStack để kiểm chứng toàn bộ khả năng quan sát và toàn bộ chu trình nghiệp vụ video khép kín đối chiếu với các hợp đồng kiến trúc.

**Independent Test**: Chạy `./mvnw -B test -Dtest="ObservabilityLoggingIT,VideoFullLifecycleIT"` pass 100% với 0 failures, 0 errors.

- [x] T016 [P] [US3] Xây dựng bài kiểm thử tích hợp `ObservabilityLoggingIT` sử dụng `OutputCaptureExtension` kiểm tra trích xuất header `X-Correlation-ID`, làm giàu MDC trong Controller/Service, và xác nhận log stream không chứa password hay S3 signature trong `backend/src/test/java/com/transvidcloud/backend/common/logging/ObservabilityLoggingIT.java`
- [x] T017 [P] [US3] Xây dựng bài kiểm thử tích hợp vòng đời trọn vẹn `VideoFullLifecycleIT` chạy với Testcontainers LocalStack (DynamoDB, S3, SQS): kiểm chứng chuỗi Auth -> Project -> API Key -> Upload Init -> S3 Upload -> Confirm -> Query trong `backend/src/test/java/com/transvidcloud/backend/video/repository/VideoFullLifecycleIT.java`
- [x] T018 [US3] Bổ sung bước kiểm tra thẩm định tính tương thích của message SQS nhận được từ queue `tvc-local-jobs` đối chiếu với hợp đồng `../contracts/sqs-job-message.schema.json` trong `backend/src/test/java/com/transvidcloud/backend/video/repository/VideoFullLifecycleIT.java`
- [x] T019 [US3] Bổ sung bước kiểm tra Multi-tenant Isolation trong `VideoFullLifecycleIT.java`: tạo Tenant B cố tình dùng JWT hoặc API Key của mình để confirm, xem chi tiết hoặc xóa video của Tenant A, khẳng định bị từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`) trong `backend/src/test/java/com/transvidcloud/backend/video/repository/VideoFullLifecycleIT.java`

**Checkpoint**: User Story 3 hoàn thành — toàn bộ chu trình tích hợp và hợp đồng kiến trúc đã được kiểm chứng bằng Real Verification.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Mục tiêu**: Đảm bảo toàn bộ hệ thống đạt chuẩn Zero Regression, đồng bộ tài liệu và lập hồ sơ Feature Evidence.

- [x] T020 [P] Cập nhật tài liệu kỹ thuật trong `backend/docs/testing-strategy.md` và `docs/docs-backend/10-testing-and-quality.md` phản ánh việc hoàn thiện bộ test tích hợp F-009
- [x] T021 Chạy kiểm chứng toàn diện Zero Regression bằng `./scripts/verify.sh` (`./mvnw -B clean verify`), đảm bảo 100% test suite dự án pass
- [x] T022 Lập hồ sơ Feature Verification Evidence tại `backend/quality/evidence/F-009-observability-integration-tests.md` ghi nhận toàn bộ kết quả kiểm thử thực tế
- [x] T023 Chạy `/speckit-converge` để đảm bảo 0 gap giữa spec, plan, tasks và implementation

---

## Dependencies & Execution Order

### Phase Dependencies
- **Setup (Phase 1)**: Không phụ thuộc — có thể bắt đầu ngay.
- **Foundational (Phase 2)**: Phụ thuộc vào Phase 1 — **BẮT BUỘC** hoàn thành trước khi triển khai các User Story.
- **User Stories (Phase 3 -> 5)**: Đều phụ thuộc vào Phase 2.
  - User Story 1 (P1): Triển khai ngay sau Phase 2 để tạo MVP.
  - User Story 2 (P2): Triển khai sau khi `CorrelationIdFilter` và Logback cấu hình xong.
  - User Story 3 (P3): Triển khai sau khi hoàn tất US1 và US2 để kiểm chứng tích hợp toàn diện.
- **Polish (Phase 6)**: Thực hiện sau khi tất cả các User Stories hoàn thành.

### Parallel Opportunities
- **Phase 1**: T001 và T002 có thể làm song song.
- **Phase 2**: T003 và T006 có thể làm song song trước khi hoàn thiện T004/T005.
- **Phase 3**: T007, T008, T009 có thể làm song song trên các service khác nhau.
- **Phase 4**: T012, T013, T015 có thể làm song song.
- **Phase 5**: T016 và T017 có thể viết test class song song.

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Hoàn thành Phase 1: Setup (`T001`, `T002`).
2. Hoàn thành Phase 2: Foundational (`T003` -> `T006`).
3. Hoàn thành Phase 3: User Story 1 (`T007` -> `T011`).
4. **DỪNG LẠI & KIỂM CHỨNG**: Gửi request kiểm tra header `X-Correlation-ID` và MDC log.

### Incremental Delivery
1. Setup + Foundational -> Nền tảng MDC & Filter sẵn sàng.
2. User Story 1 -> MVP: Log có Correlation ID và ngữ cảnh nghiệp vụ.
3. User Story 2 -> Bảo mật: Log tự động che giấu secret và chữ ký S3.
4. User Story 3 -> Kiểm thử: Toàn bộ vòng đời và SQS contract được bảo vệ bởi Testcontainers LocalStack IT.
5. Polish -> Zero Regression, hồ sơ Feature Evidence đầy đủ.
