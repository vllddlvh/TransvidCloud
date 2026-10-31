---
description: "Task list for Feature F-003: SaaS User Authentication — Register, Login & JWT Security"
---

# Tasks: Xác thực người dùng SaaS — Register, Login & JWT Security (Feature F-003)

**Input**: Tài liệu thiết kế từ `specs/003-saas-auth-jwt/` (`spec.md`, `plan.md`, `data-model.md`, `research.md`, `contracts/auth-api.yaml`, `quickstart.md`)  
**Prerequisites**: `plan.md`, `spec.md`, `data-model.md`, `contracts/auth-api.yaml`  
**Tests**: Bao gồm đầy đủ Unit Tests, WebMvcTests và Integration Tests với LocalStack 3.0 Testcontainers (`UserAuthIT`) theo chiến lược Real Verification & Zero Regression của dự án.  

## Format: `- [ ] [TaskID] [P?] [Story?] Description with file path`

- **[P]**: Có thể thực hiện song song (tệp riêng biệt, không phụ thuộc vào task chưa hoàn thành)
- **[Story]**: Gắn với User Story tương ứng (`[US1]`, `[US2]`, `[US3]`)
- Mô tả kèm đường dẫn tệp tuyệt đối/tương đối chính xác

---

## Phase 1: Setup & Error Codes (Shared Infrastructure)

**Purpose**: Bổ sung các mã lỗi chuẩn xác thực và khởi tạo cấu trúc package tính năng.

