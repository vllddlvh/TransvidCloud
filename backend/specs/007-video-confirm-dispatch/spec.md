# Feature Specification: Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job

**Feature Branch**: `phuong-backend`

**Created**: 2026-11-01

**Status**: Draft

**Input**: User description: "F-007: Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job (Epic A3/A5)"

## Clarifications

### Session 2026-11-01

- Q: Hệ thống nên xử lý tính nhất quán như thế nào nếu việc gửi thông điệp vào hàng đợi SQS thất bại sau khi trạng thái video đã được cập nhật sang `PENDING` trong DynamoDB? → A: **Áp dụng mẫu thiết kế Transactional Outbox Event kết hợp thông tin Idempotency cố định cho Worker**. Khi chuyển trạng thái video từ `UPLOADING` sang `PENDING`, hệ thống sử dụng giao dịch nguyên tử (`TransactWriteItems`) để đồng thời lưu một bản ghi `OutboxEvent` chứa thông điệp SQS đã được đóng gói sẵn với `jobId` và `requestedAt` bất biến. Sau giao dịch, hệ thống thực hiện đẩy thông điệp lên SQS ngay lập tức (fast-path) và duy trì tiến trình nền (Outbox Relay) quét định kỳ qua chỉ mục (không dùng `Scan`) để phát lại bền vững các sự kiện Outbox chưa gửi thành công, bảo đảm mọi thông điệp phát đi đều mang đầy đủ thông tin idempotency (`jobId`, `videoId`) giúp Worker chống xử lý trùng lặp.
- Q: Khi gọi S3 `HeadObject` trong bước confirm, hệ thống nên đối chiếu dung lượng thực tế của tệp trên S3 (`contentLength`) với dung lượng đã khai báo lúc khởi tạo video (`sizeBytes`) như thế nào? → A: **Chấp nhận linh hoạt (`0 < contentLength <= 200MB`) và đồng bộ lại `sizeBytes` thực tế**. Không bắt buộc `contentLength` trên S3 phải bằng tuyệt đối `sizeBytes` đã khai báo lúc khởi tạo để thuận tiện cho việc kiểm thử MVP; hệ thống chỉ từ chối HTTP 422 (`UPLOAD_SIZE_MISMATCH`) khi tệp rỗng (`contentLength <= 0`) hoặc HTTP 413 (`UPLOAD_LIMIT_EXCEEDED`) khi vượt quá 200 MB, và tự động cập nhật `sizeBytes` trong DynamoDB theo `contentLength` thực tế từ S3.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Xác nhận Upload thành công và Điều phối Job vào Hàng đợi SQS qua Transactional Outbox (Priority: P1)

Sau khi người dùng SaaS Web (xác thực qua Bearer JWT) hoặc ứng dụng tích hợp của bên thứ ba (xác thực qua `X-API-Key`) hoàn tất việc tải tệp video lên Amazon S3 thông qua Presigned PUT URL đã cấp, client gửi yêu cầu xác nhận `POST /api/videos/{videoId}/confirm` tới Backend. Hệ thống tiếp nhận, truy xuất siêu dữ liệu video từ cơ sở dữ liệu DynamoDB và kiểm tra tính toàn vẹn của tệp trên S3 bucket tiếp nhận (`tvc-{env}-input`) bằng thao tác `HeadObject`. Sau khi xác minh tệp có tồn tại và kích thước hợp lệ (lớn hơn 0 byte và không vượt quá 200 MB), hệ thống thực hiện một giao dịch nguyên tử (`TransactWriteItems`) trên DynamoDB để đồng thời:
1. Chuyển dịch trạng thái video từ `UPLOADING` sang `PENDING` (với điều kiện trạng thái hiện tại đang là `UPLOADING`).
2. Ghi nhận một bản ghi `OutboxEvent` ở trạng thái chờ gửi (`PENDING`) chứa đầy đủ thông điệp điều phối công việc (Job Message chuẩn `schemaVersion: 1`) với định danh công việc (`jobId`) và thời điểm yêu cầu (`requestedAt`) cố định nhằm bảo đảm tính idempotency cho Worker.

