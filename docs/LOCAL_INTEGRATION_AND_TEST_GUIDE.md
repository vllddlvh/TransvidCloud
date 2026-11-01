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

Toàn bộ 16 endpoints Backend hiện đã được tích hợp đồng bộ trên giao diện Frontend. Bạn có thể lần lượt thực hiện 7 kịch bản sau để kiểm thử trọn vẹn các tính năng mới:

### Kịch bản 1: Đăng ký tài khoản, Đăng nhập & Tạo Project mới (kèm Webhook)
1. Mở trình duyệt truy cập: **`http://localhost:8000/login`**
2. Bấm vào liên kết **"Create account"** (phía dưới nút Sign in).
3. Nhập thông tin:
   - **Full name:** `Phuong Tester`
   - **Email address:** `demo@transvidcloud.test`
   - **Password:** `DemoPass123!` (tối thiểu 8 ký tự)
4. Bấm **"Create account"**.
   - **Kết quả:** Hệ thống tạo user trên DynamoDB `tvc-local-users`, cấp JWT token và chuyển hướng tới trang **Projects** (`/projects`), góc trên bên phải hiển thị tên `Phuong Tester`.
5. Bấm nút **"Create project"**:
   - **Project name:** `Demo Cloud Video` (tối đa 120 ký tự)
   - **Description:** `Dự án video thử nghiệm chất lượng cao` (tối đa 500 ký tự)
   - **Webhook URL (tùy chọn):** `https://hooks.transvid.example/events`
   - Bấm **"Create project"**.
   - **Kết quả:** Card `Demo Cloud Video` xuất hiện trong danh sách, có huy hiệu `Webhook` màu xanh. Bấm vào card để mở trang video của Project.

---

### Kịch bản 2: Chỉnh sửa Project & Quản lý Developer API Keys (F-004 & F-005)
1. **Chỉnh sửa thông tin Project (`Edit project`):**
   - Trên thanh công cụ góc trên bên phải, bấm nút **"Edit project"**.
   - Sửa mô tả thành: `Dự án video thử nghiệm tích hợp Multi-Rendition HLS`.
   - Cập nhật Webhook URL thành: `https://hooks.transvid.example/v2`.
   - Bấm **"Save changes"**.
   - **Kết quả:** Thông báo *“Project updated”* xuất hiện; tên, mô tả và webhook URL cập nhật tức thì (`PATCH /api/projects/{projectId}`).
2. **Quản lý Developer API Keys (`API keys`):**
   - Bấm nút **"API keys"** trên thanh công cụ.
   - Hộp thoại hiển thị thông tin giới hạn: **`(0 / 5 keys)`**.
   - Nhập tên khóa: `CI/CD Automation Key` (1–100 ký tự) rồi bấm **"Create API key"**.
   - **Quan sát Banner Cảnh báo Một Lần Duy Nhất:**
     - Hộp thoại xuất hiện khung nổi bật: *“Copy your API key now — it will only be shown once”*.
     - Chuỗi khóa thô `rawKey` hiển thị đầy đủ dưới dạng: `tvc_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`.
     - Bấm nút **"Copy API key"** để sao chép vào clipboard.
     - Bấm nút **"Done"**.
   - **Kiểm tra Danh sách Khóa:**
     - Lúc này danh sách hiển thị tên `CI/CD Automation Key`, nhưng chuỗi khóa chỉ còn hiển thị tiền tố đã được che: `tvc_live_xxxxxxxx...` (đảm bảo an toàn không lộ secret key) và số lượng khóa là `(1 / 5 keys)`.
3. **Thử nghiệm gọi API Backend trực tiếp bằng `X-API-Key` qua Terminal (Dual Security):**
   - Mở terminal và chạy lệnh sau (thay `tvc_live_...` bằng chuỗi bạn vừa copy và `{projectId}` từ thanh địa chỉ trình duyệt):
     ```bash
     curl -s -H "X-API-Key: <CHUÕI_RAW_KEY_VUA_COPY>" http://localhost:8000/api/projects/<PROJECT_ID>/videos
     ```
   - **Kết quả:** Backend xác thực thành công qua header `X-API-Key` và trả về JSON danh sách video `{"items":[],"nextCursor":null,"hasMore":false}` với mã HTTP `200 OK`.
4. **Thu hồi API Key (`Revoke`):**
   - Trên hộp thoại **Developer API keys**, bấm nút **"Revoke"** màu đỏ bên cạnh khóa.
   - Khóa bị thu hồi tức thì và bộ đếm trở về `(0 / 5 keys)`. Bấm **"Close"** để đóng hộp thoại.

---

