---
description: "Task list for Feature F-004: Project Management & Resource Ownership Isolation"
---

# Tasks: Quản lý Project & Phân quyền sở hữu tài nguyên (Feature F-004)

**Input**: Tài liệu thiết kế từ `specs/004-project-management/` (`spec.md`, `plan.md`, `data-model.md`, `research.md`, `contracts/projects-api.yaml`, `quickstart.md`)  
**Prerequisites**: `plan.md`, `spec.md`, `data-model.md`, `contracts/projects-api.yaml`  
**Tests**: Bao gồm đầy đủ Unit Tests, WebMvcTests và Integration Tests với LocalStack 3.0 Testcontainers (`ProjectManagementIT`) theo chiến lược Real Verification & Zero Regression của dự án.  

## Format: `- [ ] [TaskID] [P?] [Story?] Description with file path`

- **[P]**: Có thể thực hiện song song (tệp riêng biệt, không phụ thuộc vào task chưa hoàn thành)
- **[Story]**: Gắn với User Story tương ứng (`[US1]`, `[US2]`, `[US3]`, `[US4]`, `[US5]`)
- Mô tả kèm đường dẫn tệp chính xác

---

## Phase 1: Setup & Error Codes (Shared Infrastructure)

**Purpose**: Bổ sung các mã lỗi chuẩn quản lý tài nguyên và khởi tạo cấu trúc package tính năng.