- [X] T001 Bổ sung mã lỗi `EMAIL_ALREADY_EXISTS` (HTTP 409 Conflict) và `INVALID_CREDENTIALS` (HTTP 401 Unauthorized) vào enum `ErrorCode` trong `backend/src/main/java/com/transvidcloud/backend/common/exception/ErrorCode.java`
- [X] T002 [P] Khởi tạo cấu trúc các thư mục package `com.transvidcloud.backend.auth` (`controller`, `dto`, `entity`, `mapper`, `repository`, `service`) trong `backend/src/main/java/com/transvidcloud/backend/auth/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Xây dựng toàn bộ hạ tầng DTO, Entity DynamoDB, MapStruct Mapper và dịch vụ phát hành JWT Token mà các User Stories dùng chung.

**⚠️ CRITICAL**: Không thể bắt đầu các User Stories cho đến khi Phase 2 hoàn thành.

- [X] T003 [P] Tạo các request DTO `RegisterRequest.java` (`email` `@NotBlank` `@Email`, `password` `@NotBlank` `@Size(min=8, max=100)`, `fullName` `@Size(max=100)`) và `LoginRequest.java` (`email` `@NotBlank` `@Email`, `password` `@NotBlank`) dạng Java `record` trong `backend/src/main/java/com/transvidcloud/backend/auth/dto/`
- [X] T004 [P] Tạo các response DTO `UserSummary.java` (`userId`, `email`, `fullName`, `createdAt`) và `AuthResponse.java` (`accessToken`, `tokenType`, `expiresInSeconds`, `user`) dạng Java `record` trong `backend/src/main/java/com/transvidcloud/backend/auth/dto/`
- [X] T005 [P] Tạo DynamoDB Bean `UserItem.java` với các annotation `@DynamoDbBean`, `@DynamoDbPartitionKey` cho `userId`, GSI secondary partition key cho `email` (`email-index`), các thuộc tính `passwordHash`, `fullName`, `createdAt`, `itemType`, `targetUserId` trong `backend/src/main/java/com/transvidcloud/backend/auth/entity/UserItem.java`
- [X] T006 [P] Tạo MapStruct mapper interface `UserMapper.java` ánh xạ giữa `UserItem` và `UserSummary` (bảo đảm không bao giờ map `passwordHash`) trong `backend/src/main/java/com/transvidcloud/backend/auth/mapper/UserMapper.java`
- [X] T007 Cài đặt dịch vụ phát hành token `JwtTokenService.java` ký HMAC-SHA256 sử dụng Nimbus JOSE JWT (`SignedJWT`, `MACSigner`) và cấu hình `TvcProperties.SecurityProperties.JwtProperties` (`secret`, `expirationSeconds`), đóng gói các claims `sub` (userId), `email`, `iat`, `exp` trong `backend/src/main/java/com/transvidcloud/backend/auth/service/JwtTokenService.java`
- [X] T008 [P] Viết Unit Test `JwtTokenServiceTest.java` kiểm tra phát hành token đúng thời hạn, claims và xác thực tương thích với Nimbus `JwtDecoder` trong `backend/src/test/java/com/transvidcloud/backend/auth/service/JwtTokenServiceTest.java`

**Checkpoint**: Hạ tầng nền tảng hoàn tất — các User Story có thể bắt đầu triển khai độc lập.

---

## Phase 3: User Story 1 - Đăng ký tài khoản người dùng SaaS mới (Priority: P1) 🎯 MVP

**Goal**: Người dùng mới có thể đăng ký tài khoản với email duy nhất; hệ thống băm mật khẩu BCrypt, ghi đồng thời User Item và Email Lock Item qua DynamoDB `TransactWriteItems`, và trả về `AuthResponse` (HTTP 201 Created) chứa JWT token.

**Independent Test**: Gửi `POST /api/auth/register` với email mới -> nhận 201 Created và Access Token hợp lệ; gửi lại cùng email -> nhận 409 Conflict với mã lỗi `EMAIL_ALREADY_EXISTS`.

### Implementation for User Story 1

- [X] T009 [P] [US1] Xây dựng `UserRepository.java` với phương thức `registerUserInTransaction(UserItem userItem, UserItem lockItem)` thực thi `DynamoDbClient.transactWriteItems` gồm 2 PutItem với điều kiện `attribute_not_exists(userId)` trên bảng `tvc-{env}-users` trong `backend/src/main/java/com/transvidcloud/backend/auth/repository/UserRepository.java`
- [X] T010 [US1] Xây dựng `AuthService.java` triển khai nghiệp vụ `register(RegisterRequest request)`: chuẩn hóa email (`trim().toLowerCase()`), sinh `userId` dạng `usr_<12-hex>`, băm mật khẩu `passwordEncoder.encode()`, gọi repository giao dịch, bắt `TransactionCanceledException` để ném `AppException(ErrorCode.EMAIL_ALREADY_EXISTS)`, và phát hành token qua `JwtTokenService` trong `backend/src/main/java/com/transvidcloud/backend/auth/service/AuthService.java`
- [X] T011 [US1] Xây dựng REST Controller `AuthController.java` triển khai endpoint `@PostMapping("/auth/register")` nhận `@Valid @RequestBody RegisterRequest` và trả về `ResponseEntity.status(HttpStatus.CREATED).body(response)` trong `backend/src/main/java/com/transvidcloud/backend/auth/controller/AuthController.java`
- [X] T012 [P] [US1] Viết Unit Test `AuthServiceTest.java` kiểm tra luồng đăng ký thành công, chuẩn hóa email, mã hóa BCrypt và bắt lỗi hủy transaction trùng email ném 409 trong `backend/src/test/java/com/transvidcloud/backend/auth/service/AuthServiceTest.java`
- [X] T013 [P] [US1] Viết WebMvcTest `AuthControllerTest.java` kiểm tra endpoint `POST /api/auth/register` (thành công 201 Created, lỗi validation 400 Bad Request, xung đột email 409 Conflict) trong `backend/src/test/java/com/transvidcloud/backend/auth/controller/AuthControllerTest.java`
- [X] T014 [US1] Viết Integration Test `UserAuthIT.java` với Testcontainers LocalStack 3.0 kiểm chứng giao dịch `TransactWriteItems` trên DynamoDB thực tế và khả năng chặn đăng ký đồng thời cùng một email trong `backend/src/test/java/com/transvidcloud/backend/auth/repository/UserAuthIT.java`

**Checkpoint**: User Story 1 hoàn thành 100% — hệ thống đã có thể đăng ký tài khoản độc lập và cấp phiên đăng nhập hợp lệ (MVP hoàn chỉnh).

---

## Phase 4: User Story 2 - Đăng nhập tài khoản SaaS và nhận Access Token (Priority: P1)

**Goal**: Người dùng đã đăng ký có thể đăng nhập bằng email và mật khẩu; hệ thống tìm user trên GSI `email-index`, so khớp mật khẩu BCrypt, và trả về `AuthResponse` (HTTP 200 OK) chứa JWT token.

**Independent Test**: Gửi `POST /api/auth/login` với thông tin chính xác -> nhận HTTP 200 OK và Access Token; gửi sai mật khẩu hoặc email không tồn tại -> nhận HTTP 401 Unauthorized với thông điệp chung.

### Implementation for User Story 2

- [X] T015 [US2] Mở rộng `UserRepository.java` bổ sung phương thức `findByEmail(String email)` truy vấn GSI `email-index` trên bảng `users` với điều kiện `email = :email` và lọc `itemType = "USER"` trong `backend/src/main/java/com/transvidcloud/backend/auth/repository/UserRepository.java`
- [X] T016 [US2] Mở rộng `AuthService.java` triển khai nghiệp vụ `login(LoginRequest request)`: chuẩn hóa email, tìm kiếm user qua repository, kiểm tra `passwordEncoder.matches()`, ném `AppException(ErrorCode.UNAUTHENTICATED)` nếu sai thông tin, và phát hành token qua `JwtTokenService` trong `backend/src/main/java/com/transvidcloud/backend/auth/service/AuthService.java`
- [X] T017 [US2] Mở rộng `AuthController.java` bổ sung endpoint `@PostMapping("/auth/login")` nhận `@Valid @RequestBody LoginRequest` và trả về `ResponseEntity.ok(response)` trong `backend/src/main/java/com/transvidcloud/backend/auth/controller/AuthController.java`
- [X] T018 [P] [US2] Mở rộng `AuthServiceTest.java` viết Unit Test cho luồng đăng nhập thành công, đăng nhập sai mật khẩu và đăng nhập với email không tồn tại (ném 401) trong `backend/src/test/java/com/transvidcloud/backend/auth/service/AuthServiceTest.java`
- [X] T019 [P] [US2] Mở rộng `AuthControllerTest.java` viết WebMvcTest cho endpoint `POST /api/auth/login` (thành công 200 OK, thiếu dữ liệu 400 Bad Request, sai thông tin 401 Unauthorized) trong `backend/src/test/java/com/transvidcloud/backend/auth/controller/AuthControllerTest.java`
- [X] T020 [US2] Mở rộng Integration Test `UserAuthIT.java` kiểm chứng trọn vẹn luồng Đăng ký -> Đăng nhập thành công -> Đăng nhập sai bị từ chối trên DynamoDB LocalStack trong `backend/src/test/java/com/transvidcloud/backend/auth/repository/UserAuthIT.java`

**Checkpoint**: Cả User Story 1 và User Story 2 đều hoạt động hoàn chỉnh và có thể kiểm thử độc lập.

---

## Phase 5: User Story 3 - Truy cập tài nguyên bảo vệ bằng Bearer Token và từ chối truy cập trái phép (Priority: P2)

**Goal**: Đảm bảo các endpoint bảo mật (yêu cầu `BearerAuth`) chấp nhận token do `JwtTokenService` phát hành, trích xuất đúng danh tính `userId`, và từ chối `401 Unauthorized` đối với các request thiếu token, token giả mạo hoặc hết hạn.

**Independent Test**: Gửi request kèm `Authorization: Bearer <valid_token>` tới endpoint bảo vệ -> thành công; gửi không kèm token hoặc token giả mạo -> nhận HTTP 401 Unauthorized.

### Implementation for User Story 3

- [X] T021 [US3] Bổ sung endpoint kiểm tra phiên `@GetMapping("/auth/me")` yêu cầu xác thực Bearer Token để trả về thông tin `UserSummary` của người dùng hiện tại trích xuất từ `Authentication` (`Jwt.getSubject()`) trong `backend/src/main/java/com/transvidcloud/backend/auth/controller/AuthController.java`
- [X] T022 [P] [US3] Viết Component Test `SecurityAuthFilterTest.java` kiểm tra chuỗi Security Filter: token hợp lệ được phép truy cập, thiếu token bị chặn 401, token sai chữ ký số bị chặn 401, token hết hạn bị chặn 401 trong `backend/src/test/java/com/transvidcloud/backend/auth/controller/SecurityAuthFilterTest.java`
- [X] T023 [US3] Mở rộng Integration Test `UserAuthIT.java` kiểm chứng end-to-end: Đăng nhập nhận token -> Sử dụng token gọi `GET /api/auth/me` thành công với HTTP 200 và đúng `userId` trong `backend/src/test/java/com/transvidcloud/backend/auth/repository/UserAuthIT.java`

**Checkpoint**: Toàn bộ 3 User Stories đều hoàn thiện và được kiểm chứng độc lập.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Đảm bảo chất lượng toàn diện, cập nhật tài liệu, bộ Postman collection và xác minh Zero Regression.

- [X] T024 [P] Tạo bộ kiểm thử Postman `F-003-saas-auth-jwt.postman_collection.json` với 6 requests và test scripts kiểm tra đăng ký mới, chặn trùng email, đăng nhập thành công, từ chối sai mật khẩu và truy cập `/api/auth/me` bằng token trong `backend/postman/F-003-saas-auth-jwt.postman_collection.json`
- [X] T025 [P] Cập nhật tài liệu kiến trúc bảo mật trong `docs/docs-backend/07-security-and-authorization.md` phản ánh thiết kế JWT HMAC-SHA256 và cơ chế khóa email duy nhất trên DynamoDB
- [X] T026 Thực thi lệnh kiểm chứng chính thức `./scripts/verify.sh` tại `backend/` bảo đảm Zero Regression: cả 24 tests cũ của F-001/F-002 và toàn bộ tests mới của F-003 đều PASS 100%
- [X] T027 Thực hiện xác minh thủ công các kịch bản cURL trong `specs/003-saas-auth-jwt/quickstart.md` và ghi nhận kết quả thực tế

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup & Error Codes (Phase 1)**: Không phụ thuộc — thực hiện ngay lập tức.
- **Foundational (Phase 2)**: Phụ thuộc vào Phase 1 — **BLOCKS tất cả các User Stories**.
- **User Story 1 (Phase 3 - P1 🎯 MVP)**: Phụ thuộc vào Foundational (Phase 2).
- **User Story 2 (Phase 4 - P1)**: Phụ thuộc vào Foundational (Phase 2) và tái sử dụng `UserRepository`/`AuthService` từ US1.
- **User Story 3 (Phase 5 - P2)**: Phụ thuộc vào US1 và US2 để có token kiểm thử luồng bảo vệ.
- **Polish & Verification (Phase 6)**: Phụ thuộc vào việc hoàn thành cả 3 User Stories.

### Within Each User Story

- DTOs và Entities trước Services
- Repository trước Service logic
- Service logic trước Controller endpoint
- Unit Tests song song với Controller/Service
- Integration Test `UserAuthIT` là bước kiểm chứng cuối cùng của mỗi story

### Parallel Opportunities

- **Trong Phase 2**: `T003`, `T004`, `T005`, `T006`, `T008` đều thao tác trên các tệp độc lập và có thể chạy song song.
- **Trong Phase 3**: `T009` (Repository), `T012` (Unit Test) và `T013` (WebMvcTest) có thể phân chia song song sau khi Service interface được xác định.
- **Trong Phase 4**: `T018` và `T019` có thể chạy song song.
- **Trong Phase 6**: `T024` (Postman) và `T025` (Docs) có thể chạy song song.

---

## Parallel Example: Foundational Phase

```bash
# Thực hiện đồng thời các DTO, Entity và Mapper độc lập:
Task T003: "Create RegisterRequest.java and LoginRequest.java in backend/src/main/java/com/transvidcloud/backend/auth/dto/"
Task T004: "Create UserSummary.java and AuthResponse.java in backend/src/main/java/com/transvidcloud/backend/auth/dto/"
Task T005: "Create UserItem.java in backend/src/main/java/com/transvidcloud/backend/auth/entity/UserItem.java"
Task T006: "Create UserMapper.java in backend/src/main/java/com/transvidcloud/backend/auth/mapper/UserMapper.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Hoàn thành Phase 1 (Setup) và Phase 2 (Foundational).
2. Hoàn thành Phase 3 (User Story 1 - Đăng ký tài khoản & cấp token).
3. **DỪNG VÀ KIỂM CHỨNG**: Chạy `./mvnw -B test -Dtest="*Register*,*UserAuthIT*"` để đảm bảo User Story 1 hoạt động trọn vẹn độc lập (MVP sẵn sàng).

### Incremental Delivery

1. Setup + Foundational -> Hạ tầng DTO, Entity, JwtTokenService sẵn sàng.
2. User Story 1 -> Đăng ký mới, cấp token, đảm bảo email duy nhất qua `TransactWriteItems` (MVP).
3. User Story 2 -> Đăng nhập, so khớp mật khẩu BCrypt, tra cứu GSI `email-index`.
4. User Story 3 -> Bảo vệ endpoint bằng Bearer JWT token, trích xuất `userId`.
5. Polish -> Postman collection, tài liệu hóa và verification suite (`./scripts/verify.sh`).

---

## Notes

- Toàn bộ 27 tasks đều tuân thủ định dạng bắt buộc `- [ ] [TaskID] [P?] [Story?] Description with file path`.
- Mọi ràng buộc dữ liệu từ `data-model.md` (chuẩn hóa email, mật khẩu 8-100 ký tự, `attribute_not_exists(userId)`) đều được trích dẫn cụ thể trong mô tả từng task.
- Không đánh dấu bất kỳ task nào hoàn thành nếu chưa chạy lệnh kiểm thử và đối chiếu kết quả thực tế.
