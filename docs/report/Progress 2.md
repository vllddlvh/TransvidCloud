# Báo cáo Progress 2 — TransVidCloud

> **Môn học:** Điện toán đám mây — UET-VNU | **Tuần báo cáo:** Tuần 8–9 (mốc M2–M4 theo [timeline chung v4](../introduction/1.%20Timelines.md))
> **Phạm vi phần này:** Trình bày kiến trúc về tính năng của hệ thống — lớp **SaaS** và lớp **PaaS (Developer Platform/API)** (mục 1) và kế hoạch triển khai (mục 3). Mục 2 — kiến trúc triển khai AWS — nằm trong `transvidcloud_report2_deployment_section_v2.tex` (tham chiếu [n] trong mục 3 là theo bảng 15 giao diện của mục đó).
> **Nguồn sự thật kỹ thuật:** [`docs/contracts/`](../contracts/README.md) (API, SQS message, state machine, DynamoDB, S3 key, upload limits) và [ADR](../docs-common/5.%20Architecture%20decisions.md).

---

# 1. Trình bày kiến trúc về tính năng của hệ thống

## 1.1. Nguyên tắc kiến trúc tổng quát: một backend, hai cách dùng, một lõi xử lý

TransVidCloud không tách thành hai hệ thống riêng cho người dùng cuối và developer. Cả hai lớp **SaaS Web** và **Developer API (lớp PaaS)** đều gọi vào **cùng một backend Spring Boot** và cùng sử dụng **một lõi xử lý video duy nhất** (S3 → SQS → Worker Go + FFmpeg → HLS/Assets). Sự khác biệt chỉ nằm ở **cách xác thực** (JWT cho SaaS, API key cho Developer API) và **phương thức truy cập** (giao diện React web so với REST API).

> **Ghi chú về định vị:** theo đặc tả SRS (mục 1.2), trong phạm vi đồ án lớp "PaaS" được gọi chính xác là **Developer Platform / API integration layer** — tức năng lực "platform-as-a-service" được biểu hiện qua việc một hệ thống bên ngoài (vd. E-learning) tích hợp toàn bộ năng lực xử lý video của TransVidCloud qua API, thay vì qua giao diện web. Sản phẩm chính là SaaS; API là lớp tích hợp dành cho developer.

```text
                    ┌─────────────┐     ┌──────────────────┐
  Người dùng SaaS ──┤  React Web  │     │  App bên ngoài   │
                    │   (SaaS)    │     │ (E-learning,...) │
                    └──────┬──────┘     └────────┬─────────┘
                           │    JWT / API key     │
                           └──────────┬───────────┘
                                      v
                           ┌──────────────────────┐
                           │  API (Spring Boot)   │   ← MỘT BACKEND DÙNG CHUNG
                           │  metadata, presign,  │
                           │  confirm, status     │
                           └───┬────────┬────┬────┘
                               │        │    │
                          DynamoDB    S3   SQS          ← LÕI XỬ LÝ DÙNG CHUNG
                               │        │    │
                               │        │    v
                               │        │  Worker (Go+FFmpeg)
                               │        │    transcode HLS 360p/720p
                               │        └─── + thumbnail
                               └──────────── cập nhật trạng thái
```

**Hình 1.1 — Kiến trúc tổng thể: hai lớp tiếp cận, một lõi xử lý.**

![[saas-paas-architecture.png]]

**Lợi ích của thiết kế "một lõi, hai mặt"**:

- Không tốn gấp đôi chi phí hạ tầng để phục vụ hai nhóm người dùng.
- Một tính năng xử lý (thêm rendition, đổi profile FFmpeg...) có giá trị ngay cho cả SaaS và API.
- Thay đổi contract chỉ cần thực hiện một nơi; SaaS Web thực chất là một "API client đầu tiên" của chính hệ thống.
- Trạng thái, asset và bảo mật được kiểm soát tại một điểm duy nhất (backend + DynamoDB), tránh hai nguồn sự thật.

---

## 1.2. Kiến trúc tính năng lớp SaaS

SaaS là lớp giao diện cho **người dùng cuối** (content creator, giảng viên, media operator). Các tính năng P0 và kiến trúc thực hiện của chúng như sau:

