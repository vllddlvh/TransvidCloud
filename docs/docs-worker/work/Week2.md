# Work — Tuần 2: Hoàn thiện pipeline và độ tin cậy Worker (Sprint 2)

> Tuần 2 trong thư mục `work/` ứng với **Sprint 2 — cuối tuần 7 đến hết tuần 8 học kỳ**, không phải một tuần bổ sung sau tuần 8. Đối chiếu [[docs/introduction/1. Timelines|timeline chung v4]] và mục 3 của [[docs/docs-worker/4. Timelines|timeline Worker]].
> Người phụ trách: **Vũ** — A4, phần Worker của A5; phối hợp Phương, Lưu và Nguyên để đạt **M2 cuối tuần 8**.
> Đây là **kế hoạch**, lập ngày **28/10/2026**. Kế thừa bằng chứng tuần 1 nhưng không mặc định công việc tuần 2 đã hoàn thành; cập nhật tiến độ thực tế ở mục 6.

## 1. Mục tiêu

Kết thúc tuần 2, phần Worker phải đáp ứng:

1. **A4 — Tích hợp local thật:** `make up`, upload qua API → confirm → SQS → Worker → HLS 360p/720p + thumbnail → `COMPLETED`; phát được qua `/media/.../master.m3u8`.
2. **A5 — Giữ và trả job đúng:** heartbeat gia hạn visibility khi đang xử lý; SIGTERM ngừng nhận việc, dừng job đang chạy và trả message sớm để worker khác nhận lại.
3. **A5 — Retry và dữ liệu nhất quán:** không ghi đè `COMPLETED`, không ACK khi còn lỗi hệ thống; kiểm chứng message trùng, worker chết giữa job và lỗi lặp lại vào DLQ.
4. **M2 — Pipeline AWS:** cùng phiên bản image đã qua local chạy được trên AWS; có video thật, trạng thái/assets đúng và bằng chứng retry cơ bản.
5. **Chuẩn bị M3/M4:** bàn giao log phục vụ đo, dataset và kịch bản cho Lưu/Nguyên; viết phần triển khai Worker trong báo cáo từ tuần này.

**Không mở rộng trong tuần 2:** thêm rendition 1080p, tinh chỉnh bitrate/preset mới, concurrency nhiều job trong một worker, checkpoint/resume FFmpeg, webhook. Scale-in protection, dashboard/metric đầy đủ và demo scale 1 → 2 → 1 thuộc Sprint 3; benchmark 1/2 worker và chaos AWS chính thức thuộc Sprint 4, đều trong tuần 9.

## 2. Hiện trạng đầu tuần và việc chuyển tiếp

Đối chiếu [[docs/docs-worker/work/Week1|Week1]] mục 6 với mã nguồn hiện có. Bằng chứng tuần 1 là **kết quả đã ghi trong nhật ký**, không phải kết quả chạy lại khi lập tài liệu này.

| Hạng mục | Điểm xuất phát | Việc cần làm trong tuần 2 |
|---|---|---|
| POC và pipeline Go | Week1 ghi nhận đã nhận message tự tạo trên LocalStack, chạy ffprobe/FFmpeg, upload output, ghi `COMPLETED`, ACK | Giữ cấu hình đã kiểm chứng; chạy lại bằng image và luồng upload/confirm thật |
| Validation, assets, Content-Type | Đã có logic và test nền cho ffprobe, transcode, storage, parser | Kiểm thử hồi quy đủ nhánh; rà giới hạn dung lượng giữa config, code và contract |
| Idempotency | Đã bỏ qua video `COMPLETED`; các thao tác ghi có điều kiện | Kiểm tra hai worker xử lý đồng thời; điều kiện `status <> COMPLETED` **không phải khóa độc quyền** |
| Heartbeat | Consumer hiện mới nhận/xóa message; config đã có thông số heartbeat/visibility | Nối `ChangeMessageVisibility`, quản lý vòng đời heartbeat và test lỗi gia hạn |
| Shutdown | Đã bắt SIGTERM/SIGINT và hủy context | Chưa có thao tác trả message với visibility 30s; phải kiểm chứng khi đang chạy FFmpeg/upload, không chỉ khi idle |
| Local full stack | Week1 còn mục bàn giao build/run/env/IAM chưa đánh dấu gửi | Gửi ngay đầu tuần; cùng Lưu ghép Dockerfile/compose và cùng Phương chạy upload/confirm |
| E2E/chaos | Chưa có bằng chứng runner/assertions E2E hoàn chỉnh; script chaos còn placeholder | Triển khai kiểm thử thật; exit 0 của placeholder không phải nghiệm thu |
| AWS | Chưa có bằng chứng M2 trong Week1 | Phụ thuộc C1/C2/C3: tài nguyên, IAM, image, compute; Vũ hỗ trợ tích hợp và xác minh job |
| Contract | Week1 ghi đã review nhưng các file trong `docs/contracts/` vẫn mang nhãn `DRAFT`; schema bảng chưa có trường lease | Đối chiếu quyết định với Phương, đặc biệt codec, ownership và `attemptCount`; không tự coi lease đã được triển khai |

**Khối lượng tham chiếu:** Week1 mục 6.3 đề xuất còn **A4 ~1,0 pd + phần Vũ của A5 ~1,5 pd**. Đây là ước lượng trước khi kiểm chứng tích hợp/concurrency, không phải cam kết ba ngày sẽ đủ. Rà lại ngay ngày 1 nếu phải bổ sung ownership hoặc sửa lỗi tích hợp. A8/R1 và frontend A6 vẫn tính trong tổng tải của Vũ, không có quỹ ngày công riêng cho mỗi kế hoạch.

