# scripts/ - Script triển khai dùng chung

**Phụ trách:** Nguyên (cloud/vận hành).

Được GitHub Actions gọi (và chạy tay được khi cần).

| Script | Mục đích |
|---|---|
| `deploy-api.sh` | Triển khai image API mới lên EC2 qua SSM Run Command |
| `bump-worker-tag.sh` | Ghi tag image Worker vào SSM Parameter Store, kích hoạt ASG Instance Refresh |

Yêu cầu: AWS CLI đã đăng nhập (OIDC trên CI, hoặc profile cá nhân khi chạy tay).