Ngay sau khi giao dịch cơ sở dữ liệu hoàn tất, hệ thống kích hoạt phát hành thông điệp từ Outbox vào hàng đợi Amazon SQS (`tvc-{env}-jobs`) và đánh dấu bản ghi Outbox là đã phát hành (`PUBLISHED`). Hệ thống phản hồi mã trạng thái HTTP 202 Accepted kèm toàn bộ thông tin cập nhật của video để client biết yêu cầu đã được tiếp nhận bền vững.

**Why this priority**: Đây là mắt xích kết nối then chốt (Epic A3 sang Epic A5) giữa tầng tiếp nhận tệp của người dùng và tầng điều phối xử lý ngầm (Worker), đồng thời đảm bảo độ tin cậy tuyệt đối (at-least-once delivery) ngay cả khi SQS tạm thời gián đoạn.

**Independent Test**: Gửi yêu cầu `POST /api/videos/{videoId}/confirm` cho một video đang ở trạng thái `UPLOADING` mà tệp đối tượng tương ứng đã tồn tại trên S3 LocalStack. Xác minh hệ thống phản hồi HTTP 202 Accepted, trường `status` chuyển thành `PENDING`, bản ghi Video và OutboxEvent trong DynamoDB được ghi nhận chính xác, và trong hàng đợi SQS `tvc-{env}-jobs` xuất hiện đúng 1 message chứa các thông tin `jobId`, `videoId`, `projectId`, `inputKey`, `requestedAt`, `schemaVersion: 1`.

**Acceptance Scenarios**:

1. **Given** Video có định danh `videoId` đang ở trạng thái `UPLOADING` trong DynamoDB và tệp video gốc đã được tải lên thành công tại S3 `inputKey` (`{projectId}/{videoId}/source.{ext}`), **When** Client gửi yêu cầu `POST /api/videos/{videoId}/confirm` với thông tin xác thực hợp lệ (JWT sở hữu Project hoặc API Key của Project), **Then** Hệ thống gọi S3 `HeadObject` xác minh thành công, thực thi giao dịch nguyên tử cập nhật trạng thái video sang `PENDING` kèm lưu `OutboxEvent` trên DynamoDB, đẩy job message vào SQS queue `tvc-{env}-jobs`, đánh dấu Outbox đã phát hành và phản hồi HTTP 202 Accepted kèm dữ liệu `VideoResponse`.
2. **Given** Quá trình xác nhận thành công, **When** Kiểm tra nội dung message trong hàng đợi SQS, **Then** Message phải tuân thủ nghiêm ngặt schema JSON `contracts/sqs-job-message.schema.json`: chứa `schemaVersion: 1`, `jobId` định dạng hợp lệ và cố định cho lần xác nhận của video, `videoId`, `projectId`, `inputKey` chính xác, và mốc thời gian `requestedAt` dạng ISO-8601 để Worker có đầy đủ thông tin chống xử lý trùng lặp.
3. **Given** Quá trình cập nhật trạng thái diễn ra, **When** Lưu vào DynamoDB, **Then** Trường `sizeBytes` được đồng bộ cập nhật theo dung lượng thực tế quan sát được từ S3 `HeadObject` (Content-Length), và trường `updatedAt` được làm mới.

---

### User Story 2 - Xử lý Gọi lặp Idempotent, Chống Tranh chấp Đồng thời và Phát hành Bền vững qua Outbox Relay (Priority: P1)

Do tính chất không tin cậy của mạng internet hoặc cơ chế tự động thử lại (auto-retry) của client/SDK, client có thể gửi nhiều lần cùng một yêu cầu `POST /api/videos/{videoId}/confirm` cho một `videoId`, hoặc kết nối tới SQS có thể gặp lỗi tạm thời ngay sau khi giao dịch DynamoDB thành công. Hệ thống phải bảo đảm tính chất bất biến/lặp lại (Idempotency) và phát hành bền vững:
1. Nếu video đã được xác nhận trước đó và đang ở trạng thái `PENDING`, `PROCESSING` hoặc `COMPLETED`:
   - Nếu bản ghi `OutboxEvent` của video đã ở trạng thái `PUBLISHED` (hoặc video đã sang `PROCESSING`/`COMPLETED`), hệ thống **tuyệt đối không gửi trùng lặp message vào SQS**, và phản hồi HTTP 200 OK kèm thông tin chi tiết hiện tại của video.
   - Nếu bản ghi `OutboxEvent` vẫn đang ở trạng thái `PENDING` (do lần gửi SQS tức thì trước đó gặp sự cố mạng), hệ thống sử dụng lại chính xác bản ghi `OutboxEvent` đó (giữ nguyên `jobId` và `requestedAt` ban đầu) để thử gửi lại vào SQS và phản hồi HTTP 200 OK.
