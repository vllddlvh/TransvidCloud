# Work — Tuần 3: Scaling, benchmark, chaos và đóng băng (Sprint 3 + Sprint 4)

> Tuần 3 trong thư mục `work/` ứng với **tuần 9 học kỳ**: Sprint 3 (M3) ở đầu tuần và Sprint 4 (M4) ở phần còn lại — gộp trong cùng một tuần, không có tuần benchmark bổ sung sau đó. Đối chiếu [[docs/introduction/1. Timelines|timeline chung v4]] và mục 4 của [[docs/docs-worker/4. Timelines|timeline Worker]].
> Người phụ trách: **Vũ** — A4, phần Worker của A5, phối hợp C5/A8, frontend A6; phối hợp Lưu (harness/dataset), Nguyên (compute/scaling/observability), Phương (confirm/ownership).
> Đây là **kế hoạch**, lập ngày **31/10/2026**. Tuần này **không khởi đầu từ con số 0**: kết luận cuối [[docs/docs-worker/work/Week2|Week2]] là A4 chưa đạt (thiếu E2E qua API), M2 chưa đạt (chưa có AWS) và ownership chưa chốt — các khoản nợ đó phải đóng trong tuần này trước khi M3/M4 có ý nghĩa. Cập nhật tiến độ thực tế ở mục 6.

## 1. Mục tiêu

Kết thúc tuần 3, phần Worker phải đáp ứng:

1. **Đóng nợ M2/A4:** E2E local qua API thật (upload → confirm → queue → worker → HLS + thumbnail → `COMPLETED`, playback `/media/*`); cùng image chạy pipeline thật trên AWS có bằng chứng retry cơ bản.
2. **Đóng nợ A5:** ownership/lease hai worker cùng job được chốt với Phương và kiểm thử (hoặc nhóm chấp nhận ghi rõ giới hạn xử lý đôi); chaos chạy trong container theo `make chaos` của Lưu.
3. **M3 — Scaling:** cùng Nguyên chứng minh scale **1 → 2 → 1** có bằng chứng; worker đang giữ job được **scale-in protection**; log/metric đủ để Cloud (C5) dùng.
4. **M4 — Benchmark và chaos AWS:** cùng Lưu đo **1 worker và 2 worker on-demand** cùng instance type/image/dataset; ít nhất một ca worker chết giữa job trên AWS với nhận lại và DLQ đúng; dữ liệu thô + phân tích hạn chế cho báo cáo.
5. **M4 — Báo cáo và đóng băng:** phần Worker trong báo cáo hoàn tất theo code thật; hai lần diễn tập chung; **đóng băng phiên bản cuối tuần 9** để tuần 10 chỉ rà hồ sơ và nộp.

**Không mở rộng trong tuần 3:** rendition 1080p, tinh chỉnh bitrate/preset, concurrency nhiều job/worker, checkpoint/resume FFmpeg, webhook, min 0 khi chưa kiểm chứng đường đánh thức, CI/CD (C6). Tuần 10 **không phải tuần phát triển bù** — không đẩy bất kỳ việc nào của M3/M4 sang tuần 10.

## 2. Hiện trạng đầu tuần và việc chuyển tiếp

Đối chiếu [[docs/docs-worker/work/Week2|Week2]] mục 6 (cập nhật 2026-10-30). Bằng chứng Week2 là **kết quả đã ghi trong nhật ký**, không phải kết quả chạy lại khi lập tài liệu này.

| Hạng mục | Điểm xuất phát | Việc cần làm trong tuần 3 |
|---|---|---|
| A4 — E2E qua API | Chưa đạt: backend confirm còn trống; chaos mới là worker integration | Chạy lại toàn luồng upload → confirm → worker khi Phương xong A3; đây là **gate đầu tiên của tuần**, mọi việc sau phụ thuộc nó |
| A5 — Reliability code | Heartbeat, SIGTERM, DLQ, path safety đã có unit/chaos local pass | Chỉ sửa khi phát sinh lỗi; không mở rộng hệ thống khóa ngoài thiết kế đã review |
| Ownership hai worker | Chưa chốt với Phương; heartbeat chỉ giảm giao lại, không exactly-once | Chốt quyết định đầu tuần (lease hoặc chấp nhận giới hạn); nếu lease thì review contract trước khi code |
| Chaos trong container | `make chaos` pass với worker binary trên host; container bị chặn do Docker Desktop không share `Documents` | Lưu xử lý file sharing/init mount; chạy lại trong container trước khi mang lên AWS |
| `make e2e` | Vẫn placeholder | Runner/assertions thật cùng Lưu, dựa trên ca E2E qua API ngày 1 |
| M2 — AWS | Chưa có hạ tầng/credential AWS | Phối hợp Nguyên (C2/C3) chốt image digest, IAM, ASG; chạy pipeline thật + 1 retry có kiểm soát |
| Log/metric | Log JSON đã có jobId/videoId/attempt/durationMs/queueWaitMs; metric CloudWatch chưa có | Đối chiếu với Nguyên đầu vào C5; không tuyên bố metric có chỉ vì log có |
| Benchmark | Dataset/config đã thống nhất hướng chuẩn bị (Week2 mục 3.8) nhưng chưa đo | Đo trong tuần này — **không dành riêng tuần nào khác cho benchmark** |
| Frontend A6 | Song song, trong tổng tải 9,5 pd của Vũ | Chỉ mức tối thiểu upload/status/player nếu tranh tài nguyên; pipeline/reliability ưu tiên |
| Báo cáo R1 | Phần Worker đã bắt đầu từ tuần 2 | Hoàn tất theo kết quả đo thật; không điền số liệu dự kiến thành số đo |

