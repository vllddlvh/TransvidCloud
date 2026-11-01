# Feature Specification: F-009 — Quan sát hệ thống, Bảo mật Log & Bộ kiểm thử Tích hợp / Contract (Epic A5/A8/C5)

**Feature Branch**: `009-observability-integration-tests`  
**Created**: 2026-11-01T17:33:00+07:00  
**Status**: Draft  
**Input**: User description: "F-009: Quan sát hệ thống, Bảo mật Log & Bộ kiểm thử Tích hợp / Contract (Epic A5/A8/C5)"

---

## Clarifications

### Session 2026-11-01
- Q: Định dạng xuất log (Logging Format) của Backend nên được điều khiển như thế nào giữa môi trường phát triển cục bộ (local/test) và môi trường triển khai container/cloud? → A: Cấu hình linh hoạt qua biến môi trường `LOG_FORMAT` hoặc Spring profile `docker`/`prod`; mặc định sử dụng dạng text màu có cấu trúc trên console cho môi trường `local`/`test`, và tự động chuyển sang định dạng JSON một dòng (Single-line JSON) cho môi trường container/cloud.
- Q: Cơ chế che giấu (Masking) dữ liệu nhạy cảm trong log nên được triển khai theo giải pháp kỹ thuật nào để đảm bảo an toàn tối đa mà không gây nghẽn hiệu năng hệ thống? → A: Phòng thủ theo chiều sâu (Defense-in-Depth): Kết hợp Logback Masking Message Converter (tự động phát hiện và che giấu các khóa nhạy cảm `password`, `key`, `secret`, `signature` và query string của S3 URL trước khi xuất stream) đồng thời chủ động loại trừ các trường nhạy cảm trong `toString()` của các DTO / Request model.
- Q: Bộ kiểm thử tích hợp mới của F-009 nên được tổ chức cấu trúc như thế nào trong mã nguồn kiểm thử (`src/test/java`) để tối ưu hóa tính độc lập và thời gian chạy CI? → A: Tách thành 2 test classes kiểm thử tích hợp Testcontainers LocalStack chuyên biệt: `ObservabilityLoggingIT` (chuyên kiểm tra trích xuất header correlation, gán MDC, Log Masking và không rò rỉ secret) và `VideoFullLifecycleIT` (chuyên kiểm tra chuỗi nghiệp vụ khép kín: Auth -> Project -> API Key -> Upload Init -> S3 Upload -> Confirm -> SQS Message Schema -> Worker Update -> Query -> Multi-tenant Isolation).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Quan sát Hệ thống qua Structured Logging có Ngữ Cảnh và Truy Vết (Priority: P1)

Là một Kỹ sư Vận hành (SRE / DevOps) hoặc Lập trình viên, tôi muốn mọi yêu cầu (HTTP request) và tiến trình xử lý nền (như Outbox Relay) của Backend đều sinh ra log có cấu trúc chuẩn (JSON hoặc Structured Log) mang theo mã tương quan (`correlationId`), định danh người dùng (`userId`), dự án (`projectId`) và video (`videoId`), để tôi có thể dễ dàng truy vết trọn vẹn toàn bộ hành trình của một thao tác trên CloudWatch Logs Insights hoặc các công cụ phân tích tập trung mà không bị thất lạc ngữ cảnh.

**Why this priority**: Khả năng quan sát (Observability) là nền tảng cốt lõi của vận hành phân tán (Epic C5). Khi xảy ra sự cố hoặc nghẽn cổ chai trong quy trình tải lên/điều phối, việc lọc theo `correlationId` hoặc `videoId` giúp xác định nguyên nhân gốc rễ (root cause) trong vài giây thay vì phải tra cứu thủ công từng file log.

**Independent Test**: Có thể kiểm chứng độc lập bằng cách gửi một HTTP request tới bất kỳ endpoint nào (có kèm hoặc không kèm header `X-Correlation-ID`), kiểm tra bản ghi log sinh ra: xác nhận `correlationId` xuất hiện đồng nhất trong mọi dòng log của request đó và được trả về trong header phản hồi HTTP.

**Acceptance Scenarios**:

1. **Given** một request từ client không có header tương quan, **When** gửi tới Backend, **Then** hệ thống tự động sinh một `correlationId` mới (chuỗi UUID), gán vào MDC (Mapped Diagnostic Context), ghi vào mọi bản ghi log của request đó và trả về trong header HTTP phản hồi `X-Correlation-ID`.
2. **Given** một request từ client có kèm header `X-Correlation-ID: custom-trace-12345` (hoặc `X-Request-ID`), **When** gửi tới Backend, **Then** hệ thống tiếp nhận và tái sử dụng giá trị này xuyên suốt các bản ghi log và trả ngược lại cho client trong header response.
3. **Given** các thao tác nghiệp vụ liên quan đến dự án hoặc video cụ thể (như khởi tạo video, confirm, truy vấn), **When** xử lý trong Service/Repository, **Then** các trường ngữ cảnh `projectId` và `videoId` được tự động bổ sung vào MDC để xuất hiện trong các bản ghi log nghiệp vụ.
4. **Given** tiến trình chạy nền `OutboxRelayScheduler`, **When** quét và gửi lại message SQS, **Then** mỗi chu kỳ hoặc mỗi lượt xử lý message đều có một `jobId` / `outboxTraceId` rõ ràng trong log để phân biệt với các luồng HTTP thông thường.