2. Ngoài luồng gọi trực tiếp, tiến trình nền **Outbox Relay** định kỳ truy vấn các bản ghi `OutboxEvent` đang ở trạng thái `PENDING` (thông qua Secondary Index, không dùng `Scan`) để tự động gửi bù vào SQS cho đến khi thành công, đảm bảo không có bất kỳ job nào bị thất lạc.
3. Nếu xảy ra hai hoặc nhiều yêu cầu xác nhận được gửi đến hoàn toàn đồng thời (race condition) khi video còn đang ở trạng thái `UPLOADING`, giao dịch nguyên tử `TransactWriteItems` với điều kiện (`#status = :uploading` trên Video và `attribute_not_exists` trên OutboxEvent) bảo đảm chỉ có duy nhất một yêu cầu thực hiện giao dịch thành công (trả về HTTP 202 Accepted). Yêu cầu đồng thời còn lại khi gặp lỗi hủy giao dịch (`TransactionCanceledException` do `ConditionalCheckFailed`) sẽ tự động tải lại trạng thái video mới nhất và phản hồi HTTP 200 OK an toàn.

**Why this priority**: Bảo đảm tính toàn vẹn dữ liệu giữa DynamoDB và SQS, chống mất job khi SQS chập chờn và cung cấp thông tin idempotency nhất quán để Worker chống xử lý trùng lặp.

**Independent Test**:
- Kiểm thử lặp lại: Gọi `POST /api/videos/{videoId}/confirm` lần 1 nhận về HTTP 202 Accepted. Gọi tiếp lần 2 ngay sau đó nhận về HTTP 200 OK; kiểm tra số lượng message trong SQS chỉ duy nhất là 1, không tăng thêm.
- Kiểm thử phục hồi Outbox: Giả lập lỗi gửi SQS ở thời điểm confirm (hoặc tạo bản ghi Outbox ở trạng thái `PENDING`), kích hoạt Outbox Relay và xác minh message được đẩy vào SQS thành công với đúng `jobId` ban đầu, sau đó bản ghi Outbox chuyển sang `PUBLISHED`.

**Acceptance Scenarios**:

1. **Given** Video đã ở trạng thái `PENDING` (với Outbox đã `PUBLISHED`), `PROCESSING` hoặc `COMPLETED`, **When** Client gửi yêu cầu `POST /api/videos/{videoId}/confirm`, **Then** Hệ thống không tạo Outbox mới, không gửi thêm message vào SQS, và phản hồi HTTP 200 OK kèm thông tin video hiện tại.
2. **Given** Video đang ở trạng thái `UPLOADING` và có 2 yêu cầu confirm được kích hoạt đồng thời, **When** Cả 2 cùng cố gắng thực thi giao dịch chuyển trạng thái, **Then** Đúng 1 yêu cầu nhận về HTTP 202 Accepted và chỉ đúng 1 bản ghi `OutboxEvent` được tạo; yêu cầu còn lại nhận về HTTP 200 OK.
3. **Given** Giao dịch cập nhật Video sang `PENDING` và ghi `OutboxEvent` đã thành công nhưng lần phát SQS tức thì gặp lỗi mạng tạm thời, **When** Tiến trình Outbox Relay chạy quét định kỳ (hoặc client gọi lại confirm), **Then** Thông điệp trong `OutboxEvent` được phát lại lên SQS với nguyên vẹn `jobId` và `requestedAt` ban đầu, sau đó trạng thái Outbox được cập nhật thành `PUBLISHED`.

---

### User Story 3 - Kiểm tra Tính Toàn vẹn Tệp trên S3 và Từ chối Tệp Không Hợp Lệ (Priority: P1)

