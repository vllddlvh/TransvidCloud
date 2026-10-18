# Epic và phân việc (bản v3, đã chốt phân công)

Hai nhóm: **App** (Vũ, Phương: backend, worker, frontend) và **Cloud** (Lưu, Nguyên: hạ tầng và triển khai từ local lên cloud).
Ưu tiên: **P0** = cốt lõi, **P1** = nâng cao, hoãn nếu chậm. Effort lấy từ Bảng 3.3 của báo cáo (pd); số đánh dấu "ước" là ước tính mới của nhóm.

---

## 0. Cân đối năng lực (đọc trước)

| | pd |
|---|---|
| Baseline báo cáo (P0 + P1) | 43,0 |
| P0 đã cắt gọn và điều chỉnh theo phân công mới (mục 3) | **khoảng 34** |
| Capacity hữu dụng ở 1,5 ngày/người/tuần (báo cáo) | 24 |
| Capacity hữu dụng ở 2 ngày/người/tuần (4 người x 5 tuần x 2 x 0,8) | 32 |

Bỏ hết P1 mà P0 vẫn vượt capacity báo cáo khoảng 10 pd. Nhóm chọn một trong hai:
1. Nâng lên khoảng 2 ngày/người/tuần (vừa khít, gần như không có dự phòng); hoặc
2. Giữ 1,5 ngày/tuần và cắt tiếp theo mục 7.

**M2 (cuối Sprint 2)** là điểm quyết định: chưa có pipeline end-to-end chạy trên AWS thì không làm P1.

---

## 1. Phân vai (đã chốt)

| Người | Nhóm | Phụ trách | Thư mục chính |
|---|---|---|---|
| **Phương** | App | Backend, Developer API, contract | `backend/`, `contracts/` |
| **Vũ** | App | Worker (Go + FFmpeg), frontend React | `worker/`, `frontend/` |
| **Lưu** | Cloud | Local stack + Docker, hạ tầng dữ liệu/mạng (Terraform), e2e/benchmark harness | `local/`, `docker-compose.yml`, `tests/e2e/`, `infra/terraform/modules/{storage,queue,database,network}` |
| **Nguyên** | Cloud | IAM, compute, tên miền + Cloudflare Tunnel + CloudFront, quan sát và co giãn, CI/CD | `infra/terraform/modules/{iam,compute,edge,observability}`, `scripts/`, `.github/` |

Ranh giới App - Cloud:
- App viết **code** và cung cấp lệnh chạy, cổng, biến môi trường. Cloud viết **Dockerfile, compose, script deploy** và review chéo với App.
- Cloud sở hữu đường đi **local -> cloud**: `make up` chạy được, rồi cùng image đó chạy được trên AWS.

Tải ước tính theo người (pd, đã tính việc dùng chung):

| Người | Việc chính | pd |
|---|---|---|
| Phương | A1 (0,75), A2 (5,5), A3 (2,0), A5 (0,5), A8 (0,5), R1 (0,75) | 10,0 |
| Vũ | A1 (0,75), A4 (4,0), A5 (1,5), A6 (2,0), A8 (0,5), R1 (0,75) | 9,5 |
| Lưu | C0 (2,5), C1 (1,5), C4 hosting (0,5), A8 (1,5), R1 (0,75) | 6,75 |
| Nguyên | C2 (1,5), C3 (2,0), C4 (1,5), C5 (1,5), A8 (0,5), R1 (0,75) | 7,75 |
| **Tổng** | | **34,0** |

Cặp App nặng hơn Cloud khoảng 3 pd mỗi người. Nếu App chậm, hai việc có thể chuyển sang Lưu mà không đổi ranh giới: viết lớp truy cập DynamoDB/SQS cho backend (báo cáo A03 vốn ghi "Lưu hỗ trợ tích hợp") và dựng bộ khung trang frontend.

---

## 2. Quyết định đã chốt cho MVP

