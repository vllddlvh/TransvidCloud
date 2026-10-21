# 9. Development Guide

## Yêu cầu

- Java 17 theo tech stack dự kiến.
- Build tool và lệnh chạy phải theo `backend/` thực tế; không đoán tên task Maven/Gradle.
- AWS SDK/cấu hình endpoint được quản lý qua cấu hình, không hard-code trong business logic.

## Local setup

Root README mô tả quy trình nền tảng:

```bash
cp .env.example .env
# Điền các giá trị local cần thiết theo tài liệu repo
make help
make infra
make smoke
```

Xem root [`../README.md`](../README.md) và tài liệu triển khai local trong `../docs/` để biết đầy đủ cấu hình Docker/LocalStack. Sau khi infrastructure local sẵn sàng, khởi chạy Backend bằng lệnh được định nghĩa trong project/backend docs.

## Local và cloud

- Dùng cùng code, khác cấu hình.
- Theo root README, `AWS_ENDPOINT_URL` có giá trị thì trỏ tới LocalStack; để trống khi dùng AWS thật.
- Không dùng credentials thật trong test local nếu không cần thiết.
- Không commit `.env`, credentials, `*.tfvars` hoặc secret.

## Khi thay đổi Backend

1. Xác định use case và contract liên quan.
2. Thực hiện thay đổi nhỏ theo module.
3. Chạy các test phù hợp và contract/e2e test khi liên quan.
4. Cập nhật tài liệu nếu thay đổi behavior, dependency hoặc cách chạy.
5. Nếu thay đổi API/SQS/state/DynamoDB key/S3 key/upload limit, xem quy trình thay đổi trong `contracts/README.md`.