### 1.2.1. Bản đồ tính năng

| Tính năng SaaS | Kiến trúc thực hiện | Nguồn/bằng chứng |
|---|---|---|
| Đăng nhập | `POST /auth/login` → JWT; đăng ký qua `POST /auth/register` (UI đăng ký không thuộc MVP) | `openapi.yaml`, frontend doc mục 1 |
| Quản lý project/workspace | `GET/POST /projects`; mỗi user chỉ thấy project của mình (ownership) | `openapi.yaml`, bảng DynamoDB `projects` |
| Video Library (danh sách video) | `GET /projects/{projectId}/videos` (phân trang cursor); hiển thị trạng thái theo state machine | `openapi.yaml`, `dynamodb-tables.md` |
| Upload video (presigned, không qua API) | `POST /projects/{projectId}/videos` → presigned URL → trình duyệt `PUT` thẳng lên **S3 input** | `upload-limits.md`, `s3-key-convention.md` |
| Xác nhận upload & đẩy job | `POST /videos/{videoId}/confirm` → backend HeadObject kiểm tra, chuyển `UPLOADING → PENDING`, gửi message SQS | `openapi.yaml`, `sqs-job-message.schema.json` |
| Theo dõi trạng thái xử lý | Frontend **poll** `GET /videos/{videoId}` mỗi 3–5 giây khi có video `PENDING`/`PROCESSING` | `video-state-machine.md` |
| Trang chi tiết video | `GET /videos/{videoId}`: trạng thái, `errorCode`, `assets` khi hoàn tất | `openapi.yaml` |
| Trình phát HLS | `hls.js` phát master playlist qua đường dẫn `/media/...` (CloudFront → bucket output) | frontend doc mục 5, docs-cloud |
| Trang Integration/Embed | Hiển thị HLS URL, mã nhúng `<iframe>` trỏ trang embed | frontend doc mục 5–6 |

### 1.2.2. Sơ đồ kiến trúc luồng upload của SaaS

**Hình 1.2 — Luồng upload presigned của SaaS (byte video không đi qua API).**

![[saas-upload-flow.png]]

Điểm kiến trúc quan trọng nhất của SaaS: **video không bao giờ đi qua application server**. Backend chỉ phát quyền (presigned URL) và quản lý metadata; byte video lớn chạy trực tiếp giữa trình duyệt và S3, sau đó output được phát qua CDN.

```text
┌──────────┐  1. POST /projects/{id}/videos        ┌───────────────┐
│  React   │ ─────────────────────────────────────▶ │ API Spring    │
│  (SaaS)  │ ◀───────────────────────────────────── │ Boot          │
└──────────┘  2. presigned URL (hạn 15 phút)        └──────┬────────┘
     │                                               3. tạo bản ghi
     │  3. PUT video trực tiếp (200 MB, mp4/mov/        ▼
     │     mkv/webm, ≤ 5 phút, ≤ 1080p)             DynamoDB (UPLOADING)
     ▼
┌───────────┐   ┌──────────────────────────────────────────────┐
│ S3 input  │   │ 4. POST /videos/{id}/confirm                 │
└───────────┘   │    API: HeadObject → UPLOADING→PENDING → SQS  │
                └──────────────────────────────────────────────┘
```

**Vì sao thiết kế như vậy:**

1. **Không làm nghẽn API server:** FFmpeg và transfer file lớn hoàn toàn nằm ngoài vòng request-response của web tier; API chỉ xử lý metadata nhẹ.
2. **Xử lý bất đồng bộ:** sau bước confirm, người dùng tiếp tục dùng app; worker xử lý nền và người dùng theo dõi qua polling trạng thái.
3. **Kiểm soát upload ngay từ đầu:** backend từ chối (413/422) nếu vượt giới hạn **200 MB / 5 phút / tối đa 1080p** trước khi presign — giới hạn được kiểm tra lại ở worker (single source of truth là `upload-limits.md`).

### 1.2.3. State machine hiển thị trong SaaS

SaaS UI phản ánh trực tiếp trạng thái chuẩn dùng chung với Worker:

```text
UPLOADING → PENDING → PROCESSING → COMPLETED
                          │
                          ├→ FAILED_VALIDATION  (vượt thời lượng/resolution, không phải video → ACK ngay)
                          └→ FAILED_TRANSCODE | FAILED_STORAGE (worker lỗi → SQS retry, sau 3 lần vào DLQ)
```

| Trạng thái | SaaS hiển thị |
|---|---|
| `UPLOADING` | "Đang tải lên" (hiếm khi thấy — upload gián đoạn) |
| `PENDING` | "Đang chờ xử lý" — đã confirm, chờ worker nhận |
| `PROCESSING` | "Đang xử lý" — spinner/progress |
| `COMPLETED` | "Hoàn tất" — bật player, link streaming + mã nhúng |
| `FAILED_*` | Lỗi kèm `errorCode` và gợi ý (vd vượt 5 phút, quá 1080p) |

### 1.2.4. Kiến trúc phân phối nội dung cho SaaS

- **Local (dev):** nginx tại `localhost:8000` gộp một domain cho web (`/`), API (`/api/*`) và media (`/media/*` → LocalStack bucket output).
- **AWS:** **một entry HTTPS duy nhất qua CloudFront** — web (S3 hosting), API (`/api/*` qua Cloudflare Tunnel → EC2 API), HLS (`/media/*` → bucket output). Người xem nhận segment từ edge, không đánh thẳng vào origin.

---

## 1.3. Kiến trúc tính năng lớp PaaS — Developer Platform / API

Lớp này biến TransVidCloud thành một **nền tảng tích hợp**: developer của một hệ thống bên ngoài (use case tham chiếu: **E-learning**) dùng API key để thực hiện trọn vòng đời `upload → process → lấy asset → embed` mà **không cần biết bất kỳ chi tiết hạ tầng AWS nào**.

### 1.3.1. Nguyên tắc trừu tượng hóa của lớp platform

Developer nhìn thấy và thao tác với **khái niệm nghiệp vụ**:

```text
Project  ·  Video  ·  Asset (HLS/rendition/thumbnail)  ·  API Key
```

Developer **không cần biết** (và không thể thấy): bucket S3 nào, SQS receipt handle nào, EC2 instance ID nào, Auto Scaling policy nào. Đây chính là đặc tính "as-a-service" của lớp này: toàn bộ S3/SQS/EC2/DynamoDB được đóng gói sau một REST API mô tả nghiệp vụ.

### 1.3.2. Nhóm API cốt lõi (theo `openapi.yaml`)

| Endpoint | Chức năng platform | Vai trò trong vòng đời tích hợp |
|---|---|---|
| `POST /auth/register` | Đăng ký tài khoản | Tao tài khoản developer |
| `POST /auth/login` | Lấy JWT | Quản trị qua dashboard |
| `GET /projects`, `POST /projects` | Liệt kê/tạo project | Tách không gian làm việc |
| `POST /projects/{projectId}/api-keys` | **Cấp API key cho project** | Chìa khóa tích hợp cho app ngoài |
| `POST /projects/{projectId}/videos` | Khai báo video (size, content-type) → nhận **presigned URL** | Xin quyền upload trực tiếp S3 |
| `POST /videos/{videoId}/confirm` | Xác nhận đã upload → `PENDING`, đẩy job vào SQS | **Submit processing job** |
| `GET /videos/{videoId}` | Trạng thái + assets (HLS URL, thumbnail, renditions) | **Poll status / get assets** trong một call |

Vòng đời tích hợp hoàn chỉnh:

```text
Tạo project ──▶ tạo API key ──▶ POST /videos (presign)
   ──▶ PUT file lên S3 ──▶ POST /confirm (job vào queue)
   ──▶ GET /videos/{id} (poll đến COMPLETED)
   ──▶ nhận assets: master.m3u8 + thumb + renditions
   ──▶ nhúng vào app ngoài (iframe embed / HLS URL)
```

### 1.3.3. Sơ đồ luồng tích hợp External App → TransVidCloud

**Hình 1.3 — Vòng đời tích hợp Developer API (lớp PaaS).**

![[developer-integration-flow.png]]