---

### User Story 2 - Bảo Mật Log, Lọc Bỏ & Masking Dữ Liệu Nhạy Cảm (Priority: P2)

Là một Cán bộ An toàn Thông tin hoặc Quản trị viên Hệ thống, tôi muốn đảm bảo tuyệt đối không có bất kỳ dữ liệu nhạy cảm nào (mật khẩu người dùng, raw API Key, token JWT, khóa bí mật AWS hoặc đường dẫn Presigned URL đầy đủ kèm query parameter chữ ký) bị rò rỉ ra các luồng log (stdout, file log hoặc CloudWatch), để đảm bảo tuân thủ tiêu chuẩn an ninh và loại trừ nguy cơ đánh cắp quyền truy cập tạm thời vào tài nguyên lưu trữ S3.

**Why this priority**: Tuân thủ nguyên tắc bảo mật tối thượng (Principle III & VI của Constitution). Log thường được chia sẻ cho nhiều thành viên trong đội ngũ vận hành hoặc chuyển tiếp qua các hệ thống bên thứ ba; rò rỉ secret hoặc Presigned URL trong log là lỗ hổng an ninh nghiêm trọng.

**Independent Test**: Kiểm chứng bằng cách thực hiện các thao tác chứa dữ liệu nhạy cảm (đăng ký/đăng nhập với mật khẩu, tạo API Key, cấp Presigned PUT URL, hoặc cố ý gây lỗi ngoại lệ hệ thống); trích xuất toàn bộ log của các thao tác này và xác nhận các chuỗi nhạy cảm đều được che giấu (`[PROTECTED]`, `***`, hoặc chỉ giữ lại prefix/suffix an toàn) và Presigned URL chỉ hiển thị bucket + path key (không chứa `X-Amz-Signature`).

**Acceptance Scenarios**:

1. **Given** một request đăng ký hoặc đăng nhập (`POST /api/auth/register`, `POST /api/auth/login`), **When** hệ thống log nội dung request hoặc thông tin xử lý, **Then** trường `password` tuyệt đối không xuất hiện dạng bản rõ trong bất kỳ dòng log nào.
2. **Given** thao tác sinh hoặc xác thực API Key, **When** log quá trình kiểm tra hoặc tạo key, **Then** hệ thống chỉ log định danh `keyHash` (SHA-256) hoặc tiền tố nhận diện an toàn, tuyệt đối không log chuỗi `rawApiKey` đầy đủ.
3. **Given** thao tác cấp S3 Presigned URL (`POST /api/projects/{projectId}/videos`), **When** ghi log sự kiện cấp link tải lên, **Then** hệ thống chỉ log `bucket`, `objectKey` và thời gian hết hạn (`expiresInMinutes`), tuyệt đối không ghi lại toàn bộ chuỗi URL chứa tham số ký (`X-Amz-Credential`, `X-Amz-Signature`).
4. **Given** một exception bất ngờ xảy ra ở tầng AWS Client hoặc DynamoDB (ví dụ lỗi xác thực AWS hoặc mạng), **When** `GlobalExceptionHandler` ghi nhận lỗi, **Then** message lỗi và stack trace được chuẩn hóa an toàn, không để lộ secrets hoặc cấu trúc cấu hình nội bộ.

---

### User Story 3 - Bộ Kiểm Thử Tích Hợp Toàn Bộ Vòng Đời & Khớp Hợp Đồng (Full-Lifecycle Integration & Contract Tests) (Priority: P3)

Là một Kỹ sư Phát triển Backend hoặc Hệ thống Tích hợp Tự động (CI Pipeline), tôi muốn một bộ kiểm thử tích hợp toàn diện (`*IT.java`) chạy với môi trường đám mây giả lập (Testcontainers LocalStack bao gồm DynamoDB, S3, SQS) kiểm chứng xuyên suốt toàn bộ vòng đời video (từ xác thực, tạo project, cấp API Key, upload init, confirm upload, gửi job SQS, cập nhật trạng thái video, đến truy vấn danh sách/chi tiết và cô lập quyền truy cập đa người thuê), để đảm bảo 100% các tính năng Backend phối hợp hoàn hảo và tuân thủ tuyệt đối các hợp đồng kiến trúc (SSOT).

