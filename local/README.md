# local/ - Môi trường chạy thử trên máy

**Phụ trách:** Cloud (Lưu chủ trì, Nguyên review) - epic C0.

Chứa mọi thứ chỉ phục vụ chạy local; **không bao giờ được deploy lên AWS**.

| Thư mục | Nội dung |
|---|---|
| `localstack/init/` | Script tạo S3, SQS+DLQ, DynamoDB trên LocalStack khi khởi động |
| `nginx/` | Cấu hình nginx giả lập CloudFront (1 domain: `/`, `/api/*`, `/media/*`) |
| `scripts/` | `smoke-test.sh`, `seed.sh`, `chaos-kill-worker.sh` |

Quy tắc: tên tài nguyên, key schema, cấu hình queue ở đây phải **khớp `contracts/`** và Terraform.
Hướng dẫn chạy: xem `docs/LOCAL_AND_CICD.md`.
