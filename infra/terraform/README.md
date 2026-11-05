# infra/terraform/

```
modules/   khối tái sử dụng (mỗi thư mục một nhóm tài nguyên)
envs/      dev, demo: gọi cùng modules, chỉ khác biến cấu hình
```

## Quy ước
- Tên tài nguyên: `tvc-{env}-{resource}`; triển khai dev hiện chọn region `ap-southeast-2` (Sydney) theo người vận hành.
- Remote state trong S3 (Terraform >= 1.10 hỗ trợ khóa state bằng S3, `use_lockfile`).
- Không commit `*.tfstate`, `*.tfvars` (chỉ `*.tfvars.example`).
- Chạy: `cd envs/dev && terraform init && terraform plan`; apply sau khi review plan.
- Key schema bảng DynamoDB, cấu hình SQS phải khớp `docs/contracts/` và `local/localstack/init`.

Đã có bốn module C1 và root `envs/dev`. Làm theo [runbook dev](envs/dev/README.md) để cấu hình profile/backend, init, validate và review plan trước apply. `envs/demo` chưa triển khai.