**Khối lượng tham chiếu:** timeline chung ghi A4 + phần Vũ của A5 ≈ 5,5 pd baseline, A8 phần Vũ 0,5 pd, C5 phối hợp, A6 2,0 pd, R1 0,75 pd — tất cả trong tổng tải 9,5 pd, **không có quỹ riêng cho từng kế hoạch tuần**. Tuần 3 là tuần nặng nhất của Vũ; nếu quá tải, cắt theo mục 7 (giảm phạm vi), không dời mốc sang tuần 10.

## 3. Công việc chi tiết theo ngày

"Ngày 1–3" dưới đây là **ba chặng làm việc của tuần 3**, không đánh lại ngày của Sprint 1/2 và không ấn định ngày lịch. Thứ tự cứng: **không bắt đầu M3 nếu M2 chưa đạt** — scale một pipeline chưa chạy là bằng chứng vô nghĩa. Gửi yêu cầu hỗ trợ Cloud (AWS env, ASG, CloudWatch) từ ngày 1, không chờ đến lúc cần đo.

### Ngày 1 — Đóng nợ M2/A4/A5: E2E thật, container, ownership, AWS

#### 3.1. E2E local qua API thật (A4)

- [ ] Cùng **Phương** xác nhận A3 confirm đã chạy được; chạy toàn luồng bằng request thật: đăng nhập/tạo project → `POST videos` → PUT file → `POST confirm` → Worker xử lý → API trả `COMPLETED`. Dùng `worker/testdata/sample-10s-720p.mp4` trước, sau đó các case ma trận Week2 mục 5.2 còn thiếu (không audio, nguồn 360p, 301s, 1440p, codec lạ, quá 200 MB).
- [ ] Cùng **Lưu** đưa `make e2e` từ placeholder thành runner/assertions thật: assert theo `jobId`/`videoId` (state, assets, key/Content-Type, playback), không theo queue count; timeout phải làm lệnh fail. Kết quả ca E2E qua API là điều kiện để đánh dấu A4 hoàn thành — không dùng kết quả "worker integration" của Week2 thay thế.
- [ ] Chạy lại `make chaos` **trong container** sau khi Lưu xử lý xong Docker file sharing/init mount: SIGTERM khi đang transcode và đang upload, SIGKILL, DLQ. Nếu Docker Desktop vẫn không share `Documents`, Lưu đưa init vào image/compose rồi chạy lại; không giữ workaround mount `/tmp` làm bằng chứng nghiệm thu.

#### 3.2. Ownership hai worker (A5)

- [ ] Chốt với **Phương** quyết định ownership đầu tuần, chọn một trong hai: (a) lease/attempt token theo contract đã review, hoặc (b) chấp nhận at-least-once có xử lý đôi có kiểm soát và ghi giới hạn vào báo cáo. **Không tự triển khai lease khi contract còn `DRAFT`.**
- [ ] Nếu chọn (a): review contract trước (điều kiện claim, gia hạn, nhận lại sau crash, kiểm tra owner khi ghi kết quả — tham chiếu Week2 mục 3.4), code, test hai worker cùng nhận hai message cùng job, ca giao lại khi worker cũ còn chạy; kiểm tra cả trạng thái lẫn playlist/segments trong S3.
- [ ] Nếu chọn (b): test hai worker cùng job vẫn phải chạy để **đo và ghi** hành vi thực (output cuối nhất quán/phát được, không ghi lùi `COMPLETED`); ghi rõ "không chống xử lý đôi" trong báo cáo thay vì bỏ trống.

