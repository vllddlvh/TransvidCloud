# Work — Tuần 1: Nền tảng Worker (Sprint 1)

> Tuần 1 trong thư mục `work/` ứng với **Sprint 1 — tuần 7 học kỳ** của [timeline chung v4](../../introduction/1.%20Timelines.md) và mục 2 "Tuần 7 — Nền tảng và tích hợp local" của [[4. Timelines]].
> Tài liệu này ghi **kế hoạch công việc chi tiết** của Vũ cho phần Worker; tiến độ thực tế cập nhật ở mục 6.

## 1. Mục tiêu

Kết thúc tuần 1 đạt được phần Worker của mốc **M1** (Sprint 1, tuần 7):

1. Contract được cả nhóm review và chốt (A1 — làm chung với Phương).
2. **POC FFmpeg** chạy độc lập trên máy cá nhân: HLS 360p + 720p + master playlist + thumbnail từ một video thật.
3. **Khung Go** (`worker/`) chạy được ở máy cá nhân, nối được LocalStack: nhận message SQS, đọc input, ghi output/state.
4. **Bàn giao sớm** cho Lưu/Nguyên: lệnh build/run, biến môi trường, danh sách quyền IAM — không chờ pipeline hoàn chỉnh.
5. Ước lượng lại khối lượng còn lại của A4/A5 sau khi rà hiện trạng (baseline A4 = 4,0 pd).

Không thuộc phạm vi tuần 1 (dồn sang tuần 2/Sprint 2): heartbeat, xử lý message trùng hoàn chỉnh, SIGTERM, DLQ, chạy trên AWS — xem [[4. Timelines]] mục 3.

## 2. Hiện trạng đầu tuần

| Hạng mục | Trạng thái lúc lập kế hoạch |
|---|---|
| `worker/` | Chỉ có `README.md` và `testdata/README.md`; **chưa có code Go**, chưa có `go.mod` |
| POC FFmpeg | Chưa có bằng chứng chạy; cấu hình ở [[3. Architecture]] mục 3.5 mới là thiết kế |
| Video mẫu | `local/samples/` và `worker/testdata/` chưa có bộ test cố định (mục 3.13) |
| LocalStack | Phụ thuộc M0 của Lưu (`make infra` + `make smoke`) |

→ Tuần 1 là tuần **nền**: mọi việc mở khóa cho Sprint 2 (pipeline hoàn chỉnh, M2 cuối tuần 8).

## 3. Công việc chi tiết theo ngày

Sprint 1 tương ứng **ngày 1–3** làm việc (theo timeline chung). Nếu local chưa sẵn sàng, hoán đổi ngày 1 và ngày 2 — POC FFmpeg không phụ thuộc AWS.

### Ngày 1 — Rà hiện trạng, chốt contract, mở POC

- [x] Rà `worker/` và các POC đã có; đánh dấu phần đã xong kèm bằng chứng (lệnh đã chạy, output); ước lượng lại phần thiếu so với baseline 4,0 pd của A4.
- [x] Cùng **Phương** chốt 5 file trong `docs/contracts/`: `video-state-machine.md`, `sqs-job-message.schema.json`, `s3-key-convention.md`, `dynamodb-tables.md`, `upload-limits.md`; gửi cả 4 người review (tiêu chí "Xong khi" của epic A1).
- [x] Đặc biệt chốt với Phương 2 điểm còn mở:
  - Danh sách codec được chấp nhận (h264 + codec phổ biến trong mp4/mov/mkv/webm) → errorCode `CODEC_UNSUPPORTED`.
  - Quyền sở hữu job khi hai worker cùng nhận message (lease/`attemptCount`) để tránh xử lý đôi.
- [x] Bắt đầu POC FFmpeg theo lệnh ở [[3. Architecture]] mục 3.5: chạy trên file local, kiểm tra 2 playlist rendition sinh ra đúng.
  - *Bằng chứng:* chạy lệnh mục 3.5 trên `local/samples/poc/source.mov` (video thật 720p, 9,1s) — sinh đủ `360p/index.m3u8` + 2 segment, `720p/index.m3u8` + 2 segment (mục 6).

### Ngày 2 — Hoàn thiện POC FFmpeg + ffprobe + video mẫu

