# Hướng dẫn Cài đặt, Vận hành & Tự Kiểm thử Toàn hệ thống (Backend – Worker – Frontend) trên LocalStack

Tài liệu này hướng dẫn chi tiết từng bước để dựng toàn bộ hệ thống **TransVidCloud** trên máy local (LocalStack + Spring Boot Backend + Go FFmpeg Worker + Nginx Edge + React/Vite Frontend) và tự tay kiểm thử mọi luồng nghiệp vụ trước khi bàn giao cho đội Cloud.

---

## 1. Kiến trúc Chạy Local (Full Stack)

```
Trình duyệt (Browser)
  │
  ├──► http://localhost:8000 (Nginx Edge — giả lập CloudFront)
  │      ├── /api/*    ──► container `backend:8080` (Spring Boot 3.4.5 / Java 21)
  │      ├── /media/*  ──► container `localstack:4566/tvc-local-output/*` (S3 Output Bucket)
  │      └── /*        ──► `host.docker.internal:5173` (Frontend Vite Dev Server trên Host)
  │
  └──► PUT http://localhost:4566/tvc-local-input/... (Upload trực tiếp file video lên S3 qua Presigned URL)
         │
         ▼
  Backend (`/api/videos/{id}/confirm`)
         │  (DynamoDB Transactional Outbox -> SQS `tvc-local-jobs`)
         ▼
  Worker (`transvidcloud-worker` — Go + FFmpeg)
         ├── Tải video gốc từ `tvc-local-input`
         ├── Kiểm tra hợp lệ (`ffprobe`: codec, thời lượng <= 300s, chiều cao <= 1080p)
         ├── Transcode HLS Adaptive Bitrate (`360p`, `720p`, `master.m3u8`) + Thumbnail (`default.jpg`)
         ├── Đẩy 8+ file output lên `tvc-local-output`
         └── Cập nhật trạng thái `COMPLETED` (hoặc `FAILED_*`) vào bảng DynamoDB `tvc-local-videos`
```

---

## 2. Yêu cầu Môi trường (Prerequisites)

- **Docker & Docker Compose v2** (`docker compose version`)
- **Node.js >= 20.19** & **npm** (để chạy giao diện Frontend)
- **cURL**, **Python 3** (có sẵn trên Linux/macOS để chạy script E2E)
- *(Không bắt buộc cài Java/Maven, Go hay FFmpeg trên máy host nếu chạy qua Docker Compose, vì `backend/Dockerfile` và `worker/Dockerfile` đã đóng gói sẵn toàn bộ runtime).*

---

## 3. Các bước Khởi động Toàn bộ Hệ thống (Step-by-Step Setup)

### Bước 1: Chuẩn bị file `.env` ở thư mục gốc
Tại thư mục gốc `/home/phuong/workspace/TransvidCloud`:
```bash
cp -n .env.example .env
```
> **Lưu ý quan trọng:** Biến `JWT_SECRET` trong `.env` phải dài tối thiểu **32 ký tự** (ví dụ: `JWT_SECRET=dev-only-change-me-must-be-at-least-32-bytes-long`). Mặc định trong `.env.example` và `docker-compose.yml` đã được cấu hình chuẩn.

### Bước 2: Khởi chạy Backend + Worker + LocalStack + Nginx Edge
Chạy lệnh sau tại thư mục gốc:
```bash
make up
```
*(Lệnh này tương đương `docker compose --profile full up -d --build`).*

Kiểm tra các container đã lên `healthy` / `running`:
```bash
docker compose --profile full ps
```
Kiểm tra tài nguyên LocalStack (3 S3 buckets, 2 SQS queues, 4 bảng DynamoDB) và Healthcheck của Backend:
```bash
make smoke
curl -s http://localhost:8080/api/actuator/health
curl -s http://localhost:8000/api/actuator/health
```
Kết quả mong đợi: `make smoke` báo `OK` toàn bộ 9 mục và cả 2 lệnh `curl` đều trả về `{"status":"UP","groups":["liveness","readiness"]}`.

### Bước 3: Khởi chạy Giao diện Frontend ở chế độ kết nối Backend thật (`real` mode)
Mở một cửa sổ terminal mới, di chuyển vào thư mục `frontend/`:
```bash
cd frontend
npm install
npm run dev:real
```
> **Giải thích:**
> - `npm run dev:real` khởi chạy Vite ở chế độ `VITE_API_MODE=real` (tắt hoàn toàn MSW mock, gọi API thật tới Backend và S3 LocalStack).
> - Bạn có thể mở trình duyệt tại **`http://localhost:8000`** (đi qua Nginx Edge giống hệt kiến trúc CloudFront trên AWS) hoặc **`http://localhost:5173`** (Vite dev server đã cấu hình proxy `/api` và `/media`). Khuyến nghị dùng **`http://localhost:8000`**.

---

## 4. Hướng dẫn Tự Test trên Trình duyệt (Manual UI Testing)

### Kịch bản 1: Đăng ký tài khoản & Đăng nhập (SaaS Auth)
1. Mở trình duyệt truy cập: **`http://localhost:8000/login`**
2. Ở khung bên phải, bấm vào liên kết **"Create account"** (bên dưới nút Sign in).
3. Nhập thông tin bất kỳ, ví dụ:
   - **Full name:** `Phuong Tester`
   - **Email address:** `demo@transvidcloud.test`
   - **Password:** `DemoPass123!` (tối thiểu 8 ký tự)
4. Bấm **"Create account"**.
5. **Kết quả:** Hệ thống tạo user trên bảng DynamoDB `tvc-local-users`, cấp JWT token và tự động chuyển hướng vào trang **Projects** (`/projects`), góc phải trên hiển thị tên `Phuong Tester`.

### Kịch bản 2: Tạo Project mới
1. Tại trang **Projects**, bấm nút **"Create project"**.
2. Nhập:
   - **Project name:** `Demo Cloud Video`
   - **Description:** `Kiểm thử tích hợp BE - FE - Worker`
3. Bấm **"Create project"**.
4. **Kết quả:** Card project `Demo Cloud Video` xuất hiện ngay trong danh sách. Bấm vào card này để mở trang danh sách video của Project.

### Kịch bản 3: Upload Video thật -> Transcode HLS -> Xem Video & Embed
1. Trong trang chi tiết Project, bấm nút **"Upload video"**.
2. Chọn một file video `.mp4` (bạn có thể chọn sẵn file mẫu của dự án tại đường dẫn: `worker/testdata/sample-10s-720p.mp4` — dung lượng ~1.5 MB, thời lượng 10 giây).
3. Bấm **"Start upload"**.
4. **Quan sát tiến trình:**
   - Hộp thoại nhảy qua 3 bước: *1. Prepare upload* (`POST /api/projects/{id}/videos`) -> *2. Upload file* (`PUT` trực tiếp lên S3 LocalStack `:4566`) -> *3. Confirm video* (`POST /api/videos/{id}/confirm`).
   - Sau khi hộp thoại đóng lại, video xuất hiện trong danh sách với trạng thái **`Pending`** -> chuyển sang **`Processing`** -> và sau khoảng **2–4 giây** tự động chuyển sang **`Completed`** kèm ảnh thu nhỏ (Thumbnail)!
5. **Xem Video HLS (`hls.js`):**
   - Bấm vào dòng video vừa `Completed` để mở trang **Video details**.
   - Trình phát video phát mượt mà luồng HLS (`/media/{projectId}/{videoId}/hls/master.m3u8` với 2 chất lượng `360p` và `720p`).
6. **Kiểm tra tính năng Chia sẻ / Nhúng (`Copy embed code`):**
   - Bấm nút **"Copy embed code"**.
   - Mở một cửa sổ ẩn danh (Incognito — chưa đăng nhập) và dán URL trong `src="..."` của thẻ `<iframe>` (dạng `http://localhost:8000/embed?video=vid_...&project=prj_...`).
   - **Kết quả:** Trang `/embed` phát được video HLS ngay cả khi người xem không đăng nhập.

### Kịch bản 4: Kiểm thử Luồng Lỗi (Negative Test)
1. **Upload file quá 200 MB hoặc sai đuôi định dạng (`.exe`, `.txt`):**
   - Hộp thoại Upload chặn ngay trên UI và hiển thị cảnh báo đỏ, nút *Start upload* bị khóa.
2. **Upload file giả mạo video (ví dụ đổi tên một file text bất kỳ thành `fake.mp4`):**
   - Tạo nhanh 1 file lỗi: `echo "not a video" > /tmp/fake.mp4` rồi chọn upload file `/tmp/fake.mp4`.
   - File vẫn upload lên S3 và Confirm thành công, nhưng khi Worker tải về chạy `ffprobe` sẽ phát hiện không phải container video hợp lệ -> cập nhật trạng thái thành **`Validation failed`** (`FAILED_VALIDATION`).
   - Bấm vào video sẽ thấy thông báo: **`Processing failed — Error code: NOT_A_VIDEO`**.

---

## 5. Hướng dẫn Chạy Kiểm thử Tự động bằng CLI (Automated E2E & Test Suites)

Bạn có thể chạy lại toàn bộ các bài test tự động bất cứ lúc nào bằng các lệnh sau tại thư mục gốc:

### 5.1. Kiểm thử E2E Toàn luồng qua API thật (Epic A4)
```bash
make e2e
```
- Tự động thực hiện: Đăng ký/Đăng nhập -> Tạo Project -> Khởi tạo Video -> Upload `sample-10s-720p.mp4` lên S3 bằng Presigned URL -> Confirm -> Chờ Worker transcode `COMPLETED` -> Tải `master.m3u8`, `360p/index.m3u8`, `720p/index.m3u8`, các segment `.ts` và `thumb/default.jpg` qua Nginx `/media/*` -> Dùng `ffprobe` kiểm tra đúng chiều cao 360/720 và số lượng frame -> Xóa dữ liệu test qua `DELETE /api/videos/{id}` và `DELETE /api/projects/{id}`.
- Log bằng chứng được lưu tại: `local/e2e-logs/<timestamp>-api/summary.txt`.

### 5.2. Kiểm thử E2E riêng cho Worker
```bash
make e2e-worker
```

### 5.3. Kiểm thử E2E Giao diện Trình duyệt Thật (Playwright + Chrome + Full Stack)
Khi `make up` và `npm run dev:real` đang chạy:
```bash
cd frontend
E2E_REAL_STACK=1 E2E_BASE_URL=http://localhost:8000 npx playwright test e2e/real-stack.spec.ts
```
*(Nếu muốn xem trình duyệt Chrome mở lên thao tác trực tiếp, thêm cờ `--headed` vào cuối lệnh).*

### 5.4. Chạy toàn bộ Unit & Integration Tests của cả 3 thành phần
```bash
# 1. Backend (Unit Tests + 24 Testcontainers LocalStack Integration Tests):
cd backend && ./scripts/verify.sh

# 2. Worker + Frontend + Backend (chạy nhanh qua Makefile ở thư mục gốc):
make test
```

---

## 6. Các lệnh Theo dõi Log & Xử lý sự cố (Troubleshooting)

- **Xem log trực tiếp của Backend (Structured Logging + Correlation ID + Masking):**
  ```bash
  make logs S=backend
  ```
- **Xem log trực tiếp của Worker (Go `slog` JSON):**
  ```bash
  make logs S=worker
  ```
- **Thử nghiệm scale nhiều Worker chạy song song (Competing Consumers):**
  ```bash
  make worker-scale N=3
  ```
- **Reset sạch toàn bộ dữ liệu LocalStack để test lại từ đầu:**
  ```bash
  make reset
  make up
  ```
- **Dừng toàn bộ hệ thống Docker Compose:**
  ```bash
  make down
  ```

---

## 7. Checklist Thông số Bàn giao cho Đội Cloud (AWS Deployment Handoff)

Khi đưa lên môi trường AWS thật (ECS/EC2 + S3 + SQS + DynamoDB + CloudFront), đội Cloud chỉ cần cấu hình các biến môi trường sau:

1. **Cho `backend` container (`backend/Dockerfile`, cổng `8080`, healthcheck `GET /api/actuator/health`):**
   - `AWS_REGION=ap-southeast-1`
   - `AWS_ENDPOINT_URL=` *(để trống trên AWS thật để dùng endpoint mặc định của AWS SDK v2 & IAM Role / Instance Profile)*
   - `S3_PRESIGN_ENDPOINT=` *(để trống trên AWS thật)*
   - `TVC_INPUT_BUCKET=tvc-<env>-input` (hoặc `S3_BUCKET_INPUT`)
   - `TVC_OUTPUT_BUCKET=tvc-<env>-output` (hoặc `S3_BUCKET_OUTPUT`)
   - `TVC_JOBS_QUEUE_URL=https://sqs.ap-southeast-1.amazonaws.com/<account>/tvc-<env>-jobs` (hoặc `SQS_QUEUE_NAME=tvc-<env>-jobs`)
   - `DDB_TABLE_PREFIX=tvc-<env>-`
   - `TVC_MEDIA_BASE_URL=/media`
   - `JWT_SECRET=<secret-tối-thiểu-32-ký-tự-từ-SSM-Parameter-Store>`
   - `CORS_ALLOWED_ORIGINS=https://<cloudfront-domain>`
   - `LOG_FORMAT=json` (để xuất single-line JSON lên CloudWatch Logs)
2. **Cho `worker` container (`worker/Dockerfile`):**
   - `AWS_REGION=ap-southeast-1`
   - `TVC_INPUT_BUCKET=tvc-<env>-input`
   - `TVC_OUTPUT_BUCKET=tvc-<env>-output`
   - `TVC_DDB_VIDEOS=tvc-<env>-videos`
   - `TVC_JOBS_QUEUE_URL=https://sqs.ap-southeast-1.amazonaws.com/<account>/tvc-<env>-jobs`
3. **Cho `frontend` build (`cd frontend && VITE_API_MODE=real npm run build` -> thư mục `frontend/dist`):**
   - Sync `frontend/dist` lên S3 `tvc-<env>-frontend`.
   - Cấu hình CloudFront routing:
     - `/api/*` -> ALB / Backend origin (`:8080`)
     - `/media/*` -> S3 `tvc-<env>-output` (OAC, strip prefix `/media/`)
     - Default (`/*`) -> S3 `tvc-<env>-frontend` (SPA fallback `403`/`404` về `/index.html`).