### Kịch bản 3: Upload Video với Tiêu đề Tùy chỉnh (`title`) & Theo dõi Tiến trình
1. Trong trang chi tiết Project, bấm nút **"Upload video"**.
2. Chọn file video mẫu: `worker/testdata/sample-10s-720p.mp4`.
3. **Quan sát trường `Video title`:**
   - Hệ thống tự động điền gợi ý `sample-10s-720p.mp4`.
   - Bạn có thể chỉnh sửa thành tiêu đề thân thiện: **`Giới thiệu TransvidCloud 2026`** (1–200 ký tự).
4. Bấm **"Start upload"**:
   - Trình duyệt khởi tạo upload (`POST /api/projects/{id}/videos` gửi kèm `title`) -> upload trực tiếp lên S3 LocalStack `:4566` qua Presigned PUT URL -> xác nhận hoàn tất (`POST /api/videos/{id}/confirm`).
   - Hộp thoại đóng lại và video xuất hiện với tiêu đề **`Giới thiệu TransvidCloud 2026`**.
5. **Theo dõi vòng đời xử lý tự động (Auto-refresh):**
   - Trạng thái tự động nhảy từ **`Pending`** $\rightarrow$ **`Processing`** $\rightarrow$ **`Completed`** (trong vòng 2–4 giây) kèm ảnh đại diện Thumbnail vừa được Go FFmpeg Worker tạo ra.

---

### Kịch bản 4: Trải nghiệm Trình phát HLS Đa Chất lượng (`Auto / 720p / 360p`) & 4 Transcoded Assets
1. Bấm vào video **`Giới thiệu TransvidCloud 2026`** để mở trang **Video details**.
2. **Kiểm tra Trình phát HLS (`HlsPlayer`):**
   - Video tự động nạp và phát luồng HLS Adaptive Bitrate.
   - Ngay dưới khung video xuất hiện thanh điều khiển **Playback quality**:
     - **`Auto (ABR)`** *(mặc định)*: Trình phát dùng thuật toán ABR của `hls.js` tự động cân bằng theo băng thông mạng (`master.m3u8`).
     - Bấm chọn **`720p HD`**: Trình phát chuyển ngay sang luồng `/hls/720p/index.m3u8`, hiển thị nút active, và **giữ nguyên thời gian video đang phát dở**.
     - Bấm chọn **`360p SD`**: Trình phát chuyển sang luồng `/hls/360p/index.m3u8` mượt mà không bị reload trang.
     - Bấm lại **`Auto (ABR)`**: Trình phát quay lại luồng Master ABR.
3. **Kiểm tra Danh mục 4 Video Assets & Siêu dữ liệu Vòng đời:**
   - Ở cột bên phải (**Transcoded assets**), hệ thống liệt kê đầy đủ 4 tài nguyên do Worker sinh ra kèm nút **Copy** riêng lẻ:
     - **Master HLS (ABR):** `/media/{projectId}/{videoId}/hls/master.m3u8`
     - **720p HD HLS:** `/media/{projectId}/{videoId}/hls/720p/index.m3u8`
     - **360p SD HLS:** `/media/{projectId}/{videoId}/hls/360p/index.m3u8`
     - **Thumbnail JPEG:** `/media/{projectId}/{videoId}/thumb/default.jpg`
   - Bấm nút **Copy** bên cạnh bất kỳ asset nào để kiểm tra thông báo sao chép thành công.
   - Kiểm tra bảng **File information**: hiển thị đầy đủ `Title`, `File name`, `Size`, `Format`, `Attempts (1)`, `Added`, `Started`, `Completed`, `Updated` và `Video ID`.

---

### Kịch bản 5: Chia sẻ & Nhúng Video Public (`/embed`)
1. Tại trang **Video details**, bấm nút **"Copy embed code"**.
2. Mở một **cửa sổ ẩn danh mới (Incognito Window)** hoặc một trình duyệt khác (hoàn toàn chưa đăng nhập).
3. Lấy đường dẫn URL nằm trong thuộc tính `src="..."` của đoạn mã iframe vừa copy (dạng `http://localhost:8000/embed?video=vid_...&project=prj_...`) và dán vào thanh địa chỉ.
4. **Kết quả:**
   - Trang `/embed` công khai hiển thị trình phát HLS với đầy đủ video và phát mượt mà mà không bắt người xem phải đăng nhập tài khoản.

---

### Kịch bản 6: Kiểm thử Luồng Lỗi (Negative Tests) & Bảo vệ Xóa (Conflict Guards)
1. **Kiểm tra chặn upload file quá dung lượng (Client validation):**
   - Mở lại hộp thoại Upload, thử chọn một file > 200 MB hoặc file `.exe`.
   - Giao diện báo lỗi đỏ ngay tại chỗ và nút *Start upload* bị vô hiệu hóa.