| Chủ đề | Quyết định | Ảnh hưởng |
|---|---|---|
| Giới hạn upload | Tối đa **200 MB**, **5 phút**, độ phân giải tối đa **1080p**, định dạng mp4/mov/mkv/webm; cấu hình bằng biến môi trường. Chi tiết: `contracts/upload-limits.md` | A3 (API), A4 (Worker kiểm tra), C1 (lifecycle S3) |
| Tên miền | Domain đã đăng ký, DNS ở Cloudflare; **API đi qua Cloudflare Tunnel** (`cloudflared` chạy trên EC2 API); CloudFront giữ vai trò phân phối frontend và HLS | C3, C4; báo cáo mục 2.2 phải sửa |
| Worker | **Tối ưu chi phí**: Spot, số máy tối thiểu cấu hình được (0 khi không dùng, 1 khi demo/benchmark), máy nhỏ, giữ nguyên ASG + SQS | C3, C5, A8 |

---

## 3. Bảng epic và đối chiếu báo cáo

| Epic | Báo cáo | pd | Chủ | Sprint | Ưu tiên |
|---|---|---|---|---|---|
| A1 Contract và khung code | A01 | 1,5 (báo cáo 2,5; phần Docker chuyển sang C0) | Phương, Vũ | 1 | P0 |
| A2 Auth, project, video | A03 | 5,5 | Phương | 1-3 | P0 |
| A3 Upload và điều phối job | A04 | 2,0 | Phương | 2 | P0 |
| A4 Worker pipeline | A05 | 4,0 (báo cáo 5,0; phần deploy EC2 chuyển sang C3) | Vũ | 1-2 | P0 |
| A5 Độ tin cậy job | A06 | 2,0 | Vũ, Phương | 2 | P0 |
| A6 Frontend tối thiểu | A07, A08 (player) | 2,0 (báo cáo 3,0) | Vũ | 2-3 | P0 |
| A7 Webhook | A10 | 1,5 | Phương | 3 | **P1** |
| A8 E2E và benchmark | A12 | 3,0 (báo cáo 4,0) | Lưu chủ trì, Vũ/Phương hỗ trợ | 4 | P0 |
| C0 Local stack và Docker | (mới) | 2,5 | Lưu | 1 | P0 |
| C1 Hạ tầng dữ liệu bằng Terraform | A02 | 1,5 | Lưu | 1, 3 | P0 (4 module) |
| C2 IAM và bảo mật | A02 | 1,5 | Nguyên | 1-2 | P0 |
| C3 Compute và deploy | A05, A09 | 2,0 | Nguyên | 2 | P0 |
| C4 Tên miền, Cloudflare Tunnel, CloudFront | A08 | 2,0 | Nguyên (Lưu hosting React) | 3 | P0 |
| C5 Quan sát và co giãn | A09 | 1,5 | Nguyên, Vũ phối hợp | 3 | P0 |
| C6 CI/CD và Terraform phần còn lại | A11 | 7,0 | Nguyên | 3-4 | **P1** |
| R1 Báo cáo và demo | A13 | 3,0 | Cả nhóm | Chốt | P0 |

Khác báo cáo: C0 là việc mới; Terraform 4 module dữ liệu/mạng lên P0; C4 thêm Cloudflare Tunnel; A6, A8, C5 giảm phạm vi; thêm R1 vào danh sách epic.

---

## 4. Nhóm App

### A1. Contract và khung code (Sprint 1) - Phương, Vũ
- [ ] Chốt `video-state-machine.md`, `sqs-job-message.schema.json`, `s3-key-convention.md`, `dynamodb-tables.md`, `upload-limits.md`
- [ ] `openapi.yaml` đủ endpoint luồng chính
- [ ] Khung `backend/` (Spring Boot) và `worker/` (Go) chạy được ở máy cá nhân; ghi rõ cổng và biến môi trường để Lưu viết Dockerfile
- **Xong khi:** cả 4 người review contract.