## 3. Công việc chi tiết theo ngày

“Ngày 1–3” dưới đây là **ba chặng làm việc của tuần 2**, không đánh lại ngày 1–3 của Sprint 1 và không ấn định ngày lịch. Có thể làm heartbeat/unit test trong lúc chờ Docker/backend; không được bỏ gate E2E local hoặc AWS. Gửi yêu cầu hỗ trợ Cloud từ ngày 1, không chờ đến ngày 3.

### Ngày 1 — Chốt đầu vào và khép kín pipeline local (A4)

#### 3.1. Bàn giao và rà điều kiện tích hợp

- [ ] Gửi nội dung bàn giao còn lại của Week1 mục 6.1 cho **Lưu/Nguyên**; xác nhận người nhận, thời điểm nhận, thiếu gì để build/deploy.
- [ ] Cùng **Lưu** kiểm tra `make infra`/`make smoke` trên máy đang dùng, Dockerfile có `ffmpeg`/`ffprobe`, thư mục tạm ghi được và entrypoint chuyển tín hiệu tới tiến trình Go. Ghi phiên bản image LocalStack/FFmpeg thực tế; xử lý lại lỗi mount Docker hoặc license nếu còn, không mặc định workaround tuần 1 đã sửa môi trường chung.
- [ ] Rà env trong compose và khi chạy trực tiếp: bốn tài nguyên bắt buộc là `TVC_INPUT_BUCKET`, `TVC_OUTPUT_BUCKET`, `TVC_JOBS_QUEUE_URL`, `TVC_DDB_VIDEOS`; ghi rõ `AWS_REGION`. Endpoint/queue URL phải truy cập được **từ trong container**, không sao chép mù URL `localhost` dành cho tiến trình trên host.
- [ ] Cùng **Phương** đối chiếu message `schemaVersion=1`, key input, bốn trường assets, danh sách codec và ý nghĩa `attemptCount`. Ghi quyết định ownership để ngày 2 thực hiện; thay đổi contract phải được review, không tự thêm field/status vào code trước.

#### 3.2. E2E và hồi quy pipeline

- [ ] Cùng Phương/Lưu chạy bằng request thật: đăng nhập/tạo project → yêu cầu upload → PUT file hợp lệ → confirm → Worker xử lý → API trả `COMPLETED`. Dùng `worker/testdata/sample-10s-720p.mp4` trước; không cần đợi giao diện frontend hoàn chỉnh.
- [ ] Kiểm tra thứ tự hoàn tất: tải input → ffprobe → FFmpeg → upload **đủ mọi output** → cập nhật `assets` và `COMPLETED` → ACK → cleanup. Không ACK trước khi ghi trạng thái thành công.
- [ ] Đối chiếu output với [[docs/contracts/s3-key-convention|S3 key convention]]:
  - `{projectId}/{videoId}/hls/master.m3u8`, `hls/360p/index.m3u8`, `hls/720p/index.m3u8`, segments và `thumb/default.jpg`.
  - `.m3u8`: `application/vnd.apple.mpegurl`; `.ts`: `video/mp2t`; `.jpg`: `image/jpeg`.
  - `assets` đủ `hls`, `hls360`, `hls720`, `thumbnail`; xác nhận cách lưu key với Phương để API tạo URL đúng.
- [ ] Phát master qua `http://localhost:8000/media/{projectId}/{videoId}/hls/master.m3u8`; thử hai rendition, kiểm tra segment và thumbnail tải được. Dùng ffprobe kiểm tra số stream, codec, kích thước và audio của **output do code Go sinh ra**, không chỉ dựa vào POC hay test chuỗi arguments; rà `-map`/encoding options theo từng output nếu có lệch. File tồn tại trong S3 chưa đủ chứng minh media/routing đúng.
- [ ] Chạy lại video không audio, nguồn 360p không upscale, file giả, video 301s, video 1440p và codec ngoài danh sách. Validation fail phải dừng trước FFmpeg, ghi đúng `FAILED_VALIDATION`/`errorCode`, rồi mới ACK.
- [ ] Rà `MAX_UPLOAD_BYTES=209715200`: API khai báo/confirm phải chặn quá giới hạn. Architecture còn mô tả kiểm tra dung lượng tại worker; không coi việc đọc env hay log `bytesIn` là đã enforce. Nếu bổ sung kiểm tra phòng vệ tại worker, chốt mã lỗi với Phương trước; không tự sáng tạo mã lỗi contract trong kế hoạch này.
- [x] Rà đường lỗi ghi DynamoDB và `DeleteMessage`: nếu chưa lưu được kết quả thì giữ message để retry; nếu đã `COMPLETED` nhưng ACK lỗi, lần nhận lại phải bỏ qua transcode và thử ACK lại. Bổ sung assertions cho các nhánh đang bỏ qua lỗi xóa message; khi retry thành công kiểm tra không còn `errorCode` cũ gây hiểu nhầm trên API.
- [x] Kiểm chứng thư mục job được dọn sau thành công/lỗi có kiểm soát. Với SIGKILL, `defer` không chạy: kiểm tra cơ chế dọn thư mục mồ côi khi khởi động, chỉ trong vùng tạm do worker đó quản lý.
- [x] Rà an toàn đường dẫn trước download/cleanup: `jobId` không được làm đường dẫn thoát khỏi `TVC_TMP_DIR`; không xóa thư mục dùng chung của worker khác hoặc gốc cấu hình sai. Kiểm thử ID có `..`/dấu phân cách, duplicate cùng thư mục và config temp không an toàn; thống nhất validation tương ứng với Phương, không âm thầm thay contract.

