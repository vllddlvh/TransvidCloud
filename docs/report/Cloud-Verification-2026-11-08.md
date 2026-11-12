# Kết quả kiểm thử và metrics ngày 08/11/2026

## Kết luận để đưa vào báo cáo

Cloud API và luồng điều phối xử lý video chạy được trong lượt đo này: **903 request đã ghi nhận, 0 HTTP 5xx, 0 phản hồi ngoài mong đợi; batch 12/12 video COMPLETED ở attempt 1**. Throughput quan sát từ client là **4.29 video/phút** trên **1 worker t3.large Spot**. FFmpeg trung bình **13.358 s/video 10 s**; p95 queue wait **151.210 s**.

**Chưa đạt toàn bộ cổng kiểm định**: frontend còn 2 test fail, OpenAPI generated types chưa đồng bộ, và kiểm tra FFprobe phát hiện rendition 360p chứa 2 luồng video + 2 luồng audio. Các số hiệu năng dưới đây phản ánh đúng image đang triển khai, bao gồm lỗi rendition này; chưa dùng để kết luận hệ thống đã tối ưu hay đạt chất lượng ABR cuối cùng.

## 1. Bối cảnh và phương pháp

| Thông tin | Giá trị |
| --- | --- |
| Domain đo | https://vbeeaistt.com — API qua CloudFront/Cloudflare; PUT trực tiếp S3 |
| Lượt đo cloud (ICT, UTC+7) | 23:14:05 8/11/2026 → 23:18:07 8/11/2026 |
| Thời lượng runner cloud | 241.174 s |
| Local source | Branch nguyen-1; HEAD a5a6392; có thay đổi script vận hành chưa commit |
| Client | Windows; Node 24.22.1; npm 11.19.1 |
| Frontend tools | Vite 7.3.7; Vitest 4.1.12; TypeScript 5.9.3; openapi-typescript 7.13.0 |
| Browser tools | Playwright 1.64.1; Chromium; E2E_BASE_URL=https://vbeeaistt.com |
| API EC2 | t3.small, ap-southeast-1a, running |
| Worker EC2 | t3.large Spot, ap-southeast-1a, running; ASG min=0/desired=1/max=3 |
| FFmpeg/FFprobe trên worker | 5.1.9-0+deb12u1, xác minh trực tiếp qua SSM |
| Dataset batch | 12 bản sao sample-10s-720p.mp4; nguồn 10,000 s, 1280×720, 30 fps, H.264 + AAC |
| Dung lượng mỗi input | 1520913 B |
| SHA-256 input | `13dd66cc88dc257931648cce38d937e9feba09d285860f0ba0d66e474c38715d` |
| Project kiểm thử | `prj_1c8857d4046c` |

- API chính: 30 request tuần tự/endpoint; 3 request health warmup không tính vào mẫu. Latency đo tại client từ trước fetch đến khi đọc xong response body, gồm mạng, proxy và thời gian xử lý; không phải thời gian xử lý nội bộ backend.
- Batch: tạo ticket và PUT S3 với concurrency 2, upload xong toàn bộ 12 file rồi confirm với concurrency 2. Poll tối đa 4 request đồng thời, nghỉ 1 s giữa các vòng; một vòng còn gồm thời gian request nên sai số phát hiện COMPLETED có thể vượt 1 s.
- p50/p95 tính bằng nearest-rank: phần tử thứ ceil(n×p) sau khi sắp xếp. Với n=12, p95 chính là max. Không coi p95 của mẫu nhỏ là bảo đảm SLA.
- Kiểm thử local chạy đồng thời trong một phần thời gian lấy mẫu cloud, nên tải máy client có thể ảnh hưởng latency quan sát. Không có stress test tìm giới hạn RPS hay benchmark 1-vs-2 worker trong lượt này.
- Chỉ tạo dữ liệu test riêng; không đổi desired capacity, deploy image hay restart dịch vụ trong lượt kiểm thử này. Có 2 tài khoản/project test do runner cloud và browser tạo; 15 video hợp lệ được giữ để đối chiếu, 2 video giả đã xóa qua API.