```text
┌──────────────┐  API key   ┌────────────────┐        ┌─────────────────────┐
│ E-learning   │───────────▶│  TransVidCloud │  SQS   │  EC2 Worker ASG     │
│ Platform     │            │  API           │───────▶│  (Go + FFmpeg)      │
│              │  PUT file  │  (Spring Boot) │        │  360p/720p + HLS    │
│  Giảng viên  │───────────▶│  presigned URL │        │  + thumbnail        │
│  upload      │      │     └────────────────┘        └─────────┬───────────┘
└──────┬───────┘      ▼                                       │
       │         S3 input                              S3 output + DynamoDB
       │  poll GET /videos/{id}                                │
       │◀────────────── COMPLETED + assets ◀───────────────────┘       │
       ▼
  Nhúng player (iframe/HLS) vào Course/Lecture
```

**Điểm kiến trúc đáng chú ý:**

- **API key gắn với project** (không gắn với user) — scope rõ ràng, dễ thu hồi; backend chỉ lưu key hash trong bảng `apikeys`, không lưu plaintext.
- **Xác thực kép trên cùng backend:** SaaS dùng JWT (hành vi người dùng), Developer API dùng API key (hành vi máy-máy). Cả hai đều đi qua cùng các service domain → cùng enforce ownership (project của ai thì video của người đó).
- **"Một call cho trạng thái + asset":** `GET /videos/{videoId}` trả cả trạng thái lẫn assets, nên vòng poll của bên tích hợp đơn giản và rẻ (không cần endpoint riêng cho status/assets ở MVP).
- **Webhook khi hoàn tất là P1** — MVP dùng polling; thiết kế message SQS và state machine không đổi khi sau này thêm webhook.

---

## 1.4. Kiến trúc chức năng (Functional Architecture)

Ngoài cách nhìn theo hai lớp truy cập (SaaS/PaaS), hệ thống có thể phân rã theo **chức năng** thành các module (A–H theo đặc tả SRS mục 26), xếp thành năm tầng. Cách nhìn này cho thấy mỗi tính năng thuộc module nào, module đó chạy trên thành phần nào của cloud, và phần ngang hệ thống phục vụ cả các tầng.

**Hình 1.4 — Kiến trúc chức năng: module A–H theo tầng.**

![[functional-architecture.png]]

| Tầng | Module | Trách nhiệm chức năng | Chạy trên |
|---|---|---|---|
| Trình bày | SaaS Web + Integrate endpoint | UI đăng nhập, project, library, upload, trạng thái, player HLS, iframe embed | React + Vite (CloudFront/S3) |
| Ứng dụng / API | **A — Identity & Project** | Auth JWT/API key, user, project, quyền sở hữu theo project | Spring Boot trên EC2 |
| | **B — Video Management** | Metadata, library, chi tiết, trạng thái (poll) | Spring Boot → DynamoDB |
| | **C — Upload Service** | Presigned URL, kiểm tra giới hạn (200MB/5 phút/≤1080p), confirm | Spring Boot → S3/SQS |
| | **D — Job Management** | Tạo job, đẩy SQS, state machine, retry/idempotency | Spring Boot → SQS/DynamoDB |
| | **F — Asset & Integration** | Assets map, HLS URL, embed, Developer API | Spring Boot |
| Xử lý | **E — Worker / Transcoding** | SQS consumer → FFmpeg → HLS 360p/720p + thumbnail → ghi state/assets → ACK | Go worker trong EC2 ASG (Spot) |
| Dữ liệu | S3 input/output, SQS + DLQ, DynamoDB | Lưu trữ object, hàng đợi job, nguồn sự thật metadata/state | AWS managed services |
| Ngang hệ thống | **G — Cloud/DevOps**, **H — Benchmark** | VPC, IAM, ASG scale theo backlog, CloudWatch, deploy (SSM/Instance Refresh), k6 | Terraform + AWS |

**Điểm cần nhấn mạnh khi trình bày:**

- Các module **A, B, C, D, F đều nằm trong cùng một backend Spring Boot** — đây là hiện thực hóa trực tiếp của nguyên tắc "một backend, hai cách dùng" ở mục 1.1: SaaS Web và Developer API gọi vào cùng bộ module chức năng, chỉ khác lớp xác thực.
- **Module E (worker) là module duy nhất chạy FFmpeg**, tách hẳn khỏi tầng ứng dụng — đảm bảo workload CPU nặng không bao giờ chặn API.
- Module **G/H là phần ngang**: không trực tiếp hiện diện trong luồng nghiệp vụ nhưng chi phối toàn bộ vận hành (scale, quan sát, bảo mật, deploy) và cung cấp bằng chứng số liệu cho thực nghiệm.