2. **Kiểm tra Worker bắt lỗi file giả mạo (`FAILED_VALIDATION`):**
   - Tạo nhanh một file giả mạo: `echo "invalid content" > /tmp/fake-video.mp4`.
   - Chọn upload file `/tmp/fake-video.mp4`.
   - File vẫn được tải lên S3 và xác nhận, nhưng sau 2 giây Worker dùng `ffprobe` quét thấy không phải video hợp lệ $\rightarrow$ cập nhật trạng thái **`Validation failed`**.
   - Bấm vào video xem chi tiết: Giao diện hiển thị cảnh báo đỏ **`Processing failed — Error code: NOT_A_VIDEO`** kèm hướng dẫn khắc phục.
3. **Kiểm tra Xóa Video (`Delete video`):**
   - Tại trang chi tiết video lỗi vừa tạo, bấm nút **"Delete video"** trên thanh tiêu đề.
   - Hộp thoại xác nhận mở ra, bấm **"Confirm delete"**.
   - Video bị xóa ngay lập tức (`DELETE /api/videos/{videoId}`) và trình duyệt tự động quay về trang danh sách video của Project.
   - *(Lưu ý: Nếu một video đang ở trạng thái `Processing`, nút "Delete video" sẽ tự động bị mờ/khóa kèm tooltip giải thích quy tắc bảo vệ của hệ thống, không cho phép xóa video đang transcode dở).*
4. **Kiểm tra Bảo vệ Xóa Project (`Delete project` - Conflict Guard 409):**
   - Tại trang danh sách video của Project, bấm nút **"Delete project"**.
   - Bấm **"Confirm delete project"**.
   - Do Project đã có video hoàn tất hoặc video đang xử lý, nếu còn ràng buộc chưa cho phép xóa hoặc xóa thành công, hệ thống sẽ phản hồi đúng chuẩn:
     - Nếu còn video đang `PROCESSING`: Backend trả `409 RESOURCE_IN_USE` và modal hiển thị cảnh báo đỏ ngăn chặn xóa.
     - Nếu các video đã dọn dẹp xong: Project được xóa mềm an toàn (`DELETE /api/projects/{projectId}`) và điều hướng về trang `/projects`.

---

### Kịch bản 7: Kiểm thử Phân trang Con trỏ (Cursor Pagination & Load More)
1. Trong một Project có nhiều hơn 20 video (hoặc khi gọi API `GET /api/projects/{projectId}/videos?limit=5`):
2. Backend trả về trường `hasMore: true` kèm mã con trỏ mã hóa Base64URL `nextCursor`.
3. Dưới danh sách video xuất hiện nút **"Load more videos"**.
4. Bấm **"Load more videos"**:
   - Frontend gửi yêu cầu `api.listVideosPage(id, { cursor: nextCursor })`.
   - Các video của trang tiếp theo được nối tiếp vào danh sách mượt mà mà không làm mất các video đã tải trước đó.

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
Kịch bản `frontend/e2e/real-stack.spec.ts` tự động hóa 100% các thao tác người dùng trên trình duyệt thật kết nối trực tiếp với toàn bộ hạ tầng Docker Compose + LocalStack:
- Đăng ký tài khoản mới (`/login` -> Register).
- Tạo Project và chỉnh sửa cấu hình Project (`PATCH /api/projects/:id`).
- Tạo Developer API Key, kiểm tra banner hiển thị `rawKey` một lần duy nhất, che `keyPrefix`, và thu hồi (`Revoke`).
- Upload video kèm tiêu đề tùy chỉnh `title` qua S3 Presigned PUT.
- Chờ Worker chuyển mã thành công sang trạng thái `COMPLETED`.
- Mở xem video, chuyển đổi giữa các độ phân giải `Auto (ABR)`, `720p HD`, `360p SD` và kiểm tra 4 đường dẫn assets.
- Mở xem trang nhúng `/embed` ở ngữ cảnh không đăng nhập.
- Upload file lỗi để kiểm tra Worker bắt mã lỗi `NOT_A_VIDEO`.
- Xóa video lỗi qua nút `Delete video`.

Cách chạy:
```bash
# Đảm bảo Docker Compose đang chạy (make up) và Vite đang chạy (npm run dev:real)
cd frontend
E2E_REAL_STACK=1 E2E_BASE_URL=http://localhost:8000 npx playwright test e2e/real-stack.spec.ts
```
*(Thêm cờ `--headed` vào cuối lệnh nếu bạn muốn xem trình duyệt Chrome mở lên và tự động click chuột).*

### 5.4. Chạy toàn bộ Unit & Integration Tests của cả 3 thành phần
```bash
# 1. Frontend (Kiểm tra OpenAPI schema drift, 142 Unit/Contract/Integration tests & Production build):
cd frontend
npm run api:check
npm test
npm run build

# 2. Backend (100% Unit Tests + 24 Testcontainers LocalStack Integration Tests):
cd ../backend
./scripts/verify.sh

# 3. Worker (10/10 Go packages):
cd ../worker
go test ./...

# Hoặc chạy kiểm thử nhanh toàn bộ dự án từ thư mục gốc qua Makefile:
cd ..
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
