# Research: Xác thực người dùng SaaS — Register, Login & JWT Security

**Feature**: `F-003: Xác thực người dùng SaaS — Register, Login & JWT Security (Epic A2)`  
**Date**: 2026-10-31  
**Status**: Completed  

---

## Nghiên cứu 1: Đảm bảo tính duy nhất của Email trong DynamoDB bằng TransactWriteItems

### Ngữ cảnh và vấn đề
DynamoDB là NoSQL cơ sở dữ liệu khóa-giá trị / tài liệu. Bảng `users` có Partition Key là `userId` (`usr_<random>`) và GSI `email-index` (Partition Key là `email`). 
Vì GSI trong DynamoDB có tính chất eventual consistency (nhất quán cuối cùng) và DynamoDB không hỗ trợ ràng buộc duy nhất (Unique Constraint) tự động trên GSI, việc hai yêu cầu đăng ký đồng thời cùng một email có thể dẫn đến việc tạo hai tài khoản trùng email nếu chỉ kiểm tra bằng GSI query trước khi ghi.

### Quyết định kỹ thuật (Decision)
Sử dụng mẫu thiết kế **Lock Item / Unique Email Marker** trong cùng bảng `users` được thực thi nguyên tử qua **`TransactWriteItems`**:
1. Khi đăng ký, mỗi giao dịch ghi bao gồm 2 thao tác `PutItem` trong 1 giao dịch `TransactWriteItems`:
   * **Item 1 (Bản ghi tài khoản chính)**: 
     * `userId`: `usr_<nanoid/uuid>`
     * `email`: `normalizedEmail`
     * `passwordHash`: `bcryptHash`
     * `fullName`: `fullName`
     * `createdAt`: ISO-8601 UTC
     * `type`: `USER`
   * **Item 2 (Bản ghi khóa email duy nhất)**:
     * `userId`: `EMAIL#` + `normalizedEmail`
     * `email`: `normalizedEmail`
     * `targetUserId`: `usr_<nanoid/uuid>`
     * `type`: `EMAIL_LOCK`
     * Điều kiện kiểm tra: `attribute_not_exists(userId)`
2. Nếu email đã được đăng ký trước đó, Item 2 vi phạm điều kiện `attribute_not_exists(userId)`, DynamoDB lập tức hủy toàn bộ giao dịch và ném lỗi `TransactionCanceledException` với lý do `ConditionalCheckFailed`.
3. Tầng Service bắt lỗi này và ném `AppException(ErrorCode.EMAIL_ALREADY_EXISTS)` (HTTP 409 Conflict).

### Cơ sở lựa chọn (Rationale)
* Tuân thủ tuyệt đối quy định trong `contracts/dynamodb-tables.md` (mục 16: *"Email duy nhất: dùng `TransactWriteItems` với item khóa `EMAIL#<email>` + `attribute_not_exists`"*).
* Tính nguyên tử ACID 100%: Bảo đảm ngay cả khi có 100 request đăng ký cùng email cùng một mili-giây, chỉ có duy nhất 1 request thành công.
* Không cần thêm bảng phụ: Cả hai item đều nằm trong bảng `users`, tận dụng Partition Key `userId` dạng String của bảng.

### Các phương án thay thế đã đánh giá (Alternatives considered)
* **Phương án 1: Dùng GSI Query kiểm tra trước rồi mới PutItem**:
  * *Nhược điểm*: Gây race condition (Time-of-check to time-of-use - TOCTOU) khi có hai request đồng thời, vi phạm tiêu chuẩn bảo vệ tính toàn vẹn dữ liệu của dự án. -> *Bị loại bỏ*.
* **Phương án 2: Đặt `email` làm Partition Key chính của bảng `users`**:
  * *Nhược điểm*: Vi phạm hợp đồng kiến trúc trong `contracts/dynamodb-tables.md` (nơi quy định PK của `users` là `userId` và GSI là `email-index`). Ngoài ra, nếu người dùng đổi email trong tương lai thì Partition Key không thể sửa đổi được. -> *Bị loại bỏ*.

---

## Nghiên cứu 2: Phát hành và xác thực JWT Bearer Token với Nimbus Jose JWT & Spring Security 6