## 1.5. Lõi xử lý dùng chung — nơi hai lớp gặp nhau

Cả SaaS và Developer API kết thúc ở cùng một contract nội bộ: một message SQS và một item DynamoDB. Đây là điểm đồng bộ kiến trúc quan trọng nhất:

| Thành phần | Vai trò trong tính năng | Ai phụ trách |
|---|---|---|
| **S3 (2 bucket input/output)** | Input `{projectId}/{videoId}/source.ext`; output HLS 360p/720p + `thumb/default.jpg` theo `s3-key-convention.md` | Cloud |
| **SQS + DLQ** | Buffer job; absorb burst; visibility timeout cho retry; DLQ sau 3 lần thất bại | Cloud |
| **Worker (Go + FFmpeg) trên EC2 ASG (Spot)** | Nhận message → download → transcode → upload output → ghi `COMPLETED`/`FAILED_*` → ACK | Vũ |
| **DynamoDB (4 bảng: users/projects/apikeys/videos)** | Nguồn sự thật duy nhất cho metadata + trạng thái job (không có bảng job riêng — [ADR 0001]); asset nằm trong map `assets` của item `videos` | Phương |
| **CloudFront + Cloudflare Tunnel** | Một entry HTTPS cho web/API/HLS; worker không public | Lưu + Nguyên |
| **CloudWatch** | Queue backlog, số worker, CPU, latency, success/failure — căn cứ cho Auto Scaling và thực nghiệm | Nguyên |

**Minh chứng kiến trúc elastic dùng chung:** khi 100 video được upload (từ SaaS hoặc từ API — hệ thống không phân biệt), chúng vào SQS; queue backlog tăng → ASG scale-out worker; queue rút → scale-in. Năng lực "co giãn theo workload" này là giá trị nền tảng mà cả hai lớp sản phẩm đều hưởng.

---

## 1.6. So sánh kiến trúc truy cập hai lớp

| Khía cạnh | SaaS Web | Developer API (lớp PaaS) |
|---|---|---|
| Người dùng | Người dùng cuối (creator/giảng viên) | Hệ thống bên ngoài / developer |
| Xác thực | JWT (login) | API key theo project |
| Giao diện | React + Vite (hls.js player, upload progress, polling UI) | REST (`/api`), presigned PUT, HTTP poll |
| Bước "khai báo video" | Tự động trong luồng upload UI | `POST /projects/{id}/videos` |
| Bước "submit job" | Ẩu sau nút upload (confirm) | `POST /videos/{id}/confirm` |
| Xem trạng thái | UI polling + badge trạng thái | `GET /videos/{id}` |
| Tiêu thụ kết quả | Player HLS trong app, trang embed | HLS URL / asset URL nhúng vào app của họ |
| Lõi xử lý | **S3 → SQS → Worker → S3/DynamoDB (chung)** | **S3 → SQS → Worker → S3/DynamoDB (chung)** |

## 1.7. Tóm tắt

Kiến trúc tính năng của TransVidCloud tổ chức theo **ba lớp giá trị nhất quán** dùng cho báo cáo và demo:

1. **Product (SaaS):** một web app thực sự để đăng nhập, upload, theo dõi và phát video qua HLS.
2. **Platform (Developer API):** một hệ thống bên ngoài tích hợp trọn vòng đời xử lý video chỉ bằng API key + 3 endpoint chính (`POST /videos`, `POST /confirm`, `GET /videos/{id}`) — không lộ chi tiết AWS.
3. **Cloud core:** S3 → SQS → EC2 Auto Scaling (Spot) → FFmpeg → HLS, với DynamoDB làm nguồn sự thật trạng thái và CloudWatch làm nền quan sát — dùng chung cho cả hai lớp trên.

---

# 3. Kế hoạch triển khai

## 3.1. Cơ sở kế hoạch

