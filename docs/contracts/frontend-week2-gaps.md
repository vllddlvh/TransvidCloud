# Frontend Week 2 — contract gaps

Ngày ghi nhận: 2026-10-30. Mọi mục dưới đây là **giả định — chờ Phương xác nhận** và được mock tạm ở frontend; không thay đổi OpenAPI hay contract backend.

## 1. Embed công khai (chặn demo thật)

- Trang `/embed?video=<id>` không đăng nhập được, nhưng API detail thật vẫn yêu cầu bearer. Hiện chỉ mock cho phép đọc không bearer và chỉ trả video `COMPLETED`; real backend trả 401.
- Cần một trong hai hướng: endpoint/presigned embed URL công khai chỉ cho video `COMPLETED`, hoặc xác nhận embed dùng token riêng. Chưa chốt được quyền, thời hạn, thu hồi hay giới hạn domain.
- Hệ quả: e2e/manual Week 2 chỉ chứng minh hành vi trên mock. Demo bằng backend thật phải chờ quyết định contract.

## 2. Polling toàn danh sách (tối ưu, chưa chặn)

- F4 poll `GET /projects/{id}/videos` mỗi 3 giây khi còn video đang xử lý. Với danh sách lớn, đây là tải không cần thiết và không cho biết video cụ thể thay đổi.
- Đề xuất contract batch-status (ví dụ `GET /videos?ids=...` hoặc một status endpoint gọn) để chỉ poll trạng thái. Frontend **không** tự thêm endpoint này và không đổi UI để che việc thiếu contract.
- Chưa có bằng chứng tải/độ trễ với worker thật; interval 3 giây là lựa chọn frontend.

## 3. Media và hls.js với output thật (dời tuần 9)

- Fixture hiện tại do FFmpeg `testsrc` sinh (10 giây, 360p + 720p, 5 segment mỗi rendition, tổng 431.184 byte). Không chứng minh được bitrate, keyframe, fMP4/`PROGRAM-DATE-TIME`, Content-Type hay cache của output worker.
- Chưa kiểm chứng `/media/*` qua nginx `localhost:8000` (stack `full`) và CloudFront thật; chưa kiểm chứng autoplay policy thật trên Safari/native HLS.

## 4. Worker errorCode (dời tuần 9)

- UI map `DURATION_EXCEEDED`, `HEIGHT_EXCEEDED`, `CODEC_UNSUPPORTED`, `NOT_A_VIDEO`, và fallback cho `FAILED_TRANSCODE`/`FAILED_STORAGE` ("hệ thống sẽ thử lại tự động"). Cần A4 xác nhận tập mã, cách retry tự động và câu chữ cuối cùng.