Trước khi chuyển trạng thái và đưa job vào hàng đợi SQS, hệ thống bắt buộc phải kiểm tra đối tượng tệp thực tế trên Amazon S3 thông qua thao tác `HeadObject`. Hệ thống phát hiện và từ chối các trường hợp bất thường:
1. Client chưa thực sự tải tệp lên S3 (S3 báo lỗi `404 Not Found` / `NoSuchKey`).
2. Tệp trên S3 rỗng (`contentLength == 0`).
3. Tệp trên S3 có kích thước vượt quá giới hạn tối đa MVP là 200 MB (`contentLength > 209715200`).

Trong các trường hợp này, trạng thái video trong DynamoDB phải được giữ nguyên là `UPLOADING` (để client có cơ hội sửa đổi hoặc tải lại đúng cách), không tạo OutboxEvent hay đưa job vào SQS, và phản hồi mã lỗi HTTP tương ứng kèm thông điệp rõ ràng.

**Why this priority**: Ngăn ngừa đưa rác hoặc các tác vụ không hợp lệ vào hàng đợi Worker, bảo vệ tài nguyên tính toán của hệ thống và phản hồi lỗi sớm cho người dùng.

**Independent Test**:
- Khởi tạo video nhưng không tải byte nào lên S3, gọi confirm -> nhận lỗi HTTP 422 Unprocessable Entity (`S3_OBJECT_NOT_FOUND`).
- Tải lên S3 tệp 0 byte, gọi confirm -> nhận lỗi HTTP 422 Unprocessable Entity (`UPLOAD_SIZE_MISMATCH`).
- Giả lập tệp trên S3 vượt quá 200 MB, gọi confirm -> nhận lỗi HTTP 413 Payload Too Large (`UPLOAD_LIMIT_EXCEEDED`).

**Acceptance Scenarios**:

1. **Given** Video đang ở `UPLOADING` nhưng đối tượng S3 tại `inputKey` chưa tồn tại, **When** Client gọi confirm, **Then** Hệ thống phản hồi HTTP 422 Unprocessable Entity, giữ nguyên trạng thái `UPLOADING` và không tạo OutboxEvent hay gửi message vào SQS.
2. **Given** Tệp trên S3 có `contentLength = 0`, **When** Client gọi confirm, **Then** Hệ thống từ chối với HTTP 422 Unprocessable Entity.
3. **Given** Tệp trên S3 có dung lượng vượt quá 200 MB (209,715,200 bytes), **When** Client gọi confirm, **Then** Hệ thống từ chối với HTTP 413 Payload Too Large.

---

### User Story 4 - Phân quyền Truy cập và Cô lập Tài nguyên Đa Người dùng (Priority: P1)

Hệ thống bảo đảm tính bảo mật đa người thuê (Multi-tenancy Isolation) thông qua kiến trúc xác thực kép (Dual Security):
1. **Xác thực JWT (SaaS Web):** Người dùng phải đăng nhập hợp lệ và bắt buộc phải là chủ sở hữu (`ownerId`) của Project chứa video đó. Nếu người dùng B cố tình xác nhận video thuộc Project của người dùng A, hệ thống từ chối ngay lập tức.
2. **Xác thực API Key (Developer Client):** Yêu cầu mang tiêu đề `X-API-Key` hợp lệ phải thuộc đúng Project chứa video. Nếu sử dụng API Key của Project X để xác nhận video thuộc Project Y, hệ thống từ chối ngay lập tức.
3. Nếu không có thông tin xác thực hoặc token/key không hợp lệ: từ chối với HTTP 401 Unauthorized.
4. Nếu `videoId` không tồn tại trong hệ thống: từ chối với HTTP 404 Not Found.
5. Nếu video đang ở một trong các trạng thái thất bại (`FAILED_VALIDATION`, `FAILED_TRANSCODE`, `FAILED_STORAGE`), việc gọi confirm là không hợp lệ theo chu trình sống: hệ thống từ chối với HTTP 409 Conflict.

**Why this priority**: Bảo vệ an toàn dữ liệu, chống tấn công leo thang đặc quyền (IDOR) hoặc can thiệp trái phép vào tài nguyên giữa các khách hàng khác nhau.

