# Quickstart: Hướng dẫn Xác minh Feature F-005

**Feature ID**: `F-005` | **Spec**: [specs/005-apikey-dual-auth/spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Tài liệu này cung cấp hướng dẫn từng bước để kiểm chứng tính đúng đắn của tính năng Quản lý API Key & Xác thực Dual Security (JWT + `X-API-Key`) trên cả hai môi trường: Kiểm thử tự động hóa (Automated Tests) và Xác minh thủ công qua API (Manual API Scenarios).

---

## 1. Yêu cầu tiên quyết (Prerequisites)

- Java 21 LTS cài đặt sẵn (`java -version`).
- Docker daemon đang hoạt động (để chạy Testcontainers LocalStack 3.0 cho Integration Tests).
- Maven Wrapper `./mvnw` có quyền thực thi.

---

## 2. Kiểm chứng tự động (Automated Verification)

### 2.1 Chạy toàn bộ Unit Tests của module API Key
Kiểm thử bộ sinh khóa CSPRNG, thuật toán băm SHA-256, logic nghiệp vụ tạo/liệt kê/thu hồi và kiểm tra giới hạn 5 keys:
```bash
./mvnw -B test -Dtest="ApiKeyGeneratorTest,ApiKeyServiceTest"
```
**Kỳ vọng**: BUILD SUCCESS, tất cả các bài test đạt PASS 100%.

### 2.2 Chạy WebMvc Slice Tests cho API Controller
Kiểm thử tầng REST Controller, serialization/deserialization DTOs, validation và mã phản hồi HTTP:
```bash
./mvnw -B test -Dtest="ApiKeyControllerTest"
```
**Kỳ vọng**: BUILD SUCCESS, 100% tests pass.

### 2.3 Chạy Real Verification Integration Tests (LocalStack 3.0 DynamoDB)
Kiểm thử tương tác CSDL thực tế với bảng `projects` và `apikeys` trên LocalStack DynamoDB, kiểm tra GSI `project-index`, xác thực Dual Security và phân quyền sở hữu:
```bash
# Kiểm thử CRUD API Key, GSI project-index, giới hạn 5 keys, cô lập tenant
./mvnw -B test -Dtest="ApiKeyManagementIT"

# Kiểm thử bộ lọc xác thực Dual Security (X-API-Key vs Bearer JWT)
./mvnw -B test -Dtest="DualSecurityAuthIT"
```
**Kỳ vọng**: Testcontainers khởi động LocalStack container, chạy thành công 100% các ca kiểm thử tích hợp thực tế (Real Verification).

### 2.4 Chạy Lệnh Kiểm chuẩn Toàn diện & Zero Regression
Bảo đảm tính năng mới F-005 không làm ảnh hưởng bất kỳ tính năng nào trước đó:
```bash
./scripts/verify.sh
```
**Kỳ vọng**: 
- `scripts/check-setup.sh` -> PASS.
- `./mvnw -B clean verify` -> BUILD SUCCESS, toàn bộ 100% tests của F-001, F-002, F-003, F-004 và F-005 đều PASS.

---

## 3. Kịch bản Xác minh Thủ công qua cURL (Manual cURL Scenarios)

Giả định ứng dụng Backend đang chạy tại `http://localhost:8080/api` và LocalStack đang chạy (`make infra`).

### Bước 1: Đăng nhập lấy Bearer JWT Token
```bash
LOGIN_RES=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"owner@transvidcloud.io","password":"StrongPassword123@"}')

TOKEN=$(echo $LOGIN_RES | grep -o '"token":"[^"]*' | cut -d'"' -f4)
echo "JWT Token: $TOKEN"
```

### Bước 2: Tạo một Project mới
```bash
PROJECT_RES=$(curl -s -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Transvid Production"}')

PROJECT_ID=$(echo $PROJECT_RES | grep -o '"projectId":"[^"]*' | cut -d'"' -f4)
echo "Project ID: $PROJECT_ID"
```

### Bước 3: Tạo API Key 1 cho Project
```bash
KEY_RES=$(curl -s -i -X POST http://localhost:8080/api/projects/$PROJECT_ID/api-keys \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Server CI/CD Key"}')

echo "$KEY_RES"
# Kỳ vọng: HTTP/1.1 201 Created
# Nhận được rawKey có dạng tvc_live_... và keyPrefix tvc_live_xxxxxxxx...
RAW_KEY=$(echo "$KEY_RES" | grep -o '"rawKey":"[^"]*' | cut -d'"' -f4)
KEY_HASH=$(echo "$KEY_RES" | grep -o '"keyHash":"[^"]*' | cut -d'"' -f4)
echo "Raw API Key: $RAW_KEY"
echo "Key Hash: $KEY_HASH"
```

### Bước 4: Liệt kê danh sách API Key
```bash
curl -s -i -X GET http://localhost:8080/api/projects/$PROJECT_ID/api-keys \
  -H "Authorization: Bearer $TOKEN"

# Kỳ vọng: HTTP/1.1 200 OK
# Danh sách có 1 phần tử chứa keyHash, keyPrefix, tuyệt đối KHÔNG có rawKey
```

### Bước 5: Gọi API bằng X-API-Key (Dual Security Test)
```bash
curl -s -i -X GET http://localhost:8080/api/projects/$PROJECT_ID/videos \
  -H "X-API-Key: $RAW_KEY"

# Kỳ vọng: HTTP/1.1 200 OK (xác thực thành công qua API Key)
```

### Bước 6: Gọi API chéo Project bằng X-API-Key (Kiểm tra cách ly Tenant)
```bash
curl -s -i -X GET http://localhost:8080/api/projects/prj_another_project/videos \
  -H "X-API-Key: $RAW_KEY"

# Kỳ vọng: HTTP/1.1 403 Forbidden (ACCESS_DENIED)
```

### Bước 7: Thu hồi (Revoke) API Key
```bash
curl -s -i -X DELETE http://localhost:8080/api/projects/$PROJECT_ID/api-keys/$KEY_HASH \
  -H "Authorization: Bearer $TOKEN"

# Kỳ vọng: HTTP/1.1 204 No Content
```

### Bước 8: Thử gọi lại API bằng API Key vừa thu hồi
```bash
curl -s -i -X GET http://localhost:8080/api/projects/$PROJECT_ID/videos \
  -H "X-API-Key: $RAW_KEY"

# Kỳ vọng: HTTP/1.1 401 Unauthorized (UNAUTHENTICATED)
```

---

## 4. Kiểm chứng E2E với Postman Collection Runner

Bộ sưu tập Postman độc lập:
`backend/postman/F-005-apikey-dual-auth.postman_collection.json`

Có thể chạy tự động bằng Newman CLI:
```bash
newman run backend/postman/F-005-apikey-dual-auth.postman_collection.json \
  --env-var "baseUrl=http://localhost:8080/api"
```
Bao phủ 100% các kịch bản thành công và ngoại lệ (vượt 5 keys, 403 gọi chéo, 401 sau thu hồi).