- [X] T001 Bổ sung mã lỗi `RESOURCE_NOT_FOUND` (HTTP 404 Not Found, code 1005) và `RESOURCE_IN_USE` (HTTP 409 Conflict, code 1009) vào enum `ErrorCode` trong `backend/src/main/java/com/transvidcloud/backend/common/exception/ErrorCode.java`
- [X] T002 [P] Khởi tạo cấu trúc các thư mục package `com.transvidcloud.backend.project` (`controller`, `dto`, `entity`, `mapper`, `repository`, `service`) trong `backend/src/main/java/com/transvidcloud/backend/project/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Xây dựng toàn bộ hạ tầng DTOs, DynamoDB Entity `ProjectItem`, MapStruct Mapper và các phương thức cơ sở của Repository mà tất cả User Stories dùng chung.

**⚠️ CRITICAL**: Không thể bắt đầu các User Stories cho đến khi Phase 2 hoàn thành.

- [X] T003 [P] Tạo các request DTO `CreateProjectRequest.java` (`name` `@NotBlank` `@Size(min=1, max=120)`, `description` `@Size(max=500)`, `webhookUrl` `@Pattern(regexp="^(https?://.+)?$")`) và `UpdateProjectRequest.java` (`name` `@Size(min=1, max=120)`, `description` `@Size(max=500)`, `webhookUrl` `@Pattern(regexp="^(https?://.+)?$")`) dạng Java `record` trong `backend/src/main/java/com/transvidcloud/backend/project/dto/`
- [X] T004 [P] Tạo các response DTO `ProjectResponse.java` (`projectId`, `ownerId`, `name`, `description`, `webhookUrl`, `createdAt`, `updatedAt`) và `ProjectListResponse.java` (`items`) dạng Java `record` trong `backend/src/main/java/com/transvidcloud/backend/project/dto/`
- [X] T005 [P] Tạo DynamoDB Bean `ProjectItem.java` với các annotation `@DynamoDbBean`, `@DynamoDbPartitionKey` cho `projectId`, `@DynamoDbSecondaryPartitionKey(indexNames = "owner-index")` cho `ownerId`, `@DynamoDbSecondarySortKey(indexNames = "owner-index")` cho `createdAt`, cùng các trường `name`, `description`, `webhookUrl`, `status` ("ACTIVE"|"DELETED"), `updatedAt`, `deletedAt` trong `backend/src/main/java/com/transvidcloud/backend/project/entity/ProjectItem.java`
- [X] T006 [P] Tạo MapStruct mapper interface `ProjectMapper.java` chuyển đổi giữa `ProjectItem`, `CreateProjectRequest`, `ProjectResponse` và `ProjectListResponse` trong `backend/src/main/java/com/transvidcloud/backend/project/mapper/ProjectMapper.java`
- [X] T007 Xây dựng `ProjectRepository.java` với `DynamoDbEnhancedClient` tương tác bảng `tvc-{env}-projects` (sử dụng tên bảng từ `TvcProperties.DynamoDbProperties`): triển khai phương thức cơ bản `save(ProjectItem item)` và `findById(String projectId)` trong `backend/src/main/java/com/transvidcloud/backend/project/repository/ProjectRepository.java`

**Checkpoint**: Hạ tầng nền tảng hoàn tất — các User Story có thể bắt đầu triển khai độc lập.

---

## Phase 3: User Story 1 - Khởi tạo Project mới cho người dùng SaaS (Priority: P1) 🎯 MVP

**Goal**: Người dùng đã đăng nhập có thể tạo Project mới với tên hợp lệ (1-120 ký tự), mô tả (tối đa 500 ký tự) và webhookUrl tùy chọn; hệ thống sinh `projectId` dạng `prj_<12-hex>`, gán `ownerId` từ JWT principal, đặt `status = ACTIVE`, lưu vào DynamoDB và trả về `ProjectResponse` với mã HTTP 201 Created.

**Independent Test**: Gửi `POST /api/projects` với token hợp lệ và tên dự án -> nhận HTTP 201 Created kèm đầy đủ metadata dự án.

### Implementation for User Story 1

- [X] T008 [US1] Xây dựng phương thức `createProject(CreateProjectRequest request, String ownerId)` trong `ProjectService.java`: sinh `projectId = "prj_" + HexUtils.randomHex(12)`, khởi tạo `status = "ACTIVE"`, `createdAt`, `updatedAt`, gọi `projectRepository.save()` và trả về `ProjectResponse` qua `ProjectMapper` trong `backend/src/main/java/com/transvidcloud/backend/project/service/ProjectService.java`
- [X] T009 [US1] Xây dựng REST endpoint `POST /api/projects` trong `ProjectController.java`: nhận `@Valid @RequestBody CreateProjectRequest`, trích xuất `currentUserId` từ `Authentication`, gọi `projectService.createProject()`, trả về `ResponseEntity.status(HttpStatus.CREATED).body(response)` trong `backend/src/main/java/com/transvidcloud/backend/project/controller/ProjectController.java`
- [X] T010 [P] [US1] Viết Unit Test cho luồng tạo dự án trong `ProjectServiceTest.java`: kiểm tra sinh đúng tiền tố `prj_`, gán đúng `ownerId`, lưu trạng thái `ACTIVE` trong `backend/src/test/java/com/transvidcloud/backend/project/service/ProjectServiceTest.java`
- [X] T011 [P] [US1] Viết WebMvcTest cho endpoint `POST /api/projects` trong `ProjectControllerTest.java`: kiểm tra trả về 201 Created khi hợp lệ, 400 Bad Request khi tên rỗng hoặc vượt quá 120 ký tự hoặc URL sai định dạng, 401 Unauthorized khi thiếu token trong `backend/src/test/java/com/transvidcloud/backend/project/controller/ProjectControllerTest.java`
- [X] T012 [US1] Viết Integration Test với Testcontainers LocalStack DynamoDB kiểm chứng tạo project thực tế trong `ProjectManagementIT.java` trong `backend/src/test/java/com/transvidcloud/backend/project/repository/ProjectManagementIT.java`

**Checkpoint**: User Story 1 hoàn thành 100% — hệ thống đã có thể khởi tạo Project độc lập và trả về định danh duy nhất (MVP hoàn chỉnh).

---

## Phase 4: User Story 2 - Liệt kê và Xem chi tiết Project thuộc sở hữu (Priority: P1)

**Goal**: Người dùng có thể liệt kê tất cả các project đang hoạt động của mình qua GSI `owner-index` (sắp xếp mới nhất lên đầu, loại trừ project đã xóa) và xem chi tiết một project theo `projectId`.

**Independent Test**: Tạo 2 dự án cho user -> gọi `GET /api/projects` thấy đủ 2 dự án theo thứ tự mới nhất lên đầu; gọi `GET /api/projects/{projectId}` thấy chi tiết dự án đó.

### Implementation for User Story 2

- [X] T013 [US2] Mở rộng `ProjectRepository.java` bổ sung phương thức `findByOwnerId(String ownerId)` truy vấn GSI `owner-index` với `scanIndexForward(false)` (mới nhất lên đầu) và lọc loại trừ bản ghi có `status = "DELETED"` trong `backend/src/main/java/com/transvidcloud/backend/project/repository/ProjectRepository.java`
- [X] T014 [US2] Mở rộng `ProjectService.java` bổ sung các phương thức `listProjects(String ownerId)` và `getProject(String projectId, String currentUserId)` (ném 404 `RESOURCE_NOT_FOUND` nếu không tồn tại hoặc đã bị xóa mềm `status == DELETED`, ném 403 `ACCESS_DENIED` nếu `ownerId != currentUserId`) trong `backend/src/main/java/com/transvidcloud/backend/project/service/ProjectService.java`
- [X] T015 [US2] Mở rộng `ProjectController.java` bổ sung các endpoints `@GetMapping("/projects")` và `@GetMapping("/projects/{projectId}")` trong `backend/src/main/java/com/transvidcloud/backend/project/controller/ProjectController.java`
- [X] T016 [P] [US2] Viết Unit Test trong `ProjectServiceTest.java` kiểm tra `listProjects` trả về đúng danh sách và `getProject` trả về chi tiết hoặc ném 404 khi không tìm thấy / đã xóa mềm trong `backend/src/test/java/com/transvidcloud/backend/project/service/ProjectServiceTest.java`
- [X] T017 [P] [US2] Viết WebMvcTest trong `ProjectControllerTest.java` cho `GET /api/projects` (200 OK) và `GET /api/projects/{projectId}` (200 OK, 404 Not Found) trong `backend/src/test/java/com/transvidcloud/backend/project/controller/ProjectControllerTest.java`
- [X] T018 [US2] Mở rộng Integration Test `ProjectManagementIT.java` kiểm chứng truy vấn GSI `owner-index` thực tế trên DynamoDB và thứ tự sắp xếp mới nhất lên đầu trong `backend/src/test/java/com/transvidcloud/backend/project/repository/ProjectManagementIT.java`

**Checkpoint**: User Story 1 và User Story 2 đều hoạt động hoàn chỉnh và có thể kiểm thử độc lập.

---

## Phase 5: User Story 3 - Cập nhật thông tin Project và Cấu hình Webhook (Priority: P2)

**Goal**: Người dùng có thể cập nhật từng phần (PATCH) tên, mô tả và URL webhook (hỗ trợ xóa webhook bằng giá trị null), tự động làm mới `updatedAt`.

**Independent Test**: Gửi `PATCH /api/projects/{projectId}` với tên mới -> nhận 200 OK và thông tin sau cập nhật, `updatedAt` được làm mới.

### Implementation for User Story 3

- [X] T019 [US3] Mở rộng `ProjectRepository.java` bổ sung phương thức `updateProject(ProjectItem item)` cập nhật các thuộc tính trong DynamoDB trong `backend/src/main/java/com/transvidcloud/backend/project/repository/ProjectRepository.java`
- [X] T020 [US3] Mở rộng `ProjectService.java` bổ sung phương thức `updateProject(String projectId, UpdateProjectRequest request, String currentUserId)`: kiểm tra tồn tại và `status != DELETED` (404), kiểm tra quyền sở hữu (403), áp dụng các giá trị thay đổi không null, làm mới `updatedAt = Instant.now().toString()`, lưu và trả về response trong `backend/src/main/java/com/transvidcloud/backend/project/service/ProjectService.java`
- [X] T021 [US3] Mở rộng `ProjectController.java` bổ sung endpoint `@PatchMapping("/projects/{projectId}")` nhận `@Valid @RequestBody UpdateProjectRequest` trong `backend/src/main/java/com/transvidcloud/backend/project/controller/ProjectController.java`
- [X] T022 [P] [US3] Viết Unit Test trong `ProjectServiceTest.java` kiểm tra cập nhật tên, mô tả, xóa webhook và xác minh cập nhật `updatedAt` trong `backend/src/test/java/com/transvidcloud/backend/project/service/ProjectServiceTest.java`
- [X] T023 [P] [US3] Viết WebMvcTest trong `ProjectControllerTest.java` cho `PATCH /api/projects/{projectId}` (200 OK, 400 Bad Request khi tên rỗng hoặc vượt quá 120 ký tự) trong `backend/src/test/java/com/transvidcloud/backend/project/controller/ProjectControllerTest.java`
- [X] T024 [US3] Mở rộng Integration Test `ProjectManagementIT.java` kiểm chứng cập nhật dữ liệu thực tế trên DynamoDB trong `backend/src/test/java/com/transvidcloud/backend/project/repository/ProjectManagementIT.java`

**Checkpoint**: User Stories 1, 2 và 3 đều hoạt động trọn vẹn và độc lập.

---

## Phase 6: User Story 4 - Xóa an toàn Project bằng Soft Delete và Chặn xóa khi còn video đang xử lý (Priority: P2)

**Goal**: Chặn xóa 409 Conflict (`RESOURCE_IN_USE`) nếu dự án còn video đang in-flight (`UPLOADING`, `PENDING`, `PROCESSING`); nếu an toàn, thực hiện Soft Delete (`status = DELETED`, `deletedAt = now()`), giữ nguyên toàn bộ lịch sử video và file S3, trả về 204 No Content.

**Independent Test**: Tạo video giả lập `PROCESSING` -> gửi DELETE -> bị từ chối 409; chuyển video sang `COMPLETED` -> gửi DELETE -> thành công 204, project bị ẩn khỏi danh sách.

### Implementation for User Story 4

- [X] T025 [US4] Xây dựng `VideoSafetyChecker.java` truy vấn bảng `tvc-{env}-videos` qua GSI `project-index` kiểm tra có video nào mang `status` in `[UPLOADING, PENDING, PROCESSING]` với `limit(1)` trong `backend/src/main/java/com/transvidcloud/backend/project/repository/VideoSafetyChecker.java`
- [X] T026 [US4] Mở rộng `ProjectRepository.java` bổ sung phương thức `softDelete(String projectId)` cập nhật `status = "DELETED"`, `deletedAt = Instant.now().toString()`, `updatedAt = Instant.now().toString()` trong `backend/src/main/java/com/transvidcloud/backend/project/repository/ProjectRepository.java`
- [X] T027 [US4] Mở rộng `ProjectService.java` bổ sung phương thức `deleteProject(String projectId, String currentUserId)`: kiểm tra tồn tại và `status != DELETED` (404), kiểm tra quyền sở hữu (403), gọi `videoSafetyChecker.hasActiveVideos()` (nếu true -> ném 409 `RESOURCE_IN_USE`), gọi `projectRepository.softDelete()` trong `backend/src/main/java/com/transvidcloud/backend/project/service/ProjectService.java`
- [X] T028 [US4] Mở rộng `ProjectController.java` bổ sung endpoint `@DeleteMapping("/projects/{projectId}")` trả về `@ResponseStatus(HttpStatus.NO_CONTENT)` trong `backend/src/main/java/com/transvidcloud/backend/project/controller/ProjectController.java`
- [X] T029 [P] [US4] Viết Unit Test trong `ProjectServiceTest.java` kiểm tra chặn xóa ném 409 khi còn video in-flight và thực hiện Soft Delete thành công khi không còn video in-flight trong `backend/src/test/java/com/transvidcloud/backend/project/service/ProjectServiceTest.java`
- [X] T030 [P] [US4] Viết WebMvcTest trong `ProjectControllerTest.java` cho `DELETE /api/projects/{projectId}` (204 No Content, 404 Not Found, 409 Conflict) trong `backend/src/test/java/com/transvidcloud/backend/project/controller/ProjectControllerTest.java`
- [X] T031 [US4] Mở rộng Integration Test `ProjectManagementIT.java` kiểm chứng end-to-end: chặn xóa khi có video in-flight trên DynamoDB LocalStack và Soft Delete thành công khi an toàn trong `backend/src/test/java/com/transvidcloud/backend/project/repository/ProjectManagementIT.java`

**Checkpoint**: User Stories 1, 2, 3 và 4 đều hoàn thành và được kiểm chứng.

---

## Phase 7: User Story 5 - Cô lập quyền sở hữu tài nguyên đa người dùng (Multi-tenant Isolation) (Priority: P1)

**Goal**: Đảm bảo 100% các thao tác đọc chi tiết, sửa hoặc xóa Project của User A bởi User B đều bị từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`), và danh sách project của User B không chứa dự án của User A.

