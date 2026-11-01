# Implementation Plan: Quản lý Project & Phân quyền sở hữu tài nguyên

**Branch**: `phuong-backend` | **Date**: 2026-10-31 | **Spec**: [specs/004-project-management/spec.md](spec.md)

**Input**: Feature specification từ `/specs/004-project-management/spec.md`

## Summary

Triển khai tính năng Quản lý Project & Phân quyền sở hữu tài nguyên (Epic A2) cho TransvidCloud Backend, bao gồm:
1. **Tầng lưu trữ DynamoDB**: Ánh xạ bảng `projects` (`tvc-{env}-projects`) sử dụng AWS SDK v2 `DynamoDbEnhancedClient`, hỗ trợ GSI `owner-index` (PK `ownerId`, SK `createdAt`) để truy vấn danh sách dự án của người dùng hiện tại theo thứ tự mới nhất lên đầu (`scanIndexForward(false)`), tuyệt đối không dùng thao tác quét toàn bảng (DynamoDB Scan).
2. **REST API Quản lý Project**:
   - `POST /api/projects`: Tạo dự án mới, tự động phát sinh `projectId` dạng `prj_<hex>`, tự động gán `ownerId` từ JWT principal, trạng thái ban đầu `ACTIVE`.
   - `GET /api/projects`: Liệt kê danh sách dự án đang hoạt động (`ACTIVE`) thuộc sở hữu của người dùng hiện tại.
   - `GET /api/projects/{projectId}`: Xem thông tin chi tiết một dự án.
   - `PATCH /api/projects/{projectId}`: Cập nhật từng phần (Partial update) tên, mô tả và địa chỉ Webhook (hỗ trợ xóa webhook bằng giá trị null), làm mới `updatedAt`.
   - `DELETE /api/projects/{projectId}`: Xóa dự án an toàn theo cơ chế Soft Delete.
3. **Phân quyền sở hữu tài nguyên (Ownership Isolation)**: Mọi thao tác đọc chi tiết, cập nhật và xóa Project đều kiểm tra quyền sở hữu tại tầng Service (`project.ownerId == currentUserId`); từ chối truy cập trái phép với mã HTTP 403 Forbidden (`ACCESS_DENIED`).
4. **Cơ chế xóa an toàn & Soft Delete**:
   - Kiểm tra an toàn (Safety Guard): Truy vấn bảng `videos` trên GSI `project-index` để kiểm tra có video nào đang trong trạng thái xử lý dở dang (`UPLOADING`, `PENDING`, `PROCESSING`) hay không; nếu có, từ chối xóa với mã HTTP 409 Conflict (`RESOURCE_IN_USE`).
   - Nếu an toàn, thực hiện Soft Delete (cập nhật `status = DELETED`, ghi nhận `deletedAt`), bảo toàn nguyên vẹn bản ghi video lịch sử và file trên S3; loại trừ dự án đã xóa khỏi danh sách và từ chối mọi yêu cầu biến đổi dữ liệu tiếp theo với mã HTTP 404 (`RESOURCE_NOT_FOUND`).

---

## Technical Context