### A2. Auth, project, video (Sprint 1-3) - Phương
- [ ] Đăng ký/đăng nhập JWT; tạo API key (lưu hash) và xác thực bằng API key
- [ ] CRUD project; danh sách video theo project (phân trang cursor)
- [ ] Phân quyền: chỉ thấy tài nguyên của mình
- [ ] CORS cấu hình qua biến môi trường (chỉ cần nếu frontend và API khác origin)

### A3. Upload và điều phối job (Sprint 2) - Phương
- [ ] `POST videos`: client khai báo `sizeBytes`, `contentType`; **từ chối nếu vượt giới hạn**; ký presigned URL 15 phút kèm Content-Length đã khai báo (cần thử để chắc S3 từ chối file sai kích thước)
- [ ] `POST confirm`: HeadObject kiểm tra tồn tại và dung lượng, rồi `UPLOADING -> PENDING` và gửi SQS
- [ ] `GET video`: trạng thái, assets, link streaming
- **Xong khi:** upload file 201 MB bị từ chối, file hợp lệ vào queue.

### A4. Worker pipeline (Sprint 1-2) - Vũ
- [ ] **POC FFmpeg** trên file local: HLS 360p + 720p và thumbnail
- [ ] Tối ưu chi phí CPU: giải mã một lần, tạo hai bản (filter `split`), preset `veryfast`, không tạo 1080p
- [ ] ffprobe kiểm tra thời lượng, độ phân giải, codec -> `FAILED_VALIDATION` nếu vượt giới hạn hoặc không phải video
- [ ] Consumer SQS, tải input, đẩy output đúng key và Content-Type, ghi DynamoDB có điều kiện, dọn thư mục tạm sau mỗi job
- **Xong khi:** `make up`, upload video mẫu cho ra `COMPLETED` và đủ assets.

### A5. Độ tin cậy job (Sprint 2) - Vũ, Phương
- [ ] Gia hạn visibility (~120s/lần); idempotency; `FAILED_*`; 3 lần nhận thì vào DLQ
- [ ] **Xử lý SIGTERM** để Worker Spot bị thu hồi thì dừng gọn và trả message về queue
- [ ] Hoàn thiện `local/scripts/chaos-kill-worker.sh` (kill giữa chừng, retry, DLQ)

### A6. Frontend tối thiểu (Sprint 2-3) - Vũ
- [ ] Đăng nhập, danh sách/tạo project
- [ ] Upload bằng presigned URL, **kiểm tra dung lượng/định dạng phía client**, danh sách video và trạng thái (polling)
- [ ] Trình phát HLS (hls.js), link streaming và đoạn mã nhúng dạng copy
- Luôn gọi đường dẫn tương đối `/api/...`, `/media/...`.

### A7. Webhook (Sprint 3, P1) - Phương
- [ ] Cấu hình `webhookUrl`, callback khi `COMPLETED`/`FAILED_*` có ký và retry

---

## 5. Nhóm Cloud

### C0. Local stack và Docker (Sprint 1) - Lưu, Nguyên review
- [ ] `make infra` và `make smoke` pass trên máy từng người (có `LOCALSTACK_AUTH_TOKEN`)
- [ ] Dockerfile cho `backend` (JRE), `worker` (có FFmpeg), build frontend; `make up` chạy toàn bộ qua nginx `localhost:8000`
- [ ] README quickstart; kiểm tra trên máy Windows của cả nhóm
- **Xong khi:** M0 (ngày 3) cho `make infra`; Dockerfile xong trước cuối Sprint 1. **Việc chặn nhóm App, làm đầu tiên.**