- Kế hoạch triển khai TransVidCloud gồm **4 thành viên, 4 sprint (mỗi sprint 1 tuần)** và **1 tuần dự phòng** để hoàn thiện báo cáo, kiểm thử và chuẩn bị demo. Mỗi thành viên dành 2–3 ngày làm việc tập trung mỗi tuần.
- Khối lượng công việc tính theo đơn vị **person-day (pd)** — 1 pd tương đương một người làm việc tập trung trong một ngày. Effort trong bảng 3.2 là **phần việc còn lại**, ước lượng theo **hiện trạng code thực tế trong repo** (rà soát 27/10) chứ không lấy số của Báo cáo 1 làm mặc định.

| Thông số | Giá trị | Ghi chú |
|---|---|---|
| Số thành viên | 4 người | |
| Số sprint | 4 sprints | Tương ứng 4 tuần |
| Thời gian dự phòng | 1 tuần | Hoàn thiện báo cáo, sửa lỗi, chuẩn bị demo |
| Capacity ước tính | 32–48 pd | 4 người × 2–3 ngày/tuần × 4 tuần |
| Capacity sử dụng (kế hoạch) | 40 pd | Giả định trung bình 2,5 ngày/tuần/người × 4 tuần |

**Bảng capacity planning**

| Thành viên | Vai trò | Trách nhiệm |
|---|---|---|
| Bùi Hải Phương | Frontend, Backend | Phát triển Backend, Developer API, giao diện SaaS |
| Đào Lê Long Vũ | Frontend, Backend | Phát triển Backend, Worker, giao diện SaaS |
| Nguyễn Hữu Lưu | DevOps | Thiết lập các dịch vụ cloud |
| Hồ Văn Tiến Nguyên | DevOps | Thiết lập các dịch vụ cloud |

**Căn cứ ước lượng theo hiện trạng** (chi tiết: [[docs-common/2. Implementation status|hiện trạng triển khai]], `docs/*/work/Week1.md`):

| Kết luận từ repo | Ảnh hưởng lên kế hoạch |
|---|---|
| Worker (Go + FFmpeg) đã chạy trọn pipeline local tới `COMPLETED`, có unit test — ~75% đầu việc | Effort worker giảm còn 2,5 pd (dòng 4), giải phóng tải cho Vũ sang frontend |
| Backend Spring Boot chưa có code | Hạng mục lớn nhất (7,5 pd, dòng 2–3) — đường găng của kế hoạch |
| Frontend chưa có code; chiến lược mock-first đã duyệt | Tách riêng phần hợp nhất real (PUT S3/CORS không mock được) gộp vào dòng 5 |
| Terraform/IAM/compute/edge/giám sát chưa có | Toàn bộ nhóm Cloud (dòng 6–11) tính từ số 0 |

## 3.2. Kế hoạch triển khai

> **Kiến trúc liên quan**: số [n] là STT giao diện trong bảng 15 giao diện của mục kiến trúc triển khai — Hình 2.1 (triển khai thời gian chạy, [1]–[10]) và Hình 2.2 (vận hành — CI/CD, giám sát & co giãn, [11]–[15]). **Độ ưu tiên**: High = bắt buộc (P0 trên đường găng), Medium = nên làm nếu còn capacity, Low = P1 ngoài cam kết.

