# local/localstack/

Script trong `init/` được LocalStack chạy tự động mỗi lần khởi động (`ready.d`).
Dữ liệu **không được lưu** giữa các lần chạy: `make reset` luôn cho ra trạng thái sạch.

- `init/01-resources.sh`: tạo 3 bucket, queue + DLQ (visibility 300s, long polling 20s, `maxReceiveCount=3`), 4 bảng DynamoDB.
- `init/cors.json`: CORS cho bucket input để trình duyệt PUT bằng presigned URL.

Sửa key schema ở đây thì **phải sửa `docs/contracts/dynamodb-tables.md` và Terraform** (module `database`) cùng PR.