Image tag đang chạy được đối chiếu bằng SSM; digest tra từ ECR theo tag:

| Component | Tag | ECR manifest digest |
| --- | --- | --- |
| api | `2d6581c460f5a77c9609a57f60d1f65126da8176-42188700401-1` | `sha256:54725d4295a336e75646e8b3fd283f0a1f114854ae73067b9659ca6eed9be92e` |
| worker | `2d6581c460f5a77c9609a57f60d1f65126da8176-42188700401-1` | `sha256:447e5d1930580ef77a3f32ebef8b585429dd192725b779bf94f23ccd29b315df` |

## 2. Kết quả kiểm thử phần mềm

| Lớp kiểm tra | Kết quả | Số lượng / thời gian |
| --- | --- | --- |
| Backend unit + web slice | PASS | 175 test; 0 fail / 0 error / 0 skip; tổng suite time 9.85 s |
| Backend integration LocalStack/Testcontainers | PASS | 24 test; 0 fail / 0 error / 0 skip; tổng suite time 110.84 s |
| Backend clean verify | PASS | 199 test; Maven báo 03:15; wall command 212.107 s, gồm pull/setup |
| Worker Go + race detector | PASS | 59 test cấp trên / 120 named test events gồm subtest; 10 package; 0 fail / 0 skip |
| Worker wall command | Đã đo | 80.998 s gồm apt, tải dependencies, compile và test; cửa sổ JSON test events 1.169 s |
| Frontend Vitest | FAIL | 140/142 pass (98.59%); 2 fail; 12/13 file pass; command 6.692 s |
| Frontend TypeScript | PASS | 10.010 s |
| Production Vite build | PASS | 6.083 s wall; Vite báo 4,51 s, 1.618 modules |
| Production bundle guard | PASS | node scripts/check-production-bundle.mjs; 0.087 s |
| OpenAPI generated-types check | FAIL | Generated types are not up-to-date; 0.905 s |
| Cloud measured API/pipeline runner | PASS | 903 request, 12 batch + 2 edge video hợp lệ + 1 video giả bị từ chối đúng |
| Browser E2E trên domain thật | PASS | 1 case; 38.022 s runner, 38.413 s wall; 0 fail / 0 skip |
| FFprobe đọc frame qua HLS | PASS | 300 frame H.264 / 10 s ở cả luồng 360p và 720p |
| Rendition 360p một video + một audio | FAIL | Có 2 video (640×360 và 1280×720), 2 audio trong cùng rendition/TS |

Static checks:

| Kiểm tra | Kết quả | Wall time |
| --- | --- | --- |
| terraform-fmt | PASS | 0.511 s |
| terraform-validate | PASS | 1.724 s |
| powershell-parse | PASS | 0.010 s |
| node-syntax | PASS | 0.039 s |

Browser E2E đi qua đăng ký → tạo/sửa project → tạo/revoke API key → S3 upload → Worker COMPLETED → đổi Auto/720p/360p → embed ẩn danh → file giả FAILED_VALIDATION → xóa video lỗi. Bài test xác nhận manifest parsed và trạng thái UI; chưa assert số frame đã phát hoặc chất lượng của stream giải mã. Vì vậy browser PASS không phủ nhận finding FFprobe.

Frontend bundle: main JS **322.298 B** (gzip 100,84 kB), HlsPlayer **597.976 B** (gzip 187,17 kB), CSS **59,47 kB** (gzip 21,76 kB). HlsPlayer vượt ngưỡng cảnh báo chunk 500 kB; build vẫn exit 0.

## 3. Latency API công khai

Đơn vị ms; mọi request trong các nhóm 30 mẫu bên dưới đều trả mã HTTP dự kiến.

| Endpoint | n | Min | Mean | p50 | p95 | Max |
| --- | --- | --- | --- | --- | --- | --- |
| GET /api/actuator/health | 30 | 85.10 | 331.24 | 179.28 | 1325.98 | 1467.51 |
| GET /api/auth/me | 30 | 86.47 | 272.40 | 175.21 | 483.39 | 1580.29 |
| GET /api/projects | 30 | 97.83 | 168.24 | 131.28 | 352.73 | 433.31 |
| GET /api/projects/:id | 30 | 82.64 | 137.78 | 126.47 | 192.45 | 438.71 |
| POST /api/auth/login | 30 | 202.95 | 250.78 | 243.92 | 314.42 | 347.37 |

