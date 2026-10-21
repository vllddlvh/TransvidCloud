# 5. API Design & Contracts

## Nguồn sự thật

[`../contracts/openapi.yaml`](../contracts/openapi.yaml) là nguồn định nghĩa REST API. OpenAPI hiện ghi rõ là **DRAFT**, chỉ liệt kê khung endpoint, chưa đủ request/response schema, security scheme và error model.

Các endpoint hiện có trong draft:

| Method | Path | Mục đích |
|---|---|---|
| `POST` | `/auth/register` | Đăng ký |
| `POST` | `/auth/login` | Đăng nhập, cấp JWT |
| `GET` | `/projects` | Liệt kê project của user |
| `POST` | `/projects` | Tạo project |
| `POST` | `/projects/{projectId}/api-keys` | Tạo API key |
| `GET` | `/projects/{projectId}/videos` | Liệt kê video theo cursor |
| `POST` | `/projects/{projectId}/videos` | Khởi tạo video và presigned URL |
| `POST` | `/videos/{videoId}/confirm` | Xác nhận upload |
| `GET` | `/videos/{videoId}` | Chi tiết, trạng thái và assets |

Không tự thêm endpoint hoặc tự chốt body/status code khi OpenAPI chưa định nghĩa. Nếu cần API mới, cập nhật contract và review theo quy trình trong [`../contracts/README.md`](../contracts/README.md).

## Quy ước cần hoàn thiện trong OpenAPI

- Request/response schema và field bắt buộc.
- JWT/API key security scheme và security theo endpoint.
- Error response, validation và status code.
- Cursor format và hành vi khi cursor không hợp lệ.
- Idempotency của thao tác confirm và cách biểu diễn lỗi.

## Quy trình thay đổi

1. Sửa contract trước hoặc cùng PR với code thích ứng.
2. Khi đổi API/message có ảnh hưởng thành phần khác, báo các bên liên quan review.
3. Không để Frontend/Worker dựa vào response chưa được ghi trong contract.
4. Thêm/cập nhật test để xác nhận implementation khớp contract.