### C1. Hạ tầng dữ liệu bằng Terraform (Sprint 1, 3) - Lưu
- [ ] Bootstrap bucket lưu state; module `storage`, `queue`, `database`, `network`; `envs/dev` apply được
- [ ] **Lifecycle S3:** xóa object bucket input sau 7 ngày (MVP), CORS cho presigned PUT
- **Xong khi:** khớp `contracts/` và `local/`.

### C2. IAM và bảo mật (Sprint 1-2) - Nguyên
- [ ] Role riêng API/Worker/CI (least privilege); SSM Parameter Store `/tvc/{env}/...`
- [ ] Security Group: **API không mở inbound** (Cloudflare Tunnel chỉ cần outbound); Worker không mở inbound
- [ ] Review chéo với Phương, Vũ về quyền DynamoDB/SQS

### C3. Compute và deploy (Sprint 2) - Nguyên
- [ ] EC2 API (Docker): container API + container `cloudflared` (`--network host`, token lấy từ Parameter Store dạng SecureString)
- [ ] ECR cho `tvc-backend`, `tvc-worker`
- [ ] Launch Template + ASG Worker: **Spot, nhiều loại máy nhỏ** (mixed instances, on-demand base 0), min/max cấu hình bằng biến, EBS gp3 nhỏ; user-data đọc tag image từ Parameter Store, pull từ ECR, chạy container
- [ ] Session Manager, không mở SSH; **kiểm tra Service Quotas (vCPU on-demand và Spot) ngay tuần 1**, xin tăng nếu cần
- **Xong khi:** M2, pipeline end-to-end chạy trên AWS.

### C4. Tên miền, Cloudflare Tunnel, CloudFront (Sprint 3) - Nguyên, Lưu hosting React
- [ ] Cloudflare Tunnel: hostname dành cho origin (ví dụ `origin-api.<domain>`) trỏ tới `http://localhost:8080` trên EC2
- [ ] CloudFront 3 behavior (`/`, `/api/*`, `/media/*`); `/api/*` dùng origin là hostname của tunnel, **không chuyển tiếp header Host của người xem**; OAC cho bucket output
- [ ] Hosting React ở bucket frontend; dùng tên `*.cloudfront.net` trước, gắn tên miền riêng (ACM ở us-east-1, CNAME ở Cloudflare, chế độ DNS only) khi pipeline đã ổn
- **Xong khi:** một địa chỉ HTTPS dùng được web, API và phát HLS.

### C5. Quan sát và co giãn (Sprint 3) - Nguyên, Vũ phối hợp
- [ ] CloudWatch log JSON (có `jobId`/`videoId`), alarm DLQ, SNS email, AWS Budgets 25/50 USD
- [ ] Co giãn theo backlog: nếu chạy **min 0** thì giữ `metricpub` (công thức `max(1, workers)` cho phép tăng từ 0); nếu luôn **min >= 1** có thể dùng metric math của ASG và bỏ `metricpub` (xem mục 8.4)
- [ ] Scale-in protection khi đang transcode
- **Xong khi:** M3, worker tự tăng 1 lên 2 và giảm khi hết tải.

### C6. CI/CD và Terraform phần còn lại (Sprint 3-4, P1) - Nguyên
- [ ] OIDC, hoàn thiện `deploy.yml`, hai script deploy, CODEOWNERS; Terraform cho `iam`/`compute`/`edge`/`observability`
- **Ghi chú:** nếu chậm, tạo tay có ghi lại các bước và deploy bằng script vẫn đạt mục tiêu môn học.

### A8. E2E và benchmark (Sprint 4) - Lưu chủ trì, Vũ/Phương hỗ trợ, Nguyên cấp môi trường
- [ ] Bộ e2e chạy được với `BASE_URL` local và cloud; có ca upload quá giới hạn
- [ ] Benchmark throughput **1 vs 2 worker** (4 nếu còn thời gian) trên **on-demand, loại máy cố định** để kết quả lặp lại được; demo và dev mới dùng Spot
- [ ] 1 kịch bản Worker lỗi trên AWS; thu số liệu và biểu đồ