**Independent Test**:
- Dùng tài khoản Người dùng B gọi confirm cho video thuộc Project của Người dùng A -> nhận HTTP 403 Forbidden (`ACCESS_DENIED`).
- Dùng `X-API-Key` của Project B gọi confirm cho video thuộc Project A -> nhận HTTP 403 Forbidden (`ACCESS_DENIED`).
- Gọi confirm với `videoId` không tồn tại -> nhận HTTP 404 Not Found (`RESOURCE_NOT_FOUND`).

**Acceptance Scenarios**:

1. **Given** Người dùng đăng nhập bằng JWT hợp lệ nhưng không phải chủ sở hữu Project của video, **When** Gửi yêu cầu confirm, **Then** Hệ thống phản hồi HTTP 403 Forbidden.
2. **Given** Client gửi `X-API-Key` hợp lệ nhưng thuộc Project khác với Project chứa video, **When** Gửi yêu cầu confirm, **Then** Hệ thống phản hồi HTTP 403 Forbidden.
3. **Given** Yêu cầu không mang thông tin xác thực hợp lệ, **When** Gửi yêu cầu confirm, **Then** Hệ thống phản hồi HTTP 401 Unauthorized.
4. **Given** Yêu cầu xác nhận cho `videoId` không tồn tại trong cơ sở dữ liệu, **When** Gửi yêu cầu confirm, **Then** Hệ thống phản hồi HTTP 404 Not Found.
5. **Given** Video đang ở trạng thái `FAILED_VALIDATION` hoặc `FAILED_TRANSCODE`, **When** Gửi yêu cầu confirm, **Then** Hệ thống phản hồi HTTP 409 Conflict.

---

### Edge Cases

