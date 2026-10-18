# infra/terraform/

```
modules/   khối tái sử dụng (mỗi thư mục một nhóm tài nguyên)
envs/      dev, demo: gọi cùng modules, chỉ khác biến cấu hình
```

## Quy ước
- Tên tài nguyên: `tvc-{env}-{resource}`; region `ap-southeast-1`.
- Remote state trong S3 (Terraform >= 1.10 hỗ trợ khóa state bằng S3, `use_lockfile`).
- Không commit `*.tfstate`, `*.tfvars` (chỉ `*.tfvars.example`).
- Chạy: `cd envs/dev && terraform init && terraform plan`; apply sau khi review plan.
- Key schema bảng DynamoDB, cấu hình SQS phải khớp `contracts/` và `local/localstack/init`.
