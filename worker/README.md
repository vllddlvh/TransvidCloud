# worker/ - Xử lý video (Go + FFmpeg)

**Phụ trách:** Vũ (App B).

Nhận job từ SQS, tải video gốc từ S3 input, chuyển mã HLS 360p/720p + thumbnail bằng FFmpeg,
ghi kết quả lên S3 output, cập nhật trạng thái DynamoDB có điều kiện.

## Cấu trúc thực tế

```text
cmd/worker/       main: long polling SQS, supervisor, SIGTERM
internal/
  config/         đọc và kiểm tra biến môi trường
  consumer/       nhận/xóa message SQS
  heartbeat/      gia hạn visibility, phát hiện mất quyền sở hữu
  job/            runner: idempotency, validate, transcode, upload, ACK
  transcode/      FFmpeg/ffprobe: HLS 360p/720p + thumbnail
  storage/        Get/Put S3, Content-Type/Cache-Control
  state/          DynamoDB UpdateItem có điều kiện
  validate/       giới hạn thời lượng/độ phân giải/codec
  workdir/        thư mục tạm + marker dọn rác
  shutdown/       bắt SIGTERM/SIGINT
testdata/         video mẫu ngắn 10s 720p (được commit)
Dockerfile        multi-stage, Debian bookworm-slim + ffmpeg, chạy uid 10001
```

`cmd/metricpub/` **không còn** trong code: worker chưa phát metric CloudWatch, chỉ ghi log JSON
(xem [docs-worker/5. Contact docs](../docs/docs-worker/5.%20Contact%20docs.md) mục 5.6).

## Kiểm chứng local

Yêu cầu Docker + LocalStack (`make infra`). Từ thư mục gốc repo:

```bash
make test          # go test ./...
make e2e-worker    # upload → worker container → COMPLETED → playback /media (ffprobe)
make chaos         # container: SIGTERM, SIGKILL, DLQ, hai worker cùng job
make bench         # benchmark local 1 vs 2 worker; không thay thế M4 trên AWS
```

`make e2e-worker` dùng image `tvc-worker:e2e`, nginx tạm ở `:8000` proxy `/media/*` sang LocalStack,
assert item DynamoDB, object key/Content-Type/Cache-Control, log `job_received`/`job_finished`,
toàn bộ segment HLS qua ffprobe và JPEG thumbnail. Bằng chứng: `local/e2e-logs/<UTC>-worker/`.

E2E qua API thật (`make e2e`) đã có runner nhưng dừng ở `POST /api/auth/login` vì backend chưa có code;
đây là blocker A4, không phải lỗi worker. Bằng chứng: `local/e2e-logs/<UTC>-api/summary.txt`.

## Quy tắc

- **At-least-once:** job có thể bị giao lại. Output key ổn định; video đã `COMPLETED` thì bỏ qua và ACK.
- **Không exactly-once:** hai worker cùng job có thể cùng transcode; worker ghi `COMPLETED` đầu tiên thắng,
  worker còn lại ACK message trùng và log `COMPLETED_BY_OTHER`. Giới hạn này chờ contract ownership được review.
- Chỉ ghi nhóm trạng thái xử lý (`PROCESSING`, `COMPLETED`, `FAILED_*`); không ghi lùi từ `COMPLETED`.
- Không phụ thuộc cấu trúc nội bộ backend, chỉ phụ thuộc `docs/contracts/`.
- Tối ưu chi phí: giải mã một lần, tạo 360p/720p bằng `split`, preset `veryfast`, không tạo 1080p;
  dọn thư mục tạm sau mỗi job.
- Validate bằng ffprobe theo `docs/contracts/upload-limits.md`, sai thì `FAILED_VALIDATION`.
- Chạy trên Spot: SIGTERM dừng nhận job, hủy job đang chạy và trả message về queue (visibility 30s),
  không xóa message dở dang.
