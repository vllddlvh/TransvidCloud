# Technical Research: Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job (F-007)

**Feature**: F-007 — Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job (Epic A3/A5)  
**Date**: 2026-11-01  
**Status**: Completed

---

## 1. Thiết kế Mẫu Transactional Outbox trên DynamoDB (Single-Table Design)

### Quyết định kỹ thuật
Sử dụng giao dịch nguyên tử **`TransactWriteItems`** của AWS SDK v2 `DynamoDbEnhancedClient` để đồng thời cập nhật bản ghi `VideoItem` (`UPLOADING -> PENDING`) và tạo bản ghi `OutboxItem` trên cùng bảng `tvc-{env}-videos`, kết hợp cơ chế phát hành kép (**Immediate Fast-path Dispatch** + **`@Scheduled` Outbox Relay** truy vấn qua GSI `project-index`).

### Căn cứ và Phân tích
- **Vấn đề Dual-Write trong Hệ thống Phân tán**: Nếu chỉ cập nhật DynamoDB rồi gọi `sqsAsyncClient.sendMessage(...)` độc lập, khi SQS gặp sự cố mạng hoặc timeout, trạng thái video đã chuyển sang `PENDING` nhưng không có thông điệp nào trong hàng đợi SQS. Nếu client gọi lại confirm, nhánh idempotent sẽ thấy trạng thái đã là `PENDING` và trả về `200 OK` mà không gửi lại SQS, dẫn đến mất job vĩnh viễn.
- **Tận dụng Bảng `tvc-{env}-videos` và GSI `project-index` sẵn có (Single-Table Pattern)**:
  - Bảng `tvc-{env}-videos` có Partition Key là `videoId` và GSI `project-index` (PK: `projectId`, SK: `createdAt`).
  - Bản ghi `OutboxItem` được lưu trên cùng bảng `tvc-{env}-videos` với:
    - Partition Key (`videoId`): `"OUTBOX#" + targetVideoId` (ví dụ: `OUTBOX#vid_4a77e1c8d102`).
    - GSI Partition Key (`projectId`): `"OUTBOX#PENDING"` khi chờ gửi, và chuyển thành `"OUTBOX#PUBLISHED"` sau khi gửi SQS thành công.
    - GSI Sort Key (`createdAt`): Thời điểm tạo sự kiện Outbox (ISO-8601 UTC).
  - **Không dùng DynamoDB `Scan`**: Tiến trình `OutboxRelayScheduler` truy vấn các sự kiện chờ gửi bằng lệnh `Query` trên GSI `project-index` với `projectId = "OUTBOX#PENDING"` theo thứ tự `createdAt` tăng dần (FIFO), tuân thủ nghiêm ngặt tiêu chuẩn kỹ thuật cấm dùng `Scan`.
  - **Không xâm phạm hợp đồng hạ tầng**: Giống như mẫu `EMAIL#<email>` đã áp dụng thành công trên bảng `users` ở F-003, cách tiếp cận này không đòi hỏi sửa đổi `/contracts/dynamodb-tables.md`, script khởi tạo LocalStack hay Terraform.
  - **Cô lập hoàn toàn với truy vấn nghiệp vụ**:
    - `VideoRepository.findByVideoId(id)` lọc bỏ các khóa bắt đầu bằng `"OUTBOX#"`.
    - Truy vấn video theo dự án (`projectId = "prj_..."`) trên GSI `project-index` hoàn toàn tách biệt với phân vùng `"OUTBOX#PENDING"` và `"OUTBOX#PUBLISHED"`.

### Giải pháp thay thế đã xem xét
- *Tạo bảng DynamoDB `outbox` riêng biệt*: Bị từ chối vì quy tắc dự án cấm tự ý sửa `/contracts`, `/infra`, `/local` khi chưa có yêu cầu thay đổi hạ tầng toàn cục.
- *Rollback DynamoDB khi gửi SQS lỗi*: Nếu ứng dụng crash hoặc mất kết nối tới cả DynamoDB ngay lúc rollback, trạng thái vẫn bị treo ở `PENDING` mà không có cơ chế tự phục hồi. Transactional Outbox giải quyết triệt để điểm yếu này.

---

## 2. Đảm bảo Idempotency cho cả API Client và Go Worker

### Quyết định kỹ thuật
1. **Idempotency tại tầng API (`POST /api/videos/{videoId}/confirm`)**:
   - Lần xác nhận đầu tiên (`UPLOADING -> PENDING` thành công trong `TransactWriteItems`): trả về **HTTP 202 Accepted**.
   - Các lần gọi lặp lại khi video đã ở `PENDING`, `PROCESSING` hoặc `COMPLETED` (hoặc khi xảy ra race condition 2 request đồng thời bị `TransactionCanceledException`): trả về **HTTP 200 OK** kèm thông tin video hiện tại.
   - Nếu video đang ở `PENDING` mà bản ghi `OutboxItem` (`OUTBOX#{videoId}`) vẫn chưa `PUBLISHED`, lần gọi lặp sẽ kích hoạt thử phát lại chính bản ghi `OutboxItem` đó mà không sinh thêm job mới.
