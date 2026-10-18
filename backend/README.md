# backend/ - API (Java, Spring Boot)

**Phụ trách:** Phương (App A).

Phục vụ cả SaaS Web (JWT) và Developer API (API key). Chỉ tiếp nhận yêu cầu, ghi metadata,
cấp presigned URL và đưa job vào SQS. **Không bao giờ chạy FFmpeg** và không trung chuyển video.

## Cấu trúc dự kiến
```
src/main/java/.../
  auth/       JWT, API key, filter xác thực
  project/    CRUD project, API key, cấu hình webhook
  video/      khởi tạo video, presigned URL, confirm, truy vấn trạng thái
  job/        gửi message SQS theo contracts/sqs-job-message.schema.json
  webhook/    callback khi COMPLETED/FAILED (P1)
  config/     AWS client (endpoint override cho LocalStack), bảo mật
src/test/     unit + integration (Testcontainers / LocalStack)
Dockerfile
```

## Quy tắc
- Chỉ ghi trạng thái `UPLOADING -> PENDING`. Trạng thái xử lý thuộc về Worker.
- Đọc `AWS_ENDPOINT_URL` (nội bộ) và `S3_PRESIGN_ENDPOINT` (URL cho trình duyệt) từ biến môi trường.
- API phải khớp `contracts/openapi.yaml`. Thay đổi API = sửa contract trước.
- Dùng AWS SDK v2 (DynamoDB Enhanced Client). Không thêm CSDL khác (xem `docs/adr/0001-dynamodb-only.md`).
- Giới hạn upload: kiểm tra `sizeBytes` khi khai báo và HeadObject ở `confirm` (xem `contracts/upload-limits.md`).
- Cấu hình CORS qua biến môi trường (chỉ cần khi frontend và API khác origin).
- Dockerfile do Lưu viết, bạn cung cấp: lệnh chạy, cổng, biến môi trường.
