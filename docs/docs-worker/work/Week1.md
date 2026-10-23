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

- [ ] Rà `worker/` và các POC đã có; đánh dấu phần đã xong kèm bằng chứng (lệnh đã chạy, output); ước lượng lại phần thiếu so với baseline 4,0 pd của A4.
- [ ] Cùng **Phương** chốt 5 file trong `docs/contracts/`: `video-state-machine.md`, `sqs-job-message.schema.json`, `s3-key-convention.md`, `dynamodb-tables.md`, `upload-limits.md`; gửi cả 4 người review (tiêu chí "Xong khi" của epic A1).
- [ ] Đặc biệt chốt với Phương 2 điểm còn mở:
  - Danh sách codec được chấp nhận (h264 + codec phổ biến trong mp4/mov/mkv/webm) → errorCode `CODEC_UNSUPPORTED`.
  - Quyền sở hữu job khi hai worker cùng nhận message (lease/`attemptCount`) để tránh xử lý đôi.
- [ ] Bắt đầu POC FFmpeg theo lệnh ở [[3. Architecture]] mục 3.5: chạy trên file local, kiểm tra 2 playlist rendition sinh ra đúng.

### Ngày 2 — Hoàn thiện POC FFmpeg + ffprobe + video mẫu

- [ ] POC đủ 4 đầu ra: `360p/index.m3u8` + segments, `720p/index.m3u8` + segments, thumbnail (`-ss 1` một frame, fallback `-ss 0` nếu video < 1 giây), master playlist viết tay (`RESOLUTION` lấy từ ffprobe).
- [ ] Thử **phát video thật** qua playlist master (VLC hoặc browser có HLS) — bằng chứng bắt buộc của POC.
- [ ] POC ffprobe: `ffprobe -v error -print_format json -show_format -show_streams` đọc duration/height/codec/hasVideo; thử bộ case theo [[2. Business]] mục 2.4.1:
  - video > 5 phút → `DURATION_EXCEEDED`; video 1440p → `HEIGHT_EXCEEDED`; file text đổi đuôi → `NOT_A_VIDEO`; video không có audio (vẫn phải transcode được nhờ `-map 0:a?`).
- [ ] Kiểm tra trường hợp video nguồn ≤ 360 chiều cao: bỏ bước scale cho rendition đó (không phóng to video nhỏ) — logic chọn map trong `transcode`.
- [ ] Chuẩn bị **bộ video mẫu cố định** (`worker/testdata/`, các file 5–10s được phép commit; file lớn để `local/samples/` không commit) và **ghi lại cấu hình FFmpeg** dùng cho mọi thực nghiệm sau.

### Ngày 3 — Khung Go + LocalStack + bàn giao

- [ ] Dựng khung `worker/` theo cấu trúc [[3. Architecture]] mục 3.2:

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

- [ ] Chạy đường đi tối thiểu trên **LocalStack** (khi M0 của Lưu sẵn sàng): nhận một message tự tạo theo schema → tải input từ `tvc-local-input` → ffprobe → FFmpeg → đẩy output `tvc-local-output` đúng key/Content-Type → `PENDING -> PROCESSING -> COMPLETED` trong `tvc-local-videos` → ACK.
- [ ] Viết ít nhất unit test đầu tiên: bảng case ffprobe giả cho `validate` + mapping key/Content-Type (`make test`).
- [ ] **Bàn giao cho Lưu/Nguyên** (đúng hạn cuối Sprint 1):
  - Lệnh build/run: `go build ./cmd/worker` + cách chạy thẳng bằng `make infra` khi dev.
  - Bảng biến môi trường đầy đủ ([[3. Architecture]] mục 3.8) để Lưu viết Dockerfile.
  - Danh sách quyền IAM least-privilege (mục 3.9) để Nguyên viết policy (C2); review chéo sau.
- [ ] Cập nhật mục 6 tài liệu này + báo tiến độ nhóm; đánh dấu rủi ro nếu M0/Dockerfile trễ.

## 4. Bàn giao cuối tuần (đầu ra cho các bên)

| Bên | Nhận gì | Dùng để làm gì |
|---|---|---|
| Phương | Nhận xét chốt 5 file `docs/contracts/`; quy ước nhận message ([[5. Contact docs]] mục 5.1) | Backend gửi job đúng schema |
| Lưu | Lệnh build/run, bảng env, cách log ra stdout JSON | Dockerfile `tvc-worker` (C0) |
| Nguyên | Danh sách IAM mục 3.9 (kể cả các quyền cố tình **không** có) | Policy + role worker (C2) |
| Cả nhóm | Bằng chứng POC (playlist phát được) + video mẫu | M1 và demo |

## 5. Tiêu chí hoàn thành tuần 1 (đối chiếu M1)

- [ ] 5 file `docs/contracts/` được cả 4 người review; mọi thay đổi sau đó qua PR, không "sửa lặng".
- [ ] POC FFmpeg phát được video thật qua master playlist: đủ 2 rendition, segment, thumbnail, đúng cấu hình một-lần-giải-mã (`split`, preset `veryfast`, không 1080p).
- [ ] ffprobe chặn đúng 4 case sai giới hạn ở ngày 2; không chạy FFmpeg khi validate fail.
- [ ] Khung Go nhận được message thật từ LocalStack và ghi được `PROCESSING` có điều kiện (`status <> COMPLETED`).
- [ ] Tài liệu bàn giao (build/run/env/IAM) đã gửi Lưu/Nguyên **trước khi kết thúc Sprint 1**.
- [ ] Ước lượng lại A4/A5 phần còn lại đã ghi lại ở mục 6.

## 6. Tiến độ thực tế (cập nhật sau mỗi buổi làm)

| Ngày | Đã làm | Bằng chứng | Vấn đề / cần hỗ trợ |
|---|---|---|---|
| Ngày 1 | | | |
| Ngày 2 | | | |
| Ngày 3 | | | |

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