2. **Idempotency cho Go Worker (Downstream Consumer)**:
   - Ngay khi khởi tạo `OutboxItem` trong giao dịch `TransactWriteItems`, hệ thống sinh cố định `jobId` (`job_` + 12 ký tự hex CSPRNG) và `requestedAt` (ISO-8601 UTC), đóng gói thành chuỗi JSON `payload` bất biến theo đúng `contracts/sqs-job-message.schema.json`:
     ```json
     {
       "schemaVersion": 1,
       "jobId": "job_9f8e7d6c5b4a",
       "videoId": "vid_4a77e1c8d102",
       "projectId": "prj_1c05d9e2a3f4",
       "inputKey": "prj_1c05d9e2a3f4/vid_4a77e1c8d102/source.mp4",
       "requestedAt": "2026-11-01T09:00:00Z"
     }
     ```
   - Mọi lần retry từ Outbox Relay đều gửi đi **cùng một payload bất biến** với cùng `jobId`, `videoId` và `requestedAt`, giúp Worker nhận diện chính xác thông điệp trùng lặp và kết hợp `ConditionExpression: status <> COMPLETED` theo `contracts/video-state-machine.md`.

---

## 3. Kiểm tra Đối tượng S3 qua `HeadObject` và Quy tắc Dung lượng Linh hoạt

### Quyết định kỹ thuật
Sử dụng `software.amazon.awssdk.services.s3.S3Client` (đã cấu hình sẵn trong `AwsConfig.java`) để gọi `headObject(HeadObjectRequest)` kiểm tra tệp tại bucket `tvc-{env}-input` và đường dẫn `inputKey`:
- **Tệp không tồn tại trên S3** (`NoSuchKeyException` hoặc `S3Exception` có HTTP status 404): Ném `AppException(ErrorCode.S3_OBJECT_NOT_FOUND)` -> **HTTP 422 Unprocessable Entity**.
- **Tệp rỗng** (`contentLength == null || contentLength <= 0`): Ném `AppException(ErrorCode.UPLOAD_SIZE_MISMATCH)` -> **HTTP 422 Unprocessable Entity**.
- **Tệp vượt quá giới hạn tối đa 200 MB** (`contentLength > 209715200`): Ném `AppException(ErrorCode.UPLOAD_LIMIT_EXCEEDED)` -> **HTTP 413 Payload Too Large**.
- **Dung lượng hợp lệ (`0 < contentLength <= 200 MB`)**: Chấp nhận linh hoạt mà không bắt buộc phải bằng tuyệt đối `sizeBytes` đã khai báo lúc khởi tạo (theo quyết định làm rõ trong `/speckit-clarify`), đồng thời cập nhật `video.setSizeBytes(contentLength)` theo dung lượng thực tế trên S3.

---

## 4. Tích hợp Dual Security & Kiểm soát Trạng thái Video

### Quyết định kỹ thuật
- **Định tuyến trên `VideoController`**: Đưa đường dẫn cụ thể xuống từng phương thức (`@PostMapping("/projects/{projectId}/videos")` và `@PostMapping("/videos/{videoId}/confirm")`) để tập trung các API của domain Video trong một Controller duy nhất.
- **Kiểm tra quyền sở hữu tài nguyên**:
  1. Tra cứu `VideoItem` theo `videoId`. Nếu không tồn tại (hoặc khóa bắt đầu bằng `OUTBOX#`) -> ném `RESOURCE_NOT_FOUND` (HTTP 404).
  2. Gọi `validateProjectAccess(video.getProjectId(), authentication)`:
     - Nếu dùng `Bearer JWT`: kiểm tra `project.getOwnerId().equals(userId)`, sai -> `ACCESS_DENIED` (HTTP 403).
     - Nếu dùng `X-API-Key`: kiểm tra `apiKeyPrincipal.projectId().equals(video.getProjectId())`, sai -> `ACCESS_DENIED` (HTTP 403).
- **Kiểm tra trạng thái hợp lệ**:
  - `UPLOADING`: Thực hiện luồng confirm chính -> trả `202 Accepted`.
  - `PENDING`, `PROCESSING`, `COMPLETED`: Thực hiện luồng idempotent -> trả `200 OK`.
  - `FAILED_VALIDATION`, `FAILED_TRANSCODE`, `FAILED_STORAGE`: Ném `AppException(ErrorCode.INVALID_STATE_TRANSITION)` -> trả `409 Conflict`.