### R1. Báo cáo và demo (giai đoạn chốt) - Cả nhóm
- [ ] Báo cáo phản ánh kiến trúc **thực tế** (Cloudflare Tunnel, Spot, giới hạn upload), kết quả thí nghiệm
- [ ] Demo chạy thử ít nhất 2 lần; dựng môi trường `demo` trước và `destroy` sau

---

## 6. Điểm đồng bộ và mốc

| Điểm | Bên App | Bên Cloud | Khi nào |
|---|---|---|---|
| Local stack, Dockerfile | Cung cấp lệnh chạy, cổng, env | Lưu dựng, App review | Ngày 1-3 và cuối Sprint 1 |
| Key schema DynamoDB | Viết code theo | Lưu tạo bảng (local + Terraform) | Sprint 1 |
| Message SQS, giới hạn upload | Gửi/nhận, kiểm tra | Queue, lifecycle, alarm | Sprint 1-2 |
| IAM theo bảng/queue | Liệt kê quyền cần | Nguyên viết policy | Sprint 2 |
| Image Docker | Code build được | ECR, EC2, ASG | Cuối Sprint 2 |
| SIGTERM/Spot | Worker xử lý tín hiệu dừng | ASG Spot, thời gian chờ | Sprint 2-3 |
| Benchmark, chaos | Kịch bản, hỗ trợ | Lưu chủ trì, Nguyên cấp môi trường | Sprint 4 |

| Mốc | Tiêu chí | Quyết định |
|---|---|---|
| **M0** (ngày 3) | `make infra` chạy; backend/worker nối được LocalStack | Mở khóa App |
| **M1** (cuối Sprint 1) | Contract chốt; POC FFmpeg; Dockerfile; Terraform tạo được S3/SQS/DynamoDB thật | |
| **M2** (cuối Sprint 2) | Pipeline end-to-end chạy trên AWS | **Giữ hay hoãn P1** |
| **M3** (Sprint 3) | SaaS + API qua CloudFront và tên miền; worker scale 1 lên 2 | |
| **M4** (Sprint 4) | Benchmark và chaos có số liệu | |
| **M5** | Báo cáo và demo | |

---

## 7. Cắt giảm khi thiếu thời gian (theo thứ tự)

1. A7 Webhook, C6 CI/CD và Terraform ngoài 4 module dữ liệu/mạng (đã là P1).
2. Gắn tên miền riêng cho CloudFront (dùng `*.cloudfront.net` cho demo).
3. A6: bỏ đoạn mã nhúng, chỉ giữ trình phát và link streaming.
4. A8: benchmark 1 vs 2 worker, một kịch bản lỗi.
5. C5: chỉ làm scaling theo backlog, bỏ alarm phụ ngoài DLQ.
6. A2: JWT trước, API key sau (cắt cuối cùng vì là điểm khác biệt của Developer API).

Không cắt: pipeline upload -> queue -> worker -> HLS -> `COMPLETED`, một thí nghiệm co giãn có số liệu, một thí nghiệm khôi phục lỗi, giới hạn upload, báo cáo và demo.

---

## 8. Rủi ro kỹ thuật cần chốt sớm