**Independent Test**: Dùng token User B gọi `GET /api/projects/{projectIdA}`, `PATCH /api/projects/{projectIdA}`, `DELETE /api/projects/{projectIdA}` -> đều nhận 403 `ACCESS_DENIED`.

### Implementation for User Story 5

- [X] T032 [P] [US5] Mở rộng `ProjectServiceTest.java` viết Unit Test chuyên biệt kiểm chứng bảo vệ quyền sở hữu: ném `AppException(ErrorCode.ACCESS_DENIED)` cho mọi hàm `getProject`, `updateProject`, `deleteProject` khi `ownerId != currentUserId` trong `backend/src/test/java/com/transvidcloud/backend/project/service/ProjectServiceTest.java`
- [X] T033 [P] [US5] Mở rộng `ProjectControllerTest.java` viết WebMvcTest xác nhận controller bắt `ACCESS_DENIED` và trả về HTTP 403 Forbidden kèm mã lỗi chuẩn 1007 trong `backend/src/test/java/com/transvidcloud/backend/project/controller/ProjectControllerTest.java`
- [X] T034 [US5] Mở rộng Integration Test `ProjectManagementIT.java` kiểm chứng đa người thuê thực tế: tạo 2 user A và B, xác minh user B bị chặn 403 khi thao tác trên project của A, và `listProjects` của B không thấy project của A trong `backend/src/test/java/com/transvidcloud/backend/project/repository/ProjectManagementIT.java`