**Language/Version**: Java 21 LTS  
**Primary Dependencies**: Spring Boot 3.4.5, Spring Security 6, Spring Cloud AWS 3.3.0, AWS SDK for Java v2 (`DynamoDbEnhancedClient`), MapStruct, Lombok  
**Storage**: Amazon DynamoDB (`tvc-{env}-projects` với GSI `owner-index`, truy vấn kiểm tra trên `tvc-{env}-videos` GSI `project-index`, LocalStack 3.0 / Testcontainers)  
**Testing**: JUnit 5, Mockito, Spring WebMvcTest (`@WebMvcTest`), Testcontainers LocalStack 3.0 (`*IT.java`)  
**Target Platform**: Linux Server / Docker Container  
**Project Type**: Web REST API Service (Spring Boot)  
**Performance Goals**: Thời gian phản hồi tạo/sửa/xóa/đọc chi tiết < 1 giây; thời gian liệt kê danh sách < 1 giây cho 100 dự án; Zero Table Scans trên DynamoDB  
**Constraints**: 
* Hoàn toàn phi trạng thái (Stateless Bearer JWT trích xuất danh tính `userId`).
* CSDL duy nhất là DynamoDB (tuân thủ ADR 0001, tuyệt đối không dùng JPA/SQL, không dùng Scan).
* Phân quyền sở hữu nghiêm ngặt: User A không bao giờ được đọc/sửa/xóa dự án của User B.
* Xóa mềm (Soft Delete) bảo tồn 100% video records kết thúc và dữ liệu S3.
**Scale/Scope**: 5 REST endpoints quản lý Project, 1 package tính năng `project/` độc lập, bảo đảm zero-regression cho toàn bộ test suite hiện có.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc / Gate | Tiêu chuẩn đánh giá | Kết quả | Ghi chú bằng chứng |
|---|---|---|---|
| **I. Code Quality & Maintainability** | Code rõ ràng, YAGNI, KISS, DTO bất biến (`record`), tách biệt domain qua MapStruct | **PASS** | Mô hình phân tầng chuẩn: `ProjectController -> ProjectService -> ProjectRepository / VideoSafetyChecker`, DTO dùng Java record |
| **II. Architecture & Modularity** | Tôn trọng ranh giới module, không tạo phụ thuộc vòng, độc lập logic Project | **PASS** | Đóng gói trong package `com.transvidcloud.backend.project`, tái sử dụng `common`, giao tiếp video qua interface kiểm tra an toàn |
| **III. Security & Privacy** | Xác thực Bearer JWT, kiểm tra quyền sở hữu tại Service layer, không lộ tài nguyên người khác | **PASS** | Tự động lấy `currentUserId` từ JWT, chặn 403 `ACCESS_DENIED`, ẩn project của người khác |
| **IV. Correctness & Data Integrity** | Chặn xóa khi có video in-flight (`RESOURCE_IN_USE` 409), Soft Delete an toàn | **PASS** | Truy vấn GSI `project-index` bảng `videos` giới hạn `limit(1)`, Soft Delete giữ nguyên S3 và video history |
| **V. Testing & Verification** | Real Verification: có Unit Test + WebMvcTest + Integration Test Testcontainers LocalStack | **PASS** | Lập kế hoạch `ProjectServiceTest`, `ProjectControllerTest` và `ProjectManagementIT` |
| **VI. Error Handling & Observability** | Chuẩn hóa `ErrorResponse`, không nuốt lỗi, mã lỗi thống nhất | **PASS** | Tích hợp `GlobalExceptionHandler`, mở rộng `ErrorCode` (`RESOURCE_NOT_FOUND`, `RESOURCE_IN_USE`) |
| **VII. Single Source of Truth** | Khớp hoàn toàn contract `contracts/openapi.yaml` và `contracts/dynamodb-tables.md` | **PASS** | Trích xuất contract cục bộ tại `contracts/projects-api.yaml`, schema khớp 100% |
| **VIII. Dependency Discipline** | Không bổ sung thư viện bên ngoài không cần thiết | **PASS** | Sử dụng toàn bộ thư viện sẵn có trong `pom.xml` (DynamoDbEnhancedClient, MapStruct, Lombok) |
| **IX. Human Oversight** | Mọi quyết định kỹ thuật đều có cơ sở từ contract và làm rõ qua `/speckit-clarify` | **PASS** | Quyết định Soft Delete và thứ tự sắp xếp được phê duyệt trực tiếp bởi người dùng |

---

## Project Structure

### Documentation (this feature)