### 8.1. Cloudflare Tunnel kết hợp CloudFront
- Chuỗi chạy: người dùng -> CloudFront -> Cloudflare (proxy) -> Tunnel -> EC2. Origin của CloudFront là hostname tunnel có chứng chỉ hợp lệ do Cloudflare cấp, nên không còn lỗi chứng chỉ khi nối tới EC2 (vấn đề của thiết kế gốc dùng tên DNS mặc định của EC2).
- CloudFront **không được chuyển tiếp Host của người xem** tới origin (dùng origin request policy loại trừ Host), nếu không Cloudflare không khớp ingress của tunnel.
- Token tunnel là bí mật: lưu SecureString trong Parameter Store, không commit, không in log. Tạo tunnel là bước thủ công một lần, ghi lại trong `docs/`.
- Hostname origin vẫn truy cập được trực tiếp từ internet (không qua CloudFront). Với MVP chấp nhận vì API có xác thực; có thể thêm header bí mật hoặc Cloudflare Access sau.
- Upload video đi thẳng lên S3 bằng presigned URL nên **không bị giới hạn kích thước request của Cloudflare**.
- **Phương án đơn giản hơn nếu chuỗi trên rắc rối:** bỏ `/api/*` khỏi CloudFront, frontend ở `app.<domain>` (CloudFront), API ở `api.<domain>` (Tunnel). Đổi lại phải cấu hình CORS (API và bucket output) và local không còn giống cloud.
- Local không cần Cloudflare; nginx vẫn giả lập một domain. Có thể dùng `cloudflared` tạm để chia sẻ bản local cho thành viên khác xem.

### 8.2. Giới hạn upload
- Presigned PUT không tự giới hạn kích thước; lớp bảo vệ gồm: client (UX) -> API từ chối khai báo vượt giới hạn và ký kèm Content-Length -> `confirm` kiểm tra HeadObject -> Worker kiểm tra ffprobe -> lifecycle S3 dọn file.
- 200 MB nằm trong một lần PUT, không cần multipart.
- Worker cần ổ đĩa đủ cho input + output của một job (EBS gp3 20-30 GB là dư).

### 8.3. Tối ưu chi phí Worker
- **Spot:** hợp với thiết kế vì SQS giao lại message khi Worker chết. Khi bị thu hồi, Worker nhận cảnh báo (khoảng 2 phút) nên xử lý SIGTERM để trả job. Dùng nhiều loại máy nhỏ để giảm rủi ro hết Spot capacity.
- **Min 0:** tiết kiệm nhất khi không dùng, đổi lại khởi động nguội 2-4 phút (khởi tạo máy + pull image). Dùng min 1 khi demo/benchmark.
- **Quota:** tài khoản AWS mới có thể có hạn mức vCPU thấp; 4 Worker 2 vCPU + 1 API có thể vượt, cần kiểm tra Service Quotas sớm.
- **Public IPv4:** mỗi địa chỉ IPv4 công khai bị tính phí theo giờ, kể cả Worker; vẫn rẻ hơn NAT Gateway. Với Cloudflare Tunnel, EC2 API không cần Elastic IP.
- **T3 và benchmark:** dòng `t3` có CPU credits, hết credits sẽ bị giới hạn làm sai kết quả; dùng dòng `c` (hoặc bật unlimited) khi benchmark.
- Bản ARM (Graviton) rẻ hơn nhưng phải build image arm64; bỏ qua ở MVP.
- Terraform `destroy` môi trường không dùng; AWS Budgets 25/50 USD.

### 8.4. Co giãn từ 0 và metric math
- Công thức báo cáo `BacklogPerInstance = backlog / max(1, workers)` cho phép tăng từ 0 máy. Metric math của ASG (`ApproximateNumberOfMessagesVisible / GroupInServiceInstances`) được AWS hướng dẫn cho SQS nhưng chia cho số máy đang chạy nên **không dùng được khi 0 máy**. Quyết định theo chế độ: min 0 -> `metricpub`; min >= 1 -> metric math, bớt một thành phần cần viết và vận hành.

### 8.5. Khác
- **Instance Refresh** với ít máy: dùng `ScaleInProtectedInstances=Wait` để không thay máy đang transcode.
- **Tên tài nguyên:** `tvc-{env}-api`, `tvc-{env}-worker-asg`, `/tvc/{env}/worker/image-tag` được script dùng cứng; Terraform phải đặt đúng tên.
- **Effort deploy EC2/ASG** chỉ được gộp trong A05, A09 ở báo cáo; C3 đã tách riêng để theo dõi.
