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
- **Runbook chi tiết:** Xem [5. Cloud-Deployment-Runbook.md](../../docs/docs-cloud/5.%20Cloud-Deployment-Runbook.md) để biết các bước thao tác, phân công và kiểm soát FinOps.
