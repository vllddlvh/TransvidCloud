# Implementation Plan: Quản lý API Key & Xác thực Dual Security JWT + X-API-Key

**Branch**: `phuong-backend` | **Date**: 2026-10-31 | **Spec**: [specs/005-apikey-dual-auth/spec.md](spec.md)

**Input**: Feature specification từ `/specs/005-apikey-dual-auth/spec.md`

## Summary

Triển khai tính năng Quản lý API Key & Xác thực Dual Security JWT + X-API-Key (Epic A2) cho TransvidCloud Backend, bao gồm:
1. **Tầng lưu trữ DynamoDB**:
   - Ánh xạ bảng `apikeys` (`tvc-{env}-apikeys`) sử dụng AWS SDK v2 `DynamoDbEnhancedClient`.
   - Partition Key: `keyHash` (String, SHA-256 hash của chuỗi `rawKey`).
   - GSI `project-index`: Partition Key `projectId` (String), Projection `ALL` — phục vụ liệt kê toàn bộ API Key của dự án theo chuẩn Query, tuyệt đối không dùng DynamoDB Scan.
2. **Bộ sinh khóa & Băm an toàn (ApiKeyGenerator)**:
   - Sử dụng `SecureRandom` (CSPRNG) sinh 16 bytes ngẫu nhiên (32 ký tự hex) gắn tiền tố `tvc_live_` -> Tạo chuỗi `rawKey` chuẩn 41 ký tự (`tvc_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`).
   - Băm mật mã một chiều `SHA-256` trên toàn bộ chuỗi `rawKey` để tạo `keyHash` (64 ký tự hex) lưu vào CSDL.
   - Trích xuất `keyPrefix` hiển thị: `tvc_live_` + 8 ký tự hex đầu + `...` (ví dụ: `tvc_live_7f9a8b2c...`).
3. **REST API Quản lý API Key**:
   - `POST /api/projects/{projectId}/api-keys`: Tạo API Key mới, kiểm tra quyền sở hữu Project của người dùng web, kiểm tra giới hạn tối đa 5 API Key/Project, lưu `keyHash` vào DynamoDB và trả về `rawKey` duy nhất một lần (`HTTP 201 Created`).
   - `GET /api/projects/{projectId}/api-keys`: Liệt kê các API Key của Project thông qua GSI `project-index`, kiểm tra quyền sở hữu, trả về metadata và `keyPrefix`, tuyệt đối không trả lại `rawKey` (`HTTP 200 OK`).
   - `DELETE /api/projects/{projectId}/api-keys/{keyHash}`: Thu hồi (xóa) API Key khỏi bảng DynamoDB, kiểm tra quyền sở hữu và bảo đảm key thuộc đúng Project, làm key mất hiệu lực tức thì (`HTTP 204 No Content`).
4. **Bộ lọc xác thực bảo mật kép (Dual Security Filter)**:
   - Xây dựng `ApiKeyAuthenticationFilter` (kế thừa `OncePerRequestFilter`) tích hợp vào Spring Security `SecurityFilterChain`.
   - Cơ chế thẩm định kép: Ưu tiên `Authorization: Bearer <JWT>` của người dùng SaaS Web; nếu không có Bearer token mới thẩm định qua `X-API-Key`.
   - Khi có `X-API-Key`: Băm SHA-256, tra cứu DynamoDB, thiết lập `ApiKeyAuthenticationToken` (Principal: `ApiKeyPrincipal`, Authority: `ROLE_API_KEY`).
   - Kiểm soát phân quyền & cô lập tài nguyên đa người thuê (Multi-tenant Isolation):
     - Với các endpoint thuộc phạm vi Project (`/api/projects/{projectId}/**`): Đối chiếu `auth.getProjectId().equals(projectId)`. Nếu không khớp, từ chối ngay với HTTP 403 Forbidden (`ACCESS_DENIED`).
     - Với các endpoint chỉ dành cho Web User (như quản lý Project, tạo API Key): Từ chối các request dùng `ROLE_API_KEY` với HTTP 403 Forbidden (`ACCESS_DENIED`).
5. **Chiến lược Kiểm chứng Thực tế (Real Verification)**:
   - Unit Tests: `ApiKeyGeneratorTest`, `ApiKeyServiceTest`.
   - WebMvc Slice Tests: `ApiKeyControllerTest`.
   - Integration Tests (Testcontainers LocalStack 3.0 DynamoDB): `ApiKeyManagementIT`, `DualSecurityAuthIT`.
   - E2E Test Suite: Bộ sưu tập Postman Collection độc lập `backend/postman/F-005-apikey-dual-auth.postman_collection.json`.
   - Zero Regression: Lệnh `./scripts/verify.sh` đạt 100% test pass trên toàn bộ test suite tích lũy.

