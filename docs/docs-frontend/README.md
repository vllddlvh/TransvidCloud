# docs-frontend — Tài liệu frontend SaaS Web

Tài liệu cho `frontend/` — giao diện SaaS Web của TransVidCloud (React + Vite), phụ trách: **Vũ** (Lưu hỗ trợ hosting lên S3/CloudFront).

## Mục lục

| # | Tài liệu | Nội dung |
|---|---|---|
| 1 | [1. Introduction.md](1.%20Introduction.md) | Phạm vi, tech stack, cấu trúc thư mục, nguyên tắc làm việc |
| 2 | [2. Timelines.md](2.%20Timelines.md) | Epic A6 bóc nhỏ thành task theo sprint, mốc và thứ tự cắt giảm |
| 3 | [3. Pages & Features.md](3.%20Pages%20%26%20Features.md) | Màn hình và luồng người dùng: đăng nhập, project, upload, trạng thái video, trình phát HLS |
| 4 | [4. Design System.md](4.%20Design%20System.md) | Hệ thiết kế áp dụng từ template YouTube-style (token màu, chữ, khoảng cách, component) |
| 5 | 5. API Integration.md | *(đang để trống — chờ backend chốt API contract trước khi viết)* |

## Tài liệu liên quan

- **Template thiết kế:** [`Frontend template/`](Frontend%20template/) — bản tham chiếu YouTube-style: `DESIGN.md` (token, component, do's & don'ts), `theme.css`, `variables.css`, `tokens.json`. Đọc `4. Design System.md` để biết cách áp dụng vào app.
- **Hợp đồng tích hợp:** [`docs/contracts/`](../contracts/) — nguồn sự thật cho API (`openapi.yaml`, đang DRAFT), trạng thái video (`video-state-machine.md`), giới hạn upload (`upload-limits.md`).
- **Kế hoạch chung:** [`docs/introduction/1. Timelines.md`](../introduction/1.%20Timelines.md) — epic, phân công và mốc M0–M5 của cả nhóm.
- **Chạy local:** [`docs/introduction/2. LOCAL_AND_CICD.md`](../introduction/2.%20LOCAL_AND_CICD.md) — chạy dev qua nginx ở `http://localhost:8000`.

## Quy tắc

- Quyết định thiết kế quan trọng (đổi thư viện player, đổi cách quản lý state, đổi hệ thiết kế) thì cập nhật docs này và mở ADR nếu ảnh hưởng nhóm khác.
- Muốn đổi endpoint, message, trạng thái: sửa `docs/contracts/` trước (contract-first), không tự quyết trong frontend.
