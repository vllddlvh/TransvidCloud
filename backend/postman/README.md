# Postman Collections — TransvidCloud Backend

Thư mục này lưu trữ các bộ kiểm thử Postman (Postman Collections v2.1) cho từng Feature của Backend.

---

## 1. Quy ước Đặt tên Tập tin (Naming Convention)

Mỗi feature sau khi hoàn thành sẽ có **duy nhất 1 tập tin Postman collection** tương ứng đặt tại thư mục này theo quy tắc:

```text
{FEATURE_ID}-{feature-name-slug}.postman_collection.json
```

- Tiền tố: Mã định danh Feature ổn định (`F-001`, `F-002`, `F-003`, ...).
- Tên mô tả: Tóm tắt rõ ràng, súc tích và bao quát toàn bộ nội dung của feature đó.
- Đuôi tệp: Chuẩn xuất khẩu của Postman `.postman_collection.json`.

---

## 2. Danh mục Collections Hiện tại

| Feature ID | Tên tệp Collection | Mô tả phạm vi kiểm thử |
|---|---|---|
| `F-002` | [`F-002-common-aws-cors-error-foundation.postman_collection.json`](./F-002-common-aws-cors-error-foundation.postman_collection.json) | Kiểm tra Health check, OpenAPI docs, Preflight CORS (cho phép & chặn), Xử lý lỗi chuẩn 404, 405 và Bảo mật 401. |
| `F-003` | *TBD (sẽ tạo khi hoàn thành F-003)* | Đăng ký tài khoản, đăng nhập, phát hành và thẩm định JWT Bearer Token. |
| `F-004` | *TBD* | CRUD Project và kiểm tra phân quyền sở hữu tài nguyên (ownership isolation). |
| `F-005` | *TBD* | Sinh, liệt kê, thu hồi API Key và xác thực kép (Dual Auth). |
| `F-006` | *TBD* | Khởi tạo video, validate upload limits và cấp S3 Presigned PUT URL. |
| `F-007` | *TBD* | Xác nhận upload (HeadObject), chuyển trạng thái PENDING và điều phối SQS Job. |
| `F-008` | *TBD* | Phân trang cursor danh sách video, chi tiết video, assets và xóa video. |
| `F-009` | *TBD* | Quan sát hệ thống, structured log và E2E/Contract test luồng hoàn chỉnh. |

---

## 3. Cách Sử dụng

1. Mở **Postman** -> chọn **Import** -> chọn tệp collection tương ứng cần test.
2. Kiểm tra/chỉnh sửa biến môi trường trong Collection:
   - `baseUrl`: `http://localhost:8080/api` (mặc định)
   - `frontendOrigin`: `http://localhost:5173`
3. Chạy từng request hoặc sử dụng **Run collection** để chạy tự động toàn bộ kịch bản kiểm thử kèm các bài test assertions.