**Checkpoint**: Toàn bộ 5 User Stories đều hoàn thiện và được kiểm chứng độc lập.

---

## Phase 8: Polish, Verification & Evidence

**Purpose**: Hoàn thiện tài liệu, Postman collection, kiểm chứng Zero Regression và tạo hồ sơ Feature Evidence.

- [X] T035 [P] Tạo bộ Postman collection `backend/postman/F-004-project-management.postman_collection.json` với 8 requests kiểm thử đầy đủ CRUD và các trường hợp bảo mật 403/404/409
- [X] T036 Chạy lệnh kiểm thử verification chính thức `./scripts/verify.sh` (`./mvnw -B clean verify`), bảo đảm 100% tests PASS và tạo Feature Evidence tại `backend/quality/evidence/F-004-project-management.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Không có phụ thuộc — bắt đầu ngay.
- **Phase 2 (Foundational)**: Phụ thuộc vào Phase 1 — **BẮT BUỘC HOÀN THÀNH** trước khi triển khai các User Stories.
- **Phase 3 (User Story 1 - P1)**: Phụ thuộc vào Phase 2 — Triển khai MVP cốt lõi.
- **Phase 4 (User Story 2 - P1)**: Phụ thuộc vào Phase 2 & Phase 3 (tận dụng `ProjectService` và `ProjectController`).
- **Phase 5 (User Story 3 - P2)**: Phụ thuộc vào Phase 2, Phase 3 & Phase 4.
- **Phase 6 (User Story 4 - P2)**: Phụ thuộc vào Phase 2, Phase 3 & Phase 4.
- **Phase 7 (User Story 5 - P1)**: Phụ thuộc vào các User Stories 1-4 (kiểm tra phân quyền trên toàn bộ các endpoints).
- **Phase 8 (Polish & Evidence)**: Phụ thuộc vào toàn bộ các User Stories 1-5 hoàn tất.

### Parallel Opportunities

- Trong Phase 1: `T002` có thể chạy song song với `T001`.
- Trong Phase 2: `T003`, `T004`, `T005`, `T006` có thể chạy song song (các tệp riêng biệt).
- Trong các User Story: Các Unit Test và WebMvcTest đánh dấu `[P]` có thể được viết và thực thi song song.

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Hoàn thành Phase 1: Setup & Error Codes
2. Hoàn thành Phase 2: Foundational (DTOs, Entity, Mapper, Repository cơ sở)
3. Hoàn thành Phase 3: User Story 1 (Tạo Project thành công, kiểm thử Unit + IT)
4. **Kiểm chứng MVP**: Chạy kiểm thử độc lập cho User Story 1.

### Incremental Delivery
1. Foundation + US1 (Tạo Project) -> MVP.
2. Thêm US2 (Liệt kê GSI `owner-index` + Xem chi tiết) -> Người dùng xem và quản lý danh sách.
3. Thêm US3 (Cập nhật thông tin/webhook) -> Cho phép chỉnh sửa cấu hình.
4. Thêm US4 (Xóa an toàn Soft Delete + Guard video in-flight) -> Hoàn tất chu trình xóa dữ liệu an toàn.
5. Thêm US5 (Phân quyền sở hữu cô lập đa người thuê) -> Hoàn thiện an ninh bảo mật.
6. Chạy `./scripts/verify.sh` để nghiệm thu Zero Regression toàn hệ thống.
