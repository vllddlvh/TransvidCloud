# tests/bench/ - Benchmark worker 1 vs 2

Đo throughput worker trên **một máy local** (Docker Desktop + LocalStack cùng host).
Đây là bằng chứng local cho M4, **không thay thế** benchmark trên AWS on-demand:
số liệu local không dùng để kết luận M4 và không trộn với hành vi autoscaling.

## Chạy

```bash
make bench
# hoặc tùy chỉnh
BENCH_JOBS=12 BENCH_ROUNDS=2 BENCH_WORKERS="1 2" bash tests/bench/run.sh
```

Yêu cầu: LocalStack đang chạy (`make infra`), Docker, `python3`, `ffprobe`.
Mỗi lượt dùng queue/DLQ/project riêng; mặc định xóa sau khi xong
(`KEEP_BENCH_RESOURCES=1` để giữ lại kiểm tra).

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `BENCH_JOBS` | `12` | số job mỗi lượt (mỗi job là một bản sao video mẫu) |
| `BENCH_ROUNDS` | `1` | số lượt lặp cho mỗi số worker |
| `BENCH_WORKERS` | `1 2` | các cấu hình worker cần đo |
| `BENCH_PREWARM` | `3` | giây chờ worker ổn định trước khi gửi job |
| `BENCH_TIMEOUT` | `600` | timeout chờ tất cả job COMPLETED |
| `BENCH_IMAGE` | `tvc-worker:bench` | image dùng cho mọi lượt |
| `BENCH_TARGET_DRAIN_SECONDS` | `120` | mục tiêu thời gian xử lý hết backlog để tính target backlog |

## Cách đo

1. Build một image duy nhất cho mọi lượt.
2. Mỗi lượt: tạo queue/DLQ riêng (visibility 300s, long-poll 20s, DLQ `maxReceiveCount=3`),
   upload N bản sao `worker/testdata/sample-10s-720p.mp4`, seed N item `PENDING`.
3. Chạy W container worker với cùng cấu hình production-like
   (`TVC_HEARTBEAT_INTERVAL=120s`, `TVC_VISIBILITY_TIMEOUT=300s`, `TVC_FFMPEG_TIMEOUT=5m`),
   pre-warm, rồi gửi N message SQS theo batch 10 (có `requestedAt`).
4. Chờ DynamoDB báo đủ `COMPLETED`; đối chiếu log `job_received`/`job_finished`,
   queue rỗng, worker thoát 0; phân tích từ log JSON của worker.

Số liệu chính (mỗi job): `queueWaitMs`, `durationMs`, `ffmpegMs`, `latencyMs`,
`bytesIn/bytesOut`, worker nhận job. Mỗi lượt: wall, throughput, p50/p95,
worker utilization (`sum(durationMs) / (wall × workers)`).

`make bench` không đổi cấu hình FFmpeg/image giữa các lượt.

## Kết quả và giới hạn

- Bằng chứng nằm ở `local/bench-logs/<UTC>/` (không commit): `summary.txt`,
  `jobs.csv`, `results.csv`, log từng worker, `messages.jsonl`, `submits.csv`.
- Dataset hiện là **N bản sao của cùng video 10s 720p**, chưa phải dataset đa dạng
  đã chốt với Lưu; không suy rộng cho video dài/nhiều độ phân giải.
- Các worker container chia sẻ CPU của một host. Khi chạy 2 worker, hai tiến trình
  FFmpeg tranh cùng số core nên `durationMs` mỗi job tăng; throughput có thể
  **không tăng** dù utilization cao. Đây là giới hạn của phép đo local, không phải
  kết luận về scale trên AWS (mỗi instance có CPU riêng).
- `TARGET targetBacklogPerInstance` chỉ là gợi ý từ p95 duration đo local; khi có
  AWS phải đo lại cùng dataset/image/instance type trước khi dùng cho autoscaling.
- Không lưu access key/token/presigned URL trong bằng chứng.