Các thao tác upload/poll, đo trong workload này:

| Thao tác | n | Mean ms | p50 ms | p95 ms | Max ms |
| --- | --- | --- | --- | --- | --- |
| POST /api/projects/:id/videos | 15 | 365.99 | 208.51 | 1517.22 | 1517.22 |
| PUT S3 sample | 15 | 478.22 | 469.17 | 791.75 | 791.75 |
| POST /api/videos/:id/confirm | 15 | 371.09 | 305.59 | 1227.80 | 1227.80 |
| GET /api/videos/:id polling | 641 | 237.81 | 131.69 | 677.51 | 1509.71 |

903 request gồm **641 request polling**; không diễn giải thành 903 người dùng đồng thời hoặc công suất tối đa. Phân bố HTTP: 200=867, 201=17, 202=15, 204=1, 401=2, 403=1. Các HTTP 401 và 403 là kiểm thử âm có chủ đích, không phải request ngoài mong đợi. Ba warmup, traffic browser và các lần FFprobe không nằm trong 903 mẫu này.

## 4. Batch worker và phân tách thời gian

| Đại lượng | Giá trị |
| --- | --- |
| Video batch hoàn tất | 12/12, 100%; mỗi video có đúng 1 job_received và 1 job_finished COMPLETED trong log |
| Số worker thực nhận batch | 1 |
| Attempt | 12/12 attempt 1, không thấy nhận/xử lý lặp cho batch |
| Wall client: trước confirm đầu → sau quan sát COMPLETED cuối | 167.702 s |
| Throughput theo wall client | 4.29 video/phút |
| Window log: job_received đầu → job_finished cuối | 166.584 s |
| Throughput theo log server | 4.32 video/phút |
| Worker busy time / window | 99.92% — tỷ lệ chiếm dụng thời gian job, KHÔNG phải CPU utilization |
| FFmpeg real-time factor trung bình | 1.336 s xử lý / s video |
| Tổng bytes input / output | 18250956 / 84781956 B |
| Output / input | 4.65× — gồm cả HLS/rendition/thumbnail hiện có lỗi stream mapping |
| Queue và DLQ tại snapshot | Visible=0, in-flight=0; DLQ visible=0, in-flight=0 |

Đơn vị giây cho bảng phân phối dưới đây:

| Đại lượng | n | Min | Mean | p50 | p95 | Max |
| --- | --- | --- | --- | --- | --- | --- |
| Chờ dispatch/queue (requestedAt → job_received) | 12 | 0.061 | 75.568 | 69.270 | 151.210 | 151.210 |
| Xử lý worker, đến sau ACK | 12 | 13.786 | 13.871 | 13.863 | 13.983 | 13.983 |
| Chạy FFmpeg | 12 | 13.323 | 13.358 | 13.348 | 13.414 | 13.414 |
| Confirm → client quan sát COMPLETED | 12 | 15.943 | 90.468 | 84.121 | 166.187 | 166.187 |
| PUT S3 của video batch | 12 | 0.247 | 0.483 | 0.283 | 0.792 | 0.792 |

Queue wait trong log được tính từ requestedAt của message đến job_received theo đồng hồ worker. Nó gồm dispatch/outbox và thời gian trong SQS; không phải phép đo riêng thời gian lưu trong SQS. Worker duration gồm đọc trạng thái, tải file, probe, FFmpeg, upload output, cập nhật DynamoDB và ACK. Thời gian client confirm → observed còn gồm HTTP và sai số polling.

Chi tiết từng video batch:

| Video ID | Queue s | Worker s | FFmpeg s | Client s | Bytes out | Attempt |
| --- | --- | --- | --- | --- | --- | --- |
| `vid_58928e4117ce` | 0.061 | 13.879 | 13.378 | 15.943 | 7065539 | 1 |
| `vid_b55ec43ed3f5` | 69.270 | 13.841 | 13.348 | 84.121 | 7064411 | 1 |
| `vid_404e86444acd` | 13.646 | 13.832 | 13.378 | 28.360 | 7065539 | 1 |
| `vid_722c67c1135f` | 96.678 | 13.983 | 13.363 | 111.030 | 7064975 | 1 |
| `vid_7d6179b544b8` | 124.507 | 13.863 | 13.362 | 139.345 | 7064975 | 1 |
| `vid_06fceafbf6d7` | 110.312 | 13.864 | 13.329 | 125.170 | 7065539 | 1 |
| `vid_07f08a52b3e0` | 138.026 | 13.918 | 13.376 | 152.558 | 7066479 | 1 |
| `vid_397332f420e5` | 26.631 | 13.877 | 13.341 | 41.037 | 7064975 | 1 |
| `vid_ef37bec5ef8d` | 40.546 | 13.786 | 13.338 | 56.039 | 7064035 | 1 |
| `vid_d0e657364776` | 54.000 | 13.921 | 13.414 | 68.896 | 7065539 | 1 |
| `vid_97a076a1d80e` | 81.929 | 13.852 | 13.323 | 96.934 | 7065539 | 1 |
| `vid_ff9b4211222a` | 151.210 | 13.835 | 13.347 | 166.187 | 7064411 | 1 |

## 5. Edge cases và kiểm chứng media

| Input / tình huống | Kết quả | Confirm → observed |
| --- | --- | --- |
| sample-360p.mp4 | COMPLETED | 5.552 s |
| sample-no-audio.mp4 | COMPLETED | 7.236 s |
| invalid-video | FAILED_VALIDATION / NOT_A_VIDEO | 1.845 s |
| GET projects không có JWT | 401 đúng dự kiến | Một request |
| Login sai mật khẩu | 401 đúng dự kiến | Một request |
| Media không tồn tại | 403; không trả HTML SPA | Một request |

14 video hợp lệ được kiểm tra master playlist và thumbnail qua HTTP 200, đúng MIME. Đọc toàn bộ segment của 3 video đại diện (batch đầu, nguồn 360p, nguồn không audio): **8 segment khác nhau**, mỗi segment đọc lần đầu và lặp lại. Đây là xác minh tài sản khả dụng, tách biệt với kiểm tra cấu trúc stream bên dưới.

FFprobe -count_frames trên asset công khai của video batch đầu:

| Asset | Số video | Số audio | Video quan sát | Duration |
| --- | --- | --- | --- | --- |
| source | 1 | 1 | 1280×720, 300 frames | 10.000 s |
| 360p | 2 | 2 | 640×360, 300 frames; 1280×720, 300 frames | 10.000 s |
| 720p | 1 | 1 | 1280×720, 300 frames | 10.000 s |

**Finding HLS-01 (FAIL):** file TS đầu tiên trong playlist 360p cũng chứa 2 H.264 (640×360 và 1280×720) cùng 2 AAC, xác nhận đây là cấu trúc segment thật, không chỉ cách FFprobe liệt kê master. Khai báo nhãn 360p không cô lập luồng 360p như dự kiến. Có nguy cơ tốn băng thông và làm hành vi chọn chất lượng không đúng kỳ vọng; chưa định lượng tác động tới trải nghiệm phát trên từng thiết bị.

Code hiện tại trong [worker/internal/transcode/transcode.go](../../worker/internal/transcode/transcode.go), hàm BuildArgs, đặt cả map360 và map720 trước output HLS đầu tiên; output thứ hai không có bộ -map riêng. Đây là nguyên nhân phù hợp với quan sát FFprobe và cần sửa rồi đo lại. Lượt này chỉ kiểm thử, chưa sửa/deploy worker.

Không dùng format.size/bit_rate của FFprobe trên URL m3u8 làm dung lượng/bitrate video: các trường đó có thể là kích thước playlist. Bytes output trong bảng worker lấy từ log UploadDir.

Quan sát CDN (đơn vị ms; cỡ mẫu nhỏ):