---

## Technical Context

**Language/Version**: Java 21 LTS  
**Primary Dependencies**: Spring Boot 3.4.5, Spring Security 6, Spring Cloud AWS 3.3.0, AWS SDK for Java v2 (`DynamoDbEnhancedClient`), MapStruct, Lombok  
**Storage**: Amazon DynamoDB (`tvc-{env}-apikeys` với GSI `project-index`, liên kết kiểm tra `tvc-{env}-projects`, LocalStack 3.0 / Testcontainers)  
**Testing**: JUnit 5, Mockito, Spring WebMvcTest (`@WebMvcTest`), Testcontainers LocalStack 3.0 (`*IT.java`), Postman/Newman E2E  
**Target Platform**: Linux Server / Docker Container  
**Project Type**: Web REST API Service (Spring Boot)  
**Performance Goals**: Thời gian sinh và lưu API Key < 500ms; tra cứu xác thực `X-API-Key` theo Partition Key $O(1)$ < 20ms; Zero Table Scans trên DynamoDB  
**Constraints**: 
* Hoàn toàn phi trạng thái (Stateless Dual Auth: Bearer JWT và `X-API-Key`).
* CSDL duy nhất là DynamoDB (tuân thủ ADR 0001, cấm JPA/SQL, cấm Scan).
* Chỉ lưu mã băm SHA-256 của API Key, tuyệt đối không lưu rawKey hoặc ghi rawKey vào logs.
* Giới hạn tối đa 5 API Key đang hoạt động cho mỗi Project.
* Cô lập tài nguyên nghiêm ngặt: `X-API-Key` của Project A không thể truy cập tài nguyên của Project B.
**Scale/Scope**: 3 REST endpoints quản lý API Key, 1 Security Filter xác thực kép, 1 package tính năng `apikey/` độc lập, bảo đảm zero-regression cho toàn bộ test suite.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc / Gate | Tiêu chuẩn đánh giá | Kết quả | Ghi chú bằng chứng |
|---|---|---|---|
| **I. Code Quality & Maintainability** | Code rõ ràng, YAGNI, KISS, DTO bất biến (`record`), tách biệt domain qua MapStruct | **PASS** | Tách tầng chuẩn: `ApiKeyController -> ApiKeyService -> ApiKeyRepository / ApiKeyGenerator`, DTO dùng Java record |
| **II. Architecture & Modularity** | Tôn trọng ranh giới module, không tạo phụ thuộc vòng, độc lập logic API Key | **PASS** | Đóng gói trong package `com.transvidcloud.backend.apikey`, tái sử dụng `project/service/ProjectService` để kiểm tra quyền sở hữu |
| **III. Security & Privacy** | Sinh khóa CSPRNG, chỉ lưu SHA-256 hash, không log secret, phân quyền đa người thuê | **PASS** | `SecureRandom`, `MessageDigest` SHA-256, chỉ trả về `rawKey` 1 lần lúc 201 Created, chặn 403 truy cập chéo project |
| **IV. Correctness & Data Integrity** | Kiểm soát giới hạn 5 keys/project, thu hồi xóa tức thì, đối chiếu phạm vi project | **PASS** | Kiểm tra count qua GSI `project-index` trước khi tạo, xóa vật lý `keyHash` khi revoke, kiểm tra khớp `projectId` |
| **V. Testing & Verification** | Real Verification: Unit Test + WebMvcTest + Integration Test Testcontainers + E2E Postman | **PASS** | Lập kế hoạch `ApiKeyServiceTest`, `ApiKeyControllerTest`, `ApiKeyManagementIT`, `DualSecurityAuthIT` và Postman Collection |
| **VI. Error Handling & Observability** | Chuẩn hóa `ApiResponse`, không nuốt lỗi, mã lỗi thống nhất | **PASS** | Tích hợp `GlobalExceptionHandler`, sử dụng `ErrorCode.INVALID_KEY`, `UNAUTHENTICATED`, `ACCESS_DENIED`, `RESOURCE_NOT_FOUND` |
| **VII. Single Source of Truth** | Khớp hoàn toàn contract `contracts/openapi.yaml` và `contracts/dynamodb-tables.md` | **PASS** | Trích xuất contract cục bộ tại `contracts/api-keys-api.yaml`, schema `apikeys` khớp 100% |
| **VIII. Dependency Discipline** | Không bổ sung thư viện bên ngoài không cần thiết | **PASS** | Tận dụng JDK `java.security`, Spring Security 6 và AWS SDK v2 sẵn có trong `pom.xml` |
| **IX. Human Oversight** | Mọi quyết định kỹ thuật đều có cơ sở từ contract và làm rõ qua `/speckit-clarify` | **PASS** | Quyết định giới hạn 5 keys, ưu tiên Bearer JWT, định dạng prefix đã được người dùng phê duyệt trực tiếp |

