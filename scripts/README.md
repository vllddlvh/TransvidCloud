# scripts/ - Script triển khai dùng chung

**Phụ trách:** Nguyên (cloud/vận hành).

Được GitHub Actions gọi (và chạy tay được khi cần).

| Script | Mục đích |
|---|---|
| `deploy-api.sh` | Triển khai image API mới lên EC2 qua SSM Run Command |
| `bump-worker-tag.sh` | Ghi tag image Worker vào SSM Parameter Store, kích hoạt ASG Instance Refresh |
| `cloud-dev.ps1` | Kiểm tra, bật hoặc hạ tải AWS dev trên Windows; dừng worker qua SSM trước khi gỡ protection |

Yêu cầu: AWS CLI đã đăng nhập (OIDC trên CI, hoặc profile cá nhân khi chạy tay).

PowerShell: `./scripts/cloud-dev.ps1 -Action status` (hoặc `start`, `stop`). Khi không có access key trong môi trường và không truyền `-Profile`, script tự đọc `tvc-dev_accessKeys.csv` tại thư mục gốc repo, kể cả khi chạy từ thư mục khác; cách này tránh dùng phiên đăng nhập profile đã hết hạn. Nếu không có CSV, script dùng profile mặc định `tvc-dev`. Có thể truyền `-CredentialCsv <đường-dẫn>` để chọn file cụ thể; tùy chọn này được ưu tiên hơn credentials hiện có. Truyền `-Profile <tên>` để dùng profile thay vì tự chọn CSV. Key chỉ được nạp vào biến môi trường của tiến trình, không ghi vào cấu hình AWS. `stop` từ chối nếu queue còn job và không xóa bucket/dữ liệu.

`deploy-api.sh` cập nhật image tag trong SSM, gọi `systemctl restart tvc.service` trên API và đợi health check từ bootstrap; khôi phục tag trước nếu deploy lỗi. Bootstrap đọc JWT/tunnel token trực tiếp từ SSM.

Khi API đang dừng, script chỉ lưu tag cho lần bật máy sau. `bump-worker-tag.sh` cũng chỉ lưu tag nếu ASG không có instance. Khi có worker đang chạy, script dừng container graceful qua SSM, đợi xác nhận thành công rồi mới gỡ protection và theo dõi Instance Refresh; drain lỗi thì giữ protection và báo thất bại. Pipeline không tự bật compute đang hạ tải.

CI tự gọi Deploy khi push `nguyen-1` và tất cả kiểm tra PASS; OIDC AWS chỉ cho phép đúng nhánh này. Không deploy từ pull request hoặc `main`. Các biến Actions dùng giá trị trong tài liệu tiến độ; không lưu AWS key tĩnh lên GitHub.

Smoke AWS: `node tests/e2e/cloud-smoke.mjs https://<cloudfront-domain>`. Khi chẩn đoán từ EC2, đặt `E2E_API_BASE_URL=http://127.0.0.1:8080` để kiểm tra API nội bộ; kết quả này chưa chứng minh đường API qua Cloudflare/CloudFront. Test tạo tài khoản/project/video riêng và giữ lại để kiểm tra playback.