| Nhóm | n | Mean | p50 | p95 |
| --- | --- | --- | --- | --- |
| GET media master first | 14 | 149.03 | 153.04 | 227.38 |
| GET media master repeat | 3 | 142.22 | 166.93 | 174.55 |
| GET media segment first | 8 | 441.12 | 398.74 | 723.22 |
| GET media segment repeat | 8 | 330.45 | 230.49 | 720.20 |

X-Cache ghi nhận: GET media segment first | Miss from cloudfront = 8; GET media segment repeat | Miss from cloudfront = 8. "First" chỉ lần đọc đầu của runner; kết luận cache dựa vào header, không tự giả định mọi first request là cold cache.

## 6. CloudWatch và snapshot vận hành

Cửa sổ query: **23:14:05 8/11/2026 → 23:25:37 8/11/2026 ICT**. EC2 dùng period=300 s vì basic monitoring; SQS/DynamoDB/CloudFront period=60 s. Dữ liệu thu lại sau lượt đo để giảm thiếu do ingestion delay. Bucket có thể bao trùm traffic ngoài test hoặc thời gian idle; đây không phải profile CPU chỉ riêng FFmpeg. Memory bên dưới chỉ là snapshot sau test, không phải peak.

| EC2 | Số bucket CPU | Mean bucket CPU % | Max CloudWatch Maximum % | NetworkIn B (sum) | NetworkOut B (sum) | StatusCheckFailed max |
| --- | --- | --- | --- | --- | --- | --- |
| api | 2 | 2.07 | 5.67 | 3816993 | 5797927 | 0 |
| worker | 2 | 30.45 | 94.05 | 21818052 | 100570055 | 0 |

| Metric dịch vụ | Datapoint | Giá trị tổng hợp |
| --- | --- | --- |
| SQS visible backlog max | 11 | 10 |
| SQS in-flight max | 11 | 1 |
| SQS oldest-message age max | 11 | 161 s |
| SQS sent / received / deleted | 11 | 17 / 17 / 17 |
| DynamoDB videos consumed read / write units | 11 | 351.00 / 140.00 |
| DynamoDB videos ReadThrottleEvents / WriteThrottleEvents | 0 / 0 | Không có datapoint; không thay dữ liệu thiếu bằng số 0 |
| CloudFront Requests sum | 7 | 1023 |
| CloudFront 5xxErrorRate max theo phút | 7 | 0.00% |
| CloudFront TotalErrorRate max theo phút | 7 | 5.88% |
| CloudFront BytesDownloaded sum | 7 | 43529931 B |

Request count của CloudFront/SQS là toàn tài nguyên trong cửa sổ đo, có thể gồm browser E2E, kiểm thử âm và traffic khác. Không yêu cầu nó bằng 903 mẫu client hoặc đúng 12 job batch. Không lấy trung bình đơn giản các error-rate theo phút để suy ra tỷ lệ lỗi tổng có trọng số. CloudWatch không có dữ liệu memory time series trong phép thu này.

Snapshot container qua SSM:

| Instance | Thời điểm ICT | Số liệu sau test |
| --- | --- | --- |
| i-0217b5e1fd2286b39 | 23:21:00 8/11/2026 | tvc-api CPU=0.33% MEM=330.5MiB / 1.865GiB MEM_PERCENT=17.31%; tvc-cloudflared CPU=0.22% MEM=18.95MiB / 1.865GiB MEM_PERCENT=0.99% |
| i-0cdda007edf62f393 | 23:21:04 8/11/2026 | tvc-worker CPU=0.00% MEM=8.867MiB / 7.637GiB MEM_PERCENT=0.11% |

SSM cả hai instance trả Success. API tvc.service active, ExecMainStatus=0; backend/tunnel/worker container running, health nội bộ UP. Các số memory/CPU snapshot không dùng để khẳng định mức sử dụng cao nhất khi transcode.

## 7. Lỗi còn tồn tại và giới hạn kết luận