**Why this priority**: Đây là tiêu chuẩn kiểm chứng thực tế tối thiểu (Real Verification & Zero Regression Policy trong `docs/testing-strategy.md` và Epic A5/A8). Bộ test này bảo đảm khi ghép nối với Go Worker và SaaS Frontend, toàn bộ luồng tích hợp không phát sinh lỗi lệch pha contract hoặc mất tính toàn vẹn dữ liệu.

**Independent Test**: Chạy lệnh `./mvnw -B verify` trên test suite tích hợp mới; xác nhận toàn bộ các kịch bản kiểm thử vòng đời video hoàn tất thành công 100% với 0 lỗi, 0 thất bại.

**Acceptance Scenarios**:

1. **Given** môi trường LocalStack với đầy đủ bảng DynamoDB, S3 bucket và hàng đợi SQS, **When** chạy kịch bản tích hợp vòng đời hoàn chỉnh (Full Video Lifecycle Flow):
   - Đăng ký và đăng nhập người dùng lấy Bearer JWT;
   - Tạo Project và sinh API Key;
   - Khởi tạo video upload qua API Key và nhận Presigned URL;
   - Tải file nhị phân giả lập lên đúng S3 input bucket và object key;
   - Gọi xác nhận upload (`POST /api/videos/{videoId}/confirm`);
   - Kiểm tra message xuất hiện trong SQS queue `tvc-local-jobs` đúng chuẩn `schemaVersion: 1`;
   - Giả lập Worker cập nhật trạng thái `COMPLETED` và danh mục `assets` trên DynamoDB;
   - Gọi API tra cứu chi tiết (`GET /api/videos/{videoId}`) và nhận đủ link assets đã ghép `mediaBaseUrl`;
   **Then** tất cả các bước đều hoàn thành với mã HTTP tương ứng và dữ liệu trả về khớp hoàn toàn với schema của `openapi.yaml`.
2. **Given** hai người dùng và hai dự án độc lập (Tenant A và Tenant B), **When** Tenant B cố tình dùng JWT hoặc API Key của mình để confirm, xem chi tiết hoặc xóa video của Tenant A, **Then** hệ thống từ chối dứt khoát với HTTP 403 Forbidden (`ACCESS_DENIED`).
3. **Given** một message được Backend gửi vào hàng đợi SQS, **When** kiểm tra payload message, **Then** cấu trúc JSON phải thỏa mãn 100% schema quy định tại `../contracts/sqs-job-message.schema.json` (bao gồm đầy đủ `jobId`, `videoId`, `projectId`, `inputBucket`, `inputKey`, `outputBucket`, `outputPrefix`, `createdAt`, `schemaVersion`).
4. **Given** kịch bản video bị lỗi chuyển mã do Worker ghi nhận (`status = FAILED_TRANSCODE`, `errorCode = FFMPEG_EXIT_1`), **When** gọi tra cứu chi tiết, **Then** Backend phản hồi chính xác mã lỗi mà không làm sập ứng dụng hay trả về dữ liệu rỗng.

---

### Edge Cases

