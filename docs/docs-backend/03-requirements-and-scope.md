# 3. Requirements & Scope

## Backend P0

- API đăng ký/đăng nhập cơ bản.
- API tạo/liệt kê project.
- API khởi tạo video và cấp presigned URL.
- API confirm upload, tích hợp S3/DynamoDB/SQS.
- API liệt kê video theo project và lấy chi tiết, trạng thái, assets.
- Kiểm tra quyền truy cập tài nguyên.
- Xử lý validation, lỗi và request trùng ở mức cần thiết.
- Chạy được local và tham gia kiểm thử end-to-end.

OpenAPI hiện có endpoint tạo API key cho project; mức ưu tiên/lifecycle cuối cùng cần thống nhất với scope tổng thể trước khi mở rộng implementation.

## Input constraints hiện ghi trong contract DRAFT

| Quy tắc | Giá trị hiện tại |
|---|---|
| Dung lượng tối đa | 200 MB |
| Thời lượng tối đa | 300 giây |
| Chiều cao tối đa | 1080 pixel |
| Định dạng | MP4, MOV, MKV, WebM |
| Presigned URL | Hết hạn sau 15 phút |

Backend kiểm tra giới hạn dung lượng khi khai báo và confirm qua `HeadObject`. Worker chịu trách nhiệm kiểm tra media như thời lượng/độ phân giải/codec bằng `ffprobe`. Các giá trị trên chỉ được coi là chính thức sau khi contract được nhóm chốt.

## Non-goals mặc định

- Transcoding trong Backend.
- Billing, SDK riêng, multi-region, Kubernetes/EKS.
- AI tự động sinh transcript/quiz.
- Webhook và vận hành nâng cao nếu P0 chưa ổn định.

## Acceptance criteria tối thiểu

- Client có thể tạo video, upload trực tiếp lên S3 và confirm.
- Job được đưa vào SQS theo contract; Worker có thể xử lý và cập nhật trạng thái.
- API trả trạng thái/assets phù hợp với dữ liệu hiện có.
- User không thể đọc tài nguyên của user/project khác.
- Các luồng lỗi quan trọng có test; contract và docs được cập nhật khi thay đổi hành vi.