```text
specs/004-project-management/
├── spec.md              # Yêu cầu nghiệp vụ & Acceptance Criteria (/speckit-specify, /speckit-clarify)
├── plan.md              # Kế hoạch triển khai & kiến trúc kỹ thuật (/speckit-plan)
├── research.md          # Nghiên cứu kỹ thuật: GSI query, Soft Delete, Safety Guard, Ownership Isolation
├── data-model.md        # Mô hình dữ liệu DynamoDB ProjectItem, DTOs, Mappers & Lifecycle
├── quickstart.md        # Hướng dẫn xác minh tự động và kịch bản cURL thủ công
├── contracts/
│   └── projects-api.yaml # Contract OpenAPI trích xuất cho các endpoint Project
└── checklists/
    └── requirements.md  # Checklist chất lượng đặc tả
```

### Source Code (repository layout)

```text
backend/src/main/java/com/transvidcloud/backend/
├── TransvidCloudBackendApplication.java
├── project/
│   ├── controller/
│   │   └── ProjectController.java         # REST Controller: POST/GET /api/projects, GET/PATCH/DELETE /api/projects/{projectId}
│   ├── dto/
│   │   ├── CreateProjectRequest.java      # Request DTO tạo mới Project (record)
│   │   ├── UpdateProjectRequest.java      # Request DTO cập nhật thông tin Project (record)
│   │   ├── ProjectResponse.java           # Response DTO chi tiết Project (record)
│   │   └── ProjectListResponse.java       # Response DTO danh sách Project (record)
│   ├── entity/
│   │   └── ProjectItem.java               # DynamoDB Enhanced Client Entity (@DynamoDbBean)
│   ├── mapper/
│   │   └── ProjectMapper.java             # MapStruct interface: ProjectItem <-> ProjectResponse/DTOs
│   ├── repository/
│   │   ├── ProjectRepository.java         # Tương tác DynamoDB: save, findById, queryByOwner, update, softDelete
│   │   └── VideoSafetyChecker.java        # Truy vấn kiểm tra video in-flight trên bảng videos qua GSI project-index
│   └── service/
│       └── ProjectService.java            # Nghiệp vụ: CRUD Project, kiểm tra quyền sở hữu (ownerId), kiểm tra an toàn trước khi xóa
├── auth/                                  # Hoàn thành từ Feature F-003
└── common/
    ├── config/                            # TvcProperties, AwsConfig, SecurityConfig, CorsConfig
    ├── dto/                               # ErrorResponse
    └── exception/                         # AppException, ErrorCode (bổ sung RESOURCE_NOT_FOUND, RESOURCE_IN_USE)

backend/src/test/java/com/transvidcloud/backend/
├── project/
│   ├── controller/
│   │   └── ProjectControllerTest.java     # WebMvcTest kiểm tra endpoints, validation, phân quyền, HTTP status
│   ├── service/
│   │   └── ProjectServiceTest.java        # Unit test logic nghiệp vụ với Mockito (CRUD, owner isolation, safety guard)
│   └── repository/
│       └── ProjectManagementIT.java       # Integration Test với Testcontainers LocalStack DynamoDB (CRUD, GSI, Soft Delete)
└── ...
```

---

## Implementation Phases & Quality Gates

### Phase 1: Kiến trúc dữ liệu & Tầng Repository
- Mở rộng enum `ErrorCode` với `RESOURCE_NOT_FOUND` (404), `RESOURCE_IN_USE` (409).
- Tạo `ProjectItem` với đầy đủ annotations DynamoDB Enhanced Client cho Partition Key `projectId` và GSI `owner-index` (PK `ownerId`, SK `createdAt`), cùng các trường `status`, `deletedAt`.
- Triển khai `ProjectRepository`:
  - `save(ProjectItem)`: lưu dự án mới.
  - `findById(String projectId)`: đọc bản ghi dự án theo ID.
  - `findByOwnerId(String ownerId)`: truy vấn GSI `owner-index` với `scanIndexForward(false)` (mới nhất lên đầu) và lọc loại trừ `DELETED`.
  - `softDelete(String projectId)`: cập nhật `status = DELETED` và `deletedAt = now()`.
