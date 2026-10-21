# Cloud — Hạ tầng và triển khai TransVidCloud

Bộ tài liệu dành cho Lưu và Nguyên, mô tả đường đi từ môi trường local đến AWS của nền tảng xử lý video. Nhóm App dùng bộ này để biết Cloud cung cấp gì, khi nào tích hợp và cách nghiệm thu.

| Thứ tự | Tài liệu | Nội dung |
|---|---|---|
| 1 | [Introduction](1.%20Introduction.md) | Mục tiêu, phạm vi, đầu việc C0–C6, phân công và bàn giao |
| 2 | [Timelines](2.%20Timelines.md) | Thời gian triển khai, phụ thuộc, mốc M0–M5 và phương án giảm phạm vi |
| 3 | [Designs](3.%20Designs.md) | Kiến trúc local/AWS, dữ liệu, mạng, IAM, edge, scaling và vận hành |
| 4 | [Implement](4.%20Implement.md) | Các bước dựng local, kiểm thử, đưa lên AWS, rollback và thu dọn |

## Cách đọc và nguồn đối chiếu

- [Tổng quan dự án](../../README.md) và [SRS](../introduction/3.%20SRS%20highlevel.md): bài toán SaaS + Developer API dùng chung một pipeline video.
- [Timeline và phân công v4](../introduction/1.%20Timelines.md): căn cứ cho người phụ trách, phạm vi MVP, P0/P1 và M0–M5.
- [Local và CI/CD](../introduction/2.%20LOCAL_AND_CICD.md): định hướng triển khai, được cụ thể hóa theo trạng thái repo trong Implement.
- [ADR 0001 — DynamoDB only](../docs-common/5.%20Architecture%20decisions.md): quyết định đã chấp nhận về DynamoDB. Bản kiến trúc viết riêng giai đoạn đầu đã rút khỏi repo; tham chiếu lịch sử qua các mục ⛔ trong [SRS](../introduction/3.%20SRS%20highlevel.md) và git history.
- [Contracts](../contracts/README.md): nguồn đối chiếu API, SQS message, trạng thái video, S3 key, bảng và giới hạn upload. Các contract còn ghi DRAFT cần review chung trước M1.

Khi các nguồn khác nhau, dùng timeline v4 cho kế hoạch MVP, ADR đã chấp nhận cho quyết định kiến trúc, contract cho giao diện tích hợp, và file thực thi trong repo để mô tả khả năng chạy hiện tại. Điểm chưa thống nhất phải được ghi lại và xử lý cùng App, không tự đổi contract trong tài liệu Cloud.

## Những điều đã thống nhất cho bộ tài liệu này

| Chủ đề | Cách áp dụng |
|---|---|
| Phân công | Lưu: local, dữ liệu/mạng, harness E2E/benchmark; Nguyên: IAM, compute, edge, quan sát/scaling và CI/CD |
| Database | Chỉ DynamoDB on-demand; không RDS/PostgreSQL |
| Upload | 209715200 byte, 300 giây, chiều cao tối đa 1080; mp4/mov/mkv/webm; output MVP 360p/720p |
| Đường API | CloudFront `/api/*` → Cloudflare Tunnel → EC2 API; frontend/HLS qua CloudFront và S3 private |
| Worker | Spot cho dev/demo; min 0 khi đã kiểm chứng đánh thức từ 0, min 1 khi demo; benchmark on-demand cố định |
| IaC và tự động hóa | Bốn module `storage`, `queue`, `database`, `network` là P0; C6 hoàn thiện CI/CD và Terraform còn lại là P1 |
| Lịch | Hiện tại tuần 7, học kỳ 10 tuần; Sprint 1 ở tuần 7, Sprint 2 cuối tuần 7–8, Sprint 3/4 trong tuần 9; hồ sơ sẵn sàng đầu tuần 10 |

## Trạng thái tài liệu và repo

Rà soát ngày **21/10/2026**: đã có Compose, script khởi tạo/smoke LocalStack, nginx, Makefile và khung workflow/script deploy. Các thư mục ứng dụng và Terraform chưa có mã triển khai; E2E và chaos còn là khung. Vì vậy các checklist dưới đây là **kế hoạch nghiệm thu**, không phải xác nhận đã chạy thành công trên local hoặc AWS.

Khi hoàn thành một mốc, cập nhật bằng chứng (commit, môi trường, log/kết quả test và người kiểm tra). Quyết định kiến trúc mới được ghi theo [quy ước ADR](../docs-common/5.%20Architecture%20decisions.md).