| ID  | Công việc                                           | Kiến trúc liên quan             | Sprint         | Độ ưu tiên | Effort (pd) | Người chịu trách nhiệm |
| --- | --------------------------------------------------- | ------------------------------- | -------------- | ---------- | ----------- | ---------------------- |
| 1   | Chốt kiến trúc và khung dự án                       | Toàn bộ kiến trúc               | 1              | High       | 1,0         | Phương                 |
| 2   | Backend Auth, Project, Video                        | [1], [3], [5]                   | 1–2            | High       | 6,0         | Phương                 |
| 3   | Upload & điều phối job                              | [4], [5], [6], [9]              | 2              | High       | 2,5         | Phương                 |
| 4   | Hoàn thiện Worker pipeline                          | [7], [8], [9]                   | 1–2            | High       | 2,5         | Vũ                     |
| 5   | Xây dựng giao diện người dùng                       | [1], [2], [4], [10]             | 2–3            | High       | 3,5         | Vũ                     |
| 6   | IAM & bảo mật                                       | [5], [6], [8], [9], [12]        | 1–2            | High       | 1,5         | Nguyên                 |
| 7   | Đóng gói Docker & stack local                       | [12]                            | 1–2            | High       | 1,5         | Lưu                    |
| 8   | Terraform hạ tầng dữ liệu                           | [4], [5], [8], [9]              | 1, 3           | High       | 2,5         | Lưu                    |
| 9   | Compute, edge & deploy                              | [1], [2], [3], [10], [11], [12] | 2–3            | High       | 4,0         | Nguyên                 |
| 10  | Giám sát, co giãn & thực nghiệm E2E/benchmark/chaos | [1]–[10], [13], [14], [15]      | 3–4            | High       | 4,0         | Lưu, Nguyên            |
| 11  | Báo cáo & demo                                      | Toàn bộ kiến trúc               | 1–4 + dự phòng | High       | 3,0         | Cả nhóm                |

**Tổng kế hoạch P0: 32 pd — bằng 80% capacity 40 pd**, còn đệm ~8 pd cho rủi ro. Dòng 9 gộp phần edge (Cloudflare Tunnel + CloudFront 3 behavior + hosting React — nếu tách riêng chính là mục "Tên miền & edge"); dòng 10 gộp giám sát/co giãn (alarm DLQ + SNS, Budgets, Target Tracking theo backlog — [13]–[15]; phần *lắp đặt* cấu hình làm cuối sprint 3 cùng dòng 9, sprint 4 *chạy* thí nghiệm) vì không có số liệu CloudWatch thì không đo được thí nghiệm scale. Hạng mục tùy chọn/P1 (không tính vào tổng): `metricpub` 0,5 pd (Medium — chỉ làm nếu chốt ASG Min = 0), A7 Webhook 1,5 pd (Low — Phương), C6 CI/CD hoàn chỉnh 1,5 pd (Low — Nguyên).

## 3.3. Cân đối tải và phương án dự phòng

Phân bố theo sprint (capacity mỗi sprint ~10 pd = 4 người × 2,5 ngày; chia đều cho các dòng trải nhiều sprint):

| Sprint | 1 | 2 | 3 | 4 | Dự phòng |
|---|---|---|---|---|---|
| pd | ~7,0 | ~9,75 | ~8,25 | ~6,0 | ~1,0 |

Sprint 1 nhẹ hơn (dựng khung, môi trường); sprint 2–3 là đỉnh tải (backend + cloud); sprint 4 dành cho thực nghiệm và đóng băng báo cáo — không xếp tính năng mới vào sprint 4.

Phân tải theo người (capacity mỗi người ~10 pd = 2,5 ngày × 4 tuần):

| Thành viên | pd theo bảng 3.2 | /10 | Ghi chú |
|---|---|---|---|
| Phương | ~10,3 (dòng 1+2+3 + 0,75 R1) | **103%** | **vượt nhẹ capacity** — đường găng; cần Vũ san bằng cách nhận review/skeleton hoặc lớp truy cập DynamoDB/SQS khi chậm |
| Nguyên | ~7,8 (dòng 6+9 + 1,5/4,0 dòng 10 + 0,75 R1) | 78% | edge ([1],[2],[3],[10]) dồn sprint 2–3 |
| Lưu | ~7,3 (dòng 7+8 + 2,5/4,0 dòng 10 + 0,75 R1) | 73% | dòng 7 chặn mọi thứ chạy container — làm sớm nhất có thể |
| Vũ | ~6,8 (dòng 4+5 + 0,75 R1) | 68% | worker gần xong nên dồn được sang dòng 5; phần dư san sang hỗ trợ Phương |

Đệm ~8 pd toàn kế hoạch là **đủ nếu backend (dòng 2–3) đúng hạn**. Nếu backend trễ, thứ tự ứng phó (theo timeline §7):