- Triển khai `VideoSafetyChecker`:
  - `hasActiveVideos(String projectId)`: truy vấn bảng `videos` qua GSI `project-index`, kiểm tra `status` in `[UPLOADING, PENDING, PROCESSING]` với `limit(1)`.

### Phase 2: Tầng Nghiệp vụ (Service) & MapStruct Mapper
- Triển khai `ProjectMapper` (MapStruct) chuyển đổi giữa entity và DTOs.
- Triển khai `ProjectService`:
  - `createProject(CreateProjectRequest, String ownerId)`: sinh `projectId` (`prj_...`), thiết lập `status = ACTIVE`, lưu và trả về response.
  - `listProjects(String ownerId)`: lấy danh sách dự án của user, trả về `ProjectListResponse`.
  - `getProject(String projectId, String currentUserId)`: kiểm tra tồn tại, kiểm tra soft delete (404), kiểm tra quyền sở hữu (403), trả về response.
  - `updateProject(String projectId, UpdateProjectRequest, String currentUserId)`: kiểm tra quyền sở hữu (403), cập nhật các trường được chỉ định, cập nhật `updatedAt`.
  - `deleteProject(String projectId, String currentUserId)`: kiểm tra quyền sở hữu (403), gọi `VideoSafetyChecker.hasActiveVideos()` (nếu có -> ném 409 `RESOURCE_IN_USE`), thực hiện `softDelete()`.

### Phase 3: Tầng Giao diện (Controller) & Cấu hình Bảo mật
- Triển khai `ProjectController` cung cấp các REST endpoints theo hợp đồng OpenAPI:
  - `POST /api/projects`: `@ResponseStatus(HttpStatus.CREATED)`
  - `GET /api/projects`: `@ResponseStatus(HttpStatus.OK)`
  - `GET /api/projects/{projectId}`: `@ResponseStatus(HttpStatus.OK)`
  - `PATCH /api/projects/{projectId}`: `@ResponseStatus(HttpStatus.OK)`
  - `DELETE /api/projects/{projectId}`: `@ResponseStatus(HttpStatus.NO_CONTENT)`
- Trích xuất danh tính người dùng (`userId`) từ `Authentication` của Spring Security đưa vào các phương thức Service.
- Xác nhận `SecurityConfig`: các endpoints `/api/projects/**` được bảo vệ tự động bằng Bearer JWT (đã cấu hình `authenticated()` trong F-003).

### Phase 4: Kiểm thử tự động (Unit, WebMvc, Integration Test)
- `ProjectServiceTest`: Kiểm tra 100% các nhánh logic:
  - Tạo project thành công.
  - Liệt kê danh sách project.
  - Xem chi tiết project thành công.
  - Xem/sửa/xóa project của người khác -> ném `ACCESS_DENIED` (403).
  - Xem/sửa/xóa project không tồn tại hoặc đã bị xóa mềm -> ném `RESOURCE_NOT_FOUND` (404).
  - Xóa project khi còn video in-flight -> ném `RESOURCE_IN_USE` (409).
  - Xóa project an toàn thành công -> Soft Delete được gọi.
- `ProjectControllerTest` (`@WebMvcTest`): Kiểm tra HTTP status codes, validation annotations, mapping response.
- `ProjectManagementIT` (Testcontainers LocalStack 3.0): Kiểm tra tích hợp thực tế toàn bộ vòng đời: Tạo project -> Liệt kê qua GSI `owner-index` -> Sửa thông tin -> Kiểm tra chặn xóa khi có video giả lập `PROCESSING` -> Xóa an toàn khi không còn video -> Kiểm tra loại trừ khỏi danh sách.

### Phase 5: Verification & Zero Regression
- Chạy `./scripts/verify.sh` (`./mvnw -B clean verify`), bảo đảm toàn bộ 47 tests cũ và toàn bộ tests mới đều PASS 100%.
- Kiểm tra cấu trúc starter và harness qua `./scripts/check-setup.sh`.
