# TransVidCloud – Tổng hợp Báo cáo tiến độ lần 1 và lần 2

**Môn học:** Điện toán đám mây (INT3319 1) – Trường Đại học Công nghệ, ĐHQGHN
**Giảng viên:** TS. Hoàng Xuân Tùng
**Nhóm 12:**

| Thành viên | MSSV | Vai trò (BC2) |
|---|---|---|
| Đào Lê Long Vũ | 23021750 | Frontend, Backend (Worker) |
| Bùi Hải Phương | 23021666 | Frontend, Backend (API) |
| Nguyễn Hữu Lưu | 23021617 | DevOps |
| Hồ Văn Tiến Nguyên | 23021649 | DevOps |

**Đề tài:** TransVidCloud – Nền tảng xử lý và phân phối video trên AWS

---

## Mục lục

1. [Tổng quan dự án](#1-tổng-quan-dự-án)
2. [Đối tượng người dùng](#2-đối-tượng-người-dùng)
3. [Kiến trúc tính năng (7 phân hệ)](#3-kiến-trúc-tính-năng-7-phân-hệ)
4. [Luồng xử lý tổng thể](#4-luồng-xử-lý-tổng-thể)
5. [Kiến trúc triển khai trên AWS](#5-kiến-trúc-triển-khai-trên-aws)
6. [Bảng giao diện tương tác](#6-bảng-giao-diện-tương-tác)
7. [Chịu lỗi, nhất quán và co giãn](#7-chịu-lỗi-nhất-quán-và-co-giãn)
8. [Vận hành, CI/CD và giám sát](#8-vận-hành-cicd-và-giám-sát)
9. [Đánh đổi và phương án dự phòng](#9-đánh-đổi-và-phương-án-dự-phòng)
10. [Kế hoạch triển khai và ước tính nỗ lực](#10-kế-hoạch-triển-khai-và-ước-tính-nỗ-lực)
11. [So sánh tổng hợp Báo cáo 1 và Báo cáo 2](#11-so-sánh-tổng-hợp-báo-cáo-1-và-báo-cáo-2)
12. [Nhận xét và điểm cần lưu ý](#12-nhận-xét-và-điểm-cần-lưu-ý)

---

## 1. Tổng quan dự án

TransVidCloud là nền tảng xử lý và phân phối video trên đám mây, giải quyết bài toán xử lý video tốn nhiều CPU và có tải biến động mạnh (có thể tăng đột biến khi nhiều video được tải lên cùng lúc).

**Mô hình:** kết hợp **SaaS** (giao diện Web cho người dùng cuối) và **Developer API** (cho hệ thống bên thứ ba). Cả hai dùng chung một lõi xử lý video phân tán.

**Khả năng chính:**
- Tiếp nhận và lưu trữ an toàn video gốc.
- Xử lý **bất đồng bộ** để không làm quá tải máy chủ ứng dụng.
- Chuyển mã thành nhiều độ phân giải/bitrate (360p, 720p), đóng gói **HLS**, tạo thumbnail, lưu metadata.
- Xử lý lỗi, thử lại (retry), **tự động co giãn** theo khối lượng công việc để tối ưu chi phí, thay vì duy trì cụm xử lý lớn chạy liên tục.

> **Chương 1 của Báo cáo 2 giữ nguyên hoàn toàn so với Báo cáo 1.**

---

## 2. Đối tượng người dùng

### 2.1. Người dùng SaaS trực tiếp
- **Ai:** nhà sáng tạo nội dung, giảng viên/giáo viên trực tuyến, bộ phận truyền thông số của doanh nghiệp/tổ chức – không nhất thiết có kiến thức sâu về cloud hay kỹ thuật video.
- **Nhu cầu:** giao diện trực quan, thao tác đơn giản, hỗ trợ toàn bộ quy trình.
- **Use case (Hình 1.2):** đăng ký/đăng nhập; quản lý dự án/workspace; tải video lên; xem thư viện và chi tiết video; theo dõi trạng thái xử lý; phát video đầu ra (HLS); lấy streaming URL / mã nhúng.

### 2.2. Nhà phát triển và đội ngũ kỹ thuật của ứng dụng bên ngoài
- **Ai:** kỹ sư phần mềm, kiến trúc sư giải pháp, đội vận hành hệ thống web/mobile (thương mại điện tử, mạng xã hội, cổng thông tin, truyền thông…).
- **Nhu cầu:** API và dịch vụ xử lý video sẵn có, dễ tích hợp, tự động hóa từ tải lên → xử lý → phân phối; giảm công sức vận hành hạ tầng riêng.
- **Use case (Hình 1.3):** tạo project và quản lý API key; cấu hình địa chỉ Webhook; khởi tạo video và lấy Presigned URL; tải video trực tiếp lên S3; gửi yêu cầu xử lý/transcoding; tra cứu trạng thái; nhận thông báo hoàn tất qua Webhook; lấy HLS/thumbnail và tích hợp phát video.

### 2.3. Khách hàng tham chiếu: nền tảng giáo dục trực tuyến (E-learning)
1. Giảng viên đăng tải video bài giảng lên hệ thống E-learning.
2. Thay vì để máy chủ E-learning tự giải mã/nén (có thể làm nghẽn bài tập, thi cử), hệ thống gửi yêu cầu tới TransVidCloud qua Developer API.
3. Sau khi xử lý phân tán trên AWS, video phát được cho nhiều học viên trên trình duyệt và thiết bị di động, với **Adaptive Bitrate Streaming** điều chỉnh chất lượng theo mạng.

---

## 3. Kiến trúc tính năng (7 phân hệ)

Hệ thống tách các nghiệp vụ (người dùng, dự án, tiếp nhận, điều phối) khỏi tác vụ chuyển mã nặng. Cả SaaS Web và Developer API dùng chung dịch vụ nghiệp vụ và pipeline xử lý, tránh trùng lặp logic. Báo cáo 2 xác nhận **cấu trúc các phân hệ không đổi**, chỉ cụ thể hóa thiết kế (khung API, cấu trúc dữ liệu, message tác vụ, quy tắc chuyển trạng thái, giới hạn đầu vào). Các chức năng ứng dụng **chưa được triển khai thành luồng hoàn chỉnh** tại thời điểm BC2.

### 3.1. Quản lý người dùng và xác thực
| | Nội dung |
|---|---|
| **BC1** | Người dùng SaaS xác thực bằng **JWT**; ứng dụng bên ngoài dùng **API Key**. Mọi yêu cầu được xác thực trước khi thao tác với dự án/video/tác vụ. |
| **BC2 bổ sung** | Xác định nhóm API đăng ký, đăng nhập, tạo API Key theo dự án. Mô hình dữ liệu tách riêng người dùng và API Key; khóa tra cứu API Key dạng **`keyHash`**. Phạm vi MVP giao diện: **có màn hình đăng nhập, chưa có đăng ký trực tiếp trên Web**. |

### 3.2. Quản lý dự án và tài nguyên video
| | Nội dung |
|---|---|
| **BC1** | Tổ chức video theo dự án; mỗi video liên kết với một dự án và có bản ghi metadata (định danh video, dự án sở hữu, vị trí video gốc, trạng thái, danh sách tài sản đầu ra). |
| **BC2 bổ sung** | Xác định thao tác: tạo dự án, liệt kê dự án, liệt kê video theo dự án, xem chi tiết video. API danh sách video dùng **phân trang bằng cursor**. Mô hình dữ liệu cụ thể: bảng `projects`, `videos` và chỉ mục truy vấn dự án theo chủ sở hữu, video theo dự án. |

### 3.3. Tiếp nhận và lưu trữ video
| | Nội dung |
|---|---|
| **BC1** | Dùng **Presigned URL** để Client tải thẳng lên object storage. Client gửi yêu cầu khởi tạo → Backend tạo bản ghi và cấp URL có thời hạn → sau khi tải xong Client xác nhận → trạng thái `UPLOADING` → `PENDING`. Giảm tải băng thông cho máy chủ ứng dụng. |
| **BC2 bổ sung** | Backend kiểm tra tệp bằng **`HeadObject`** trước khi chuyển sang `PENDING`. Giới hạn đầu vào: **tối đa 209.715.200 byte (200 MB)**, **thời lượng 300 giây**, **chiều cao tối đa 1080 px**. Presigned URL hiệu lực **15 phút**; video nguồn lưu **7 ngày**. Phân chia kiểm tra: Frontend (phát hiện sớm lỗi chọn tệp) → Backend (thông tin khai báo + đối tượng đã tải) → Worker (đặc tính thực tế của video). |

### 3.4. Điều phối tác vụ xử lý video
| | Nội dung |
|---|---|
| **BC1** | Mỗi tác vụ là một thông điệp chứa job, video, project, vị trí video gốc; đưa vào hàng đợi cho Worker xử lý độc lập với Backend. Người dùng không phải giữ kết nối; thời gian phản hồi API không phụ thuộc độ phức tạp video. |
| **BC2 bổ sung** | Schema message bản nháp, trường bắt buộc: `schemaVersion`, `jobId`, `videoId`, `projectId`, `inputKey`. Hàng đợi chính + **Dead Letter Queue (DLQ)**. Cấu hình: visibility timeout **300 s**, long polling **20 s**, ngưỡng nhận **3 lần** trước khi chuyển DLQ. Đây mới là cấu hình giao nhận, chưa đồng nghĩa logic xử lý lỗi trong Worker đã hoàn thành. |

### 3.5. Xử lý và chuyển mã video (phân hệ cốt lõi)
| | Nội dung |
|---|---|
| **BC1** | Worker nhận tác vụ từ hàng đợi, lấy video gốc, xử lý bằng **FFmpeg**: chuyển mã **360p và 720p**, đóng gói **HLS**, tạo **thumbnail**; lưu vào vùng đầu ra và liên kết với metadata. Có retry trong giới hạn; cập nhật trạng thái được kiểm soát để tránh xử lý trùng/ghi đè kết quả đã hoàn tất. |
| **BC2 bổ sung** | Thêm bước kiểm tra đầu vào bằng **`ffprobe`** trước khi chạy FFmpeg. Phân nhóm lỗi: **kiểm tra video / chuyển mã / lưu trữ**. Quy tắc xử lý job giao lại: kiểm tra trạng thái trước khi xử lý, **bỏ qua video đã hoàn tất**, dùng **đường dẫn đầu ra ổn định** (idempotent). |

### 3.6. Quản lý trạng thái và theo dõi tác vụ
| | Nội dung |
|---|---|
| **BC1** | Theo dõi vòng đời: khởi tạo → tải lên → chờ → đang xử lý → hoàn tất/lỗi. Trạng thái lưu tập trung, là nguồn thống nhất cho SaaS và API. **Backend** cập nhật giai đoạn tiếp nhận; **Worker** cập nhật giai đoạn xử lý. |
| **BC2 bổ sung** | Luồng trạng thái: `UPLOADING → PENDING → PROCESSING → COMPLETED`. Trạng thái lỗi: `FAILED_VALIDATION`, `FAILED_TRANSCODE`, `FAILED_STORAGE`. **`COMPLETED` là trạng thái cuối, không bị ghi đè** khi job giao lại. Số lần Worker nhận job ghi bằng `attemptCount`. Web **polling mỗi 3–5 giây** khi còn video chờ/đang xử lý; **chưa có thiết kế đo % tiến độ transcode**. |

### 3.7. Phân phối và khai thác video đầu ra
| | Nội dung |
|---|---|
| **BC1** | Cung cấp HLS, các phiên bản chất lượng, thumbnail; truy xuất đường dẫn phát và tích hợp vào web/app ngoài. Việc phân phối tách khỏi máy chủ nghiệp vụ. HLS nhiều mức chất lượng cho phép trình phát chọn bản phù hợp (ABR). |
| **BC2 bổ sung** | Dùng **master playlist** và trình phát HLS trên Web. Môi trường local đã có cấu hình đường dẫn `/media/` trỏ tới vùng lưu trữ đầu ra. Phạm vi: phát HLS và lấy liên kết là trọng tâm; **mã nhúng/trang nhúng có thể cắt giảm** tùy tiến độ. |

---

## 4. Luồng xử lý tổng thể

1. **Xác thực & khởi tạo:** người dùng SaaS/ứng dụng bên thứ ba xác thực, chọn dự án, gửi yêu cầu tạo video.
2. **Tiếp nhận:** Backend kiểm tra thông tin khai báo, tạo bản ghi `UPLOADING`, cấp Presigned URL.
3. **Tải lên & xác nhận:** Client PUT trực tiếp vào S3 Input rồi xác nhận; Backend kiểm tra tệp (`HeadObject`) → `PENDING`.
4. **Điều phối:** Backend gửi message theo schema chung vào SQS.
5. **Kiểm tra & xử lý:** Worker nhận job, kiểm tra trạng thái, chuyển `PROCESSING`, xác minh bằng `ffprobe`, chạy pipeline FFmpeg.
6. **Lưu kết quả:** Worker lưu HLS 360p/720p + thumbnail, cập nhật thông tin tài nguyên → `COMPLETED`.
7. **Truy xuất:** Client truy vấn trạng thái, lấy thông tin đầu ra để phát/tích hợp.
8. **Xử lý lỗi:** Worker ghi nhận lỗi; job chưa ACK được giao lại; đạt ngưỡng nhận → DLQ.

*BC2 bổ sung các điểm kiểm tra trước khi đưa vào hàng đợi, trước khi chuyển mã, và trước khi ghi nhận hoàn tất; cấu trúc message, trạng thái lỗi và quy tắc job giao lại đã có tài liệu riêng.*

---

## 5. Kiến trúc triển khai trên AWS

**Mô hình:** Client – Edge – Application – Processing – Data. Một backend, hai cách dùng (SaaS Web và Developer API).

**Ba nguyên tắc thiết kế (BC2):**
1. Video dung lượng lớn **không đi qua** máy chủ API.
2. Chuyển mã chạy nền, bất đồng bộ qua hàng đợi, người dùng không phải chờ.
3. Quyền truy cập tối thiểu; hạ tầng mô tả bằng mã (IaC).

> Các thông số (loại máy, số Worker, thời hạn URL, ngưỡng cảnh báo) là **cấu hình mục tiêu của MVP**, sẽ hiệu chỉnh khi triển khai thực tế. Những gì chưa triển khai/kiểm thử là thiết kế mục tiêu, **không phải kết quả đã xác nhận**.

### 5.1. Các lớp triển khai

**Lớp biên và định tuyến.** Amazon **CloudFront** là điểm vào duy nhất (một domain HTTPS) với 3 behavior:
- `/` → giao diện React từ **S3 Frontend**
- `/api/*` → Backend (không cache, để dữ liệu trạng thái luôn mới)
- `/media/*` → video đầu ra từ **S3 Output**

Các bucket giữ **private**, chỉ CloudFront đọc qua **Origin Access Control (OAC)**.

**Lớp API nghiệp vụ.** Backend **Spring Boot** chạy Docker trên **một EC2 t3.small**; quản lý xác thực, dự án, video; tạo Presigned URL; gửi job vào SQS; cung cấp API tra cứu trạng thái/tài sản. Không chạy FFmpeg nên độ trễ không phụ thuộc độ dài video. Dùng **IAM Role riêng**, không lưu khóa AWS trong mã nguồn.

**Lớp xử lý bất đồng bộ.** **SQS Standard** là vùng đệm giữa API và Worker (decoupling). Worker viết bằng **Go + FFmpeg** trong Docker, tạo HLS 360p/720p và thumbnail. Worker thuộc một **Auto Scaling Group** tự tăng giảm theo lượng job chờ.

**Lớp dữ liệu.**
- 3 bucket S3: **frontend** (bản build React), **input** (video gốc, tự xóa sau 7 ngày), **output** (HLS, thumbnail). Input và output đều private.
- **DynamoDB On-Demand** 4 bảng: `users`, `projects`, `apikeys`, `videos`. Bản ghi `videos` là **nguồn sự thật duy nhất** về trạng thái job.
- Worker truy cập S3 qua **S3 Gateway Endpoint** trong VPC → không cần NAT Gateway cho lưu lượng này.

**Bảo mật và vận hành.** JWT (SaaS) + API Key (tích hợp); IAM Role tách biệt cho API, Worker, CI/CD; Security Group không mở cổng nhận kết nối từ Internet; quản trị qua **SSM Session Manager** (không SSH); secret trong **SSM Parameter Store** (`/tvc/{env}/...`), không đưa vào mã nguồn hay Docker image.

### 5.2. Thay đổi kiến trúc triển khai giữa BC1 và BC2

Luồng cốt lõi (Client, Backend, S3, SQS/DLQ, Worker, DynamoDB) **không đổi**. BC2 chỉ điều chỉnh 3 quyết định:

| Nội dung | Báo cáo 1 | Báo cáo 2 | Lý do và tác động |
|---|---|---|---|
| **Đường vào API** | EC2 có Elastic IP, Security Group chỉ nhận HTTPS 443 từ CloudFront | CloudFront → **Cloudflare Tunnel** → EC2; EC2 không mở cổng nhận từ Internet (`cloudflared` chủ động mở kết nối ra Cloudflare; Backend nghe cổng nội bộ 8080) | Giảm bề mặt tấn công. Thêm một dịch vụ ngoài AWS cần kiểm thử; có phương án quay lại như BC1. |
| **Số Worker / loại máy** | ASG 1–4 máy | 1–4 máy; môi trường **dev cho phép Min = 0**; có thể dùng **máy Spot** | Tiết kiệm chi phí. Đổi lại khởi động chậm hơn khi Min = 0; Spot có thể bị thu hồi (SQS giao lại job). |
| **Phát video HLS** | CloudFront đọc từ S3 Output | Thêm **CloudFront Function** bỏ tiền tố `/media` | URL phát thống nhất. Cần kiểm thử đường dẫn trong playlist HLS để tránh lỗi 404. |

---

## 6. Bảng giao diện tương tác

### 6.1. Báo cáo 1 (13 giao diện)

| # | Nguồn → Đích | Loại | Nội dung |
|---|---|---|---|
| 1 | Client → CloudFront | Đồng bộ | HTTPS 443, một domain cho web, `/api/*`, `/media/*` |
| 2 | CloudFront → S3 frontend / S3 output | Đồng bộ | HTTPS GET; frontend trả React tĩnh; output phát HLS qua OAC |
| 3 | CloudFront → EC2 API | Đồng bộ | REST/JSON qua HTTPS, JWT hoặc API key, theo hợp đồng OpenAPI |
| 4 | Client → S3 input | Đồng bộ | HTTPS PUT bằng presigned URL (SigV4, hạn 15 phút, CORS) |
| 5 | EC2 API → DynamoDB | Đồng bộ | PutItem/UpdateItem: ghi bản ghi video, `UPLOADING → PENDING` |
| 6 | EC2 API → SQS | Bất đồng bộ | SendMessage: JSON gồm phiên bản schema, jobId, videoId, projectId, khóa file gốc |
| 7 | SQS ↔ Worker (và DLQ) | Bất đồng bộ | Long polling 20 s, visibility 300 s; gia hạn ~120 s/lần; DeleteMessage = ACK; quá 3 lần → DLQ (giữ 14 ngày, có alarm) |
| 8 | Worker → S3 input/output | Đồng bộ | AWS SDK qua S3 Gateway Endpoint: GetObject file gốc; PutObject HLS 360p/720p + thumbnail, Content-Type chuẩn |
| 9 | Worker → DynamoDB | Đồng bộ | UpdateItem có điều kiện: PROCESSING, COMPLETED, FAILED_*, attemptCount, assets; COMPLETED không bị ghi đè |
| 10 | API, Worker → CloudWatch → SNS | Điều khiển | PutLogEvents (JSON, giữ 7 ngày); alarm DLQ/CPU/tuổi message → email qua SNS; AWS Budgets 25/50 USD |
| 11 | metricpub → CloudWatch → ASG | Điều khiển | GetQueueAttributes, PutMetricData (BacklogPerInstance); target tracking 1–4 Worker, scale-in protection |
| 12 | GitHub Actions → ECR → EC2 | Điều khiển | OIDC build/test/push image (tag = commit SHA); API deploy qua SSM Run Command; Worker: ghi tag vào Parameter Store rồi ASG Instance Refresh |
| 13 | EC2 → SSM Parameter Store | Điều khiển | GetParameter đọc cấu hình/secret khi khởi động bằng IAM Role riêng |

### 6.2. Báo cáo 2 (15 giao diện)

| STT | Nguồn → Đích | Nội dung |
|---|---|---|
| 1 | Client (SaaS, ứng dụng ngoài) → CloudFront | HTTPS 443; web `/`, API `/api/*`, video `/media/*`. SaaS dùng JWT, ứng dụng ngoài dùng API Key |
| 2 | CloudFront → S3 Frontend | HTTPS GET tệp React tĩnh; bucket private, OAC |
| 3 | CloudFront; Cloudflare Tunnel → EC2 API | `/api/*` (REST/JSON, không cache) qua Cloudflare và tunnel EC2 mở sẵn, tới Backend cổng nội bộ 8080 |
| 4 | Backend; Client → S3 Input | Backend cấp Presigned URL 15 phút; Client PUT trực tiếp, video không đi qua API |
| 5 | Backend → DynamoDB; S3 Input | Khởi tạo ghi bản ghi `UPLOADING`; khi xác nhận kiểm tra file trên S3 rồi đổi sang `PENDING` |
| 6 | Backend → SQS | JSON ngắn: schema version, jobId, videoId, projectId, vị trí file gốc |
| 7 | SQS; Worker; DLQ | Long polling 20 s; ACK sau khi xử lý xong; 300 s chưa ACK thì giao lại; job dài thì gia hạn; sau 3 lần lỗi → DLQ (14 ngày) + cảnh báo |
| 8 | Worker → S3 Input; S3 Output | Đọc video gốc, ghi HLS 360p/720p + thumbnail theo quy ước ổn định (vd. `{projectId}/{videoId}/hls/master.m3u8`) qua S3 Gateway Endpoint |
| 9 | Worker → DynamoDB | Cập nhật PROCESSING/COMPLETED/FAILED, số lần thử, danh sách tài sản; có điều kiện nên COMPLETED không bị ghi đè |
| 10 | CloudFront; CloudFront Function → S3 Output | `/media/...` bỏ tiền tố `/media` rồi đọc qua OAC (playlist HLS, segment, thumbnail) |
| 11 | GitHub Actions → ECR; SSM; ASG | Test + build image, push ECR (tag = commit); OIDC; Backend cập nhật bằng SSM Run Command; Worker: ghi tag vào Parameter Store, ASG Instance Refresh |
| 12 | ECR; SSM → EC2 API; Worker | Máy kéo image từ ECR; đọc cấu hình/secret từ Parameter Store `/tvc/{env}/...` khi khởi động |
| 13 | API; Worker; SQS → CloudWatch | Log JSON (jobId, videoId, giữ 7 ngày), CPU, số message chờ; khi Min = 0, `metricpub` công bố thêm `BacklogPerInstance` |
| 14 | CloudWatch → ASG | Target Tracking tăng/giảm Worker 1–4 máy; Worker đang transcode được bảo vệ khỏi bị thu hồi |
| 15 | CloudWatch Alarm; AWS Budgets → SNS | Email khi DLQ có message, CPU cao, job chờ lâu, và chi phí chạm 25/50 USD (Budgets chỉ cảnh báo, không tự chặn chi tiêu) |

**Ba giao diện đáng chú ý nhất (BC1):**
- **Presigned URL:** API cấp URL có chữ ký, hết hạn 15 phút → video không qua API.
- **Gửi job qua SQS:** message ngắn; Worker chỉ phụ thuộc hợp đồng message, không phụ thuộc cấu trúc nội bộ của API.
- **Ghi DynamoDB có điều kiện:** API phụ trách `UPLOADING → PENDING`; Worker chỉ cập nhật có điều kiện các trạng thái xử lý → ranh giới ghi rõ, không ghi đè lẫn nhau.

---

## 7. Chịu lỗi, nhất quán và co giãn

### 7.1. Chịu lỗi và nhất quán
- SQS Standard giao **at-least-once** → một job có thể bị giao lại (Worker chết, máy Spot bị thu hồi…).
- Worker chỉ **ACK sau khi đã lưu xong kết quả** (BC2: ghi S3 đủ → cập nhật `COMPLETED` → mới ACK). Job chưa ACK tự xuất hiện lại sau **300 s** và Worker khác nhận xử lý.
- Job dài: Worker **gia hạn visibility** (~120 s/lần theo BC1).
- Để job lặp không gây sai lệch: **output key ổn định** (ghi cùng vị trí) + **ghi DynamoDB có điều kiện**; video đã `COMPLETED` thì bỏ qua.
- **BC2 phân loại lỗi:** lỗi do dữ liệu đầu vào (video không hợp lệ) → đánh dấu thất bại ngay, **không retry**; lỗi tạm thời → thử tối đa **3 lần** rồi chuyển **DLQ** kèm cảnh báo.

### 7.2. Tự động co giãn Worker

Chỉ số tùy biến do tiện ích **`metricpub`** công bố lên CloudWatch:

```
BacklogPerInstance = Số message đang chờ / max(1, Số Worker đang chạy)
```

Vòng điều khiển: `SQS` → `metricpub` (đọc độ sâu queue) → `CloudWatch` (BacklogPerInstance) → `ASG Target Tracking` (so với mục tiêu) → scale out/in `Worker EC2 (1–4 máy)` → Worker xử lý job → backlog giảm (khép kín).

- Giá trị mục tiêu sẽ được **hiệu chỉnh theo thời gian xử lý thực tế** của một job.
- **Scale-in protection** bật khi Worker đang transcode để tránh bị thu hồi giữa chừng.
- **BC2:** demo/benchmark giữ **Min = 1**; môi trường dev có thể **Min = 0** (khi đó `metricpub` phải công bố chỉ số cả lúc không có Worker và phải kiểm thử việc tự khởi động Worker khi có job mới).

---

## 8. Vận hành, CI/CD và giám sát

- **IaC:** toàn bộ hạ tầng bằng **Terraform**; BC1 nêu quy ước đặt tên `tvc-{env}-{resource}` tại region **`ap-southeast-1`**; BC2 chia module: lưu trữ, hàng đợi, cơ sở dữ liệu, mạng, IAM, tính toán, CloudFront, giám sát. Hai môi trường **dev** và **demo** dùng chung mã, chỉ khác biến cấu hình.
- **CI/CD:** GitHub Actions lấy quyền AWS tạm thời qua **OIDC**, test + build image Backend/Worker, đẩy lên **ECR** (tag = commit SHA). Backend deploy bằng **SSM Run Command** (không SSH). Worker: ghi tag mới vào Parameter Store rồi **ASG Instance Refresh** – quá trình này không được ngắt Worker đang transcode.
- **Giám sát:** CloudWatch thu log JSON (giữ 7 ngày), CPU, số job chờ. Cảnh báo qua SNS (email) khi DLQ có message, CPU cao, job chờ lâu.
- **Chi phí:** AWS Budgets cảnh báo ở **25 USD và 50 USD** (chỉ thông báo, không phải giới hạn tự động).

---

## 9. Đánh đổi và phương án dự phòng

| Chủ đề | BC1 | BC2 |
|---|---|---|
| **Mạng** | Worker ở public subnet nhưng không mở inbound, thay cho private subnet + NAT Gateway → tiết kiệm ~32 USD/tháng; đổi lại cô lập mạng thấp hơn, cần siết IAM và routing. | Tương tự, áp dụng cho **EC2** (API và Worker): public subnet, không mở cổng inbound; cần siết Security Group và IAM. |
| **CloudFront** | Phương án chính; dự phòng: bỏ CloudFront, frontend gọi trực tiếp API và S3. | Giữ nguyên. |
| **Số Worker** | Tối thiểu 1 Worker, không scale-to-zero, để luôn sẵn sàng. | Min = 0 và Spot giúp giảm chi phí nhưng tăng thời gian khởi động và có thể bị thu hồi → **demo/benchmark dùng Min = 1**; thu hồi máy được xử lý nhờ SQS giao lại job. |
| **Cloudflare Tunnel** | — | Giảm tấn công vào EC2 nhưng phụ thuộc dịch vụ ngoài AWS. Nếu không ổn định → quay lại phương án BC1 (Elastic IP, chỉ nhận HTTPS từ CloudFront). |

---

## 10. Kế hoạch triển khai và ước tính nỗ lực

### 10.1. Cơ sở lập kế hoạch

| Thông số | Báo cáo 1 | Báo cáo 2 |
|---|---|---|
| Số thành viên | 4 | 4 |
| Số sprint | 4 sprint × 1 tuần + ~1 tuần bổ sung | 4 sprint × 1 tuần + 1 tuần dự phòng |
| Thời gian/người/tuần | 1–2 ngày | **2–3 ngày** |
| Capacity thô | 20–40 pd (5 tuần) | **32–48 pd** |
| Capacity danh nghĩa / sử dụng | 30 pd (1,5 ngày/người/tuần) | **40 pd** (2,5 ngày/người/tuần × 4 tuần) |
| Capacity hữu dụng | ~24 pd (sau dự phòng 20%) | — (không nêu) |
| Workload kế hoạch | **43 pd** (40 triển khai + 3 báo cáo/demo) – *vượt capacity* | **36 pd** (90% capacity 40 pd; TB 9 pd/người) – *trong capacity* |
| Phân loại ưu tiên | P0 (cốt lõi) / P1 (nâng cao, có thể hoãn) | High / Medium |

*1 pd (person-day) = một người làm việc tập trung trong một ngày.*

### 10.2. Phân công vai trò

**Báo cáo 1** (chuyên môn hóa theo module):

| Thành viên | Vai trò chính | Trách nhiệm |
|---|---|---|
| Bùi Hải Phương | Backend Java/Spring Boot | Backend + Developer API; JWT/API key; quản lý project/video/job; tích hợp AWS SDK (Presigned URL, SQS, DynamoDB); kiểm thử quyền truy cập |
| Đào Lê Long Vũ | Go Worker và xử lý video | Worker Go + FFmpeg; nhận job SQS; chuyển mã, HLS/thumbnail; deploy Worker lên EC2; kiểm thử scaling và khôi phục lỗi |
| Nguyễn Hữu Lưu | AWS Foundation và SaaS Web | S3, SQS/DLQ, DynamoDB, cấu hình lưu trữ/mạng; giao diện React tối thiểu (project, video, trạng thái, player, mã nhúng) |
| Hồ Văn Tiến Nguyên | AWS Compute và vận hành | EC2, ASG, CloudWatch, IAM/Security Groups, CloudFront/OAC, quản lý chi phí; SSM, Terraform, CI/CD nếu còn thời gian |

**Báo cáo 2** (phân công đơn giản hóa):

| Thành viên | Vai trò | Trách nhiệm |
|---|---|---|
| Bùi Hải Phương | Frontend, Backend | Backend, Developer API và giao diện SaaS |
| Đào Lê Long Vũ | Frontend, Backend | Backend, Worker và giao diện SaaS |
| Nguyễn Hữu Lưu | DevOps | Thiết lập các dịch vụ Cloud |
| Hồ Văn Tiến Nguyên | DevOps | Thiết lập các dịch vụ Cloud |

### 10.3. Kế hoạch hành động – Báo cáo 1 (13 hạng mục, 43 pd)

| ID | Hạng mục | Sprint | Ưu tiên | Effort (pd) | Phụ trách |
|---|---|---|---|---|---|
| A01 | Chốt kiến trúc, hợp đồng tích hợp, khung dự án | 1 | P0 | 2,5 | Phương, Vũ; cả nhóm review |
| A02 | Hạ tầng AWS nền tảng và bảo mật mạng | 1, 3 | P0 | 3,0 | Lưu, Nguyên |
| A03 | Backend và Developer API | 1–3 | P0 | 5,5 | Phương; Lưu hỗ trợ |
| A04 | Luồng upload và điều phối job | 2 | P0 | 2,0 | Phương; Lưu, Nguyên hỗ trợ |
| A05 | Pipeline Worker và chuyển mã video | 1–2 | P0 | 5,0 | Vũ; cả nhóm phối hợp |
| A06 | Xử lý lỗi và độ tin cậy job | 2 | P0 | 2,0 | Vũ, Phương; Lưu hỗ trợ |
| A07 | Giao diện SaaS tối thiểu | 1–3 | P0 | 3,0 | Lưu; Phương hỗ trợ API |
| A08 | Phát video và tích hợp nội dung | 3 | P0 | 2,0 | Lưu, Nguyên; Vũ kiểm tra |
| A09 | Giám sát và tự động co giãn cơ bản | 3 | P0 | 2,5 | Nguyên, Vũ |
| A10 | Webhook thông báo trạng thái | 3 | **P1** | 1,5 | Phương; Nguyên hỗ trợ |
| A11 | Vận hành và tự động hóa hạ tầng (SSM, alarm, Terraform, CI/CD) | 3–4 | **P1** | 7,0 | Nguyên; các thành viên hỗ trợ |
| A12 | Kiểm thử tích hợp và thực nghiệm Cloud | 4 | P0 | 4,0 | Vũ chủ trì benchmark |
| A13 | Hoàn thiện báo cáo và chuẩn bị demo | Chốt | P0 | 3,0 | Cả nhóm |
| | **Tổng** | | | **43,0** | |

**Kế hoạch theo sprint – BC1:**

| Sprint | Trọng tâm | Đầu ra cần đạt | Effort |
|---|---|---|---|
| 1 | Chốt thiết kế, dựng nền tảng | API contract, message schema, trạng thái job thống nhất; AWS resource cơ bản; Backend/giao diện có khung chạy được; FFmpeg thử nghiệm thành công với video mẫu | 8,5 pd |
| 2 | Hoàn thiện pipeline end-to-end | Upload trực tiếp S3 → SQS → Worker FFmpeg tạo HLS 360p/720p + thumbnail → DynamoDB cập nhật; truy xuất qua API | 11,0 pd |
| 3 | Hoàn thiện sản phẩm và co giãn | SaaS phát HLS, lấy mã nhúng; API truy xuất trạng thái/tài sản; Worker scale 1→2 máy khi tải tăng, scale-in khi giảm; CloudWatch metric | 9,5 pd |
| 4 | Vận hành, kiểm thử, thực nghiệm | Kiểm thử tích hợp, benchmark giữa các cấu hình Worker, một kịch bản Worker lỗi; thu số liệu/biểu đồ; Terraform & CI/CD nâng cao nếu còn thời gian | 11,0 pd |
| Chốt | Báo cáo và demo | Báo cáo đúng kiến trúc thực tế, hướng dẫn vận hành, kết quả thực nghiệm, kịch bản demo | 3,0 pd |

**Phụ thuộc & mốc kiểm tra (BC1):** thống nhất hợp đồng → hạ tầng nền → upload → job vào SQS → Worker chuyển mã → lưu kết quả → truy xuất/phát video; Auto Scaling và benchmark làm sau khi pipeline ổn định.
- **Cuối Sprint 1:** hợp đồng tích hợp thống nhất; POC FFmpeg và kết nối AWS được kiểm chứng.
- **Cuối Sprint 2:** hoàn thành luồng upload → queue → Worker → HLS/thumbnail → cập nhật trạng thái trên AWS. *Đây là mốc quan trọng nhất; nếu chưa đạt, hoãn P1 và tập trung hoàn thiện pipeline chính.*

**Mục tiêu ưu tiên:** một luồng end-to-end có thể demo trên AWS, sau đó chứng minh co giãn tài nguyên và phục hồi khi Worker lỗi bằng thí nghiệm có số liệu.

### 10.4. Kế hoạch triển khai – Báo cáo 2 (13 hạng mục, 36 pd)

| ID | Công việc | Giao diện liên quan | Sprint | Ưu tiên | Effort (pd) | Phụ trách |
|---|---|---|---|---|---|---|
| A01 | Chốt kiến trúc và khung dự án | Toàn bộ | 1 | High | 2 | Cả nhóm |
| A02 | Backend Auth, Project, Video | [1], [3], [5] | 1–2 | High | 5 | Phương |
| A03 | Upload và điều phối job | [4], [5], [6], [9] | 2–3 | High | 2,5 | Phương |
| A04 | Hoàn thiện Worker pipeline | [7], [8], [9] | 1–2 | High | 3 | Vũ |
| A05 | Xây dựng giao diện người dùng | [1], [2], [4], [10] | 2–3 | High | 4 | Vũ |
| A06 | IAM và bảo mật | [5], [6], [8], [9], [12] | 1–2 | High | 1,5 | Nguyên |
| A07 | Đóng gói Docker và stack local | [12] | 1–2 | Medium | 1,5 | Lưu |
| A08 | Terraform hạ tầng dữ liệu | [4], [5], [8], [9] | 1–2 | High | 3 | Lưu |
| A09 | Compute và deploy | [11], [12] | 2–3 | High | 4 | Nguyên |
| A10 | Kiểm thử E2E và tích hợp | [1]–[10] | 3–4 | High | 3 | Lưu |
| A11 | Benchmark và chaos test | [13], [14] | 4 | Medium | 2 | Nguyên |
| A12 | Hỗ trợ kiểm thử E2E (Worker, giao diện) | [1], [7], [10] | 4 | Medium | 0,5 | Vũ |
| A13 | Báo cáo và demo | Toàn bộ | 1–4 + dự phòng | Medium | 4 | Cả nhóm |
| | **Tổng** | | | | **36** | |

**Timeline BC2 (Master Timeline):**

| Sprint | Thời gian | Mốc bàn giao |
|---|---|---|
| Sprint 1 | 02/11 – 06/11/2026 (D1–D5) | M1: Kiến trúc và hạ tầng nền xong |
| Sprint 2 | 09/11 – 13/11/2026 (D6–D10) | M2: Pipeline end-to-end |
| Sprint 3 | 16/11 – 20/11/2026 (D11–D15) | M3: SaaS/API và Compute xong |
| Sprint 4 | 23/11 – 27/11/2026 (D16–D20) | M4: Kiểm thử và benchmark xong |
| Dự phòng | sau D20 | Hoàn thiện báo cáo, sửa lỗi, chuẩn bị demo |

Nhóm có một Google Sheets workbook theo dõi tiến độ (BC2 có liên kết).

---

## 11. So sánh tổng hợp Báo cáo 1 và Báo cáo 2

| Khía cạnh | Báo cáo 1 | Báo cáo 2 |
|---|---|---|
| **Tính chất** | Đề xuất ban đầu ("dự kiến") | Bản "chính thức", cụ thể hóa thiết kế |
| **Chương 1** | — | Không đổi |
| **7 phân hệ chức năng** | Mô tả mức khái niệm | Cấu trúc không đổi; bổ sung chi tiết API, dữ liệu, schema message, trạng thái, giới hạn |
| **Trạng thái lỗi** | `FAILED_*` | Cụ thể: `FAILED_VALIDATION`, `FAILED_TRANSCODE`, `FAILED_STORAGE` |
| **Giới hạn video** | Chưa nêu | 200 MB, 300 s, ≤ 1080p |
| **Kiểm tra đầu vào** | Chưa nêu rõ | Frontend → Backend (`HeadObject`) → Worker (`ffprobe`) |
| **Đường vào API** | CloudFront → EC2 (Elastic IP) | CloudFront → **Cloudflare Tunnel** → EC2 |
| **Worker ASG** | 1–4 | 1–4; dev Min = 0; có thể Spot |
| **Phát HLS** | CloudFront → S3 Output | + **CloudFront Function** bỏ `/media` |
| **Số giao diện** | 13 | 15 (tách CI/CD, ECR/SSM, CloudWatch, Alarm/Budgets) |
| **Số hình kiến trúc** | Hình 2.1 (tổng) + 2.2 (co giãn) | Hình 2.1 (chức năng) + 2.2 (runtime) + 2.3 (CI/CD, giám sát, co giãn) |
| **Phụ thuộc ngoài AWS** | Không | Cloudflare Tunnel (có phương án quay lại) |
| **Thời gian/người/tuần** | 1–2 ngày | 2–3 ngày |
| **Tổng effort** | 43 pd (vượt capacity ~24–30 pd) | 36 pd (90% capacity 40 pd) |
| **Phạm vi** | P0 + P1 (Webhook, Terraform/CI/CD nâng cao có thể hoãn) | Terraform, Docker local, benchmark/chaos test là hạng mục chính; không còn Webhook trong bảng |
| **Phân công** | Chuyên môn hóa 4 vai trò | 2 người Frontend/Backend + 2 người DevOps |
| **Tiêu chí thận trọng** | Nêu rõ: không cam kết toàn bộ tính năng | Nêu rõ: cấu hình chưa triển khai/kiểm thử là thiết kế mục tiêu |

---

## 12. Nhận xét và điểm cần lưu ý

1. **Kiến trúc cốt lõi ổn định:** Presigned URL → SQS → Worker (FFmpeg) → S3/DynamoDB giữ nguyên qua hai báo cáo; các thay đổi chủ yếu nhằm **giảm bề mặt tấn công và chi phí**.
2. **Mâu thuẫn nhỏ về Min Worker:** BC2 sơ đồ (Hình 2.2) ghi *Min 0/1 – Max 4*, trong khi văn bản chốt demo/benchmark dùng **Min = 1**; Min = 0 chỉ áp dụng môi trường dev.
3. **Rủi ro cần kiểm thử (BC2 tự nêu):** kết nối Cloudflare Tunnel; đường dẫn playlist HLS sau khi bỏ tiền tố `/media` (nguy cơ 404); khởi động Worker từ Min = 0; thu hồi máy Spot.
4. **Lỗi ghi chú trong BC2 (cần sửa nếu nộp):** chú thích Hình 2.2 và 2.3 trong sơ đồ vẫn ghi "số giao diện trong **Bảng 2.1**", trong khi đúng phải là **Bảng 2.2**. Trường hợp trích dẫn "Hình 2.1.1" trong BC1 cũng không khớp với đánh số hình thực tế.
5. **Phạm vi bị cắt giảm ở BC2:** hạng mục Webhook không còn trong bảng kế hoạch; mã nhúng/trang nhúng có thể cắt giảm; đăng ký trực tiếp trên Web ngoài phạm vi MVP; chưa đo % tiến độ chuyển mã.
6. **Kế hoạch thực tế hơn ở BC2:** tổng effort (36 pd) nằm trong capacity (40 pd), khác với BC1 vốn vượt capacity và phải dựa vào cơ chế hoãn P1.
7. **Hiện trạng triển khai (BC2):** mới có tài liệu, cấu hình nền tảng và môi trường local (ví dụ cấu hình `/media/`); **chưa có luồng ứng dụng hoàn chỉnh** (Backend, Worker, Frontend chưa hoàn thiện).