#### 3.3. Pipeline AWS với Nguyên (M2)

- [ ] Chốt với **Lưu/Nguyên** image tag/digest, phiên bản FFmpeg, target architecture đã test local; dùng **cùng artifact** trên AWS, không đổi cấu hình transcode giữa local và AWS.
- [ ] Đối chiếu bucket/queue/bảng/region, disk tạm, log; `AWS_ENDPOINT_URL` để trống, không đưa credential `test/test` của LocalStack lên AWS.
- [ ] Chạy upload → confirm → queue → worker → `COMPLETED` trên AWS bằng video thật; lưu môi trường, image digest, `jobId`/`videoId`, trạng thái/assets, object metadata, log; kiểm tra HLS/thumbnail bằng tài khoản kiểm thử có quyền đọc output.
- [ ] Một retry có kiểm soát: gây lỗi đọc input trong tài nguyên test, khôi phục input trước khi hết lượt, xác nhận nhận lại và `COMPLETED`. Chaos AWS chính thức thuộc ngày 3, đây chỉ là retry cơ bản của M2.
- [ ] Khi IAM/env/runtime lỗi: ghi lỗi gốc theo action/resource và người xử lý; **không đánh dấu M2 chỉ vì container chạy** — M2 cần luồng upload/confirm thật trên AWS.

**Đầu ra ngày 1:** trace E2E local qua API (A4 đạt), quyết định ownership có bằng chứng test, bằng chứng M2 trên AWS hoặc danh sách blocker kèm người xử lý và ảnh hưởng M3/M4.

### Ngày 2 — M3: Scale-in protection và scale 1 → 2 → 1

#### 3.4. Log/metric cho Cloud (C5)

- [ ] Đối chiếu với **Nguyên** đầu vào C5 cần từ worker: log JSON có `jobId`, `videoId`, `projectId`, attempt, kết quả/errorCode, `durationMs`, `queueWaitMs`, bytes in/out; thống nhất cách CloudWatch digest log (không sinh metric riêng trong worker trừ khi Nguyên yêu cầu).
- [ ] Có số liệu tổng hợp thành công/thất bại, thời gian chờ và thời gian xử lý từ log của các lượt chạy ngày 1; dùng **thời gian xử lý đo được** để chọn target backlog cho autoscaling — không chọn theo cảm tính rồi đo ngược lại.

#### 3.5. Scale-in protection và scale 1 → 2 → 1 với Nguyên

- [ ] Cùng Nguyên bật **scale-in protection** cho worker đang giữ job (instance protection khi đang `PROCESSING`): xác minh cơ chế ASG/API hoặc lifecycle hook thực sự bảo vệ đúng instance đang chạy job, không chỉ bảo vệ instance mới nhất.
- [ ] Kiểm chứng đường chuyển **cảnh báo Spot/dừng máy tới container** thành SIGTERM (entrypoint đã xác nhận với Lưu ở Week2; nếu Spot không ổn định, dùng on-demand cho phần scale demo và ghi rõ).
- [ ] Chạy kịch bản scale **1 → 2 → 1**: nạp backlog đủ để trigger scale-out (target theo số đo mục 3.4), đợi worker 2 nhận job, hết backlog scale-in về 1 — worker đang giữ job **không bị dừng giữa job**; ghi timeline (thời điểm trigger, instance 2 up, nhận job đầu, scale-in, bảo vệ) kèm `jobId` làm bằng chứng.
- [ ] `min 0` chỉ bật khi đường đánh thức (SQS → ASG) đã kiểm chứng end-to-end; nếu chưa, giữ `min 1` cho demo/benchmark và ghi rõ đây là giới hạn, không phải quyết định bỏ tính năng.
- [ ] Không đổi cấu hình FFmpeg/image giữa các lượt đo trong ngày — mọi thay đổi cấu hình làm mất khả năng so sánh số liệu.

**Đầu ra ngày 2:** bằng chứng M3 (trace scale 1→2→1, worker giữ job được bảo vệ), số liệu log/metric bàn giao cho C5, target backlog có dẫn nguồn từ phép đo.

### Ngày 3 — M4: Benchmark, chaos AWS, báo cáo và đóng băng

#### 3.6. Benchmark 1 và 2 worker cùng Lưu (A8)