---

## Project Structure

### Documentation (this feature)

```text
specs/005-apikey-dual-auth/
├── spec.md              # Yêu cầu nghiệp vụ & Acceptance Criteria (/speckit-specify)
├── plan.md              # Kế hoạch triển khai & kiến trúc kỹ thuật (/speckit-plan)
├── research.md          # Nghiên cứu kỹ thuật: Sinh khóa, SHA-256, Dual Security filter, Real Verification
├── data-model.md        # Mô hình dữ liệu DynamoDB ApiKeyItem, DTOs, Security models & Lifecycle
├── quickstart.md        # Hướng dẫn xác minh tự động và kịch bản cURL thủ công
├── contracts/
│   └── api-keys-api.yaml # Contract OpenAPI trích xuất cho các endpoint API Key
└── checklists/
    └── requirements.md  # Checklist chất lượng đặc tả
```

### Source Code (repository layout)

```text
backend/src/main/java/com/transvidcloud/backend/
├── TransvidCloudBackendApplication.java
├── apikey/
│   ├── controller/
│   │   └── ApiKeyController.java          # REST Controller: POST/GET /api/projects/{projectId}/api-keys, DELETE .../api-keys/{keyHash}
│   ├── dto/
│   │   ├── CreateApiKeyRequest.java       # Request DTO tạo API Key (record)
│   │   ├── ApiKeyCreatedResponse.java     # Response DTO tạo thành công (chứa rawKey duy nhất lần đầu)
│   │   ├── ApiKeyMetadataResponse.java    # Response DTO metadata (chứa keyPrefix, không có rawKey)
│   │   └── ApiKeyListResponse.java        # Response DTO danh sách API Key
│   ├── entity/
│   │   └── ApiKeyItem.java                # DynamoDB Enhanced Client Entity (@DynamoDbBean, PK keyHash, GSI project-index)
│   ├── mapper/
│   │   └── ApiKeyMapper.java              # MapStruct interface: ApiKeyItem <-> ApiKeyMetadataResponse / CreatedResponse
│   ├── repository/
│   │   └── ApiKeyRepository.java          # Tương tác DynamoDB: save, findByKeyHash, queryByProject, countByProject, deleteByKeyHash
│   ├── security/
│   │   ├── ApiKeyAuthenticationFilter.java# Filter xác thực X-API-Key, ưu tiên Bearer JWT, thiết lập SecurityContext
│   │   ├── ApiKeyAuthenticationToken.java # Spring Security Authentication Token cho API Key
│   │   └── ApiKeyPrincipal.java           # Principal đại diện cho ứng dụng ngoài (projectId, name, keyPrefix)
│   ├── service/
│   │   └── ApiKeyService.java             # Nghiệp vụ: sinh key, băm SHA-256, kiểm tra giới hạn 5 keys, kiểm tra quyền sở hữu, CRUD
│   └── util/
│       └── ApiKeyGenerator.java           # Tiện ích: CSPRNG sinh rawKey (tvc_live_...), băm SHA-256, sinh keyPrefix
├── project/                               # Hoàn thành từ Feature F-004
├── auth/                                  # Hoàn thành từ Feature F-003
└── common/
    └── config/
        └── SecurityConfig.java            # Đăng ký ApiKeyAuthenticationFilter vào SecurityFilterChain
```

### Test Suite Structure

```text
backend/src/test/java/com/transvidcloud/backend/
├── apikey/
│   ├── util/
│   │   └── ApiKeyGeneratorTest.java       # Unit test: CSPRNG entropy, prefix tvc_live_, SHA-256 hash, keyPrefix format
│   ├── service/
│   │   └── ApiKeyServiceTest.java         # Unit test Mockito: CRUD logic, giới hạn 5 keys, kiểm tra quyền sở hữu, 404/403
│   ├── controller/
│   │   └── ApiKeyControllerTest.java      # WebMvc slice test: validation, serialization, HTTP status 201/200/204/400/403/404
│   └── repository/
│       ├── ApiKeyManagementIT.java        # Integration test (Testcontainers DynamoDB): CRUD lifecycle, GSI query, limit 5, tenant isolation
│       └── DualSecurityAuthIT.java        # Integration test (Testcontainers + Spring Security): X-API-Key auth, Bearer precedence, 403 cross-project
└── postman/
    └── F-005-apikey-dual-auth.postman_collection.json # Bộ test E2E Postman Collection độc lập
```

---

## Complexity Tracking

*Không có vi phạm Constitution Check. Thiết kế duy trì tính đơn giản (KISS/YAGNI), tái sử dụng hoàn toàn hạ tầng sẵn có và tuân thủ tuyệt đối các chuẩn mực kỹ thuật của dự án.*