**Đầu ra ngày 1:** trace E2E local có `jobId`/`videoId`, trạng thái/assets, key/Content-Type, playback và lệnh tái hiện; danh sách blocker kèm người xử lý. Nếu chỉ gửi trực tiếp message thì ghi **“worker integration”**, chưa đánh dấu A4 E2E hoàn thành.

### Ngày 2 — Heartbeat, message trùng và SIGTERM (A5)

#### 3.3. Heartbeat giữ message khi job đang chạy

- [x] Bổ sung thao tác gia hạn trong `worker/internal/consumer/`; nối vòng đời heartbeat với `job.Run`, config và supervisor. Giữ long polling **20s**, **một message/một worker**.
- [x] Dùng `TVC_HEARTBEAT_INTERVAL=120s`, mỗi lần đặt `TVC_VISIBILITY_TIMEOUT=300s` bằng receipt handle của lần nhận hiện tại. Đây là **đặt lại timeout tính từ lần gia hạn**, không cộng 300s vào thời điểm hết hạn cũ.
- [x] Validate cấu hình: interval/timeout dương và interval nhỏ hơn visibility, có khoảng đệm cho network/retry. Đối chiếu visibility của queue/lần nhận ban đầu; chỉ đặt 300s lúc heartbeat không sửa được một timeout ban đầu quá ngắn.
- [x] Bao phủ toàn bộ khoảng giữ job: download, ffprobe, transcode, upload, ghi kết quả và ACK; không để xử lý kéo dài vượt visibility trước khi heartbeat bắt đầu.
- [x] Mọi nhánh return phải dừng/join heartbeat và đóng ticker; không rò goroutine hoặc còn gia hạn sau ACK/shutdown. Khi lỗi hệ thống, ngừng gia hạn để SQS có thể giao lại.
- [x] Khi gia hạn lỗi: log có job/attempt và lỗi; retry có giới hạn trong khoảng còn an toàn. Nếu không còn giữ được visibility/ownership, dừng công việc đang chạy, không tiếp tục upload/ghi kết quả như thể vẫn sở hữu job; kiểm thử receipt handle hết hiệu lực và network failure.
- [x] Unit test bằng clock/ticker và SQS giả; không bắt unit test chờ 120s. Integration test dùng queue riêng với timeout ngắn hoặc bước xử lý chậm có kiểm soát để **thời gian xử lý vượt visibility ban đầu**, rồi chứng minh có gia hạn. Không dùng video >300s để ép transcode dài vì video đó phải bị validation chặn.

> Bằng chứng 2026-10-30: unit test `worker/internal/heartbeat` + chaos SIGTERM dùng visibility 10s, heartbeat 3s, transcode kéo dài có kiểm soát 20s; sau 12s message vẫn `notVisible=1` (xem mục 6).

#### 3.4. Idempotency và quyền sở hữu job

- [x] Gửi lại message của video đã `COMPLETED`: không chạy FFmpeg, không thay assets/trạng thái, ACK message trùng. Ghi rõ kỳ vọng `attemptCount` khi bỏ qua; hiện code tuần 1 không tăng ở nhánh này, còn số lần nhận thật lấy từ `ApproximateReceiveCount`.
- [ ] Cùng Phương chốt cách tránh hai worker cùng giữ job đang `PROCESSING`. **Heartbeat giảm giao lại do hết timeout nhưng không bảo đảm exactly-once; `status <> COMPLETED` và tăng `attemptCount` đơn thuần không cấp ownership độc quyền.**
- [ ] Nếu dùng lease/attempt token: review contract trước, xác định điều kiện claim, gia hạn/hết hạn, nhận lại sau crash và kiểm tra owner khi ghi kết quả. Worker cũ mất quyền không được tiếp tục công bố output/kết quả; chỉ khóa trạng thái DynamoDB chưa đủ nếu hai worker vẫn ghi cùng key S3.
- [ ] Test hai worker cùng nhận **hai message có cùng job/video** và ca giao lại khi worker cũ còn chạy; kiểm tra cả trạng thái lẫn playlist/segments, không chỉ đếm lần ghi `COMPLETED`.
- [x] Nếu chưa triển khai ownership, ghi rõ giới hạn duplicate processing và quyết định phạm vi của nhóm; không đánh dấu tiêu chí “không xử lý đôi” đạt chỉ từ test gửi lại sau `COMPLETED`. Không âm thầm mở thêm hệ thống khóa ngoài thiết kế đã review.

#### 3.5. SIGTERM khi đang giữ job

