# Week3 — Runbook AWS cho Worker (M2/M3/M4)

> **Trạng thái: CHƯA CHẠY.** Tài liệu này là kế hoạch thao tác khi có môi trường AWS; mọi kết quả trong
> `Week3.md` mục 6 hiện chỉ là local. Không đánh dấu M2/M3/M4 đạt cho đến khi các bước dưới được chạy
> và bằng chứng được lưu.

Mục tiêu: dùng **cùng một image worker** đã kiểm local để chạy pipeline thật, đo scale/benchmark và
chaos trên AWS, thu bằng chứng đủ cho A4/A5/M2/M3/M4.

## 1. Điều kiện vào (owner)

- [ ] **Nguyên:** region, VPC/subnet/SG, ECR repo + quyền push, IAM role cho EC2 worker
  (`sqs:ReceiveMessage/DeleteMessage/ChangeMessageVisibility`, `s3:GetObject` input,
  `s3:PutObject` output, `dynamodb:GetItem/UpdateItem` bảng videos; **chưa** cấp
  `cloudwatch:PutMetricData` vì worker chưa phát metric — xem C5).
- [ ] **Nguyên:** SQS job queue + DLQ (visibility 300s, long-poll 20s, `maxReceiveCount=3`),
  bucket input/output, bảng videos đúng contract.
- [ ] **Nguyên:** AMI/instance type, ASG + launch template, scale-in protection/lifecycle hook,
  log group CloudWatch + đường gửi log container.
- [ ] **Lưu:** xác nhận image/compose, entrypoint, `TVC_TMP_DIR`, quyền ghi đĩa tạm.
- [ ] **Phương:** API login/project/video/confirm chạy được nếu nghiệm thu A4 qua API;
  nếu chưa, M2 chỉ được ghi là "worker integration" và **không** thay thế A4.
- [ ] Không dùng credential LocalStack (`test/test`), không đặt `AWS_ENDPOINT_URL`.

## 2. Cấu hình worker trên AWS

| Biến | Giá trị |
|---|---|
| `AWS_REGION` | region đã chốt |
| `AWS_ENDPOINT_URL` | **để trống** |
| `TVC_INPUT_BUCKET`, `TVC_OUTPUT_BUCKET` | bucket thật |
| `TVC_JOBS_QUEUE_URL` | queue URL thật (không phải LocalStack) |
| `TVC_DDB_VIDEOS` | bảng videos thật |
| `TVC_TMP_DIR` | thư mục riêng trên instance/container, không dùng chung |
| `TVC_HEARTBEAT_INTERVAL` / `TVC_VISIBILITY_TIMEOUT` | 120s / 300s (khớp queue) |
| `TVC_FFMPEG_TIMEOUT` | 5m cho benchmark; ghi rõ nếu đổi |

Ghi lại image digest (`docker image inspect`/ECR digest), phiên bản FFmpeg trong image, instance type,
AMI, cấu hình ASG và ngày giờ chạy. **Không đổi cấu hình FFmpeg/image giữa các lượt đo.**

## 3. M2 — Pipeline thật + retry có kiểm soát

1. Deploy đúng digest đã kiểm local; xác nhận worker khởi động, đọc đúng queue/bảng/bucket.
2. Chạy một video thật qua API (A4) hoặc message theo schema nếu A4 còn chặn.
3. Lưu: `jobId`, `videoId`, trạng thái DynamoDB, assets, danh sách object + Content-Type/Cache-Control,
   log `job_received` → `job_finished`, playback HLS/thumbnail (tài khoản có quyền đọc output).
4. **Retry có kiểm soát:** với tài nguyên test, gây lỗi đọc input (đổi tên/khóa quyền key), khôi phục
   trước khi hết lượt, xác nhận worker nhận lại (attempt > 1) và `COMPLETED`; lưu receive count/thời điểm.
   Không dùng validation fail làm ca retry.
