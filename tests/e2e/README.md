# tests/e2e/ - Kiểm thử đầu-cuối

## Đo metrics trên cloud công khai

```powershell
node tests/e2e/cloud-metrics.mjs https://vbeeaistt.com local/e2e-logs/<run-id>
```

Chạy từ thư mục gốc, cần Node và API/worker đang hoạt động. Mặc định đo 30 request tuần tự cho mỗi API chính, tạo batch 12 bản sao video 10s 720p, kiểm tra nguồn 360p/không audio và file giả. Tùy chỉnh bằng `CLOUD_METRIC_SAMPLES` (1–100), `CLOUD_METRIC_JOBS` (1–30). Script giữ dữ liệu video hợp lệ để đối chiếu; xóa video giả sau khi xác nhận `FAILED_VALIDATION / NOT_A_VIDEO`. Không tự scale worker hay deploy.

Kết quả `cloud-metrics.json` và `http-samples.csv` chỉ lưu timing, HTTP status và ID test, không lưu token/password/presigned URL. p50/p95 dùng nearest-rank; latency tính đến khi đọc xong response body tại client. Batch throughput gồm confirm và polling, chưa phải công suất tối đa. Script kiểm tra trạng thái, MIME và khả năng tải asset; cần FFprobe riêng để kiểm tra số luồng/độ phân giải thực. Kết quả chi tiết và finding rendition: [báo cáo 08/11/2026](../../docs/report/Cloud-Verification-2026-11-08.md).

**Phụ trách:** Lưu chủ trì (Vũ, Phương viết kịch bản), cả nhóm bổ sung.

Runner: `run.sh`, hai chế độ. Bằng chứng nằm ở `local/e2e-logs/<UTC>-<mode>/` (không commit).

## `worker` — worker integration (chạy được ngay)

```bash
make e2e-worker
# hoặc: bash tests/e2e/run.sh worker
```

- Tạo queue + DLQ riêng, seed video `PENDING`, upload `worker/testdata/sample-10s-720p.mp4`, gửi message schema 1.
- Chạy **image thật** `worker/Dockerfile` (mặc định tag `tvc-worker:e2e`) nối LocalStack qua `host.docker.internal`.
- Assert theo `jobId`/`videoId`: DynamoDB `COMPLETED`, `attemptCount=1`, đủ `hls/hls360/hls720/thumbnail`; object key + Content-Type + Cache-Control; log `job_received` (attempt, queueWaitMs) và `job_finished` (COMPLETED, durationMs); temp sạch; worker exit 0.
- Playback qua nginx `/media/*` do runner tự dựng ở `:8000`: HTTP 200, Content-Type, tải toàn bộ segment, `ffprobe -count_frames` ra đúng 360p/720p, thumbnail JPEG.
- Đây **không phải A4** vì không đi qua API upload/confirm.

Yêu cầu: LocalStack (`make infra`), Docker, `nginx`, `ffprobe`, `curl`. Đổi cổng bằng `E2E_PORT=18000`; dùng nginx sẵn có bằng `E2E_MEDIA_BASE=http://host:port`.

## `api` — A4 qua API thật

```bash
E2E_EMAIL=... E2E_PASSWORD=... make e2e
# cloud: BASE_URL=https://domain E2E_EMAIL=... E2E_PASSWORD=... make e2e
```

- Đăng nhập → tạo project → tạo video → PUT vào `uploadUrl` (không kèm bearer) → confirm → poll tới `COMPLETED` → assert assets + playback qua `BASE_URL`.
- Schema đang theo `docs/contracts/openapi.yaml` **DRAFT, chưa được Phương duyệt**. Không lưu access token hay presigned URL trong bằng chứng.
- Cần backend A3 + worker đang chạy. Khi backend chưa có (hiện tại), runner fail ngay tại login với thông báo rõ — đây là blocker A4, không phải kết quả test.

## Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `E2E_TIMEOUT` | `180` | Giây chờ `COMPLETED` |
| `E2E_PORT` | `8000` | Cổng nginx playback ở chế độ worker |
| `E2E_MEDIA_BASE` | — | Bỏ qua nginx tự dựng, dùng URL `/media` sẵn có |
| `E2E_WORKER_IMAGE` | `tvc-worker:e2e` | Tag image worker |
| `KEEP_E2E_RESOURCES` | `0` | Giữ queue/object/item để xem lại |
| `BASE_URL` | `http://localhost:8000` | Origin API (local hoặc CloudFront) |

Runner exit != 0 khi timeout/assertion sai và ghi chẩn đoán vào `summary.txt`.