- [x] Hoàn thiện phối hợp `shutdown`, supervisor, consumer và runner: nhận SIGTERM/SIGINT → ngừng poll/nhận job mới → hủy công việc đang chạy → đợi FFmpeg/upload và heartbeat dừng → rút visibility xuống **30s** → cleanup → thoát gọn.
- [x] Gọi trả message bằng **context riêng có timeout**, không dùng lại context job đã bị cancel. Chặn heartbeat chạy đua và gia hạn lại thành 300s sau khi vừa trả message.
- [x] Không ACK job bị ngắt; không coi hủy do shutdown là validation fail. Nếu đã lưu `COMPLETED` nhưng chưa ACK kịp, để nhánh idempotency xử lý lần nhận lại, không ghi lùi sang `FAILED_*`.
- [ ] Nếu có lease, giải phóng/hết hạn lease phải phối hợp với việc trả message; nếu không, worker mới có thể nhận message nhưng vẫn bị lease cũ chặn. Nếu gọi trả message thất bại, log lỗi và ghi rõ phải chờ visibility còn lại, không tuyên bố đã trả sau 30s.
- [ ] Kiểm thử SIGTERM ở idle, đang FFmpeg và đang upload; không còn process con hoặc job mới được nhận sau tín hiệu. Xác nhận worker khác nhận lại và hoàn tất; 30s là visibility mục tiêu, không phải SLA nhận lại chính xác tới giây.
- [ ] Cùng Lưu/Nguyên xác nhận entrypoint và thời gian chờ dừng container đủ cho cleanup/trả message. **Không mặc định cảnh báo Spot tự biến thành SIGTERM tới Go**: Cloud phải xác minh cơ chế chuyển cảnh báo/dừng máy tới container; SIGKILL vẫn cần phục hồi bằng timeout.

> Đã kiểm chứng 2026-10-30: SIGTERM khi idle và khi đang FFmpeg (chaos); chưa có ca đang upload và chưa kiểm tra entrypoint container/Spot (Lưu/Nguyên). Nhánh release lỗi được log `message_release_failed`, không tuyên bố trả thành công.

**Đầu ra ngày 2:** test cho heartbeat/ownership/shutdown, trace gia hạn và nhận lại, kết quả race test; các quyết định contract còn mở phải có người xử lý và tác động M2.

### Ngày 3 — Chaos/DLQ, AWS và bàn giao sang tuần 9 (A5/M2)

#### 3.6. Hoàn thiện kiểm thử lỗi trên local

- [x] Hoàn thiện `local/scripts/chaos-kill-worker.sh` cùng Lưu: tạo job riêng, đợi đúng `jobId` vào `transcode_started`/`PROCESSING`, kill **đúng worker đang giữ job**, khởi động worker nhận lại, đợi `COMPLETED`, assert assets và playback. Không chỉ sleep một khoảng cố định rồi kill ngẫu nhiên.
- [x] Tách **SIGTERM** với **SIGKILL**: SIGTERM kiểm tra đường trả 30s; SIGKILL không cleanup/heartbeat được, phải chờ visibility tính từ lần gia hạn cuối. Ghi thời điểm kill, lần nhận tiếp theo và hoàn tất.
- [x] Ca DLQ dùng **message đúng schema + item video hợp lệ**, cố ý tạo lỗi retryable, ví dụ key input chưa có → `FAILED_STORAGE`/`S3_GET_FAILED`. Không dùng JSON sai hoặc video validation fail vì hai nhánh đó phải ACK.
- [x] Cùng Lưu kiểm tra queue gắn DLQ, `maxReceiveCount=3`. Vẫn xử lý lần nhận thứ 3; nếu tiếp tục không ACK thì để **SQS tự chuyển** theo redrive policy. Worker không tự `SendMessage` vào DLQ, không dùng `attemptCount` trong DynamoDB làm bộ đếm redrive.
- [x] Ghi `ApproximateReceiveCount`, log các lần lỗi và message thực tế ở DLQ. Dùng deadline/polling có giới hạn; không coi thời điểm log lỗi lần 3 là bằng chứng message đã chuyển ngay sang DLQ.
- [x] Harness trả exit khác 0 khi timeout/assertion sai; lưu chẩn đoán. Dùng tài nguyên test cô lập hoặc ID riêng, không purge queue dùng chung. Tránh thao tác `ReceiveMessage` để “quan sát” queue chính vì chính thao tác đó làm tăng receive count/ẩn message.

> Đã chạy pass 2026-10-30 với worker binary trên host + LocalStack 3.8.1; mỗi ca dùng queue/DLQ riêng. Phần ghép container/entrypoint với Lưu và chạy lại bằng `docker compose` vẫn còn (Docker Desktop trên máy hiện tại không share thư mục `Documents`, xem mục 7).

#### 3.7. Cùng Nguyên nghiệm thu pipeline AWS

