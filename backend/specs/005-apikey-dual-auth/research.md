# Research: Quản lý API Key & Xác thực Dual Security JWT + X-API-Key

**Feature ID**: `F-005` | **Spec**: [specs/005-apikey-dual-auth/spec.md](spec.md)

Tài liệu này tổng hợp các nghiên cứu kỹ thuật, phân tích phương án và quyết định kiến trúc nhằm phục vụ triển khai tính năng Quản lý API Key và Xác thực kép Dual Security (Bearer JWT + `X-API-Key`) cho TransvidCloud Backend.

---

## 1. Nghiên cứu Sinh chuỗi bí mật API Key & Mã băm SHA-256

### Quyết định kỹ thuật (Decision)
- **Quy cách chuỗi rawKey**: Sinh chuỗi ngẫu nhiên bảo mật cao sử dụng `java.security.SecureRandom` (CSPRNG) với độ dài 16 bytes ngẫu nhiên (128 bits entropy), chuyển đổi sang biểu diễn Hexadecimal (32 ký tự hex) và gắn tiền tố chuẩn hóa `tvc_live_` -> Tạo chuỗi `rawKey` có định dạng `tvc_live_<32_hex_chars>` (tổng chiều dài đúng 41 ký tự, ví dụ: `tvc_live_7f9a8b2c4d6e1f3a5b7c9d0e2f4a6b8c`).
- **Mã băm lưu trữ (keyHash)**: Sử dụng thuật toán băm mật mã một chiều `SHA-256` (`MessageDigest.getInstance("SHA-256")`) trên toàn bộ chuỗi `rawKey` (bao gồm cả tiền tố `tvc_live_`), sinh ra chuỗi 64 ký tự hex viết thường. Chuỗi này được dùng làm Partition Key duy nhất trong bảng DynamoDB `apikeys`.
- **Tiền tố hiển thị (keyPrefix)**: Trích xuất `tvc_live_` kết hợp với 8 ký tự hex đầu tiên của phần ngẫu nhiên và nối thêm `...` (ví dụ: `tvc_live_7f9a8b2c...`), khớp 100% với đặc tả mẫu trong `contracts/openapi.yaml`.

### Lý do lựa chọn (Rationale)
1. **Bảo mật tuyệt đối (Secure by Design)**: `SecureRandom` bảo đảm chuỗi không thể đoán trước hoặc suy luận từ các key đã sinh trước đó.
2. **Nguyên tắc không lưu bản rõ (Zero Plaintext)**: Không bao giờ lưu trữ `rawKey` trong cơ sở dữ liệu hay ghi vào logs. Khi xác thực, hệ thống chỉ cần tính `SHA-256(requestKey)` rồi truy vấn chính xác theo Partition Key `keyHash` với độ phức tạp $O(1)$.
3. **Thân thiện với người dùng và quản trị**: Tiền tố `tvc_live_` giúp lập trình viên dễ dàng nhận diện loại khóa và môi trường (live vs test), trong khi `keyPrefix` cho phép người dùng nhận diện khóa trong danh sách mà không làm lộ bí mật.

### Các phương án thay thế đã xem xét (Alternatives Considered)
- **UUID v4**: Sinh chuỗi UUID v4 (36 ký tự). *Bị từ chối* vì không có tiền tố nhận diện nền tảng `tvc_live_` và không khớp với hợp đồng `contracts/openapi.yaml`.
- **Mã hóa đối xứng (AES-256-GCM)**: Lưu trữ bản mã hóa để có thể giải mã và hiển thị lại `rawKey`. *Bị từ chối* vì vi phạm nguyên tắc bảo mật tiêu chuẩn ngành (các dịch vụ lớn như AWS, Stripe, GitHub đều không cho xem lại API Key sau khi tạo).

---

## 2. Nghiên cứu Kiến trúc Dual Security trong Spring Security Filter Chain

### Quyết định kỹ thuật (Decision)
- **Bộ lọc bảo mật kép (`ApiKeyAuthenticationFilter`)**: Tạo một filter tùy biến kế thừa `OncePerRequestFilter`, được chèn vào chuỗi `SecurityFilterChain` của Spring Security tại vị trí thích hợp (trước `BearerTokenAuthenticationFilter` hoặc `UsernamePasswordAuthenticationFilter`).
- **Thứ tự ưu tiên thẩm định (Precedence)**:
  1. Kiểm tra tiêu đề `Authorization`: Nếu tiêu đề bắt đầu bằng `Bearer `, bộ lọc API Key sẽ bỏ qua (chain tiếp tục) để `BearerTokenAuthenticationFilter` xử lý JWT của người dùng SaaS Web.
  2. Nếu không có Bearer token, kiểm tra tiêu đề `X-API-Key`:
     - Nếu có tiêu đề `X-API-Key`: trích xuất giá trị `rawKey`, kiểm tra định dạng bắt đầu bằng `tvc_live_`.
     - Tính mã băm `SHA-256` của `rawKey` và tra cứu trong DynamoDB bảng `apikeys` qua `ApiKeyRepository`.
     - Nếu tìm thấy key hợp lệ: khởi tạo đối tượng `ApiKeyAuthenticationToken` (chứa `ApiKeyPrincipal(projectId, name, keyPrefix)` và quyền `ROLE_API_KEY`), thiết lập vào `SecurityContextHolder.getContext().setAuthentication(auth)`.
     - Nếu không tìm thấy hoặc sai định dạng: không thiết lập authentication (để Spring Security trả về HTTP 401 Unauthorized theo quy chuẩn).
  3. Nếu không có cả Bearer token lẫn `X-API-Key`: để request tiếp tục đi qua; nếu endpoint yêu cầu `.authenticated()`, Spring Security sẽ kích hoạt `AuthenticationEntryPoint` trả về HTTP 401 Unauthorized.
