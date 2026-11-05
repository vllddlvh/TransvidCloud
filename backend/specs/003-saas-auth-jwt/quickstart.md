# Quickstart & Verification Guide: Xác thực người dùng SaaS — Register, Login & JWT Security

**Feature**: `F-003: Xác thực người dùng SaaS — Register, Login & JWT Security (Epic A2)`  
**Date**: 2026-10-31  
**Status**: Ready  

---

## 1. Điều kiện tiên quyết (Prerequisites)

* Máy chủ đã cài đặt Java 21 LTS (`java -version`) và Docker (`docker ps`).
* Hạ tầng LocalStack đang chạy và các tài nguyên đã được khởi tạo:
  ```bash
  (cd .. && make infra && make smoke)
  ```
* Bảng DynamoDB `tvc-local-users` đã sẵn sàng với index `email-index`.

---

## 2. Kịch bản kiểm chứng tự động (Automated Test Execution)

### 2.1. Chạy toàn bộ Unit Tests của Auth
Kiểm tra nghiệp vụ hash mật khẩu, validation, xử lý lỗi và phát hành JWT token:
```bash
./mvnw -B test -Dtest="*Auth*,*Jwt*,*User*"
```
*Kết quả kỳ vọng*: 100% tests PASS, bao gồm các ca kiểm thử:
- Đăng ký thành công và phát sinh token.
- Đăng nhập thành công với mật khẩu đúng.
- Từ chối đăng nhập khi sai mật khẩu hoặc sai email.
- Validation dữ liệu đầu vào (email sai định dạng, mật khẩu < 8 ký tự).

### 2.2. Chạy Integration Tests với LocalStack Testcontainers
Kiểm tra giao dịch `TransactWriteItems` đảm bảo tính duy nhất của email trên DynamoDB thực tế:
```bash
./mvnw -B test -Dtest="UserAuthIT"
```
*Kết quả kỳ vọng*:
- Ghi thành công cặp User Item + Email Lock Item.
- Thử đăng ký đồng thời 2 bản ghi cùng email: chính xác 1 bản ghi thành công, bản ghi thứ 2 nhận `TransactionCanceledException` và ánh xạ thành HTTP `409 Conflict`.
- Query GSI `email-index` tìm đúng User Item theo email.

### 2.3. Chạy toàn bộ bộ kiểm thử Verify chính thức (Zero Regression)
```bash
./scripts/verify.sh
```
*Kết quả kỳ vọng*: Cả `./scripts/check-setup.sh` và `./mvnw -B clean verify` đều đạt PASS (exit code 0).

---

## 3. Kịch bản kiểm chứng thủ công qua HTTP API (Manual cURL Verification)

Khởi động ứng dụng Backend:
```bash
./mvnw spring-boot:run
```

### 3.1. Kịch bản 1: Đăng ký tài khoản mới thành công (HTTP 201 Created)
```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "tester@example.com",
    "password": "Password123!",
    "fullName": "Test User"
  }'
```
*Kết quả kỳ vọng*: HTTP 201 Created, body chứa:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 86400,
  "user": {
    "userId": "usr_...",
    "email": "tester@example.com",
    "fullName": "Test User",
    "createdAt": "..."
  }
}
```

### 3.2. Kịch bản 2: Đăng ký trùng lặp email (HTTP 409 Conflict)
Thực hiện lại lệnh cURL trên:
```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "tester@example.com",
    "password": "DifferentPassword123!",
    "fullName": "Another Name"
  }'
```
*Kết quả kỳ vọng*: HTTP 409 Conflict, body chứa:
```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "Địa chỉ email đã được sử dụng trên hệ thống",
  "timestamp": "..."
}
```

### 3.3. Kịch bản 3: Đăng nhập tài khoản thành công (HTTP 200 OK)
```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "tester@example.com",
    "password": "Password123!"
  }'
```
*Kết quả kỳ vọng*: HTTP 200 OK, trả về `accessToken` hợp lệ và `user` profile.

### 3.4. Kịch bản 4: Đăng nhập sai mật khẩu (HTTP 401 Unauthorized)
```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "tester@example.com",
    "password": "WrongPassword999!"
  }'
```
*Kết quả kỳ vọng*: HTTP 401 Unauthorized:
```json
{
  "code": "UNAUTHENTICATED",
  "message": "Email hoặc mật khẩu không chính xác",
  "timestamp": "..."
}
```

### 3.5. Kịch bản 5: Truy cập API được bảo vệ bằng Bearer Token
```bash
# Giả sử TOKEN lấy từ bước đăng nhập/đăng ký
TOKEN="<accessToken_từ_bước_trước>"

# Thử gọi API yêu cầu bảo mật (ví dụ GET /api/projects)
curl -i -X GET http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN"
```
*Kết quả kỳ vọng*: Không bị chặn 401/403 tại Security Filter.