- [x] POC đủ 4 đầu ra: `360p/index.m3u8` + segments, `720p/index.m3u8` + segments, thumbnail (`-ss 1` một frame, fallback `-ss 0` nếu video < 1 giây), master playlist viết tay (`RESOLUTION` lấy từ ffprobe).
- [x] Thử **phát video thật** qua playlist master (VLC hoặc browser có HLS) — bằng chứng bắt buộc của POC.
  - *Bằng chứng:* serve `local/samples/poc/out` bằng http.server:8099, mở `player.html` (hls.js) trong Chrome — MANIFEST_PARSED, chuyển level 1 (720p), phát hết 9,17s (`currentTime == duration`, `videoWidth=1280`). Xem mục 6.
- [x] POC ffprobe: `ffprobe -v error -print_format json -show_format -show_streams` đọc duration/height/codec/hasVideo; thử bộ case theo [[2. Business]] mục 2.4.1:
  - video > 5 phút → `DURATION_EXCEEDED`; video 1440p → `HEIGHT_EXCEEDED`; file text đổi đuôi → `NOT_A_VIDEO`; video không có audio (vẫn phải transcode được nhờ `-map 0:a?`).
- [x] Kiểm tra trường hợp video nguồn ≤ 360 chiều cao: bỏ bước scale cho rendition đó (không phóng to video nhỏ) — logic chọn map trong `transcode`.
  - *Bằng chứng:* nguồn 640x360 → cả hai rendition xuất 640x360 (không upscale), exit 0 (mục 6).
- [x] Chuẩn bị **bộ video mẫu cố định** (`worker/testdata/`, các file 5–10s được phép commit; file lớn để `local/samples/` không commit) và **ghi lại cấu hình FFmpeg** dùng cho mọi thực nghiệm sau.
  - `worker/testdata/`: `sample-10s-720p.mp4`, `sample-360p.mp4`, `sample-no-audio.mp4`, `fake-video.mp4` (tổng ~2,5 MB) + README ghi cấu hình FFmpeg 8.0.1 và lệnh sinh lại.
  - `local/samples/` (không commit, đã thêm `.gitignore`): `sample-long-301s.mp4`, `sample-1440p.mp4`, `poc/`.

### Ngày 3 — Khung Go + LocalStack + bàn giao

- [x] Dựng khung `worker/` theo cấu trúc [[3. Architecture]] mục 3.2:

  ```text
  worker/cmd/worker/main.go     # đọc config, dựng dependency, supervisor
  worker/internal/config/       # đọc env, validate biến bắt buộc (thiếu bucket/queue/bảng → thoát ngay)
  worker/internal/consumer/     # ReceiveMessage long polling 20s, MaxNumberOfMessages=1
  worker/internal/validate/     # wrapper ffprobe → ProbeResult, so giới hạn
  worker/internal/transcode/    # build args FFmpeg theo POC ngày 2
  worker/internal/storage/      # DownloadToFile, UploadDir đúng Content-Type
  worker/internal/state/        # GetStatus, MarkProcessing, MarkCompleted, MarkFailed
  worker/internal/logging/      # log JSON có jobId/videoId/projectId
  ```

  Kèm `job/` (pipeline) và `shutdown/` (bắt SIGTERM/SIGINT dừng vòng nhận) theo mục 3.2. Heartbeat/DLQ/SIGTERM-trả-message để Sprint 2.

- [x] Chạy đường đi tối thiểu trên **LocalStack**: nhận một message tự tạo theo schema → tải input từ `tvc-local-input` → ffprobe → FFmpeg → đẩy output `tvc-local-output` đúng key/Content-Type → `PENDING -> PROCESSING -> COMPLETED` trong `tvc-local-videos` → ACK.
  - *Bằng chứng:* log `job_finished status=COMPLETED durationMs=3555`; DynamoDB `status=COMPLETED` + đủ 4 assets; 8 object trong `tvc-local-output`; Content-Type đúng (`.m3u8`/`.ts`/`.jpg`); queue về 0 message; gửi lại message trùng → `skipped_completed`, attemptCount không tăng (mục 6).
- [x] Viết ít nhất unit test đầu tiên: bảng case ffprobe giả cho `validate` + mapping key/Content-Type (`make test`).
  - `make test` xanh toàn bộ package worker; có thêm test cho `config`, `transcode` (chọn map theo chiều cao nguồn, dims, master), `job` (parse message), `storage` (Content-Type/Cache-Control) và test ffprobe thật trên `worker/testdata/` (tự skip nếu thiếu ffprobe).