### Ngữ cảnh và vấn đề
`SecurityConfig.java` hiện tại đã cấu hình `NimbusJwtDecoder` với thuật toán HMAC-SHA256 dựa trên cấu hình `tvc.security.jwt.secret` (tối thiểu 32 ký tự). Hệ thống cần một dịch vụ phát hành token (`JwtTokenService`) tương thích với decoder này để tạo ra token hợp lệ cho `POST /api/auth/register` và `POST /api/auth/login`.

### Quyết định kỹ thuật (Decision)
Sử dụng thư viện `com.nimbusds:nimbus-jose-jwt` (đã có sẵn trong Spring Security OAuth2 Resource Server) để ký HMAC-SHA256:
1. **Payload Claims**:
   * `sub` (Subject): `userId` (ví dụ: `usr_a1b2c3d4e5f6`)
   * `email`: địa chỉ email của người dùng
   * `iat` (Issued At): thời điểm tạo (UTC)
   * `exp` (Expiration Time): thời điểm hết hạn (`iat + tvc.security.jwt.expiration-seconds`)
2. **Ký số**: `JWSHeader(JWSAlgorithm.HS256)` sử dụng `MACSigner` với `tvc.security.jwt.secret.getBytes(StandardCharsets.UTF_8)`.
3. **Phản hồi**: Trả về `AuthResponse`:
   * `accessToken`: Chuỗi JWT nén (`SignedJWT.serialize()`)
   * `tokenType`: `"Bearer"`
   * `expiresInSeconds`: `tvc.security.jwt.expiration-seconds` (mặc định 86400s)
   * `user`: `UserSummary` (`userId`, `email`, `fullName`, `createdAt`)

### Cơ sở lựa chọn (Rationale)
* Thư viện `nimbus-jose-jwt` đã nằm sẵn trong classpath của Spring Security 6 / Spring Boot 3.4.5, không cần thêm external dependency mới (tuân thủ nguyên tắc VIII trong Constitution).
* Hoàn toàn đồng bộ và tương thích 100% với `NimbusJwtDecoder` đã được cấu hình trong `SecurityConfig.java`.
* Hiệu năng cao, mã nguồn nhẹ và an toàn.

### Các phương án thay thế đã đánh giá (Alternatives considered)
* **Phương án 1: Thêm dependency JJWT (`io.jsonwebtoken:jjwt`)**:
  * *Nhược điểm*: Thêm dependency dư thừa trong khi Spring Security đã tích hợp sẵn Nimbus. Vi phạm nguyên tắc YAGNI và Dependency Hygiene. -> *Bị loại bỏ*.
* **Phương án 2: Tự mã hóa Base64 và HMAC thủ công**:
  * *Nhược điểm*: Dễ phát sinh lỗi bảo mật liên quan đến padding, xử lý thời gian và cấu trúc header JWT. -> *Bị loại bỏ*.

---

## Nghiên cứu 3: Quy tắc chuẩn hóa Email và phát sinh User ID

### Ngữ cảnh và vấn đề
* Email người dùng có thể chứa khoảng trắng và chữ hoa chữ thường.
* Định danh `userId` cần ngắn gọn, duy nhất và có tiền tố `usr_` theo đúng ví dụ trong `contracts/openapi.yaml` (`usr_8b31a2`).

### Quyết định kỹ thuật (Decision)
1. **Chuẩn hóa Email**:
   * Trước khi kiểm tra hoặc lưu trữ, thực hiện: `email.trim().toLowerCase(Locale.ROOT)`.
   * Kiểm tra định dạng bằng Jakarta Validation `@Email` kết hợp `@NotBlank`.
2. **Phát sinh `userId`**:
   * Định dạng: `usr_` + 12 ký tự hex ngẫu nhiên an toàn (hoặc UUID bỏ dấu gạch ngang cắt 12 ký tự), ví dụ: `usr_7f3b89a1c4e2`.
   * Đảm bảo tính duy nhất và độ dài phù hợp, khớp với các ví dụ trong OpenAPI contract.
3. **Băm mật khẩu**:
   * Dùng `BCryptPasswordEncoder` (độ mạnh mặc định 10 rounds) từ Spring Security bean đã cấu hình.

### Cơ sở lựa chọn (Rationale)
* Chuẩn hóa email ngăn ngừa triệt để các trường hợp tạo tài khoản trùng lặp do khác biệt chữ hoa/thường (`Admin@example.com` vs `admin@example.com`).
* Định dạng `userId` có tiền tố rõ ràng giúp định danh loại tài nguyên ngay trong logs và token claims mà không làm lộ thông tin cá nhân.
