# AWS dev — Sydney

C1 gồm storage, queue, database và network. Đã bổ sung [C2 runtime IAM](../../modules/iam/README.md) cho API/worker. Region được chọn cho phiên triển khai này là `ap-southeast-2`, thay cho Singapore trong bản thiết kế ban đầu. Không thay region LocalStack chỉ để khớp cloud.

Bucket state `tvc-tfstate-975473296238-ap-southeast-2` do người vận hành tạo thủ công, bật versioning/SSE-S3 và chặn public. Root này không quản lý bucket state. Backend dùng key `dev/terraform.tfstate`, S3 lockfile và kiểm tra account ID; provider cũng giới hạn đúng account.

## Chuẩn bị và xem plan

Mở **PowerShell mới** ở root repo, không nạp `.env` local. Các biến credential/endpoint local trong shell phải được xóa để tránh dùng nhầm tài khoản hoặc LocalStack:

```powershell
foreach ($name in 'AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN', 'AWS_ENDPOINT_URL', 'AWS_ENDPOINT_URL_S3', 'AWS_ENDPOINT_URL_STS', 'S3_PRESIGN_ENDPOINT') {
    Remove-Item "Env:$name" -ErrorAction SilentlyContinue
}
$env:AWS_PROFILE = 'tvc-dev'
$env:AWS_REGION = 'ap-southeast-2'
$env:AWS_DEFAULT_REGION = 'ap-southeast-2'
aws sts get-caller-identity --profile tvc-dev --region ap-southeast-2
# Account phải là 975473296238. Nếu phiên hết hạn: aws login --profile tvc-dev --region ap-southeast-2

$dev = 'infra/terraform/envs/dev'
if (-not (Test-Path "$dev/backend.hcl")) { Copy-Item "$dev/backend.hcl.example" "$dev/backend.hcl" }
if (-not (Test-Path "$dev/terraform.tfvars")) { Copy-Item "$dev/terraform.tfvars.example" "$dev/terraform.tfvars" }
terraform -chdir=infra/terraform/envs/dev init -backend-config=backend.hcl
terraform -chdir=infra/terraform/envs/dev validate
terraform -chdir=infra/terraform/envs/dev plan -out=dev.tfplan
```

Chạy từng lệnh; dừng nếu có lỗi. Không tiếp tục nếu account khác. Nếu đăng nhập AWS CLI thành công nhưng Terraform không đọc được phiên, giữ nguyên cấu hình và kiểm tra lỗi credential trước; không chép token vào mã nguồn.

## Review và apply

Plan C1 đầu tiên tạo 31 resource; sau apply và kiểm tra đã No changes. Đợt C2 tiếp theo dự kiến chỉ thêm 8 resource IAM, không đổi/xóa C1. Không có EC2, NAT Gateway, CloudFront, secret SSM hoặc alarm trong root hiện tại. Tên bucket ứng dụng có hậu tố account + region. CORS chỉ cho `http://localhost:8000`; bổ sung origin CloudFront khi có. Module queue xuất DLQ cho observability tạo alarm ở C5.

Chỉ sau khi review plan và xác nhận contract DRAFT với App, chạy:

```powershell
terraform -chdir=infra/terraform/envs/dev apply dev.tfplan
terraform -chdir=infra/terraform/envs/dev output
```

Apply tạo tài nguyên AWS thật. Lưu outputs cho App; kiểm tra riêng trên AWS cấu hình bucket/queue/table/route trước khi nghiệm thu. Không commit `dev.tfplan`, state, `backend.hcl`, tfvars thật hoặc secret; commit `.terraform.lock.hcl`.

Chưa triển khai root demo; không dùng dev state cho demo. Bucket state không được xóa khi thu dọn môi trường ứng dụng.