- **Mất kết nối S3 khi gọi `HeadObject`:** Nếu S3 gặp sự cố kỹ thuật hoặc timeout mạng, hệ thống bắt ngoại lệ và trả về lỗi HTTP 500 hoặc 503 có cấu trúc chuẩn; không thay đổi trạng thái video trên DynamoDB để client có thể thử lại an toàn.
- **Mất kết nối SQS sau khi giao dịch DynamoDB (`Video` + `OutboxEvent`) đã thành công:** Nhờ mẫu Transactional Outbox, trạng thái `PENDING` của video và bản ghi `OutboxEvent` đã được cam kết bền vững trong DynamoDB. Nếu lần đẩy SQS tức thì (immediate dispatch) bị lỗi mạng/timeout, hệ thống ghi log cảnh báo kèm `videoId`/`projectId`/`jobId` mà không làm mất sự kiện; tiến trình nền Outbox Relay (hoặc lần gọi confirm lặp lại tiếp theo của client) sẽ tự động đọc lại `OutboxEvent` chưa phát hành và gửi bù thông điệp với cùng `jobId` và `requestedAt` lên SQS.
- **Worker nhận thông điệp SQS trùng lặp (At-least-once delivery):** Do mọi lần phát lại của cùng một sự kiện confirm từ Outbox đều giữ nguyên `jobId`, `videoId`, `projectId`, `inputKey` và `requestedAt` bất biến, Worker dễ dàng sử dụng `jobId` và `videoId` cùng điều kiện trạng thái trên DynamoDB (`status <> COMPLETED`) để chống xử lý trùng hoặc ghi đè kết quả đã hoàn tất.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống MUST cung cấp endpoint `POST /api/videos/{videoId}/confirm` hỗ trợ đồng thời xác thực qua Bearer JWT và `X-API-Key`.
- **FR-002**: Hệ thống MUST xác thực quyền sở hữu tài nguyên: chỉ cho phép xác nhận video khi người dùng là chủ sở hữu (`ownerId`) của Project hoặc yêu cầu mang `X-API-Key` của đúng Project chứa video; từ chối HTTP 403 Forbidden nếu vi phạm.
- **FR-003**: Hệ thống MUST trả về HTTP 404 Not Found nếu `videoId` không tồn tại trong bảng `videos`.
- **FR-004**: Hệ thống MUST kiểm tra sự tồn tại của đối tượng video trên Amazon S3 bucket tiếp nhận (`tvc-{env}-input`) tại đường dẫn `inputKey` bằng phương thức `HeadObject`.
- **FR-005**: Hệ thống MUST từ chối với HTTP 422 Unprocessable Entity nếu đối tượng S3 chưa tồn tại (`S3_OBJECT_NOT_FOUND`) hoặc có dung lượng bằng 0 byte (`UPLOAD_SIZE_MISMATCH`).
- **FR-006**: Hệ thống MUST từ chối với HTTP 413 Payload Too Large (`UPLOAD_LIMIT_EXCEEDED`) nếu dung lượng đối tượng trên S3 vượt quá giới hạn tối đa `MAX_UPLOAD_BYTES` (200 MB / 209,715,200 bytes).
- **FR-007**: Hệ thống MUST thực hiện giao dịch nguyên tử (`TransactWriteItems`) trên DynamoDB để đồng thời chuyển trạng thái video từ `UPLOADING` sang `PENDING` (với điều kiện `#status = :uploading`) và ghi nhận một bản ghi `OutboxEvent` ở trạng thái chờ phát hành (`PENDING` với điều kiện `attribute_not_exists`).
- **FR-008**: Hệ thống MUST chấp nhận linh hoạt mọi dung lượng thực tế hợp lệ (`0 < contentLength <= MAX_UPLOAD_BYTES`) mà không bắt buộc phải bằng tuyệt đối `sizeBytes` đã khai báo lúc khởi tạo, đồng thời cập nhật trường `sizeBytes` trên DynamoDB theo `contentLength` thực tế từ S3 `HeadObject` và làm mới `updatedAt`.
- **FR-009**: Hệ thống MUST sinh định danh công việc (`jobId` có tiền tố `job_`) và mốc thời gian `requestedAt` cố định ngay khi khởi tạo `OutboxEvent`, đảm bảo mọi lần phát thông điệp của cùng một sự kiện xác nhận đều mang thông tin định danh idempotency bất biến cho Worker.
- **FR-010**: Thông điệp SQS được lưu trong `OutboxEvent` và gửi tới `tvc-{env}-jobs` MUST tuân thủ nghiêm ngặt schema JSON `contracts/sqs-job-message.schema.json`, bao gồm các thuộc tính: `schemaVersion: 1`, `jobId`, `videoId`, `projectId`, `inputKey`, và `requestedAt`.
- **FR-011**: Sau khi giao dịch lưu `OutboxEvent` thành công, hệ thống MUST kích hoạt phát hành thông điệp lên SQS ngay lập tức (immediate dispatch) và cập nhật trạng thái `OutboxEvent` sang `PUBLISHED` khi SQS xác nhận thành công, đồng thời cung cấp cơ chế **Outbox Relay** chạy nền định kỳ truy vấn qua chỉ mục GSI (tuyệt đối không dùng DynamoDB `Scan`) để phát lại bền vững các bản ghi `OutboxEvent` còn ở trạng thái `PENDING`.
- **FR-012**: Hệ thống MUST phản hồi HTTP 202 Accepted kèm thông tin `VideoResponse` khi giao dịch xác nhận lần đầu (`UPLOADING -> PENDING`) thành công.
- **FR-013**: Hệ thống MUST bảo đảm tính bất biến/lặp lại (Idempotency): nếu video đã ở trạng thái `PENDING`, `PROCESSING` hoặc `COMPLETED`, hệ thống MUST phản hồi HTTP 200 OK với thông tin video hiện tại và tuyệt đối không tạo thêm `OutboxEvent` mới hay gửi trùng message khi Outbox đã `PUBLISHED`.
- **FR-014**: Hệ thống MUST từ chối với HTTP 409 Conflict (`INVALID_STATE_TRANSITION`) nếu video đang ở các trạng thái lỗi kết thúc chu trình tải (`FAILED_VALIDATION`, `FAILED_TRANSCODE`, `FAILED_STORAGE`).
- **FR-015**: Hệ thống MUST ghi log có cấu trúc đầy đủ thông tin ngữ cảnh (`videoId`, `projectId`, `jobId`, kích thước tệp, trạng thái outbox), và tuyệt đối MUST NOT ghi lộ thông tin bí mật hay thông tin nhạy cảm vào logs.

---

### Key Entities *(include if feature involves data)*

- **Video**: Đại diện cho siêu dữ liệu của một tệp video trong hệ thống, lưu trữ tại bảng DynamoDB `tvc-{env}-videos`.
  - Các thuộc tính chính: `videoId` (PK), `projectId` (GSI PK), `title`, `status` (`UPLOADING`, `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED_*`), `sizeBytes`, `contentType`, `attemptCount`, `inputKey`, `createdAt`, `updatedAt`.