- [ ] Đo trên **on-demand** (Spot làm phép đo thiếu ổn định), cùng instance type/image/dataset qua cả hai lượt; pre-warm worker trước khi bắt đầu đo. Tách **phép đo throughput cố định (1 worker, 2 worker)** với **hành vi autoscaling** của ngày 2 — hai thứ không trộn vào một số liệu.
- [ ] Dataset đã chốt với Lưu (file/ID/hash, độ dài/độ phân giải, cấu hình FFmpeg, image digest); video lớn ở `local/samples/`, không commit.
- [ ] Lưu dữ liệu thô từng lượt (log worker, timestamp submit/start/finish, `queueWaitMs`, `durationMs`, bytes), cấu hình mỗi lượt; phân tích hạn chế — tổng hợp đủ để đưa vào báo cáo, không xây dashboard.
- [ ] Đối chiếu `requestedAt` có/không trong message: thiếu thì ghi nhận thiếu dữ liệu, không diễn giải số 0 như số đo thật (Week2 mục 3.8).

#### 3.7. Chaos AWS: worker chết giữa job

- [ ] Ít nhất một ca kill worker đang giữ job trên AWS (tín hiệu tùy chọn: SIGKILL hoặc terminate instance qua Nguyên): quan sát message được nhận lại sau visibility, retry → `COMPLETED` (hoặc DLQ nếu lỗi lặp), worker khác/khởi động lại hoàn tất; ghi thời điểm kill, lần nhận tiếp theo, receive count.
- [ ] Kiểm tra DLQ riêng trên AWS với một message lỗi lặp có kiểm soát (input không tồn tại → `FAILED_STORAGE`), xác nhận SQS tự chuyển theo redrive policy; không dùng validation fail làm ca DLQ.
- [ ] Tất cả chaos/harness trả exit khác 0 khi timeout/assertion sai; lưu chẩn đoán kèm.

#### 3.8. Báo cáo, diễn tập, đóng băng

- [ ] Hoàn tất **phần Worker trong báo cáo chung**: pipeline thực tế, giới hạn input, heartbeat/retry/DLQ, SIGTERM/scale-in protection, phương pháp và **hạn chế** kiểm thử (đặc biệt ownership nếu nhóm chọn không chống xử lý đôi); đính kèm bằng chứng đã lưu ở mục 6.1, không điền số liệu dự kiến thành số đo.
- [ ] Tham gia **hai lần diễn tập chung** với cả nhóm (demo upload → HLS → playback qua đường CloudFront/Tunnel của Nguyên, player của frontend); sửa lỗi chặn demo trước, làm đẹp sau.
- [ ] **Đóng băng phiên bản cuối tuần 9:** chốt image tag, cấu hình FFmpeg, env; mọi thay đổi sau đóng băng chỉ là sửa lỗi chặn nộp; lưu tag đã đóng băng vào bằng chứng.
- [ ] Cập nhật tài liệu Worker ([[docs/docs-worker/1. Introduction|1. Introduction]] → [[docs/docs-worker/5. Contact docs|5. Contact docs]]) khớp code thật; contract `DRAFT` được gỡ nhãn khi đã phản ánh quyết định đã review.

**Đầu ra ngày 3:** bộ số liệu benchmark 1/2 worker có dữ liệu thô, bằng chứng chaos AWS + DLQ, phần Worker của báo cáo hoàn tất, hai diễn tập đã chạy, phiên bản đã đóng băng.

## 4. Bàn giao cuối tuần (đầu ra cho các bên)

| Bên nhận | Vũ bàn giao | Thời điểm / bên phối hợp |
|---|---|---|
| Phương | Kết quả E2E upload/confirm qua API, quyết định ownership đã thực thi, trace retry/errorCode trên AWS | Chốt ownership đầu tuần; E2E ngay khi A3 xong |
| Lưu | Kết quả `make e2e`/`make chaos` trong container, dataset/log benchmark, số liệu 1/2 worker cho harness | Benchmark cả ngày 3; e2e/chaos ngày 1 |
| Nguyên | Log/metric cho C5, target backlog có số đo, bằng chứng scale-in protection, hành vi SIGTERM/Spot trên AWS | M3 ngày 2; chaos AWS ngày 3 |
| Cả nhóm | Kết luận A4/A5/M2/M3/M4 đạt/chưa đạt theo bằng chứng, phần Worker báo cáo, danh mục phiên bản đã đóng băng | Cuối tuần 9; tuần 10 chỉ rà và nộp |

**Ranh giới trách nhiệm giữ nguyên** (như Week2 mục 4): Vũ sửa code Worker và script chaos phần worker; Lưu sở hữu Dockerfile/compose/harness; Nguyên sở hữu IAM/compute/deploy/scaling; Phương sở hữu API/confirm và cùng review contract.

