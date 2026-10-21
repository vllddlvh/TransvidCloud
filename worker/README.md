# worker/ - Xử lý video (Go + FFmpeg)

**Phụ trách:** Vũ (App B).

Nhận job từ SQS, tải video gốc từ S3 input, chuyển mã HLS 360p/720p + thumbnail bằng FFmpeg,
ghi kết quả lên S3 output, cập nhật trạng thái DynamoDB có điều kiện.

## Cấu trúc dự kiến
```
cmd/worker/      main: vòng lặp consumer SQS (long polling, gia hạn visibility)
cmd/metricpub/   đẩy metric BacklogPerInstance lên CloudWatch (Nguyên triển khai)
internal/
  queue/         nhận/xóa/gia hạn message SQS
  transcode/     gọi FFmpeg, sinh HLS + thumbnail
  storage/       Get/Put S3
  store/         UpdateItem DynamoDB có điều kiện
  config/        đọc biến môi trường
testdata/        video mẫu ngắn 5-10s (được phép commit)
Dockerfile       image có sẵn ffmpeg
```

## Quy tắc
- **At-least-once:** job có thể bị giao lại. Dùng output key ổn định; video đã `COMPLETED` thì bỏ qua và xóa message.
- Chỉ ghi nhóm trạng thái xử lý (`PROCESSING`, `COMPLETED`, `FAILED_*`).
- Không phụ thuộc cấu trúc nội bộ của backend, chỉ phụ thuộc `docs/contracts/`.
- Công thức `BacklogPerInstance = messages / max(1, workers)` viết thành hàm thuần và có unit test.
- Bắt đầu bằng POC FFmpeg chạy trên file local, độc lập AWS.
- Tối ưu chi phí: giải mã một lần, tạo 360p/720p bằng filter `split`, preset `veryfast`, không tạo 1080p; dọn thư mục tạm sau mỗi job.
- Kiểm tra ffprobe (thời lượng, độ phân giải, codec) theo `docs/contracts/upload-limits.md`, sai thì `FAILED_VALIDATION`.
- Chạy trên Spot: bắt SIGTERM, dừng nhận job mới và trả message về queue (không xóa message dang dở).