5. Nếu IAM/env/runtime lỗi: ghi nguyên văn action/resource bị từ chối, người xử lý; **không** kết luận M2
   chỉ vì container chạy.

## 4. M3 — Log/metric, target backlog, scale 1 → 2 → 1

1. Xác nhận log group nhận đúng JSON (khóa `time`, `msg`, `jobId`, `status`, `durationMs`, `queueWaitMs`,
   `errorCode`). Chạy các query ở [5. Contact docs](../5.%20Contact%20docs.md) mục 5.6.
2. Tính `p95Duration` từ các job M2/benchmark AWS; chọn `targetDrainSeconds` (ví dụ 120s) và
   `targetBacklogPerInstance = ceil(targetDrainSeconds / p95DurationSeconds)`. Ghi rõ nguồn số;
   không dùng số local 23.
3. Bật scale-in protection cho instance đang giữ job; kiểm chứng ASG/lifecycle hook bảo vệ đúng
   instance đó (không chỉ instance mới nhất).
4. Nạp backlog đủ trigger scale-out; ghi timeline: thời điểm trigger, instance 2 up, job đầu tiên của
   instance 2, thời điểm scale-in, bằng chứng instance đang chạy job không bị dừng.
5. Kiểm tra đường Spot/stop → SIGTERM → worker trả message (visibility 30s). Nếu Spot không ổn định,
   dùng on-demand cho demo và ghi rõ.
6. `min 0` chỉ bật khi đường đánh thức SQS → ASG đã kiểm chứng; nếu chưa, giữ `min 1` và ghi giới hạn.

## 5. M4 — Benchmark 1 vs 2 worker và chaos AWS

- **Benchmark:** on-demand, cùng instance type/image/dataset, pre-warm; đo 1 worker và 2 worker;
  lưu timestamp submit/start/finish, `queueWaitMs`, `durationMs`, bytes. Tách số throughput cố định
  khỏi hành vi autoscaling. Dataset phải chốt với Lưu (file/ID/hash, độ dài/độ phân giải).
- **Chaos:** kill một worker đang giữ job (SIGKILL hoặc terminate instance); ghi thời điểm kill,
  lần nhận lại sau visibility, receive count, kết quả `COMPLETED`/DLQ. Thêm một ca DLQ riêng với
  input không tồn tại (`FAILED_STORAGE`) để thấy redrive policy thật.
- Mọi harness phải trả exit khác 0 khi timeout/assertion sai; lưu log chẩn đoán.

## 6. Bằng chứng tối thiểu phải lưu

- Ngày giờ, môi trường (AWS/local), source commit, image digest, FFmpeg, instance type/AMI, cấu hình
  queue/heartbeat/redrive/ASG.
- M2/A4: `jobId`/`videoId`, trace API → queue → worker, item DynamoDB, object/header, playback, retry.
- M3: timeline scale 1→2→1, bằng chứng scale-in protection, log query + p95 + target backlog có nguồn.
- M4: CSV/log thô từng lượt benchmark, kết quả chaos (kill/receive count/DLQ), cấu hình từng lượt.
- Không lưu access key, token, presigned URL còn hiệu lực, receipt handle. Chỉ xóa tài nguyên test sau
  khi đã lưu bằng chứng và xác nhận đúng tài nguyên.

## 7. Việc chưa tự động hóa

`tests/bench/run.sh` hiện gắn với LocalStack (`awslocal` trong container, endpoint `:4566`) và **không**
chạy nguyên trạng trên AWS. Khi có môi trường, hoặc (a) bổ sung chế độ AWS cho harness (dùng AWS CLI/
endpoint thật, queue/bảng riêng, kiểm tra credential không phải LocalStack), hoặc (b) chạy thủ công
theo mục 5 và lưu CSV cùng cấu trúc `tests/bench/README.md`. Chọn cách nào cũng phải ghi rõ trong bằng chứng.
