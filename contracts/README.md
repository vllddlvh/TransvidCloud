# contracts/ - Hợp đồng tích hợp (nguồn sự thật duy nhất)

**Phụ trách:** Phương (chủ trì) - cả 4 người review. Đây là thư mục quan trọng nhất ở Sprint 1.

Backend, Worker, Frontend, Terraform và script local **đều bám vào đây**. Khi code và contract
lệch nhau, contract đúng, code sai (hoặc phải sửa contract trong cùng PR).

| File | Nội dung | Ai dùng |
|---|---|---|
| `openapi.yaml` | REST API cho SaaS Web và Developer API | Backend, Frontend |
| `sqs-job-message.schema.json` | Cấu trúc message job gửi qua SQS | Backend (gửi), Worker (nhận) |
| `video-state-machine.md` | Các trạng thái video và ai được phép chuyển | Backend, Worker, Frontend |
| `dynamodb-tables.md` | Key schema, GSI, truy vấn, ví dụ item | Backend, Worker, Terraform, local init |
| `s3-key-convention.md` | Quy ước đặt tên key input/output | Backend, Worker, CloudFront |
| `upload-limits.md` | Giới hạn upload cho MVP và nơi kiểm tra từng giới hạn | Backend, Worker, Frontend, Terraform |

## Quy trình sửa contract
1. Mở PR chỉ sửa `contracts/` (hoặc kèm code thích ứng), gắn nhãn `contract-change`.
2. Cần **duyệt của cả bên App và bên Cloud** (CODEOWNERS).
3. Ghi `schemaVersion` mới nếu thay đổi message SQS không tương thích ngược.
4. Báo trong nhóm chat trước khi merge.

Các file hiện là **DRAFT** để chốt trong ngày 1-2 của Sprint 1.
