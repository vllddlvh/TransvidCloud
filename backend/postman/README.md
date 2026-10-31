# Postman Collections — TransvidCloud Backend

Thư mục này lưu trữ các bộ kiểm thử Postman (Postman Collections v2.1.0) cho từng Feature độc lập của Backend.

---

## 1. Quy ước Đặt tên Tập tin (Naming Convention)

Mỗi feature sau khi hoàn thành sẽ có **duy nhất 1 tập tin Postman collection** tương ứng đặt tại thư mục này theo quy tắc:

```text
{FEATURE_ID}-{feature-name-slug}.postman_collection.json
```

- Tiền tố: Mã định danh Feature ổn định (`F-001`, `F-002`, `F-003`, `F-004`...).
- Tên mô tả: Tóm tắt rõ ràng, súc tích và bao quát toàn bộ nội dung của feature đó.
- Đuôi tệp: Chuẩn xuất khẩu của Postman `.postman_collection.json`.

---

## 2. Danh mục Collections Hiện tại

| Feature ID | Tên tệp Collection | Mô tả phạm vi kiểm thử |
|---|---|---|
| `F-002` | [`F-002-common-aws-cors-error-foundation.postman_collection.json`](./F-002-common-aws-cors-error-foundation.postman_collection.json) | Kiểm tra Health check, OpenAPI docs, Preflight CORS (cho phép & chặn), Xử lý lỗi chuẩn 404, 405 và Bảo mật 401. |
| `F-003` | [`F-003-saas-auth-jwt.postman_collection.json`](./F-003-saas-auth-jwt.postman_collection.json) | Đăng ký tài khoản, đăng nhập, phát hành và thẩm định JWT Bearer Token, kiểm tra email trùng lặp và xác thực an toàn. |
| `F-004` | [`F-004-project-management.postman_collection.json`](./F-004-project-management.postman_collection.json) | Kiểm thử toàn diện 19 kịch bản cho Feature F-004: Khởi tạo Project (đầy đủ & tối giản), Validation 400, Liệt kê qua GSI `owner-index` (mới nhất lên đầu), Chi tiết 200/404, Cập nhật thông tin/gỡ webhook, Đa người thuê cô lập tài nguyên (Multi-tenant Isolation 403 Forbidden), Xóa an toàn Soft Delete (204 No Content), kiểm tra sau xóa mềm và Bảo mật không token (401). |
| `F-005` | [`F-005-apikey-dual-auth.postman_collection.json`](./F-005-apikey-dual-auth.postman_collection.json) | Kiểm thử toàn diện 25 kịch bản cho Feature F-005: Sinh API Key CSPRNG (201), lưu Hash SHA-256 vào DynamoDB, Liệt kê qua GSI `project-index` (200), Thu hồi xóa cứng (204/404), Giới hạn 5 keys/project (400), Xác thực Dual Security (X-API-Key vs Bearer JWT), Bảo vệ ranh giới Tenant (403), Chặn API Key gọi endpoint user (403), Từ chối key hỏng/đã thu hồi (401), và Ưu tiên Bearer JWT khi gửi đồng thời cả hai header. |
| `F-006` | *TBD* | Khởi tạo video, validate upload limits và cấp S3 Presigned PUT URL. |
| `F-007` | *TBD* | Xác nhận upload (HeadObject), chuyển trạng thái PENDING và điều phối SQS Job. |
| `F-008` | *TBD* | Phân trang cursor danh sách video, chi tiết video, assets và xóa video. |
| `F-009` | *TBD* | Quan sát hệ thống, structured log và E2E/Contract test luồng hoàn chỉnh. |

---

## 3. Cách Sử dụng

1. Mở **Postman** -> chọn **Import** -> chọn tệp collection tương ứng của feature cần test (ví dụ: `F-004-project-management.postman_collection.json`).
2. Biến môi trường mặc định đã được cấu hình sẵn bên trong Collection (`collectionVariables`):
   - `baseUrl`: `http://localhost:8080/api` (mặc định)
   - Các biến `tokenA`, `tokenB`, `projectId`, `projectMinimalId` sẽ được tự động trích xuất và gán qua các kịch bản test script.
3. Chạy từng request hoặc sử dụng tính năng **Run collection** để chạy tự động toàn bộ kịch bản kiểm thử kèm các bài test assertions từ đầu đến cuối.