- **Phân định quyền hạn theo Endpoint**:
  - Các endpoint quản lý cá nhân/project (`/api/projects`, `/api/projects/{projectId}/api-keys`): Chỉ cho phép `ROLE_USER` (người dùng SaaS Web xác thực bằng Bearer JWT). Nếu client dùng `X-API-Key` gọi tới sẽ bị từ chối với HTTP 403 Forbidden (`ACCESS_DENIED`).
  - Các endpoint nghiệp vụ Video (`/api/projects/{projectId}/videos`, v.v.): Cho phép cả `ROLE_USER` và `ROLE_API_KEY`.
    - Với `ROLE_API_KEY`: Kiểm tra ràng buộc `auth.getProjectId().equals(projectIdFromPath)`. Nếu không khớp, từ chối ngay với HTTP 403 Forbidden (`ACCESS_DENIED`).
    - Với `ROLE_USER`: Kiểm tra quyền sở hữu `project.ownerId == currentUserId`.

### Lý do lựa chọn (Rationale)
1. **Tuân thủ đúng quyết định làm rõ**: Đã thống nhất với người dùng ưu tiên Bearer JWT khi cả hai tiêu đề cùng xuất hiện, tránh xung đột ngữ cảnh.
2. **Không xâm lấn cấu trúc Spring Security hiện có**: Giữ nguyên cơ chế `oauth2ResourceServer(jwt)` cho JWT, bổ sung filter nhẹ cho API Key theo đúng mô hình pluggable của Spring Security.
3. **Bảo vệ ranh giới tài nguyên (Tenant Isolation)**: Đảm bảo một API Key của Project A tuyệt đối không thể thao tác trên tài nguyên của Project B.

### Các phương án thay thế đã xem xét (Alternatives Considered)
- **Gộp cả hai vào một Custom AuthenticationProvider**: Đòi hỏi thay thế toàn bộ cấu hình Resource Server JWT mặc định của Spring Boot. *Bị từ chối* vì gây phức tạp và rủi ro hồi quy (regression) với các test JWT đã viết ở F-003.

---

## 3. Nghiên cứu Truy vấn DynamoDB Bảng `apikeys` & GSI `project-index`

### Quyết định kỹ thuật (Decision)
- **Ánh xạ Entity `ApiKeyItem`**:
  - `@DynamoDbBean`
  - `@DynamoDbPartitionKey` trên trường `keyHash`.
  - `@DynamoDbSecondaryPartitionKey(indexNames = "project-index")` trên trường `projectId`.
  - Các trường dữ liệu khác: `name`, `keyPrefix`, `createdAt`.
- **Mẫu truy vấn (Query Patterns)**:
  1. *Xác thực API Key*: Truy vấn trực tiếp theo Partition Key: `getItem(Key.builder().partitionValue(keyHash).build())` -> Tốc độ cực nhanh $O(1)$, chi phí 1 RCU.
  2. *Liệt kê danh sách API Key theo Project*: Truy vấn trên GSI `project-index`:
     `index("project-index").query(QueryConditional.keyEqualTo(k -> k.partitionValue(projectId)))` -> Không sử dụng DynamoDB Scan, trả về toàn bộ key thuộc project.
  3. *Thu hồi (Xóa) API Key*:
     `deleteItem(Key.builder().partitionValue(keyHash).build())` -> Xóa vật lý bản ghi khỏi DynamoDB, key mất hiệu lực tức thì trên toàn hệ thống.

### Lý do lựa chọn (Rationale)
- Khớp 100% với cấu hình bảng DynamoDB đã tạo trong LocalStack script `local/localstack/init/01-resources.sh` và hợp đồng `contracts/dynamodb-tables.md`.
- Bảo đảm nguyên tắc cấm dùng DynamoDB `Scan` trong toàn bộ hệ thống.

---

## 4. Nghiên cứu Kiểm soát Giới hạn Tối đa 5 API Key / Project