1. **Rendition 360p sai cấu trúc stream (mới xác nhận):** FFprobe phát hiện 2 video + 2 audio trong playlist lẫn TS. Sửa map cho từng output FFmpeg, thêm regression test giải mã stream, rồi benchmark lại bytes/latency/throughput.
2. **Frontend 2/142 test fail:** src/mocks/media.test.ts:56, 360p và 720p. Fixture CRLF làm endsWith('.ts') không khớp vì còn ký tự CR cuối dòng. Không sửa fixture/test trong lượt báo cáo này.
3. **Generated API types stale:** npm run api:check exit 1. TypeScript compile PASS không thay thế gate đồng bộ contract này.
4. **Log integration test nhiễu:** OutboxRelayScheduler phát sinh Unknown table / Connection refused trong context LocalStack đang được dựng/dọn; 24 integration assertions vẫn PASS. Đây là finding về vòng đời fixture/test logging, không phải bằng chứng backend cloud đang lỗi DynamoDB.
5. **HTTP 502 trước đó không tái hiện trong lượt đo:** 0/903 request ghi nhận 5xx và CloudFront có max 5xxErrorRate 0% trong các bucket trả về. Không kết luận được nguyên nhân của request 502 cũ khi thiếu timestamp/request ID của nó.
6. **Chưa kiểm tra:** tải cực đại, nhiều người dùng đồng thời, AWS 1-vs-2 worker, hiệu quả autoscaling, Spot interruption/chaos, video dài >300 s hoặc lớn >1080p, trình duyệt khác, peak memory trong FFmpeg, chi phí thực tế. Dataset chính là một video tổng hợp 10 s lặp 12 lần; p95 n=12 trùng max. Không ngoại suy thành SLA hoặc throughput mọi loại video.
7. **Tooling đã xử lý khi chạy:** npm exec -- node ban đầu vướng npm cache; bundle guard chạy lại bằng node trực tiếp PASS. Lần browser đầu dùng backslash trong filter nên không tìm thấy test; chạy lại với real-stack.spec.ts thực sự chạy 1 case PASS. Các lần chưa khởi chạy test này không được tính là test sản phẩm fail.

## 8. Dữ liệu gốc và tái lập

- [API latency CSV](metrics/2026-11-08/api-latency.csv): toàn bộ nhóm request và phân phối.
- [Worker jobs CSV](metrics/2026-11-08/jobs.csv): 12 job, timestamp, timing và byte counters.
- [CloudWatch CSV](metrics/2026-11-08/cloudwatch.csv): từng datapoint thực, giữ timestamp.
- [Summary JSON](metrics/2026-11-08/summary.json): tổng hợp có cỡ mẫu, metadata và snapshot.
- [FFprobe 360p](metrics/2026-11-08/ffprobe-360p.json), [FFprobe TS đầu](metrics/2026-11-08/ffprobe-360p-first-segment.json), [FFprobe 720p](metrics/2026-11-08/ffprobe-720p.json).
- Raw bằng chứng local (gitignored): local/e2e-logs/20261108-cloud-report/ — cloud-metrics.json, http-samples.csv (903 dòng mẫu), worker-events.json (116 event của project test), JUnit XML, Go JSONL, Vitest JSON, Playwright JSON và log lệnh. Không lưu JWT, mật khẩu, AWS access key hay presigned URL trong các file metrics.

Lệnh đo cloud đã chạy từ thư mục gốc:

```powershell
node tests/e2e/cloud-metrics.mjs https://vbeeaistt.com local/e2e-logs/<run-id>
```

Mặc định CLOUD_METRIC_SAMPLES=30 và CLOUD_METRIC_JOBS=12. Mỗi lần chạy tạo dữ liệu test riêng và giữ video hợp lệ. Rerun sẽ tạo một bộ mẫu mới, không dùng chung kết quả hiện tại.

Các suite local: backend dùng Java 21 container và ./mvnw -B -ntp clean verify (Testcontainers được cấp Docker socket); worker dùng Go 1.24 + FFmpeg và go test -race -json ./...; frontend chạy npm test -- --configLoader runner, npm exec -- tsc -b, npm exec -- vite build --configLoader runner, node scripts/check-production-bundle.mjs, npm run api:check. Docker setup scripts và thời gian từng lệnh nằm trong thư mục raw evidence.

Đo lại HLS sau khi sửa là điều kiện cần trước khi dùng những số liệu này làm kết quả nghiệm thu chất lượng ABR cuối cùng.