- [ ] **Bàn giao cho Lưu/Nguyên** (đúng hạn cuối Sprint 1): nội dung đã soạn sẵn ở mục 6.1 — **chờ Vũ gửi** cho nhóm:
  - Lệnh build/run: `go build ./cmd/worker` + cách chạy thẳng bằng `make infra` khi dev.
  - Bảng biến môi trường đầy đủ ([[3. Architecture]] mục 3.8) để Lưu viết Dockerfile.
  - Danh sách quyền IAM least-privilege (mục 3.9) để Nguyên viết policy (C2); review chéo sau.
- [x] Cập nhật mục 6 tài liệu này + báo tiến độ nhóm; đánh dấu rủi ro nếu M0/Dockerfile trễ.
  - *Rủi ro đã ghi:* xem mục 6.2 — Docker Desktop không mount được `~/Documents` (TCC), image `localstack:latest` đòi license, init script thiếu bit +x (đã sửa).

## 4. Bàn giao cuối tuần (đầu ra cho các bên)

| Bên | Nhận gì | Dùng để làm gì |
|---|---|---|
| Phương | Nhận xét chốt 5 file `docs/contracts/`; quy ước nhận message ([[5. Contact docs]] mục 5.1) | Backend gửi job đúng schema |
| Lưu | Lệnh build/run, bảng env, cách log ra stdout JSON | Dockerfile `tvc-worker` (C0) |
| Nguyên | Danh sách IAM mục 3.9 (kể cả các quyền cố tình **không** có) | Policy + role worker (C2) |
| Cả nhóm | Bằng chứng POC (playlist phát được) + video mẫu | M1 và demo |

## 5. Tiêu chí hoàn thành tuần 1 (đối chiếu M1)

- [x] 5 file `docs/contracts/` được cả 4 người review; mọi thay đổi sau đó qua PR, không "sửa lặng". *(hoàn tất ngày 1 — làm chung với Phương)*
- [x] POC FFmpeg phát được video thật qua master playlist: đủ 2 rendition, segment, thumbnail, đúng cấu hình một-lần-giải-mã (`split`, preset `veryfast`, không 1080p). *(bằng chứng mục 6, ngày 2)*
- [x] ffprobe chặn đúng 4 case sai giới hạn ở ngày 2; không chạy FFmpeg khi validate fail. *(bằng chứng mục 6, ngày 2; logic nằm trong `job.Run` — validate fail thì dừng trước `transcode_started`)*
- [x] Khung Go nhận được message thật từ LocalStack và ghi được `PROCESSING` có điều kiện (`status <> COMPLETED`). *(bằng chứng mục 6, ngày 3: `PENDING -> PROCESSING -> COMPLETED`, retry qua `FAILED_*` đều bị `ConditionExpression` chặn đúng)*
- [ ] Tài liệu bàn giao (build/run/env/IAM) đã gửi Lưu/Nguyên **trước khi kết thúc Sprint 1**. *(nội dung soạn sẵn tại mục 6.1 — chờ Vũ gửi)*
- [x] Ước lượng lại A4/A5 phần còn lại đã ghi lại ở mục 6. *(mục 6.3)*

## 6. Tiến độ thực tế (cập nhật sau mỗi buổi làm)