## 5. Tiêu chí hoàn thành tuần 3 (đối chiếu A4/A5/M2/M3/M4)

### 5.1. Checklist nghiệm thu

- [ ] A4: E2E local qua API thật với `make e2e` có runner/assertions thật; ma trận case Week2 mục 5.2 các dòng còn thiếu đã chạy và pass.
- [ ] A5: ownership đã chốt và kiểm thử hai worker cùng job, **hoặc** nhóm chấp nhận giới hạn và đã ghi vào báo cáo; chaos chạy trong container.
- [ ] M2: cùng image chạy pipeline upload/confirm thật trên AWS, đủ assets + một retry có kiểm soát có bằng chứng.
- [ ] M3: scale 1 → 2 → 1 có trace/timeline; worker đang giữ job không bị scale-in dừng giữa job; log/metric đã bàn giao cho C5; target backlog dẫn nguồn từ số đo.
- [ ] M4: benchmark 1 và 2 worker on-demand cùng cấu hình có dữ liệu thô; ít nhất một ca worker chết giữa job trên AWS với nhận lại/DLQ đúng.
- [ ] Báo cáo phần Worker hoàn tất theo kết quả thật; hai diễn tập đã chạy; phiên bản đã đóng băng cuối tuần 9.

### 5.2. Ma trận kiểm thử/bằng chứng tối thiểu

| Ca | Môi trường | Kết quả bắt buộc / bằng chứng |
|---|---|---|
| E2E qua API | local (compose) | `COMPLETED`, assets/Content-Type đúng, playback `/media/*`, `make e2e` exit 0 |
| Hai worker cùng job | local | Theo quyết định ownership; output cuối nhất quán/phát được, không ghi lùi `COMPLETED` |
| Chaos trong container | local (compose) | `make chaos` pass: SIGTERM (transcode + upload), SIGKILL, DLQ |
| Pipeline AWS | AWS | Upload/confirm thật → `COMPLETED`, assets đúng, digest image đã lưu |
| Retry AWS | AWS | Lỗi input khôi phục được → nhận lại → `COMPLETED` |
| Scale 1 → 2 → 1 | AWS | Timeline trigger/up/nhận job/scale-in; worker giữ job được bảo vệ |
| Benchmark 1 worker | AWS on-demand | Throughput, `queueWaitMs`, `durationMs` theo job; dữ liệu thô + cấu hình lượt đo |
| Benchmark 2 worker | AWS on-demand | Tương tự, cùng dataset/image/instance type; không trộn với số autoscaling |
| Chaos AWS | AWS | Worker chết giữa job → nhận lại sau visibility → `COMPLETED`; DLQ riêng có message thật |
| Đóng băng | — | Image tag/cấu hình đã chốt và ghi trong bằng chứng; sau đó chỉ sửa lỗi chặn nộp |

**Lệnh kiểm tra dự kiến khi triển khai** — không phải kết quả đã chạy trong tài liệu này:

```bash
# Từ thư mục gốc repo
make test
make e2e     # chỉ nghiệm thu sau khi runner/assertions thật (ngày 1)
make chaos   # chỉ nghiệm thu khi chạy trong container (ngày 1)
# Riêng code Go
go -C worker test ./...
go -C worker test -race ./...
```

Lưu số test pass/skip, phiên bản tool/image, môi trường (local/AWS), kết quả assertions cùng log cho mỗi lượt; không viết "xanh toàn bộ" nếu chỉ chạy được một phần.

## 6. Tiến độ thực tế (cập nhật sau mỗi buổi làm)

Buổi 2026-10-31 làm **local-first** theo phạm vi đã chốt: hoàn tất mọi phần kiểm chứng được bằng LocalStack + Docker, chuẩn bị runbook cho AWS, và ghi rõ M2/M3/M4 chưa đạt vì chưa có môi trường AWS. Không có số liệu AWS nào được tạo hoặc suy diễn trong mục này.

**Định danh bản chạy:** source `b5cf8ea` (working tree có thay đổi chưa commit), image worker `sha256:613656ca5e78d6a5dad98a2f4f74cb9ff881a40b4dd582d35e1ca821652d7087` (dùng chung cho chaos/e2e/bench), FFmpeg trong image `5.1.9-0+deb12u1`, host `Darwin 24.3.0 arm64`, ffprobe host `9.0.2`, LocalStack `3.8.1`.