### Quyết định kỹ thuật (Decision)
- Trước khi thực hiện thao tác tạo mới API Key trong `ApiKeyService.createApiKey(projectId, request, currentUserId)`:
  1. Kiểm tra quyền sở hữu Project của người dùng hiện tại (`projectService.getProject(projectId, currentUserId)`).
  2. Truy vấn danh sách API Key hiện tại của Project thông qua GSI `project-index`.
  3. Nếu số lượng `count >= 5`: Lập tức ném `AppException(ErrorCode.INVALID_KEY, "Maximum 5 API keys allowed per project")`.
  4. Nếu số lượng `< 5`: Tiến hành sinh key mới và lưu vào bảng `apikeys`.

### Lý do lựa chọn (Rationale)
- Đơn giản, rõ ràng, phù hợp hoàn hảo với quy mô MVP và ngưỡng giới hạn 5 key.
- Tận dụng GSI `project-index` đã có sẵn mà không cần thêm bảng đếm phụ trợ.

---

## 5. Chiến lược Kiểm chứng Thực tế (Real Verification Strategy)

Theo chỉ đạo của người dùng và quy chuẩn dự án tại `docs/testing-strategy.md`, Feature F-005 bắt buộc phải có đầy đủ các tầng kiểm thử:

1. **Unit Tests (Mockito)**:
   - `ApiKeyGeneratorTest`: Kiểm thử tính ngẫu nhiên, định dạng tiền tố `tvc_live_`, độ dài 41 ký tự, tính toán băm SHA-256 chính xác và tạo prefix hiển thị `tvc_live_xxxxxxxx...`.
   - `ApiKeyServiceTest`: Kiểm thử logic nghiệp vụ tạo key, liệt kê key, thu hồi key, chặn khi vượt quá 5 keys, kiểm tra quyền sở hữu Project, xử lý khi project không tồn tại/xóa mềm.
2. **WebMvc Slice Tests (`@WebMvcTest`)**:
   - `ApiKeyControllerTest`: Kiểm thử các endpoint `POST`, `GET`, `DELETE` với context path `/api/projects/{projectId}/api-keys`, kiểm tra validation đầu vào (tên rỗng, tên quá 100 ký tự), HTTP response codes (201, 200, 204, 400, 403, 404).
3. **Integration Test (`*IT.java` với Testcontainers LocalStack 3.0 DynamoDB)**:
   - `ApiKeyManagementIT`:
     - Khởi tạo bảng `projects` và `apikeys` trên LocalStack DynamoDB thật.
     - Kiểm thử trọn vẹn vòng đời: Tạo project -> Tạo API Key 1 (kiểm tra `rawKey`, kiểm tra DynamoDB chỉ lưu `keyHash`) -> Liệt kê qua GSI `project-index` -> Tạo liên tiếp đến 5 keys -> Thử tạo key thứ 6 (chặn 400) -> Thu hồi key -> Kiểm tra key biến mất khỏi DynamoDB -> Thử thu hồi lại (báo 404) -> Kiểm tra cách ly đa người thuê (User B không thể xem/tạo/xóa key của User A).
   - `DualSecurityAuthIT`:
     - Kiểm thử tích hợp bộ lọc bảo mật với Spring Security thật trên LocalStack:
     - Gửi request với tiêu đề `X-API-Key` hợp lệ -> Thành công HTTP 200/201.
     - Gửi request với `X-API-Key` sai hoặc đã bị thu hồi -> Bị từ chối HTTP 401 Unauthorized.
     - Gửi request với `X-API-Key` của Project A nhưng gọi URL của Project B -> Bị từ chối HTTP 403 Forbidden.
     - Gửi request đồng thời cả Bearer JWT và `X-API-Key` -> Xác thực ưu tiên theo Bearer JWT của người dùng.
     - Dùng `X-API-Key` gọi endpoint cá nhân `POST /api/projects` -> Bị từ chối HTTP 403 Forbidden.
4. **End-to-End (E2E) Test Suite**:
   - Xây dựng bộ sưu tập Postman Collection độc lập: `backend/postman/F-005-apikey-dual-auth.postman_collection.json`.
   - Bao phủ đầy đủ các kịch bản E2E:
     1. Đăng ký & Đăng nhập User A.
     2. Tạo Project A.
     3. Tạo API Key 1 cho Project A -> Lưu `rawKey`.
     4. Liệt kê API Keys của Project A -> Xác nhận có 1 key, không có `rawKey`.
     5. Gọi API với `X-API-Key` của Project A -> Thành công.
     6. Đăng ký User B -> Tạo Project B.
     7. Gọi API của Project B bằng `X-API-Key` của Project A -> Bị chặn 403.
     8. Tạo thêm 4 API Keys cho Project A (tổng cộng 5 keys) -> Thành công.
     9. Thử tạo API Key thứ 6 -> Bị chặn 400 (`Maximum 5 API keys allowed`).
     10. Thu hồi API Key 1 của Project A -> Thành công 204.
     11. Gọi lại API bằng API Key 1 vừa thu hồi -> Bị chặn 401 (`UNAUTHENTICATED`).
5. **Zero Regression Verification**:
   - Thực thi `./scripts/verify.sh` bảo đảm toàn bộ test suite tích lũy của F-001, F-002, F-003, F-004 và F-005 đều PASS 100%.