| Ngày | Đã làm | Bằng chứng | Vấn đề / cần hỗ trợ |
|---|---|---|---|
| Ngày 1 | Rà hiện trạng + chốt contract (với Phương) xong; **POC FFmpeg mục 3.5 chạy thành công trên video thật** (`local/samples/poc/source.mov` — quay màn hình 720p 9,1s): một lần chạy sinh `hls/360p/` + `hls/720p/`, mỗi rendition `index.m3u8` + 2 segment ~4s | `ffmpeg -y -i local/samples/poc/source.mov -filter_complex "[0:v]split=2[v1][v2]; [v1]scale=-2:360[v360]; [v2]scale=-2:720[v720]" ... -f hls ...` → exit 0; `ffprobe` các playlist thấy 640x360 và 1280x720 | `0:a?` phải để trong ngoặc kép khi chạy qua zsh (glob `?`) — không ảnh hưởng code Go |
| Ngày 2 | POC đủ 4 đầu ra; **phát thật qua master playlist**; ffprobe chặn đúng 4 case; case nguồn ≤360 không upscale; bộ video mẫu cố định + ghi cấu hình FFmpeg 8.0.1 | (1) serve `local/samples/poc/out` trên :8099, mở `player.html` (hls.js) trong Chrome: `MANIFEST_PARSED levels=2` → `playing level 1 (720p)` → phát hết: `currentTime=9.166666 == duration`, `videoWidth=1280`. (2) `sample-long-301s` → duration=301 → `DURATION_EXCEEDED`; `sample-1440p` → height=1440 → `HEIGHT_EXCEEDED`; `fake-video.mp4` → ffprobe exit 1 (`moov atom not found`) → `NOT_A_VIDEO`; `sample-no-audio` → streams `['video']`, transcode với `-map 0:a?` exit 0. (3) nguồn 640x360 bỏ scale → cả 2 rendition 640x360, exit 0 | — |
| Ngày 3 | Khung Go `worker/` đủ cấu trúc 3.2 (9 package) + `go.mod`; **chạy full path trên LocalStack**: nhận message → tải input → ffprobe → FFmpeg → upload 8 object đúng Content-Type → `COMPLETED` → ACK; idempotency (gửi lại message → `skipped_completed`); `make test` xanh; bàn giao soạn ở 6.1 | Log worker (JSON): `job_received → mark_processing → downloaded bytesIn=1520913 → validated duration=10 height=720 codec=h264 → transcode_started → transcode_done → uploaded objects=8 bytesOut=7103450 → job_finished status=COMPLETED durationMs=3555 attempt=1`; DynamoDB `status=COMPLETED`, `assets` đủ 4 key, `attemptCount` tăng đúng qua 3 lần retry (`PENDING→PROCESSING→FAILED_STORAGE→FAILED_TRANSCODE→PROCESSING→COMPLETED`); Content-Type: `.m3u8→application/vnd.apple.mpegurl`, `.ts→video/mp2t`, `.jpg→image/jpeg`; queue 0 message. `go test ./...`: 5 package ok (validate/config/transcode/job/storage + test ffprobe thật tự skip). SIGTERM → log `shutdown`, exit 0 | Xem mục 6.2 — 3 vấn đề local đã xử lý; cần Lưu: Dockerfile để ghép `make up`; cần Nguyên: policy IAM theo 3.9 |

### 6.1. Bàn giao cho Lưu/Nguyên (nội dung sẵn sàng — chờ Vũ gửi)

**Lưu (Dockerfile `tvc-worker`, epic C0):**

```bash
# Build / test
cd worker && go build ./cmd/worker && go test ./...
# Chạy dev trên LocalStack (sau khi `make infra` đã khởi tạo tài nguyên)
AWS_ENDPOINT_URL=http://localhost:4566 AWS_REGION=ap-southeast-1 \
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
TVC_INPUT_BUCKET=tvc-local-input TVC_OUTPUT_BUCKET=tvc-local-output \
TVC_DDB_VIDEOS=tvc-local-videos \
TVC_JOBS_QUEUE_URL=http://localhost:4566/000000000000/tvc-local-jobs \
./bin/worker
```

- Đầy đủ biến môi trường (bắt buộc 4 biến, tùy chọn 9 biến): [[3. Architecture]] mục 3.8. Image cần `ffmpeg` + `ffprobe` trong PATH (worker thoát exit 1 nếu thiếu).
- Log **stdout một dòng JSON** (`ts, level, msg, jobId, videoId, projectId, ...`) — dùng log driver Docker mặc định, không cần agent.
- Lưu ý từ lúc test local: image `localstack/localstack:latest` (2026.10) **đòi license** — chạy local nên pin `LOCALSTACK_IMAGE=localstack/localstack:3.8.1` (xem 6.2).

**Nguyên (policy IAM + role worker, epic C2):**

- Danh sách least-privilege đầy đủ: [[3. Architecture]] mục 3.9 — gồm cả các quyền **cố ý không cấp** (`s3:PutObject` vào bucket input, `sqs:SendMessage`, các bảng users/projects/apikeys).
- Worker không đọc secret nào; danh tính = IAM role instance profile. Code không phân nhánh theo môi trường — mọi khác biệt nằm ở env.

### 6.2. Vấn đề local gặp và cách xử lý (tuần 1)