| Ngày / buổi thực tế | Đã làm | Bằng chứng (lệnh, log, job/video, artifact) | Vấn đề / cần hỗ trợ |
|---|---|---|---|
| 2026-10-31 | **Dockerfile worker + chaos trong container (A5):** multi-stage Go → Debian bookworm-slim, ffmpeg, uid 10001, `--init`; chuyển chaos script sang `TVC_CHAOS_RUNTIME=docker`; thêm kịch bản hai worker cùng job | `worker/Dockerfile`; `local/scripts/chaos-kill-worker.sh`; `local/chaos-logs/20261031T151751Z/summary.txt`: SIGTERM (heartbeat dừng, visibility 30s, message giao lại, attempt=2, `COMPLETED`), SIGKILL exit 137 (rác tmp được dọn lần khởi động sau, message giao lại, `COMPLETED`), DLQ receiveCount=4/worker nhận 3, competing 2 worker cùng job: cả hai transcode, 1 `COMPLETED` + 1 `COMPLETED_BY_OTHER`, `attemptCount=2`, output phát được, queue rỗng | Không có lease/exactly-once; contract ownership còn `DRAFT`, cần Phương/nhóm chốt giới hạn at-least-once |
| 2026-10-31 | **E2E runner thật (A4):** `tests/e2e/run.sh` hai mode; worker mode upload → worker container → DynamoDB → playback `/media`; API mode login → project → video → PUT → confirm → chờ `COMPLETED` | `make e2e-worker` PASS: `local/e2e-logs/20261031T144417Z-worker` và lần cuối `20261031T151346Z-worker` — assets `hls/hls360/hls720/thumbnail`, `attemptCount=1`, Content-Type/Cache-Control đúng, 360p/720p decode đủ 2 segment/300 frame, thumbnail JPEG, worker exit 0. `make e2e` FAIL đúng điểm chặn: `local/e2e-logs/20261031T144437Z-api/summary.txt` — `POST /api/auth/login` không gọi được vì backend chưa có code | **A4 chưa đạt** cho đến khi Phương có backend login/confirm; worker mode không thay thế A4 |
| 2026-10-31 | **Benchmark local 1 vs 2 worker (phần local của M4):** harness `tests/bench/run.sh`, queue/DLQ riêng mỗi lượt, 12 job video 10s 720p, 2 lượt mỗi cấu hình, pre-warm, log JSON + CSV thô | `local/bench-logs/20261031T150839Z/` (summary/results/jobs + log từng worker): 1 worker mean `0,31 job/s`, wall `39,5s`, p95 duration `4.437ms`, p95 queue `36.773ms`, util `0,96`; 2 worker mean `0,33 job/s` (+6,5%), wall `36,1s`, p95 duration `6.978ms`, p95 queue `30.426ms`, util `0,98`; `targetBacklogPerInstance=23` theo `ceil(120s / p95DurationMax(1 worker)=5.240ms)` | Hai worker chia sẻ CPU một host: p95 duration tăng ~57%, throughput chỉ +6,5%; **không dùng số local cho autoscaling AWS** |
| 2026-10-31 | **C5 handoff:** sửa tài liệu worker đang mô tả metric như đã phát; chốt log là nguồn duy nhất, thêm bảng ánh xạ chỉ số + Logs Insights + công thức target backlog | `docs/docs-worker/5. Contact docs.md` mục 5.5–5.6; `docs/docs-worker/3. Architecture.md` mục 3.9/3.11; `docs/docs-worker/2. Business.md` mục 2.7 | Worker **chưa phát metric** (`PutMetricData`/EMF); cần Nguyên chốt log group, metric filter/EMF, quyền IAM và ngưỡng sau khi có số AWS |
| 2026-10-31 | **Tài liệu/report/freeze candidate:** cập nhật README worker, `docs-common/2. Implementation status`, mục Worker trong `docs/report/Progress 2.md`; chụp image/commit làm ứng viên đóng băng local | README và các mục tài liệu nêu trên; image ID/commit ở đầu mục 6; tag local `tvc-worker:week3-local-freeze` (cùng digest `613656ca…`); `tests/bench/README.md` | Chưa có hai lần diễn tập chung; **chưa đóng băng chính thức** vì M2–M4 và báo cáo nhóm chưa xong; working tree còn thay đổi chưa commit (không tính là nguồn phát hành) |
| 2026-10-31 | **Rà soát mã và kiểm thử cuối:** `go test -count=1 ./...`, `go test -race -count=1 ./...`, `go vet ./...`, `gofmt -l`, `make smoke`, `make e2e-worker`, `make chaos` | Tất cả package có test đều `ok` (gồm `cmd/worker`, `consumer`, `heartbeat`, `job`, `state`, `storage`, `transcode`, `validate`, `workdir`); `logging`/`shutdown` không có test; vet/gofmt sạch; smoke 9/9 OK; e2e-worker PASS lần cuối ngày 31/10 | Chưa chạy được AWS; `make chaos` chạy lại sau khi sửa lỗi cleanup `docker exec -i` ăn stdin của vòng `while read` — lần này queue/DLQ/item/volume đều sạch (đã kiểm tra) |

