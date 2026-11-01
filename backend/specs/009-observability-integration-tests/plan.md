# Implementation Plan: F-009 — Quan Sát Hệ Thống, Bảo Mật Log & Bộ Kiểm Thử Tích Hợp / Contract

**Branch**: `009-observability-integration-tests` | **Date**: 2026-11-01T17:58:00+07:00 | **Spec**: [`specs/009-observability-integration-tests/spec.md`](spec.md)

**Input**: Feature specification from `specs/009-observability-integration-tests/spec.md`

---

## Summary

Triển khai hoàn thiện giải pháp quan sát hệ thống, bảo mật dữ liệu nhật ký và bộ kiểm thử tích hợp vòng đời toàn diện theo chuẩn Epic A5, A8, C5:
1. **Correlation Tracking & MDC Propagation**: Triển khai `CorrelationIdFilter` (`OncePerRequestFilter`) trích xuất hoặc sinh mới UUID cho `correlationId`, gán vào Logging MDC và trả về trong header HTTP `X-Correlation-ID`. Tiện ích `LogContext` làm giàu ngữ cảnh (`userId`, `projectId`, `videoId`, `jobId`) cho các luồng xử lý và background scheduler.
2. **Dual-Format Structured Logging**: Cấu hình `logback-spring.xml` hỗ trợ xuất log linh hoạt qua biến môi trường `LOG_FORMAT`: mặc định text màu có cấu trúc cho `local`/`test` và chuyển thành JSON một dòng (Single-line JSON) cho môi trường container/cloud phục vụ CloudWatch Logs Insights (Epic C5).
3. **Log Sanitization & Masking (Defense-in-Depth)**: Triển khai `MaskingMessageConverter` tự động che giấu các trường nhạy cảm (`password`, `rawApiKey`, `token`, `secret`, `signature`) và loại bỏ query string chữ ký của S3 Presigned URL, kết hợp với việc loại trừ trường nhạy cảm trong `toString()` của các DTOs.
4. **Comprehensive Integration Test Suite (Epic A5/A8)**: Xây dựng 2 test classes chuyên biệt chạy với Testcontainers LocalStack:
   - `ObservabilityLoggingIT`: Kiểm chứng Correlation Filter, MDC propagation, Log Masking và không rò rỉ secret.
   - `VideoFullLifecycleIT`: Kiểm chứng toàn bộ chu trình khép kín: Auth -> Project -> API Key -> Upload Init -> S3 Upload -> Confirm -> SQS Message Schema (`sqs-job-message.schema.json`) -> Worker Update -> Query Detail/List -> Multi-tenant Isolation.

---

## Technical Context

**Language/Version**: Java 21 (LTS), OpenJDK 64-Bit Server VM  
**Primary Dependencies**: Spring Boot 3.4.5, Spring Security 6.4, AWS SDK for Java v2 (DynamoDB Enhanced Client, S3 Client, SQS Async Client), Logback Classic, SLF4J, Jackson Databind, Lombok, MapStruct  
**Storage**: Amazon DynamoDB Single-Table (`tvc-{env}-videos`, `tvc-{env}-users`, `tvc-{env}-projects`, `tvc-{env}-apikeys`), Amazon S3 (`tvc-{env}-input`, `tvc-{env}-output`), Amazon SQS (`tvc-{env}-jobs`)  
**Testing**: JUnit 5, AssertJ, Spring Boot Test (`OutputCaptureExtension`), Testcontainers LocalStack 3.0 (DynamoDB, S3, SQS), TestRestTemplate / WebTestClient  
**Target Platform**: Linux container / AWS ECS Fargate  
**Project Type**: RESTful Web Service API & Integration Test Harness (Backend Microservice)  
**Performance Goals**: Thời gian xử lý của Correlation Filter < 1ms (overhead không đáng kể); Masking Regex tối ưu không gây suy giảm throughput; 100% request có `correlationId`  
**Constraints**: Không dùng thư viện tracing quá nặng (KISS); an toàn với Java 21 Virtual Threads (`MDC.clear()` trong `finally`); tuyệt đối không log chuỗi chữ ký S3 Presigned URL; không làm vỡ các bài test tích lũy hiện có (Zero Regression)  
**Scale/Scope**: Bao phủ 100% các luồng API hiện có và tương lai; kiểm chứng trọn vẹn chu trình tích hợp giữa Backend, AWS LocalStack và Go Worker contract  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá tuân thủ trong Thiết kế F-009 | Kết quả |
|---|---|:---:|
| **I. Code Quality & Maintainability** | Thiết kế đơn giản (KISS/YAGNI), tận dụng SLF4J MDC và Logback Converter có sẵn của Java/Spring; không over-engineering. | **PASS** |
| **II. Architecture & Modularity** | Đặt các tiện ích quan sát tập trung tại `common/logging`, không làm thay đổi ranh giới các domain service `video`, `project`, `auth`. | **PASS** |
| **III. Security & Privacy** | Phòng thủ theo chiều sâu (Defense-in-Depth) chống rò rỉ secret, mật khẩu, raw key và chữ ký Presigned URL; kiểm chứng cô lập đa người thuê trong test tích hợp. | **PASS** |
| **IV. Correctness & Data Integrity** | Thẩm định 100% cấu trúc SQS message khớp với schema hợp đồng `contracts/sqs-job-message.schema.json`; bảo đảm tính toàn vẹn trạng thái video. | **PASS** |
| **V. Testing & Verification** | Xây dựng 2 test class tích hợp thực tế với Testcontainers LocalStack (`ObservabilityLoggingIT`, `VideoFullLifecycleIT`), tuân thủ Real Verification & Zero Regression Policy. | **PASS** |
| **VI. Error Handling & Observability** | Trả `X-Correlation-ID` trong mọi response; log có cấu trúc hỗ trợ CloudWatch Logs Insights; không nuốt lỗi, không rò rỉ stack trace bí mật. | **PASS** |
| **VII. Documentation & Technical Decisions** | Cập nhật đầy đủ `spec.md`, `research.md`, `data-model.md`, `quickstart.md`, `contracts/`, `plan.md` và `feature-evidence.md`. | **PASS** |
| **VIII. Dependency & Engineering Discipline** | Tái sử dụng SLF4J/Logback và Jackson sẵn có trong Spring Boot BOM 3.4.5; không thêm dependency bên ngoài không cần thiết. | **PASS** |
| **IX. Human Oversight & Governance** | Đã làm rõ và thống nhất 3 quyết định kỹ thuật cốt lõi (Định dạng log, Cơ chế masking, Cấu trúc test suite) với người dùng qua `/speckit-clarify`. | **PASS** |

