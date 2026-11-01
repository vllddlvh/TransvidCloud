# Implementation Plan: Xác thực người dùng SaaS — Register, Login & JWT Security

**Branch**: `003-saas-auth-jwt` | **Date**: 2026-10-31 | **Spec**: [specs/003-saas-auth-jwt/spec.md](spec.md)

**Input**: Feature specification từ `/specs/003-saas-auth-jwt/spec.md`

## Summary

Triển khai tính năng xác thực người dùng SaaS (Epic A2) cho TransvidCloud Backend, bao gồm:
1. Đăng ký tài khoản người dùng (`POST /api/auth/register`) với cơ chế đảm bảo tính duy nhất của email bằng giao dịch nguyên tử DynamoDB `TransactWriteItems` (Item tài khoản + Item khóa `EMAIL#<email>`).
2. Mã hóa mật khẩu an toàn bằng thuật toán `BCrypt` kết hợp validation dữ liệu nghiêm ngặt.
3. Đăng nhập tài khoản (`POST /api/auth/login`) tra cứu qua GSI `email-index`, xác minh mật khẩu và phát hành Access Token định dạng Bearer JWT (HMAC-SHA256) qua `JwtTokenService` sử dụng thư viện Nimbus JOSE JWT có sẵn.
4. Tích hợp bộ giải mã JWT trong Spring Security (`NimbusJwtDecoder`) để bảo vệ các endpoint tài nguyên nghiệp vụ kế tiếp.

## Technical Context

**Language/Version**: Java 21 LTS  
**Primary Dependencies**: Spring Boot 3.4.5, Spring Security 6, Spring Cloud AWS 3.3.0, AWS SDK for Java v2 (`DynamoDbEnhancedClient`), Nimbus JOSE JWT, MapStruct, Lombok  
**Storage**: Amazon DynamoDB (`tvc-{env}-users` với GSI `email-index`, LocalStack 3.0 / Testcontainers)  
**Testing**: JUnit 5, Mockito, Spring WebMvcTest (`@WebMvcTest`), Testcontainers LocalStack 3.0 (`*IT.java`)  
**Target Platform**: Linux Server / Docker Container  
**Project Type**: Web REST API Service (Spring Boot)  
**Performance Goals**: Thời gian phản hồi đăng ký < 2 giây (bao gồm BCrypt hashing 10 rounds và DynamoDB transaction); thời gian phản hồi đăng nhập < 1 giây  
**Constraints**: 
* Hoàn toàn phi trạng thái (Stateless Bearer JWT, thời hạn mặc định 24h).
* CSDL duy nhất là DynamoDB (tuân thủ ADR 0001, tuyệt đối không dùng JPA/SQL).
* Không bao giờ ghi log hoặc lưu trữ mật khẩu dạng rõ, raw JWT secret hoặc dữ liệu nhạy cảm.
**Scale/Scope**: 2 REST endpoints công khai (`/api/auth/register`, `/api/auth/login`), 1 package tính năng `auth/` độc lập, bảo đảm zero-regression cho toàn bộ test suite hiện có.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc / Gate | Tiêu chuẩn đánh giá | Kết quả | Ghi chú bằng chứng |
|---|---|---|---|
| **I. Code Quality & Maintainability** | Code rõ ràng, YAGNI, KISS, DTO bất biến (`record`), tách biệt domain qua MapStruct | **PASS** | Phân tầng rõ rệt: `Controller -> Service -> Repository -> DynamoDbEnhancedClient`, DTO dùng Java record |
| **II. Architecture & Modularity** | Tôn trọng ranh giới module, không tạo phụ thuộc vòng, độc lập logic Auth | **PASS** | Gói gọn trong package `com.transvidcloud.backend.auth`, tái sử dụng `common` |
| **III. Security & Privacy** | Mật khẩu băm BCrypt, JWT HMAC-SHA256 >= 32 bytes, không log credentials | **PASS** | BCryptPasswordEncoder, `TvcProperties.SecurityProperties.JwtProperties`, không lộ stack trace |
| **IV. Correctness & Data Integrity** | Bảo đảm email duy nhất, tính nguyên tử với `TransactWriteItems`, chuẩn hóa email | **PASS** | Tuân thủ tuyệt đối quy định trong `contracts/dynamodb-tables.md` với Item khóa `EMAIL#` |
| **V. Testing & Verification** | Real Verification: có Unit Test + Integration Test Testcontainers LocalStack | **PASS** | Có `AuthControllerTest`, `AuthServiceTest`, `JwtTokenServiceTest` và `UserAuthIT` |
| **VI. Error Handling & Observability** | Chuẩn hóa `ErrorResponse`, không nuốt lỗi, không lộ stack trace | **PASS** | Tích hợp qua `GlobalExceptionHandler`, enum `ErrorCode` mở rộng `EMAIL_ALREADY_EXISTS` |
| **VII. Single Source of Truth** | Khớp hoàn toàn contract `contracts/openapi.yaml` và `contracts/dynamodb-tables.md` | **PASS** | Trích xuất contract cục bộ tại `contracts/auth-api.yaml`, schema khớp 100% |
| **VIII. Dependency Discipline** | Không bổ sung thư viện bên ngoài không cần thiết | **PASS** | Tận dụng `nimbus-jose-jwt` và `spring-security` có sẵn trong classpath |
| **IX. Human Oversight** | Mọi quyết định kỹ thuật đều có cơ sở từ contract và tài liệu đã duyệt | **PASS** | Tuân thủ chính sách ngôn ngữ Tiếng Việt cho toàn bộ tài liệu SDD |