### 6.1. Gói bằng chứng cần lưu

- Mỗi lượt: ngày giờ, local/AWS, phiên bản source và image digest, FFmpeg, dataset (file/ID/hash), cấu hình heartbeat/visibility/redrive và autoscaling đang dùng.
- E2E/A4: `jobId`/`videoId`, trace API → queue → worker, item DynamoDB, danh sách object/Content-Type, bằng chứng playback.
- M3: timeline scale 1→2→1 (trigger, instance up, job đầu của worker 2, scale-in), bằng chứng worker giữ job được bảo vệ, target backlog và số đo nguồn.
- M4: dữ liệu thô benchmark từng lượt (submit/start/finish, `queueWaitMs`, `durationMs`, bytes in/out), cấu hình mỗi lượt, kết quả chaos AWS (thời điểm kill, nhận lại, receive count, DLQ message).
- Không lưu access key, token, presigned URL còn hiệu lực hoặc receipt handle vào bản bằng chứng chia sẻ. Chỉ xóa tài nguyên test sau khi đã lưu bằng chứng và xác nhận đúng tài nguyên của ca đó.

**Bằng chứng local đã có (thư mục bị `.gitignore`, không commit):**

| Thư mục | Nội dung chính |
|---|---|
| `local/chaos-logs/20261031T151751Z/` | summary 4 kịch bản, log worker/container, trạng thái queue, output playback |
| `local/e2e-logs/20261031T151346Z-worker/` | summary E2E worker, object header, HLS/thumbnail tải về, log worker |
| `local/e2e-logs/20261031T144437Z-api/` | summary FAIL tại login — blocker A4 (không chứa token/presigned URL) |
| `local/bench-logs/20261031T150839Z/` | `summary.txt`, `aggregate.txt`, `results.csv`, `jobs.csv`, log từng worker, message/submit thô |

### 6.2. Kết luận cuối tuần

| Mốc | Kết luận 2026-10-31 | Căn cứ / việc còn lại |
|---|---|---|
| **A4 — E2E qua API thật** | **CHƯA ĐẠT** | Worker integration + runner đã pass, nhưng `make e2e` dừng ở `POST /api/auth/login` vì backend chưa có code. Phương cần backend login/project/video/confirm; Vũ chạy lại `make e2e` và lưu trace khi có |
| **A5 — Ownership + chaos** | **CHƯA ĐẠT mốc nhóm** (phần code/kiểm thử local đã xong) | Chaos container pass, hai worker cùng job đã đo và ghi giới hạn at-least-once; còn thiếu quyết định/xác nhận của Phương và nhóm để chốt (lease hay chấp nhận giới hạn). Không triển khai lease khi contract còn `DRAFT` |
| **M2 — Pipeline AWS** | **CHƯA ĐẠT** | Chưa có AWS/credential/hạ tầng. Cần Nguyên: ECR + image digest, IAM role, bucket/queue/bảng/region, instance; Lưu: xác nhận image/compose. Runbook và mẫu bằng chứng ở [[Week3-AWS-runbook]] |
| **M3 — Scale + C5** | **CHƯA ĐẠT** | Chưa có ASG/CloudWatch để chứng minh scale 1→2→1 và scale-in protection; C5 handoff đã soạn nhưng chưa có log group/metric filter thật, target backlog 23 chỉ là số local tạm thời |
| **M4 — Benchmark/chaos AWS, báo cáo, đóng băng** | **CHƯA ĐẠT** | Benchmark local 1/2 worker có dữ liệu thô nhưng không thay thế benchmark AWS on-demand; chưa có chaos AWS/DLQ AWS, chưa có hai lần diễn tập, chưa đóng băng chính thức. Ứng viên đóng băng local: tag `tvc-worker:week3-local-freeze`, image `sha256:613656ca…`, source `b5cf8ea` + working tree |

**Việc chặn nộp (tuần 10) và người phụ trách:** Phương — backend A3 để mở A4; Nguyên — môi trường AWS (ECR/IAM/ASG/CloudWatch) cho M2/M3/M4; Lưu — image/compose/harness AWS; Vũ — chạy lại E2E API, pipeline/benchmark/chaos AWS, hoàn tất phần Worker báo cáo và đóng băng sau khi có bằng chứng. Đây là **blocker môi trường**, không phải cắt phạm vi: không đánh dấu M2/M3/M4 đạt khi chưa chạy trên AWS.