| Vấn đề | Trạng thái | Xử lý / ai |
|---|---|---|
| `make infra` fail: Docker Desktop không mount được `~/Documents` (`mkdir /host_mnt/Users/dllv/Documents: operation not permitted` — macOS TCC chặn Docker Desktop truy cập thư mục Documents) | **Cần Vũ xử lý máy cá nhân** | System Settings → Privacy & Security → Files and Folders → cho Docker Desktop quyền Documents (hoặc restart Docker Desktop). Để test tuần này đã copy init script sang `/tmp` chạy container tạm |
| Image `localstack/localstack:latest` (2026.10.2) thoát với lỗi "License activation failed" — image mới đòi auth token | **Ảnh hưởng mọi người** | Chạy local nên pin `LOCALSTACK_IMAGE=localstack/localstack:3.8.1` (bản community cuối) — gợi ý Lưu cập nhật mặc định trong `docker-compose.yml` |
| `local/localstack/init/01-resources.sh` commit thiếu bit execute (mode 100644) → LocalStack không chạy được init | **Đã sửa trong PR này** (`chmod +x`) | Thuộc C0 của Lưu nhưng sửa nhỏ, đã làm luôn — ghi chú khi review |
| `purge-queue` của LocalStack xóa message mới gửi trong ~60s sau khi purge (đã quan sát) | Không cần xử lý | Chỉ ảnh hưởng thao tác test; SQS thật có hành vi tương tự theo tài liệu AWS |

### 6.3. Ước lượng lại A4/A5 (sau tuần 1, baseline A4 = 4,0 pd)

| Epic | Baseline | Còn lại (đề xuất của Vũ) | Nội dung còn lại |
|---|---|---|---|
| A4 Worker pipeline | 4,0 | **~1,0 pd** | Ghép `make up` với Dockerfile của Lưu, chạy lại kịch bản e2e qua compose, sửa lỗi tích hợp phát sinh. Checklist A4 còn thiếu đúng mục "Xong khi: `make up` … ra `COMPLETED`" |
| A5 Độ tin cậy job | 2,0 (Vũ 1,5 + Phương 0,5) | **~1,5 pd** (Vũ) | Heartbeat ~120s (chưa làm — ngoài phạm vi tuần 1 theo kế hoạch), SIGTERM trả message sớm (visibility 30s), hoàn thiện `chaos-kill-worker.sh`, kiểm chứng DLQ sau 3 lần nhận. Idempotency + `FAILED_*` + ghi có điều kiện đã làm xong từ tuần 1 |

*Lưu ý: các số trên là đề xuất của Vũ để cả nhóm đối chiếu lúc họp; không phải cam kết mới.*

Không đánh dấu hoàn thành chỉ vì đã tới cuối tuần/mốc; mỗi mục phải kèm bằng chứng (lệnh, log, output).

## 7. Phụ thuộc và rủi ro tuần 1

| Phụ thuộc/rủi ro | Tác động | Xử lý |
|---|---|---|
| M0 (LocalStack `make infra`) chưa xong | Ngày 3 không tích hợp local được | Làm POC FFmpeg/ffprobe độc lập trước; tích hợp local là điều kiện bắt buộc trên đường tới M2, không dời sang tuần mới |
| Dockerfile chưa có (Lưu) | Chưa chạy container | Bàn giao env/build sớm (ngày 3) là việc của Vũ; chạy `go run` trực tiếp khi dev |
| Contract chưa chốt với Phương | Consumer chưa viết đúng schema | Tạm dùng schema nháp để dựng khung; khóa schema trước khi viết `job` pipeline ở Sprint 2 |
| FFmpeg POC lỗi (filter `split`, 2 output HLS) | Chậm M1 | Được phép tách thành 2 lượt chạy FFmpeg nếu đủ tài nguyên; ghi rõ cấu hình để đo nhất quán |

## 8. Liên kết nhanh

- [[1. Introduction]] — phạm vi, "worker chạy được" (mục 1.7)
- [[2. Business]] — vòng đời job, các nhánh lỗi
- [[3. Architecture]] — lệnh FFmpeg/ffprobe, env, IAM, cấu trúc module
- [[4. Timelines]] — vị trí tuần 1 trong kế hoạch 7 tuần→10
- [[5. Contact docs]] — schema message, các thao tác DynamoDB
- `docs/contracts/` — nguồn sự thật cho mọi contract