## Project Structure

### Documentation (this feature)

```text
specs/003-saas-auth-jwt/
├── spec.md              # Yêu cầu nghiệp vụ & Acceptance Criteria (/speckit-specify)
├── plan.md              # Kế hoạch triển khai & kiến trúc (/speckit-plan)
├── research.md          # Nghiên cứu kỹ thuật: TransactWriteItems, Nimbus JWT, Email Normalization
├── data-model.md        # Mô hình dữ liệu DynamoDB UserItem, EmailLockItem, DTOs & Mappers
├── quickstart.md        # Hướng dẫn xác minh tự động và thủ công bằng cURL
├── contracts/
│   └── auth-api.yaml    # Contract OpenAPI trích xuất cho các endpoint Auth
└── checklists/
    └── requirements.md  # Checklist chất lượng đặc tả
```

### Source Code (repository root)

```text
backend/src/main/java/com/transvidcloud/backend/
├── TransvidCloudBackendApplication.java
├── auth/
│   ├── controller/
│   │   └── AuthController.java            # REST Controller: /api/auth/register, /api/auth/login
│   ├── dto/
│   │   ├── RegisterRequest.java           # Request DTO đăng ký (record)
│   │   ├── LoginRequest.java              # Request DTO đăng nhập (record)
│   │   ├── UserSummary.java               # Profile tóm tắt của user (record)
│   │   └── AuthResponse.java              # Response DTO trả về token và user (record)
│   ├── entity/
│   │   └── UserItem.java                  # DynamoDB Enhanced Client Entity (@DynamoDbBean)
│   ├── mapper/
│   │   └── UserMapper.java                # MapStruct interface: UserItem <-> UserSummary
│   ├── repository/
│   │   └── UserRepository.java            # Tương tác DynamoDB: TransactWriteItems, Query GSI email-index
│   └── service/
│       ├── AuthService.java               # Nghiên vụ đăng ký, kiểm tra email, băm mật khẩu, đăng nhập
│       └── JwtTokenService.java           # Phát hành JWT Access Token HMAC-SHA256 qua Nimbus
└── common/
    ├── config/                            # TvcProperties, AwsConfig, SecurityConfig, CorsConfig
    ├── dto/                               # ErrorResponse
    └── exception/                         # AppException, ErrorCode, GlobalExceptionHandler

backend/src/test/java/com/transvidcloud/backend/
├── auth/
│   ├── controller/
│   │   └── AuthControllerTest.java        # WebMvcTest kiểm tra HTTP status, validation, response schema
│   ├── service/
│   │   ├── AuthServiceTest.java           # Unit test logic nghiệp vụ với Mockito
│   │   └── JwtTokenServiceTest.java       # Unit test phát hành và xác thực token JWT
│   └── repository/
│       └── UserAuthIT.java                # Integration Test DynamoDB TransactWriteItems với Testcontainers LocalStack
└── ...
```

**Structure Decision**: Tổ chức mã nguồn theo package theo tính năng (`com.transvidcloud.backend.auth`) nhằm đảm bảo tính đóng gói cao, tách biệt rõ ràng với package `common` và sẵn sàng mở rộng các tính năng tiếp theo (`project`, `video`, `apikey`).

## Complexity Tracking

> Không có vi phạm Constitution nào cần biện minh. Thiết kế tuân thủ hoàn toàn các nguyên tắc cốt lõi, không over-engineering và không bổ sung dependency mới.