---

## Project Structure

### Documentation (this feature)

```text
specs/009-observability-integration-tests/
├── spec.md              # Đặc tả yêu cầu và các tiêu chí nghiệm thu (US1 -> US3)
├── checklists/
│   └── requirements.md  # Bảng kiểm tra chất lượng đặc tả (PASS 16/16)
├── plan.md              # Kế hoạch kiến trúc và lộ trình thực thi (Tệp này)
├── research.md          # Nghiên cứu Correlation tracking, Dual-format logging, Masking, Test IT
├── data-model.md        # Thiết kế LogContext, CorrelationIdFilter, MaskingModel, SqsContractPayload
├── quickstart.md        # Hướng dẫn xác thực nhanh qua cURL và Maven
└── contracts/
    ├── observability-http-headers.md # Hợp đồng HTTP Headers tương quan
    └── structured-log-format.md      # Đặc tả định dạng JSON Log cho CloudWatch Logs Insights
```

### Source Code Touched

```text
backend/src/
├── main/
│   ├── java/com/transvidcloud/backend/
│   │   ├── common/
│   │   │   ├── config/
│   │   │   │   └── WebFilterConfig.java            # Đăng ký CorrelationIdFilter với thứ tự cao nhất
│   │   │   └── logging/
│   │   │       ├── CorrelationIdFilter.java        # Filter trích xuất/sinh UUID, gán MDC & response header
│   │   │       ├── LogContext.java                 # Tiện ích quản lý MDC (correlationId, userId, projectId, videoId)
│   │   │       └── MaskingMessageConverter.java    # Logback Converter che giấu mật khẩu, key, token, presigned URL
│   │   └── video/
│   │       └── service/
│   │           └── OutboxRelayScheduler.java       # Gán correlationId riêng cho background scheduler
│   └── resources/
│       ├── logback-spring.xml                      # Cấu hình Dual-format Console Pattern & JSON Layout
│       └── application.yml                         # Cấu hình logging format default
└── test/
    └── java/com/transvidcloud/backend/
        ├── common/
        │   └── logging/
        │       └── ObservabilityLoggingIT.java     # Kiểm thử tích hợp trích xuất header, MDC, Log Masking
        └── video/
            └── repository/
                └── VideoFullLifecycleIT.java       # Kiểm thử tích hợp chu trình video khép kín & SQS contract
```

---

## Execution Plan

### Phase 0: Research (Hoàn thành)
- Nghiên cứu cơ chế Correlation ID & Virtual Threads an toàn: `specs/009-observability-integration-tests/research.md`.
- Nghiên cứu giải pháp Log Masking Defense-in-Depth và S3 Presigned URL sanitization.
- Thống nhất kiến trúc bộ test tích hợp LocalStack cho Epic A5/A8.

### Phase 1: Design & Contracts (Hoàn thành)
- Hoàn thành mô hình dữ liệu MDC và filter: `specs/009-observability-integration-tests/data-model.md`.
- Hoàn thành hợp đồng HTTP Header tương quan: `specs/009-observability-integration-tests/contracts/observability-http-headers.md`.
- Hoàn thành đặc tả JSON Log cho CloudWatch: `specs/009-observability-integration-tests/contracts/structured-log-format.md`.
- Hoàn thành hướng dẫn kiểm chứng nhanh: `specs/009-observability-integration-tests/quickstart.md`.

### Phase 2: Tasks Breakdown (Sẵn sàng cho `/speckit-tasks`)
- Phân rã các tasks nhỏ theo thứ tự phụ thuộc nghiêm ngặt:
  - Task Group 1: Hạ tầng Logging, MDC và Correlation Filter (`CorrelationIdFilter`, `LogContext`, `WebFilterConfig`).
  - Task Group 2: Masking Converter và Cấu hình Logback Dual-Format (`MaskingMessageConverter`, `logback-spring.xml`).
  - Task Group 3: Gắn ngữ cảnh nghiệp vụ vào Service / Controller / Scheduler.
  - Task Group 4: Xây dựng bài kiểm thử tích hợp `ObservabilityLoggingIT`.
  - Task Group 5: Xây dựng bài kiểm thử tích hợp vòng đời trọn vẹn `VideoFullLifecycleIT` (SQS Contract, Multi-tenancy).
  - Task Group 6: Chạy kiểm chứng toàn diện Zero Regression (`./scripts/verify.sh`), Feature Evidence và `/speckit-converge`.