- **Correlation ID chứa ký tự đặc biệt hoặc quá dài**: Nếu client gửi header `X-Correlation-ID` chứa ký tự không hợp lệ hoặc dài quá 128 ký tự, hệ thống tự động làm sạch (sanitize) hoặc sinh UUID mới thay thế để tránh lỗi log injection.
- **Request bất đồng bộ / Scheduler không có HTTP context**: Tiến trình nền tự sinh correlation ID riêng có tiền tố nhận diện (ví dụ `sched-outbox-...`) để đảm bảo không có dòng log nào mang giá trị rỗng.
- **Log ngắt dòng hoặc đa dòng (Multi-line log injection)**: Thông điệp log từ người dùng (như tên file, project name) được xử lý loại bỏ ký tự ngắt dòng (`\n`, `\r`) để không làm vỡ cấu trúc JSON log stream.
- **Hàng đợi SQS bị nghẽn hoặc tạm thời không khả dụng trong test**: Bộ kiểm thử tích hợp phải khẳng định cơ chế Outbox Pattern và retry đảm bảo không làm mất job message.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống MUST cung cấp bộ lọc Correlation ID Filter tự động trích xuất `X-Correlation-ID` (hoặc `X-Request-ID`) từ HTTP request header hoặc tự động tạo mới UUID nếu thiếu, gán vào MDC và thiết lập header `X-Correlation-ID` trong mọi HTTP response.
- **FR-002**: Hệ thống MUST cấu hình Structured Logging linh hoạt qua biến môi trường `LOG_FORMAT` hoặc Spring profile (`docker`/`prod`): mặc định xuất dạng text màu có cấu trúc cho môi trường `local`/`test` và xuất định dạng JSON một dòng (Single-line JSON) cho container/cloud; mọi bản ghi log phải chứa đầy đủ các trường chuẩn: `timestamp`, `level`, `logger`, `message`, `correlationId`, cùng các trường nghiệp vụ khi có: `userId`, `projectId`, `videoId`.
- **FR-003**: Hệ thống MUST áp dụng cơ chế phòng thủ theo chiều sâu (Defense-in-Depth) để lọc và che giấu (masking) tự động toàn bộ dữ liệu nhạy cảm: chủ động loại trừ trường nhạy cảm trong `toString()` của DTO / Request model và cấu hình Logback Masking Message Converter tự động quét, thay thế các trường nhạy cảm (mật khẩu người dùng, raw API Key, token JWT, AWS credentials) và loại bỏ toàn bộ query string chứa chữ ký tạm thời của S3 Presigned URL (chỉ cho phép log S3 bucket và S3 object key).
- **FR-004**: Hệ thống MUST tổ chức bộ kiểm thử tích hợp thành 2 test classes chuyên biệt chạy với Testcontainers LocalStack: `ObservabilityLoggingIT` (chuyên kiểm chứng trích xuất header correlation, gán MDC, Log Masking, không rò rỉ secret) và `VideoFullLifecycleIT` (kiểm chứng chuỗi nghiệp vụ khép kín: Xác thực -> Project -> API Key -> Upload Init -> S3 Upload -> Confirm -> SQS Dispatch -> Worker Emulation -> Query Detail/List).
- **FR-005**: Bộ kiểm thử tích hợp MUST thẩm định tính tương thích hợp đồng của message SQS đẩy ra hàng đợi đối chiếu với `../contracts/sqs-job-message.schema.json`.
- **FR-006**: Bộ kiểm thử tích hợp MUST thẩm định tính cô lập dữ liệu đa người thuê (Multi-tenancy Resource Isolation) giữa các Tenant và Project khác nhau theo chuẩn bảo mật Dual Security.
- **FR-007**: Hệ thống MUST bảo đảm 100% test suite tích lũy hiện có của dự án (từ F-001 đến F-008) tiếp tục hoạt động chính xác và đạt tỷ lệ vượt qua tuyệt đối (Zero Regression Policy).

---

### Key Entities *(include if feature involves data)*

- **LogContext / MDC Envelope**: Tập hợp các thuộc tính ngữ cảnh gắn liền với thread/request đang xử lý, bao gồm `correlationId`, `userId`, `projectId`, `videoId`, `jobId`.
- **CorrelationResponse**: Metadata đính kèm trên HTTP response headers (`X-Correlation-ID`) giúp người dùng và các client đối soát nhật ký xử lý khi có lỗi.
- **ContractVerificationPayload**: Cấu trúc message SQS và phản hồi API chuẩn được đối chiếu với JSON schema hợp đồng kiến trúc trong quá trình chạy kiểm thử tích hợp.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% các request HTTP gửi đến Backend đều có mã tương quan `correlationId` được gán trong bản ghi log và trả ngược lại cho client qua header `X-Correlation-ID`.
- **SC-002**: Tỷ lệ rò rỉ dữ liệu nhạy cảm (mật khẩu, secret, raw API key, token JWT, query params của Presigned URL) trong log đạt mức 0% trên toàn bộ các kịch bản kiểm thử.
- **SC-003**: Bộ kiểm thử tích hợp toàn trình (Full Lifecycle Integration Tests) đạt tỷ lệ vượt qua 100% (0 failures, 0 errors) khi chạy tự động trên môi trường LocalStack/Testcontainers.
- **SC-004**: 100% các message công việc được đẩy vào hàng đợi SQS trong quá trình kiểm thử tích hợp khớp hoàn toàn với schema của hợp đồng kiến trúc `sqs-job-message.schema.json`.
- **SC-005**: Toàn bộ test suite của dự án (cả unit test và integration test) đạt trạng thái PASS 100% khi chạy qua `./scripts/verify.sh` mà không phát sinh bất kỳ lỗi hồi quy nào.

---

## Assumptions

- Việc phân tích và hiển thị dashboard giám sát log trên CloudWatch do đội ngũ Hạ tầng / Cloud phụ trách (Epic C5); Backend có trách nhiệm xuất chuẩn hóa các trường dữ liệu ra stdout dưới dạng log có cấu trúc để các agent thu thập dữ liệu dễ dàng trích xuất.
- Header tương quan mặc định ưu tiên sử dụng tên `X-Correlation-ID`, đồng thời chấp nhận fallback qua `X-Request-ID` nếu client truyền vào.
- Bộ kiểm thử tích hợp sử dụng framework Testcontainers kết hợp LocalStack sẵn có của dự án để đảm bảo môi trường kiểm thử tương đồng tối đa với AWS Cloud thật mà không phát sinh chi phí hạ tầng.
