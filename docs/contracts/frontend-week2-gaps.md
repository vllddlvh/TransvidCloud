# Frontend Week 2 — contract gaps

Ngày ghi nhận: 2026-10-30. Mọi mục dưới đây là **giả định — chờ Phương xác nhận** và được mock tạm ở frontend; không thay đổi OpenAPI hay contract backend.

Cập nhật Week 3 (2026-10-31): trạng thái dưới đây chỉ ghi nhận bằng chứng; mục nào chưa có phê duyệt của Phương/Lưu/Nguyên vẫn giữ nhãn "chờ xác nhận".

## 1. Embed công khai (chặn demo thật) — **vẫn mở**

- Trang `/embed?video=<id>` không đăng nhập được, nhưng API detail thật vẫn yêu cầu bearer. Hiện chỉ mock cho phép đọc không bearer và chỉ trả video `COMPLETED`; real backend trả 401.
- Cần một trong hai hướng: endpoint/presigned embed URL công khai chỉ cho video `COMPLETED`, hoặc xác nhận embed dùng token riêng. Chưa chốt được quyền, thời hạn, thu hồi hay giới hạn domain.
- Hệ quả: e2e/manual Week 2 chỉ chứng minh hành vi trên mock. Demo bằng backend thật phải chờ quyết định contract.
- **Cập nhật Week 3 (2026-10-31):** Chưa có endpoint/presigned URL công khai được duyệt. Frontend giữ mock; demo thật phải chờ Phương chốt. Không thêm endpoint vào OpenAPI.

## 2. Polling toàn danh sách (tối ưu, chưa chặn) — **vẫn mở**

- F4 poll `GET /projects/{id}/videos` mỗi 3 giây khi còn video đang xử lý. Với danh sách lớn, đây là tải không cần thiết và không cho biết video cụ thể thay đổi.
- Đề xuất contract batch-status (ví dụ `GET /videos?ids=...` hoặc một status endpoint gọn) để chỉ poll trạng thái. Frontend **không** tự thêm endpoint này và không đổi UI để che việc thiếu contract.
- Chưa có bằng chứng tải/độ trễ với worker thật; interval 3 giây là lựa chọn frontend.
- **Cập nhật Week 3 (2026-10-31):** Chưa có batch-status endpoint. Frontend giữ re-list 3 giây; chấp nhận cho quy mô demo nếu Phương xác nhận, chưa coi là đã chốt.

## 3. Media và hls.js với output thật — **đã kiểm chứng local; CloudFront/cache/Safari còn mở**

- Fixture hiện tại do FFmpeg `testsrc` sinh (10 giây, 360p + 720p, 5 segment mỗi rendition, tổng 431.184 byte). Không chứng minh được bitrate, keyframe, fMP4/`PROGRAM-DATE-TIME`, Content-Type hay cache của output worker.
- Chưa kiểm chứng `/media/*` qua nginx `localhost:8000` (stack `full`) và CloudFront thật; chưa kiểm chứng autoplay policy thật trên Safari/native HLS.
- **Cập nhật Week 3 (2026-10-31):** đã chạy `worker/scripts/local-media.sh` với `worker/testdata/sample-10s-720p.mp4` qua LocalStack: worker `COMPLETED` trong 2.142 ms, 8 object gồm master, hai rendition playlist, 4 segment và thumbnail. `frontend/e2e/real-media.spec.ts` tải master qua nginx `http://localhost:8000/media/prj-e2e/vid-worker-sample/hls/master.m3u8`; hls.js 1.7.4 nhận `MANIFEST_PARSED` với hai level 360/720. `Content-Type` kiểm bằng curl: master và rendition `application/vnd.apple.mpegurl`, segment `video/mp2t`, thumbnail `image/jpeg`. Vẫn **chờ Lưu/Nguyên**: CloudFront OAC/cache TTL cho `.m3u8`, domain HTTPS, Safari native HLS; e2e mới parse manifest, chưa đo bitrate/keyframe/fMP4.

## 4. Worker errorCode — **worker xác nhận; envelope/approval còn mở**

- UI map `DURATION_EXCEEDED`, `HEIGHT_EXCEEDED`, `CODEC_UNSUPPORTED`, `NOT_A_VIDEO`, và fallback cho `FAILED_TRANSCODE`/`FAILED_STORAGE` ("hệ thống sẽ thử lại tự động"). Cần A4 xác nhận tập mã, cách retry tự động và câu chữ cuối cùng.
- **Cập nhật Week 3 (2026-10-31):** `worker/internal/validate/validate.go` sinh đúng bốn mã `NOT_A_VIDEO`, `CODEC_UNSUPPORTED`, `DURATION_EXCEEDED`, `HEIGHT_EXCEEDED`; unit test `validate_test.go` xác nhận từng mã. Lỗi hệ thống ghi status `FAILED_TRANSCODE`/`FAILED_STORAGE` kèm mã chi tiết (`S3_GET_FAILED`, `S3_PUT_FAILED`, `LOCAL_FS_FAILED`, `FFMPEG_START_FAILED`, `MASTER_WRITE_FAILED`, lỗi FFmpeg); UI cố ý không liệt kê các mã này mà dùng hướng dẫn chung theo status, nên không lệch. Vẫn **chờ Phương**: envelope API trả `errorCode` (tên field, mapping DynamoDB → response), cách retry tự động và câu chữ cuối cùng; chưa có backend để chạy nhánh `FAILED_*` thật qua UI.