| Lever (theo thứ tự) | Tiết kiệm | Đổi lại |
|---|---|---|
| Bỏ `metricpub`, chốt ASG **Min ≥ 1** + metric math | 0,5 pd | tốn ~1 máy/đêm; demo vẫn scale 1→2 được |
| Bỏ tên miền riêng, dùng `*.cloudfront.net` | ~0,5 pd | dòng 10 rẻ hơn rõ rệt |
| Bỏ trang embed/mã nhúng, giữ player + link streaming | ~0,5 pd | vẫn đủ luồng demo cốt lõi (ghi chú P1 trong báo cáo) |
| Benchmark chỉ 1 vs 2 worker; chaos local + 1 ca AWS | ~1,0 pd | vẫn đủ 2 thí nghiệm bắt buộc |
| **Không cắt được**: dòng 2, 3 | — | không có backend thì e2e, demo, báo cáo thực nghiệm đều vô nghĩa |

Quy ước mốc theo sprint: **cuối sprint 2** phải có pipeline end-to-end chạy trên AWS (tương đương M2); **cuối sprint 3** có SaaS + API + HLS qua CloudFront và worker tự scale 1→2 (M3); **cuối sprint 4** có số liệu benchmark/chaos, báo cáo đầy đủ, 2 lần diễn tập, đóng băng (M4); tuần dự phòng chỉ sửa lỗi chặn nộp. Nếu tài nguyên AWS trễ hạn quota hoặc Tunnel không ổn định, dùng cổng thoát: tạo tay tài nguyên có ghi runbook, Terraform chỉ giữ 4 module dữ liệu.

## 3.4. Hiện trạng Worker (31/10/2026)

Phần Worker đã hoàn tất phần **local-first**; các mốc phụ thuộc AWS vẫn chờ môi trường của nhóm Cloud.

- **Pipeline thực tế:** SQS long-poll → tải input từ S3 → ffprobe validate (200 MB / 300 giây / 1080p) → FFmpeg một lần giải mã, `split` ra HLS 360p/720p + thumbnail JPEG → PUT output với Content-Type/Cache-Control → DynamoDB `COMPLETED` có điều kiện → ACK.
- **Độ tin cậy:** heartbeat gia hạn visibility 120s/300s; SIGTERM trả message về queue (visibility 30s) và thoát 0; lỗi hệ thống giữ message để retry, DLQ sau 3 lần nhận; idempotency bỏ qua video đã `COMPLETED`; **không exactly-once** — hai worker cùng job có thể transcode trùng, worker ghi `COMPLETED` đầu tiên thắng, worker sau ACK message trùng (`COMPLETED_BY_OTHER`). Đây là giới hạn đã đo, chờ chốt contract ownership.
- **Bằng chứng local (image `sha256:613656ca…`, FFmpeg 5.1.9, source `b5cf8ea`):**
  - `make e2e-worker` PASS: `COMPLETED`, `attemptCount=1`, assets và header đúng, 360p/720p decode đủ segment/frame, thumbnail JPEG, playback `/media/*`, worker exit 0.
  - `make chaos` PASS trong container: SIGTERM, SIGKILL, DLQ (`receiveCount=4`, worker nhận 3), hai worker cùng job.
  - `make bench` (12 job × 2 lượt, video 10s 720p): 1 worker 0,31 job/s, p95 duration 4.437 ms; 2 worker 0,33 job/s, p95 duration 6.978 ms. Hai worker chỉ tăng ~6,5% throughput vì chia sẻ CPU một host — số liệu này **không** dùng cho autoscaling AWS.
  - Log JSON có `jobId/videoId/projectId/attempt/queueWaitMs/durationMs/ffmpegMs/bytesIn/bytesOut/errorCode`; **worker chưa phát metric CloudWatch**, C5 lấy số liệu từ log theo bảng bàn giao ở [docs-worker/5. Contact docs](../docs-worker/5.%20Contact%20docs.md) mục 5.6.
- **Chưa đạt:** A4 (backend login/confirm chưa có), M2/M3/M4 (chưa có AWS: pipeline thật, scale 1→2→1 + scale-in protection, benchmark on-demand, chaos AWS, hai diễn tập, đóng băng). Không có số liệu AWS nào được ghi thay thế.

> Các mục tiếp theo của báo cáo (kết quả triển khai hạ tầng, thực nghiệm scaling/recovery, số liệu benchmark) sẽ được viết bổ sung sau khi các dòng 9–12 của bảng 3.2 hoàn thành.