## 7. Phụ thuộc và rủi ro tuần 3

| Phụ thuộc / rủi ro | Tác động | Cách xử lý |
|---|---|---|
| Backend confirm (Phương) vẫn trễ | A4/M2 tiếp tục bị chặn, dồn toàn bộ tuần | Nhắc đầu tuần; nếu cuối ngày 1 vẫn chưa có, chạy M2 bằng message theo schema (ghi rõ "worker integration") và điều phối nhóm; vẫn giữ E2E thật là gate nghiệm thu |
| Docker Desktop không share `Documents` (Lưu) | Chaos/e2e không chạy trong container | Lưu đưa init vào image/compose hoặc xử lý file sharing; không dùng workaround `/tmp` làm bằng chứng nghiệm thu |
| AWS chưa sẵn sàng (Nguyên: ECR/IAM/ASG) | Trễ M2 → dồn M3/M4 → va hạn đóng băng | Yêu cầu môi trường từ ngày 1; nếu thiếu Spot capacity, dùng on-demand tạm và ghi rõ |
| Ownership chọn lease → tăng khối lượng | Ăn thời gian của M3/M4 | Review contract trước khi code; nếu không kịp, chọn phương án (b) ghi giới hạn vào báo cáo — đúng theo [[docs/docs-worker/4. Timelines]] mục 6 |
| Spot/cold start làm phép đo thiếu ổn định | Số liệu benchmark không so sánh được | Đo on-demand, pre-warm worker; không đổi cấu hình FFmpeg/image giữa các lượt |
| Video test ngắn, pipeline xong nhanh | Scale không trigger, benchmark không đo được | Dùng dataset nhiều job đã chốt với Lưu để tạo backlog đủ dài; không dùng video vượt giới hạn validation |
| Vũ đồng thời làm frontend A6 + R1 | Quá tải trong tuần nặng nhất | Ưu tiên pipeline/reliability/benchmark; frontend chỉ mức tối thiểu (upload/status/player); xin hỗ trợ khung frontend theo timeline chung |
| M2 trễ quá giữa tuần | M3/M4 bị nén, va đóng băng | Báo nhóm ngay; cắt theo giảm phạm vi: bỏ min 0, giữ tối thiểu scale 1→2→1, benchmark 1/2 worker một lượt, một ca chaos AWS; **không đẩy benchmark sang tuần 10** |
| Số liệu benchmark không kịp phân tích | Báo cáo thiếu dữ liệu thật | Lưu dữ liệu thô ngay mỗi lượt; phân tích hạn chế (bảng tổng hợp), không đợi đến cuối tuần mới tổng hợp |

Nếu M3/M4 đều nguy hiểm trễ: thứ tự cắt lần lượt là min 0 → diễn tập thứ hai → tinh chỉnh báo cáo; **không cắt** benchmark 1/2 worker, một ca recovery AWS và đóng băng cuối tuần 9.

## 8. Liên kết nhanh

- [[docs/docs-worker/work/Week1|Week1]] · [[docs/docs-worker/work/Week2|Week2]] — bằng chứng nền và các khoản nợ chuyển tiếp.
- [[docs/docs-worker/1. Introduction|Giới thiệu Worker]] — phạm vi và tiêu chí worker chạy được.
- [[docs/docs-worker/2. Business|Nghiệp vụ Worker]] — validation, retry, duplicate, shutdown.
- [[docs/docs-worker/3. Architecture|Kiến trúc Worker]] — pipeline, FFmpeg, env, IAM, thư mục tạm.
- [[docs/docs-worker/4. Timelines|Timeline Worker]] — mục 4: toàn bộ yêu cầu tuần 9 (M3/M4) của kế hoạch này.
- [[docs/docs-worker/5. Contact docs|Hợp đồng giao tiếp Worker]] — bảng ACK/retry, SQS, state/assets và log.
- [[docs/introduction/1. Timelines|Timeline chung v4]] — epic, effort, chủ sở hữu, M0–M5 và mục 7 giảm phạm vi.
- [[docs/docs-cloud/2. Timelines|Timeline Cloud]] — phối hợp C2/C3/C5 và điều kiện M3/M4 phía Cloud.
- `docs/contracts/` — nguồn sự thật cho mọi contract.
- [AWS — SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html) và [dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html) — nền tảng cho chaos/retry; các giá trị 120s/300s/3 lần nhận là cấu hình của dự án.