- **OutboxEvent**: Đại diện cho bản ghi sự kiện Transactional Outbox được ghi nguyên tử cùng với quá trình chuyển trạng thái `Video`, bảo đảm thông điệp SQS không bao giờ bị thất lạc và giữ nguyên thông tin idempotency khi phát lại.
  - Các thuộc tính chính: định danh khóa phân vùng gắn với sự kiện xác nhận của video (`OUTBOX#{videoId}`), khóa chỉ mục phục vụ truy vấn các sự kiện chờ gửi mà không cần quét bảng (`OUTBOX#PENDING` / `OUTBOX#PUBLISHED`), `jobId`, `targetVideoId`, `targetProjectId`, `payload` (nội dung JSON chuẩn của `JobMessage`), `attemptCount`, `lastError`, `createdAt`, `updatedAt`, `publishedAt`.
- **JobMessage**: Đại diện cho thông điệp điều phối công việc chuyển mã được đẩy vào hàng đợi Amazon SQS `tvc-{env}-jobs`.
  - Các thuộc tính: `schemaVersion` (giá trị cố định: 1), `jobId` (chuỗi định danh duy nhất cố định của job giúp Worker chống xử lý trùng), `videoId`, `projectId`, `inputKey`, `requestedAt` (thời điểm yêu cầu định dạng ISO-8601).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% các yêu cầu xác nhận hợp lệ được hoàn tất xử lý nguyên tử (kiểm tra S3, giao dịch `TransactWriteItems` cập nhật `Video` sang `PENDING` kèm lưu `OutboxEvent`, và đẩy thông điệp vào SQS) với phản hồi HTTP 202 Accepted.
- **SC-002**: Tính chất Idempotency và chống mất mát thông điệp đạt độ tin cậy tuyệt đối (100%): các yêu cầu gọi lặp lại cho video đã ở `PENDING`, `PROCESSING`, `COMPLETED` đều nhận về HTTP 200 OK; nếu lần gửi SQS đầu tiên gặp sự cố, Outbox Relay tự động phát bù thành công với đúng `jobId` ban đầu mà không tạo thông điệp rác.
- **SC-003**: 100% các hành vi truy cập trái phép qua ranh giới người dùng/dự án (vi phạm tenant isolation) hoặc tệp S3 không hợp lệ đều bị chặn chính xác với mã trạng thái HTTP tiêu chuẩn (401, 403, 404, 409, 413, 422).
- **SC-004**: Toàn bộ luồng nghiệp vụ (bao gồm cả luồng xác nhận chính, luồng idempotent, và cơ chế phục hồi Transactional Outbox Relay) được kiểm chứng bằng Integration Test thực tế (`VideoConfirmDispatchIT`) tương tác trực tiếp với LocalStack S3, DynamoDB, SQS và duy trì Zero Regression 100% trên toàn bộ test suite dự án.

---

## Assumptions

- Môi trường S3 bucket tiếp nhận (`tvc-{env}-input`) và hàng đợi SQS (`tvc-{env}-jobs`) đã được cấu hình và sẵn sàng hoạt động (đã được khởi tạo qua LocalStack trong môi trường phát triển).
- Bản ghi `OutboxEvent` được lưu trữ theo mô hình Single-Table trên cùng bảng `tvc-{env}-videos` để tận dụng giao dịch `TransactWriteItems` và GSI `project-index` sẵn có mà không làm thay đổi cấu trúc hạ tầng hay hợp đồng bảng DynamoDB trong `/contracts`.
- Client chịu trách nhiệm hoàn tất quá trình tải tệp lên S3 qua Presigned PUT URL trước khi gọi API xác nhận `POST /api/videos/{videoId}/confirm`.
- Cấu hình dung lượng tối đa 200 MB (`MAX_UPLOAD_BYTES = 209715200`) được duy trì nhất quán theo quy định tại `contracts/upload-limits.md`.
- Worker chuyển mã (Go service) là người tiêu thụ (consumer) duy nhất của hàng đợi SQS và tuân thủ định dạng thông điệp `schemaVersion: 1`.