- [ ] Chốt image tag/digest, phiên bản FFmpeg và target architecture đã test local với Lưu/Nguyên; dùng **cùng artifact**, không đổi cấu hình transcode giữa local và AWS.
- [ ] Nguyên cung cấp ECR, EC2/ASG worker, IAM instance profile và tài nguyên C1. Vũ đối chiếu bucket/queue/bảng/region, disk tạm và log; `AWS_ENDPOINT_URL` để trống, không đưa credential `test/test` của LocalStack lên AWS.
- [ ] Kiểm tra quyền cần cho runtime: SQS receive/delete/**change visibility**, S3 đọc input/ghi output, DynamoDB đọc/cập nhật bảng videos. Không mở rộng sang `sqs:SendMessage`, ghi input hoặc bảng users/projects/apikeys để chữa lỗi tích hợp.
- [ ] Chạy upload → confirm → queue → worker → `COMPLETED` trên AWS bằng video thật. Lưu môi trường, image digest, `jobId`/`videoId`, trạng thái/assets, object metadata và log; kiểm tra HLS/thumbnail bằng tài khoản kiểm thử có quyền đọc output.
- [ ] Nếu chưa có CloudFront, theo [[docs/docs-cloud/2. Timelines|timeline Cloud]]: API qua hostname Tunnel, đọc/tải output bằng quyền AWS phù hợp và kiểm tra playlist/segments. Có thể tải bộ HLS về phát để xác minh nội dung; **không mở bucket public** và không coi việc này là hoàn thành giao diện HTTPS M3. Presigned GET cho riêng master không tự cấp quyền cho các segment tương đối.
- [ ] Chạy ít nhất một retry cơ bản có kiểm soát trên AWS: gây lỗi đọc input trong tài nguyên test, khôi phục input trước khi hết lượt, xác nhận nhận lại và `COMPLETED`. Chaos AWS chính thức/đo recovery vẫn thuộc A8 tuần 9.
- [ ] Khi IAM/env/runtime lỗi, ghi lỗi gốc và người xử lý; không đánh dấu M2 chỉ vì container chạy hoặc vì đã gửi message trực tiếp thành công. M2 cần luồng upload/confirm thật trên AWS.

#### 3.8. Chuẩn bị benchmark và báo cáo, không đợi tuần 9

- [ ] Cùng Lưu chốt dataset, ID/hash file, độ dài/độ phân giải, cấu hình FFmpeg và image dùng đo; tái sử dụng video tuần 1. File lớn ở `local/samples/`, không commit thêm video lớn làm bằng chứng.
- [ ] Bàn giao log JSON có `jobId`, `videoId`, `projectId`, attempt, kết quả/errorCode, `durationMs`, `queueWaitMs`, bytes in/out; thống nhất trường thời gian còn thiếu để harness lưu submit/start/finish và thời gian xử lý FFmpeg.
- [ ] Phân biệt thời gian chờ từ `requestedAt` tới lần nhận với thời gian chờ riêng của lần retry. `requestedAt` là tùy chọn trong schema: thiếu thì ghi nhận thiếu dữ liệu, không diễn giải số 0 mặc định như số đo thật.
- [ ] Cùng Nguyên đối chiếu đầu vào cần cho C5; tuần này chuẩn bị log/điểm phát metric, không tuyên bố metric CloudWatch đã có chỉ vì log đã có.
- [ ] Viết/cập nhật phần Worker trong báo cáo chung: pipeline thực tế, giới hạn input, retry/shutdown, phương pháp và hạn chế kiểm thử; đính kèm bằng chứng đã có, chưa điền kết quả benchmark dự kiến thành số liệu thật.

**Đầu ra ngày 3:** bộ E2E/chaos local có assertions, bằng chứng M2 hoặc danh sách blocker rõ ràng, artifact/config đã bàn giao và dataset/harness sẵn sàng cho tuần 9.

## 4. Bàn giao cuối tuần (đầu ra cho các bên)

| Bên nhận | Vũ bàn giao | Thời điểm / bên phối hợp |
|---|---|---|
| Phương | Kết quả đối chiếu schema/state/assets, quyết định ownership/attempt, errorCode và trace upload/confirm/retry | Chốt đầu tuần; cùng xử lý lỗi API ↔ Worker trong A3/A5 |
| Lưu | Build/run/env đã xác minh, yêu cầu entrypoint/stop grace, video mẫu, test assertions, script chaos và trường log cho harness | Bàn giao nền ngay ngày 1; kết quả local + dataset trước hết tuần 8 |
| Nguyên | Image digest đã qua local, runtime env, quyền heartbeat, hành vi SIGTERM/Spot, trace AWS và lỗi IAM/runtime | Yêu cầu môi trường từ ngày 1; phối hợp C2/C3 trước M2 |
| Cả nhóm | Kết luận A4/A5/M2 đạt/chưa đạt, bằng chứng theo job, rủi ro M3/M4 và nội dung báo cáo Worker | Cuối tuần 8, không chờ tuần 9 mới báo blocker |

**Ranh giới trách nhiệm giữ nguyên:** Vũ sửa code Worker và phần script chaos được giao; Lưu sở hữu Dockerfile/compose, local/data và E2E/benchmark harness; Nguyên sở hữu IAM/compute/deploy; Phương sở hữu API/confirm và cùng review contract. Các mục sửa ngoài Worker phải phối hợp với chủ sở hữu, không tự nhận toàn bộ Cloud/frontend vào tuần này.

## 5. Tiêu chí hoàn thành tuần 2 (đối chiếu A4/A5/M2)

### 5.1. Checklist nghiệm thu

- [ ] A4: E2E local qua API thật, đủ HLS 360p/720p + thumbnail, `COMPLETED`, ACK và playback `/media/*`; không chỉ message tự tạo.
- [ ] Validation đúng contract, không chạy FFmpeg khi input bị từ chối; xử lý lỗi trạng thái/ACK không làm mất job chưa ghi được kết quả.
- [x] Heartbeat thực sự gia hạn qua một khoảng xử lý dài hơn visibility ban đầu; dừng đúng khi job kết thúc/hủy, không rò goroutine.
- [ ] Duplicate sau `COMPLETED` không xử lý lại; ca hai worker cùng job kiểm tra cả state/output. Ownership đã được thống nhất và kiểm thử; nếu còn chấp nhận xử lý đôi thì ghi giới hạn, chưa đánh dấu tiêu chí chống xử lý đôi đạt.
- [x] SIGTERM đang giữ job trả message sớm khi SQS còn truy cập được, dừng process con, không nhận thêm job và không ACK job dở; SIGKILL nhận lại sau timeout.
- [x] Lỗi retryable giữ message, tăng receive count khi nhận lại và chuyển vào DLQ theo policy 3 lần nhận; validation/schema sai không dùng để chứng minh DLQ.
- [ ] `make e2e`/`make chaos` đã có runner/assertions thật; kịch bản lỗi/timeout làm lệnh thất bại thay vì chỉ in hướng dẫn rồi exit 0.
- [ ] M2: cùng image chạy pipeline upload/confirm thật trên AWS, có assets và bằng chứng retry cơ bản; không bắt buộc CloudFront trước M3.
- [ ] Handoff cho Lưu/Nguyên/Phương, dataset/log phục vụ tuần 9 và phần báo cáo Worker đã có bằng chứng nhận/cập nhật.

> Ghi chú 2026-10-30: `make chaos` đã có runner/assertions thật và pass; dòng checklist trên vẫn để mở vì `make e2e` còn placeholder và chưa chạy qua API/container. Hai worker cùng job và ownership vẫn chưa có quyết định của Phương.

### 5.2. Ma trận kiểm thử tối thiểu

| Ca | Cách tạo | Kết quả bắt buộc / bằng chứng |
|---|---|---|
| Happy path | Upload `sample-10s-720p.mp4` qua API | `COMPLETED`, assets/Content-Type đúng, phát được, ACK, dọn temp |
| Không audio / nguồn nhỏ | `sample-no-audio.mp4`, `sample-360p.mp4` | Transcode thành công, không upscale, master phản ánh kích thước thực |
| Validation | File giả, 301s, 1440p, codec không cho phép | `FAILED_VALIDATION` + đúng errorCode; không có `transcode_started`; ACK sau khi lưu kết quả |
| Upload quá lớn | File vượt 209715200 byte ở luồng upload/confirm | API từ chối theo contract; không tạo job xử lý hợp lệ cho input vượt giới hạn |
| Message sai schema | JSON hỏng/thiếu trường/version không hỗ trợ | Log `INVALID_MESSAGE`/`UNSUPPORTED_SCHEMA`, ACK; không transcode, không dùng làm ca DLQ |
| Heartbeat | Xử lý lâu hơn visibility ban đầu trong cấu hình test | Có gọi gia hạn thành công; test dừng/error path; không suy ra exactly-once từ một lần chạy |
| Duplicate đã xong | Gửi lại message video `COMPLETED` | `skipped_completed`, assets/trạng thái giữ nguyên, không FFmpeg; ACK được kiểm tra |
| Hai worker cùng job | Hai message trùng body hoặc giao lại có kiểm soát | Ownership theo quyết định đã review, không ghi lùi `COMPLETED`, output cuối nhất quán/phát được |
| Lỗi DynamoDB / ACK | Mock/inject lỗi ghi trạng thái hoặc xóa message | Chưa ghi được kết quả thì không ACK; đã `COMPLETED` thì lần nhận lại chỉ skip/ACK |
| SIGTERM | Dừng worker lúc FFmpeg/upload và lúc idle | Dừng nhận, dừng heartbeat/process con; job dở nhận lại sau visibility rút ngắn; log release thành công/thất bại |
| SIGKILL | Kill worker đang giữ đúng job | Không kỳ vọng defer chạy; nhận lại sau visibility, retry → `COMPLETED`, dọn temp mồ côi an toàn |
| DLQ | Message hợp lệ trỏ input không tồn tại | `FAILED_STORAGE`, log receive count, message thực tế ở DLQ, không worker tự gửi DLQ |
| AWS | Luồng API thật + một lỗi retryable được khôi phục | Cùng digest, input/output thật, `COMPLETED`, trace retry; không credential/secret trong log |

**Trạng thái phủ test ngày 2026-10-30 (worker integration, không phải A4):**

- Đã phủ bằng unit/integration worker: heartbeat gia hạn (fake ticker + chaos vượt visibility), duplicate sau `COMPLETED`, lỗi DynamoDB/ACK, SIGTERM khi FFmpeg, SIGKILL + dọn temp mồ côi, DLQ sau 3 lần nhận, path safety `jobId`, temp root an toàn.
- Chưa phủ: happy path qua API/confirm, video không audio/nguồn nhỏ qua E2E, validation file thật 301s/1440p/codec lạ qua API, upload quá lớn, hai worker cùng job, AWS. Các dòng này giữ trạng thái chưa đạt trong mục 5.1.

**Lệnh kiểm tra dự kiến khi triển khai** — không phải kết quả đã chạy trong tài liệu này:

```bash
# Chạy từ thư mục gốc repo
make test
# Chạy trong thư mục worker/ để kiểm tra riêng code Go, không phụ thuộc runner toàn repo
go -C worker test ./...
go -C worker test -race ./...
# Chỉ nghiệm thu hai lệnh dưới sau khi runner/assertions đã triển khai
make e2e
make chaos
```

Test ffprobe tự skip khi thiếu binary không thay thế integration test. Lưu số test pass/skip, phiên bản tool, môi trường và kết quả assertions cùng log; không viết “xanh toàn bộ” nếu chỉ chạy được một phần.

## 6. Tiến độ thực tế (cập nhật sau mỗi buổi làm)

Đã ghi nhận buổi làm ngày **2026-10-30**, chỉ các mục có bằng chứng chạy thật. A4/E2E API, ownership hai worker và M2 vẫn chưa đạt.

| Ngày / buổi thực tế | Đã làm | Bằng chứng (lệnh, log, job/video, artifact) | Vấn đề / cần hỗ trợ |
|---|---|---|---|
| 2026-10-30 | Hoàn thiện reliability phía Worker: `ChangeMessageVisibility` + heartbeat controller; job runner dừng/join heartbeat, nhánh ACK an toàn; SIGTERM trả message visibility 30s bằng context riêng; state xóa `errorCode`/`completedAt` cũ khi retry/hoàn tất; temp root có marker + validate `jobId`/`TVC_TMP_DIR`; config validate heartbeat/visibility | `go -C worker test ./...` pass 10 package có test (logging/shutdown không có test); `go -C worker test -race ./...` pass; `go vet ./...` sạch; `gofmt -l .` sạch; `TestProbeRealSamples` PASS (ffprobe 9.0.2, không skip) | Contract vẫn `DRAFT`; ownership/lease chưa chốt với Phương; mã chẩn đoán nội bộ (`LOCAL_FS_FAILED`, `MASTER_WRITE_FAILED`...) chờ review contract |
| 2026-10-30 | Viết và chạy `local/scripts/chaos-kill-worker.sh`: ca SIGTERM (transcode 20s có kiểm soát, visibility 10s/heartbeat 3s), SIGKILL, DLQ; mỗi ca queue/DLQ riêng; assert DDB, assets, Content-Type, playlist/segment, temp; exit != 0 khi assertion sai | Chạy PASS với LocalStack 3.8.1 + worker binary trên host; bằng chứng `local/chaos-logs/20261030T120420Z/summary.txt` (log worker, timings, message). SIGTERM: sau 12s message vẫn `notVisible=1`, release rồi worker khác nhận lại sau 31s, job `COMPLETED`. SIGKILL: exit 137, orphan temp được dọn khi restart, nhận lại attempt 2, `COMPLETED`. DLQ: worker nhận 3 lần, giữ message, SQS tự redrive (DLQ `ApproximateReceiveCount=4` do vượt ngưỡng 3), `FAILED_STORAGE`/`S3_GET_FAILED` | Chưa chạy qua container/entrypoint do Docker Desktop trên máy không share thư mục `Documents`; đã dùng mount init tạm từ `/tmp` để chạy, không sửa compose. Cần Lưu xử lý chia sẻ thư mục/entrypoint và chạy lại `make chaos` trong container |
| 2026-10-30 | Cập nhật hợp đồng worker: jobId không an toàn → `INVALID_MESSAGE` + ACK; xóa `errorCode`/`completedAt` cũ khi retry, xóa `errorCode` khi `COMPLETED`; quy tắc ACK lỗi/giữ message; SIGTERM release lỗi; giới hạn ownership hiện tại; env/temp an toàn | `docs/docs-worker/5. Contact docs.md` (mục 5.1, 5.2, 5.4, 5.5, 5.7) | A4 chặn bởi backend/confirm còn trống; M2 chặn bởi hạ tầng/IAM/credential AWS; `MAX_UPLOAD_BYTES` vẫn do API enforce |

### 6.1. Gói bằng chứng cần lưu

- Mỗi lượt: ngày giờ, local/AWS, phiên bản source và image digest, FFmpeg/LocalStack nếu liên quan, bộ input, giới hạn và heartbeat/visibility/redrive đang dùng.
- E2E: `jobId`/`videoId`, trace API → queue → worker, item DynamoDB, danh sách object/Content-Type, bằng chứng phát master/segments/thumbnail.
- Reliability: receive count, thời điểm gia hạn/lỗi/kill/nhận lại/hoàn tất, loại tín hiệu, kết quả ACK/release, message DLQ và kết quả assertions.
- Không lưu access key, token, presigned URL còn hiệu lực hoặc receipt handle vào bản bằng chứng chia sẻ. Chỉ xóa tài nguyên test sau khi đã lưu bằng chứng và xác nhận đúng tài nguyên của ca đó.

### 6.2. Ước lượng lại và kết luận cuối tuần

| Hạng mục | Mốc tham chiếu từ Week1 | Cập nhật sau khi làm |
|---|---|---|
| A4 còn lại | ~1,0 pd: ghép compose/E2E và sửa lỗi tích hợp | **Chưa giảm**: backend/confirm còn trống nên chưa có upload → SQS → API `COMPLETED`; chaos mới là worker integration, không thay A4 |
| A5 phần Vũ | ~1,5 pd: heartbeat, shutdown, chaos/DLQ; phải rà thêm ownership | Code/unit/chaos đã xong (2026-10-30), nhưng ước lượng ban đầu **không gồm** ownership/lease và tích hợp container; nếu nhóm chọn lease thì tăng thêm và phải review contract trước |
| Phối hợp C2/C3, chuẩn bị A8/R1 và tải frontend | Đã nằm trong tổng tải timeline chung; không tự cộng quỹ mới | Chưa có môi trường AWS để chạy M2/retry; thời gian phối hợp với Lưu (Dockerfile/entrypoint) và Phương (ownership/errorCode) cần tính vào tuần 8 |

Kết luận cuối tuần: **A4 chưa đạt** (thiếu E2E API/confirm), **A5 chưa đạt đầy đủ** (code/unit/chaos local đạt; ownership hai worker và SIGTERM khi upload/entrypoint chưa kiểm chứng), **M2 chưa đạt** (chưa có hạ tầng/credential AWS).

Cuối tuần ghi riêng **A4**, **A5** và **M2** đạt/chưa đạt, dẫn bằng chứng tương ứng. Local đạt nhưng AWS chưa đạt vẫn là **M2 chưa đạt**. Công việc chưa xong phải có người phụ trách và thứ tự ưu tiên tiếp theo; không đánh dấu hoàn thành chỉ vì tới mốc.

## 7. Phụ thuộc và rủi ro tuần 2

| Phụ thuộc / rủi ro | Tác động | Cách xử lý |
|---|---|---|
| Dockerfile/compose hoặc môi trường local chưa sẵn sàng (Lưu) | Chặn A4 E2E | Gửi bàn giao ngay; tiếp tục unit test/worker integration, nhưng giữ E2E thật là gate |
| Docker Desktop trên máy dev không chia sẻ thư mục `Documents` | `make infra`/`make up` lỗi mount `local/localstack/init`; không chạy được container/entrypoint và `make chaos` theo Makefile | Đã chạy tạm LocalStack với init mount từ `/tmp` (2026-10-30) và ghi rõ trong bằng chứng; không sửa compose. Lưu xử lý file sharing hoặc đưa init vào image/compose, sau đó chạy lại chaos trong container |
| API confirm/chốt message trễ (Phương) | Worker tự chạy được nhưng chưa có upload → job | Test riêng bằng message chuẩn, báo blocker; không dùng kết quả riêng thay M2 |
| DRAFT/ownership chưa phản ánh quyết định nhóm | Hai worker cùng ghi state/output; ước lượng A5 có thể tăng | Review với Phương từ ngày 1; ghi giới hạn hiện tại, không xem conditional write là lease |
| Heartbeat/shutdown tranh chấp context hoặc ticker | Message bị giữ thêm 300s, trả job thất bại hoặc goroutine còn chạy | Test cancellation/race; context release riêng; xác nhận heartbeat đã dừng trước release |
| Video test ngắn nên FFmpeg xong trước lúc kill | Chaos “pass” nhưng chưa thử lỗi giữa job | Đồng bộ theo log đúng job và tạo thời gian xử lý đủ dài có kiểm soát; không dùng video vượt giới hạn |
| IAM/ECR/ASG/quota/Spot chưa sẵn sàng (Nguyên) | Chặn AWS M2 | Xin cấu hình từ đầu tuần, gửi lỗi theo action/resource; nếu thiếu Spot capacity, phối hợp dùng on-demand tạm và ghi rõ |
| Chỉ test SIGTERM local rồi suy ra chịu được Spot | Thiếu đường chuyển cảnh báo tới container | Nguyên xác minh cơ chế dừng; giữ timeout recovery cho crash; chaos AWS thực tế ở A8 |
| Harness placeholder hoặc chỉ xem queue count | Bằng chứng sai về E2E/DLQ | Assert theo job, state, assets và message DLQ; timeout phải fail |
| Vũ đồng thời làm frontend, ownership/tích hợp phát sinh | Quá tải trước M2 | Ưu tiên pipeline/reliability, xin hỗ trợ khung frontend; không mở tính năng P1, không đẩy benchmark sang tuần 10 |

Nếu M2 trễ: báo nhóm ngay, dồn vào upload → queue → worker → HLS → `COMPLETED`, sửa blocker và giữ retry cơ bản. Hoãn tối ưu nâng cao; vẫn phải chuẩn bị/đo benchmark và recovery trong tuần 9, không lấy tuần nộp làm tuần phát triển bù.

## 8. Liên kết nhanh

- [[docs/docs-worker/work/Week1|Week1]] — bằng chứng nền, bàn giao còn thiếu và ước lượng A4/A5.
- [[docs/docs-worker/1. Introduction|Giới thiệu Worker]] — phạm vi và tiêu chí worker chạy được.
- [[docs/docs-worker/2. Business|Nghiệp vụ Worker]] — validation, retry, duplicate, shutdown.
- [[docs/docs-worker/3. Architecture|Kiến trúc Worker]] — pipeline, FFmpeg, env, IAM và thư mục tạm.
- [[docs/docs-worker/4. Timelines|Timeline Worker]] — Sprint 2/M2 và giới hạn phạm vi tuần 9.
- [[docs/docs-worker/5. Contact docs|Hợp đồng giao tiếp Worker]] — bảng ACK/retry, SQS, state/assets và log.
- [[docs/introduction/1. Timelines|Timeline chung v4]] — epic, effort, chủ sở hữu và M0–M5.
- [[docs/docs-cloud/2. Timelines|Timeline Cloud]] — điều kiện M2 khi chưa có CloudFront, bàn giao C2/C3.
- [[docs/contracts/sqs-job-message.schema.json|Schema message SQS]] · [[docs/contracts/video-state-machine|State machine]] · [[docs/contracts/dynamodb-tables|DynamoDB]] · [[docs/contracts/s3-key-convention|S3 keys]] · [[docs/contracts/upload-limits|Giới hạn upload]].
- [AWS — SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html) và [dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html) — đối chiếu heartbeat, at-least-once và redrive; các giá trị 120s/300s/3 lần nhận là cấu hình của dự án.
