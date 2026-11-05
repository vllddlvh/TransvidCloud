# Data Model: Xác thực người dùng SaaS — Register, Login & JWT Security

**Feature**: `F-003: Xác thực người dùng SaaS — Register, Login & JWT Security (Epic A2)`  
**Date**: 2026-10-31  
**Status**: Completed  

---

## 1. Mô hình thực thể lưu trữ DynamoDB (Storage Schema)

Tên bảng vật lý: `tvc-{env}-users` (môi trường local: `tvc-local-users`).  
Cơ chế tính cước: On-demand (Pay Per Request).  
Partition Key: `userId` (kiểu String `S`).  
Global Secondary Index (GSI): `email-index` (Partition Key: `email` kiểu `S`, Projection: `ALL`).  

### 1.1. Bản ghi tài khoản chính (User Account Item)
*Đại diện cho thông tin tài khoản người dùng.*

| Thuộc tính (Attribute) | Kiểu dữ liệu DynamoDB | Bắt buộc | Mô tả & Ràng buộc |
|---|---|---|---|
| `userId` | `S` (String) | Có (PK) | Định danh duy nhất của người dùng, format `usr_<12-hex-chars>` (ví dụ: `usr_7f3b89a1c4e2`). |
| `email` | `S` (String) | Có (GSI PK) | Địa chỉ email đã chuẩn hóa (lowercase, trimmed). Được đánh chỉ mục qua `email-index`. |
| `passwordHash` | `S` (String) | Có | Chuỗi băm mật khẩu bằng thuật toán BCrypt (độ dài 60 ký tự, ví dụ: `$2a$10$...`). |
| `fullName` | `S` (String) | Không | Họ và tên hiển thị của người dùng (tối đa 100 ký tự). |
| `createdAt` | `S` (String) | Có | Thời điểm khởi tạo tài khoản theo chuẩn ISO-8601 UTC (ví dụ: `2026-10-31T12:00:00Z`). |
| `itemType` | `S` (String) | Có | Phân biệt loại bản ghi, giá trị cố định: `"USER"`. |

### 1.2. Bản ghi khóa email duy nhất (Email Lock Item)
*Được tạo đồng thời trong cùng giao dịch `TransactWriteItems` để đảm bảo tính duy nhất tuyệt đối của email trên toàn hệ thống.*

| Thuộc tính (Attribute) | Kiểu dữ liệu DynamoDB | Bắt buộc | Mô tả & Ràng buộc |
|---|---|---|---|
| `userId` | `S` (String) | Có (PK) | Khóa giả lập định dạng: `EMAIL#<normalized-email>` (ví dụ: `EMAIL#user@example.com`). |
| `email` | `S` (String) | Có | Địa chỉ email đã chuẩn hóa. |
| `targetUserId` | `S` (String) | Có | Tham chiếu ngược đến `userId` thực tế (`usr_...`). |
| `createdAt` | `S` (String) | Có | Thời điểm tạo khóa (ISO-8601 UTC). |
| `itemType` | `S` (String) | Có | Phân biệt loại bản ghi, giá trị cố định: `"EMAIL_LOCK"`. |

---

## 2. Giao dịch nguyên tử DynamoDB (TransactWriteItems)

Khi đăng ký tài khoản mới (`POST /api/auth/register`), ứng dụng thực thi một giao dịch đơn nhất gồm 2 thao tác ghi đồng thời:

```text
TransactWriteItems:
1. PutItem:
   - TableName: tvc-{env}-users
   - Item: User Account Item (userId = "usr_...", email, passwordHash, fullName, createdAt, itemType = "USER")
   - ConditionExpression: attribute_not_exists(userId)

2. PutItem:
   - TableName: tvc-{env}-users
   - Item: Email Lock Item (userId = "EMAIL#" + normalizedEmail, email, targetUserId, createdAt, itemType = "EMAIL_LOCK")
   - ConditionExpression: attribute_not_exists(userId)
```

* **Xử lý xung đột**: Nếu `EMAIL#<email>` đã tồn tại, thao tác 2 vi phạm điều kiện `attribute_not_exists(userId)`. DynamoDB sẽ hủy toàn bộ transaction và ném `TransactionCanceledException`. Hệ thống bắt lỗi này và phản hồi HTTP `409 Conflict` kèm mã lỗi `EMAIL_ALREADY_EXISTS`.

---

## 3. Các truy vấn truy cập dữ liệu (Access Patterns)

| Access Pattern | Thao tác DynamoDB | Điều kiện truy vấn | Mục đích sử dụng |
|---|---|---|---|
| **AP-01**: Tìm tài khoản theo email | `Query` trên GSI `email-index` | `email = :email AND itemType = 'USER'` | Đăng nhập (`POST /api/auth/login`) |
| **AP-02**: Đăng ký người dùng mới | `TransactWriteItems` | Ghi đồng thời User Item và `EMAIL#` Lock Item | Đăng ký (`POST /api/auth/register`) |
| **AP-03**: Tìm tài khoản theo userId | `GetItem` trên bảng chính | `userId = :userId` | Xác thực token, lấy thông tin cá nhân |

---

## 4. Mô hình DTO (Data Transfer Objects)

Toàn bộ DTO được cài đặt bằng Java `record` bất biến (immutable), khớp hoàn toàn với `contracts/openapi.yaml`:

### 4.1. Request DTOs
* **`RegisterRequest`**:
  * `email`: `String` — `@NotBlank`, `@Email`, tối đa 255 ký tự.
  * `password`: `String` — `@NotBlank`, `@Size(min = 8, max = 100)`.
  * `fullName`: `String` — `@Size(max = 100)`.
* **`LoginRequest`**:
  * `email`: `String` — `@NotBlank`, `@Email`.
  * `password`: `String` — `@NotBlank`.

### 4.2. Response DTOs
* **`UserSummary`**:
  * `userId`: `String` (ví dụ: `usr_7f3b89a1c4e2`)
  * `email`: `String` (ví dụ: `user@example.com`)
  * `fullName`: `String` (ví dụ: `Nguyen Van A`)
  * `createdAt`: `Instant` / `String` ISO-8601 UTC
* **`AuthResponse`**:
  * `accessToken`: `String` (JWT token)
  * `tokenType`: `String` (`"Bearer"`)
  * `expiresInSeconds`: `long` (ví dụ: `86400`)
  * `user`: `UserSummary`

---

## 5. Chuyển đổi đối tượng (MapStruct Mapper)

* **`UserMapper`**:
  * Ánh xạ giữa `UserItem` (DynamoDB entity) và `UserSummary` (API DTO).
  * Đảm bảo trường `passwordHash` không bao giờ bị map sang DTO hoặc để lộ ra ngoài.
