# Implementation Plan: Nền tảng Common, AWS Clients, CORS & Xử lý lỗi chuẩn (F-002)

**Branch**: `002-common-aws-foundation` | **Date**: 2026-10-31 | **Spec**: [spec.md](file:///home/phuong/workspace/TransvidCloud/backend/specs/002-common-aws-foundation/spec.md)

**Input**: Feature specification from `specs/002-common-aws-foundation/spec.md`

## Summary

Feature F-002 thiết lập toàn bộ nền tảng hạ tầng và kỹ thuật dùng chung cho ứng dụng TransvidCloud Backend trước khi tiến hành triển khai các tính năng nghiệp vụ (Auth, Project, Video). Kế hoạch triển khai bao gồm 5 trụ cột:
1. **Cấu hình tập trung & Thẩm định (`TvcProperties`):** Đóng gói toàn bộ tham số môi trường và cấu hình ứng dụng với cơ chế kiểm tra tính hợp lệ tự động (Fail-fast) bằng Jakarta Bean Validation.
2. **Hạ tầng kết nối AWS SDK v2 (`AwsConfig`):** Khởi tạo và cung cấp các bean dùng chung (`DynamoDbClient`, `DynamoDbEnhancedClient`, `S3Client`, `S3Presigner`, `SqsAsyncClient`) hỗ trợ chuyển đổi linh hoạt giữa môi trường phát triển cục bộ (LocalStack) và đám mây (AWS Cloud).
3. **Chính sách CORS an toàn:** Cấu hình `CorsConfigurationSource` tích hợp vào Spring Security Filter Chain cho phép Web Frontend gửi yêu cầu API và preflight an toàn.
4. **Xử lý lỗi toàn cục chuẩn hóa (`GlobalExceptionHandler`):** Định nghĩa DTO `ErrorResponse`, enum `ErrorCode` và ngoại lệ nghiệp vụ `AppException` đảm bảo 100% phản hồi lỗi (4xx/5xx) khớp với hợp đồng `contracts/openapi.yaml` (SSOT), tuyệt đối không để rò rỉ stack trace.
5. **Chiến lược kiểm thử toàn diện:** Xây dựng đầy đủ cả hai lớp kiểm thử **Unit Test** (kiểm tra nhanh validation, exception handling, CORS) và **Integration Test** (sử dụng Testcontainers LocalStack để kiểm thử tương tác thực tế với DynamoDB, S3, SQS).

---

## Technical Context

- **Language/Version**: Java 21 LTS (Virtual Threads enabled qua `spring.threads.virtual.enabled=true`).
- **Primary Dependencies**:
  - Spring Boot 3.4.5 (Web, Validation, Security, Actuator).
  - Spring Cloud AWS 3.3.0.
  - AWS SDK for Java v2 (DynamoDbEnhancedClient, S3, S3Presigner, SQS).
  - Lombok & MapStruct 1.6.3.
  - Nimbus JOSE + JWT (Spring Security OAuth2 Resource Server).
- **Storage**: Amazon DynamoDB (NoSQL duy nhất theo [ADR-0001](file:///home/phuong/workspace/TransvidCloud/docs/adr/0001-dynamodb-only.md)), Amazon S3 (lưu trữ video input/output).
- **Testing**:
  - JUnit 5 & AssertJ.
  - Spring Boot Test & MockMvc (cho Unit/Web Slice Test).
  - Testcontainers LocalStack (`org.testcontainers:localstack`) & Spring Boot Testcontainers (cho Integration Test).
- **Target Platform**: Linux Container / Docker / AWS ECS/EKS.
- **Project Type**: REST API Backend Service.
- **Performance Goals**: Phản hồi lỗi diễn ra tức thì (< 5ms); khởi tạo singleton AWS clients một lần duy nhất khi ứng dụng boot.
- **Constraints**:
  - Tuân thủ tuyệt đối cấu trúc `ErrorResponse` trong [openapi.yaml](file:///home/phuong/workspace/TransvidCloud/contracts/openapi.yaml) (SSOT).
  - Chỉ sử dụng DynamoDB qua Enhanced Client, không dùng JPA/RDBMS.
  - Không truyền luồng byte video qua Backend.
  - Không để lộ thông tin nhạy cảm hoặc stack trace ra ngoài client.
- **Scale/Scope**: Module `backend` tại `/home/phuong/workspace/TransvidCloud/backend`, làm nền móng cho tất cả các feature `F-003` đến `F-010`.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **Nguyên tắc I — Single Source of Truth (SSOT):** Phản hồi lỗi tuân thủ 100% schema `ErrorResponse` và các mã trạng thái trong `contracts/openapi.yaml`. `ErrorCode` sử dụng chuỗi định danh máy đọc được (`VALIDATION_ERROR`, `UPLOAD_LIMIT_EXCEEDED`, v.v.). -> **PASS**.
- [x] **Nguyên tắc II — Kiểm thử Đầy đủ (Unit + Integration Tests):** Kế hoạch yêu cầu bắt buộc cả hai lớp kiểm thử: Unit Tests (kiểm tra cấu hình, CORS, exception handling) và Integration Tests với Testcontainers LocalStack (kiểm tra DynamoDB, S3, SQS). -> **PASS**.
- [x] **Nguyên tắc III — Ranh giới Rõ ràng & Không phá vỡ Architecture:** Không nhận byte video, chỉ dùng DynamoDB, bảo vệ secrets, tích hợp CORS chuẩn. -> **PASS**.
- [x] **Nguyên tắc IV — Fail-fast & An toàn Dữ liệu:** `TvcProperties` kiểm tra validation khi boot; không bao giờ lộ stack trace trong `GlobalExceptionHandler`. -> **PASS**.

---

## Project Structure

### Documentation (Feature F-002)

```text
specs/002-common-aws-foundation/
├── spec.md              # Đặc tả yêu cầu người dùng và tiêu chuẩn kiểm thử
├── checklists/
│   └── requirements.md  # Thẩm định chất lượng đặc tả (All PASS)
├── plan.md              # Kế hoạch triển khai kỹ thuật (Tài liệu này)
├── research.md          # Kết quả nghiên cứu và quyết định kỹ thuật
├── data-model.md        # Cấu trúc ErrorResponse, ErrorCode, TvcProperties
├── contracts/
│   └── error-response.contract.md # Hợp đồng giao tiếp lỗi REST API
├── quickstart.md        # Hướng dẫn chạy kiểm thử Unit & Integration
└── tasks.md             # Sẽ được sinh bởi lệnh /speckit-tasks
```

### Source Code (Cấu trúc mã nguồn cụ thể trong backend)

```text
backend/src/main/java/com/transvidcloud/backend/
├── TransvidCloudBackendApplication.java
└── common/
    ├── config/
    │   ├── TvcProperties.java          # Cấu hình tập trung @ConfigurationProperties & @Validated
    │   ├── AwsConfig.java              # Khởi tạo beans DynamoDb, S3, S3Presigner, Sqs
    │   ├── CorsConfig.java             # Cấu hình CorsConfigurationSource & WebMvcConfigurer
    │   └── SecurityConfig.java         # Tích hợp cors() vào HttpSecurity
    ├── dto/
    │   └── ErrorResponse.java          # Record { code, message, timestamp, details }
    └── exception/
        ├── ErrorCode.java              # Enum mã lỗi chuẩn hóa và ánh xạ HttpStatus
        ├── AppException.java           # Ngoại lệ nghiệp vụ nền tảng
        └── GlobalExceptionHandler.java # @RestControllerAdvice xử lý mọi ngoại lệ

backend/src/test/java/com/transvidcloud/backend/
├── TransvidCloudBackendApplicationTests.java
└── common/
    ├── config/
    │   ├── TvcPropertiesValidationTest.java # Unit test kiểm tra validation cấu hình
    │   └── CorsConfigTest.java              # Unit test kiểm tra preflight OPTIONS & CORS headers
    ├── exception/
    │   └── GlobalExceptionHandlerTest.java  # WebMvc slice test kiểm tra định dạng lỗi chuẩn
    └── aws/
        ├── AwsConfigUnitTest.java           # Unit test logic khởi tạo builder AWS
        └── AwsClientsIT.java                # Integration test với Testcontainers LocalStack (DynamoDB, S3, SQS)
```

**Structure Decision**: Tổ chức toàn bộ các thành phần dùng chung vào package con `com.transvidcloud.backend.common` theo các ranh giới chức năng rõ rệt (`config`, `dto`, `exception`, `aws`). Không tạo các tầng trừu tượng không cần thiết; giữ mã nguồn trực quan, dễ mở rộng và độc lập với các logic nghiệp vụ sau này.

---

## Complexity Tracking

Không có vi phạm hay quyết định phức tạp hóa nào cần giải trình. Thiết kế bám sát các chuẩn tốt nhất của Spring Boot 3 và AWS SDK for Java v2.
