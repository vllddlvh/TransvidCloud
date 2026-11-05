# Quickstart & Verification Guide: Quản lý Project & Phân quyền sở hữu tài nguyên

**Feature**: `F-004: Quản lý Project & Phân quyền sở hữu tài nguyên (Epic A2)`  
**Spec Reference**: [specs/004-project-management/spec.md](spec.md)  
**Contract Reference**: [specs/004-project-management/contracts/projects-api.yaml](contracts/projects-api.yaml)

Tài liệu này cung cấp các kịch bản thực thi và lệnh kiểm thử mẫu giúp kỹ sư và reviewer có thể xác minh nhanh chóng tính đúng đắn của tính năng Quản lý Project trên môi trường phát triển cục bộ.

---

## 1. Điều kiện tiên quyết & Khởi động môi trường

1. Đảm bảo hạ tầng LocalStack đang hoạt động:
   ```bash
   make infra && make smoke
   ```
2. Đảm bảo các bảng DynamoDB `tvc-local-users`, `tvc-local-projects`, `tvc-local-videos` đã được khởi tạo sẵn sàng.

---

## 2. Kiểm thử tự động (Automated Verification)

Chạy các lệnh kiểm thử trực tiếp từ thư mục `backend/`:

### 2.1. Kiểm thử biên dịch và kiểm tra kiểu
```bash
./mvnw -B test-compile
```

### 2.2. Chạy Unit Tests và Component Tests (WebMvcTest)
Kiểm tra nghiệp vụ `ProjectServiceTest`, xác thực DTO và bộ điều khiển `ProjectControllerTest`:
```bash
./mvnw -B test -Dtest="*Project*Test"
```

### 2.3. Chạy Integration Test thực tế với Testcontainers DynamoDB
Kiểm tra tương tác CRUD thực tế trên DynamoDB, GSI `owner-index`, kiểm tra quyền sở hữu và Soft Delete:
```bash
./mvnw -B verify -Dtest="ProjectManagementIT"
```

### 2.4. Xác nhận Zero Regression toàn bộ hệ thống
Bảo đảm không gây suy giảm hay làm hỏng bất kỳ tính năng nào trước đó:
```bash
./scripts/verify.sh
```

---

## 3. Kịch bản xác minh qua API (cURL / REST Client)

Khởi động ứng dụng Backend:
```bash
./mvnw spring-boot:run
```
*(Context path mặc định: `http://localhost:8080/api`)*

### Bước 1: Chuẩn bị 2 tài khoản người dùng để kiểm thử Multi-tenant Isolation

Tạo Người dùng A:
```bash
TOKEN_A=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "user.a@transvidcloud.io", "password": "Password123@", "fullName": "User Alpha"}' \
  | jq -r '.accessToken')
```

Tạo Người dùng B:
```bash
TOKEN_B=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "user.b@transvidcloud.io", "password": "Password123@", "fullName": "User Beta"}' \
  | jq -r '.accessToken')
```

---

### Bước 2: Tạo Project mới (Người dùng A)

```bash
PROJECT_RES=$(curl -s -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Khóa học React 2026",
    "description": "Kho video bài giảng lập trình React",
    "webhookUrl": "https://elearning.example.com/api/webhooks"
  }')

echo "$PROJECT_RES" | jq .
PROJECT_ID=$(echo "$PROJECT_RES" | jq -r '.projectId')
```
*Kết quả kỳ vọng*: HTTP 201 Created, trả về đối tượng Project có `projectId` định dạng `prj_...`, `name`, `ownerId` khớp với `userId` của Người dùng A.

---

### Bước 3: Liệt kê danh sách Project của Người dùng A

```bash
curl -s -X GET http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN_A" | jq .
```
*Kết quả kỳ vọng*: HTTP 200 OK, trả về mảng `items` chứa dự án vừa tạo, sắp xếp mới nhất lên đầu.

---

### Bước 4: Xem chi tiết Project (Người dùng A)

```bash
curl -s -X GET "http://localhost:8080/api/projects/$PROJECT_ID" \
  -H "Authorization: Bearer $TOKEN_A" | jq .
```
*Kết quả kỳ vọng*: HTTP 200 OK với đầy đủ thông tin dự án.

---

### Bước 5: Cập nhật thông tin Project (Người dùng A)

```bash
curl -s -X PATCH "http://localhost:8080/api/projects/$PROJECT_ID" \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Khóa học React 2026 - Nâng cao",
    "description": "Cập nhật tài liệu và bài giảng mới"
  }' | jq .
```
*Kết quả kỳ vọng*: HTTP 200 OK, tên và mô tả được cập nhật, `updatedAt` được làm mới.

---

### Bước 6: Kiểm tra bảo mật phân quyền (Người dùng B truy cập trái phép)

Người dùng B cố gắng xem chi tiết dự án của Người dùng A:
```bash
curl -i -s -X GET "http://localhost:8080/api/projects/$PROJECT_ID" \
  -H "Authorization: Bearer $TOKEN_B"
```
*Kết quả kỳ vọng*: **HTTP 403 Forbidden** kèm JSON:
```json
{
  "code": 1007,
  "message": "Access denied",
  "status": "FORBIDDEN"
}
```

Người dùng B cố gắng sửa hoặc xóa dự án của Người dùng A:
```bash
curl -i -s -X DELETE "http://localhost:8080/api/projects/$PROJECT_ID" \
  -H "Authorization: Bearer $TOKEN_B"
```
*Kết quả kỳ vọng*: **HTTP 403 Forbidden** (`ACCESS_DENIED`).

---

### Bước 7: Xóa an toàn Project (Soft Delete)

Thực hiện xóa Project bởi chính chủ sở hữu (Người dùng A):
```bash
curl -i -s -X DELETE "http://localhost:8080/api/projects/$PROJECT_ID" \
  -H "Authorization: Bearer $TOKEN_A"
```
*Kết quả kỳ vọng*: **HTTP 204 No Content**.

---

### Bước 8: Xác minh sau khi Soft Delete

1. Kiểm tra lại danh sách Project của Người dùng A:
   ```bash
   curl -s -X GET http://localhost:8080/api/projects \
     -H "Authorization: Bearer $TOKEN_A" | jq .
   ```
   *Kết quả kỳ vọng*: HTTP 200 OK, `items` không còn chứa `PROJECT_ID` đã xóa.

2. Cố gắng truy cập chi tiết Project đã xóa:
   ```bash
   curl -i -s -X GET "http://localhost:8080/api/projects/$PROJECT_ID" \
     -H "Authorization: Bearer $TOKEN_A"
   ```
   *Kết quả kỳ vọng*: **HTTP 404 Not Found** (`RESOURCE_NOT_FOUND`).
