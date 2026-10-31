# Feature Backlog

> Một dòng cho mỗi feature. Cột Behavior mô tả outcome ngắn; Verification nêu lệnh hoặc tiêu chí cần chạy; Evidence trỏ tới CI run, PR, commit hoặc báo cáo khi có. Chi tiết yêu cầu/task nằm trong spec tương ứng.

| ID | Feature | Priority | State | Behavior / Outcome | Verification | Spec | Evidence |
|---|---|---|---|---|---|---|---|
| F-001 | Khởi tạo môi trường Spring Boot Backend (Epic A1) | `high` | `completed` | Dựng khung Maven Wrapper, Spring Boot 3.4.5, Java 21, AWS SDK v2 (DynamoDB/S3/SQS), Security JWT, Lombok, MapStruct, Swagger UI; build và chạy thành công trên `localhost:8080/api` | `./mvnw -B clean verify` & `curl -f http://localhost:8080/api/actuator/health` | `-` | `./scripts/verify.sh` PASS |
| F-002 | Nền tảng Common, AWS Clients, CORS & Xử lý lỗi chuẩn (Epic A1/A2) | `high` | `completed` | Cấu hình `TvcProperties`, khởi tạo AWS SDK v2 beans (`DynamoDbEnhancedClient`, `S3Client`, `S3Presigner`, `SqsAsyncClient`) hỗ trợ LocalStack/Cloud, cấu hình CORS, `GlobalExceptionHandler` và chuẩn hóa response/error theo contract | `./mvnw -B test` | `specs/002-common-aws-foundation/` | `quality/evidence/F-002-common-aws-foundation.md` |
| F-003 | Xác thực người dùng SaaS — Register, Login & JWT Security (Epic A2) | `high` | `planned` | Tầng lưu trữ bảng `users` (`email-index` + `TransactWriteItems` đảm bảo email duy nhất), băm mật khẩu `BCrypt`, phát hành/xác thực Bearer JWT (`HMAC-SHA256`), triển khai `POST /api/auth/register` và `POST /api/auth/login` | `./mvnw -B verify` | `specs/F-003-saas-auth-jwt/` | `-` |
| F-004 | Quản lý Project & Phân quyền sở hữu tài nguyên (Epic A2) | `high` | `planned` | Tầng lưu trữ bảng `projects` (`owner-index`), kiểm tra quyền sở hữu (`ownerId` isolation), triển khai CRUD Project (`POST/GET /api/projects`, `GET/PATCH/DELETE /api/projects/{projectId}`, chặn `409` nếu còn video đang xử lý) | `./mvnw -B verify` | `specs/F-004-project-management/` | `-` |
| F-005 | Quản lý API Key & Xác thực Dual Security JWT + X-API-Key (Epic A2) | `high` | `planned` | Tầng lưu trữ bảng `apikeys` (`keyHash` SHA-256, `project-index`), sinh/liệt kê/thu hồi API Key (`POST/GET /api/projects/{projectId}/api-keys`, `DELETE .../api-keys/{keyHash}`), tích hợp filter xác thực đồng thời `BearerAuth` và `X-API-Key` theo đúng phạm vi Project | `./mvnw -B verify` | `specs/F-005-apikey-dual-auth/` | `-` |
| F-006 | Khởi tạo Upload Video & Cấp S3 Presigned PUT URL (Epic A3) | `high` | `planned` | Kiểm tra giới hạn upload (`sizeBytes <= 200MB` -> `413`, `contentType` `mp4/mov/mkv/webm` -> `422`), lưu bản ghi `videos` ở trạng thái `UPLOADING` (`inputKey = {projectId}/{videoId}/source.{ext}`) và ký Presigned PUT URL 15 phút (`POST /api/projects/{projectId}/videos`) | `./mvnw -B verify` | `specs/F-006-video-upload-init/` | `-` |
| F-007 | Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job (Epic A3/A5) | `high` | `planned` | Triển khai `POST /api/videos/{videoId}/confirm`: gọi S3 `HeadObject` xác minh object và dung lượng, conditional update `UPLOADING -> PENDING`, gửi message (`schemaVersion: 1`) tới SQS `tvc-{env}-jobs`; đảm bảo idempotency (`202` lần đầu, `200` khi gọi lặp) và an toàn khi retry | `./mvnw -B verify` | `specs/F-007-video-confirm-dispatch/` | `-` |
| F-008 | Truy vấn Danh sách (Cursor Pagination), Chi tiết, Assets & Xóa Video (Epic A2/A3) | `high` | `planned` | Triển khai `GET /api/projects/{projectId}/videos` (GSI `project-index`, phân trang cursor `LastEvaluatedKey`), `GET /api/videos/{videoId}` (trả `status`, `errorCode`, ghép `TVC_MEDIA_BASE_URL` cho HLS/thumbnail `assets`), và `DELETE /api/videos/{videoId}` (chặn `409` nếu đang `PROCESSING`) | `./mvnw -B verify` | `specs/F-008-video-query-lifecycle/` | `-` |
| F-009 | Quan sát hệ thống, Bảo mật Log & Bộ kiểm thử Tích hợp / Contract (Epic A5/A8/C5) | `medium` | `planned` | Chuẩn hóa structured logging kèm `videoId`/`projectId`/`jobId` (ẩn secret/presigned URL), hoàn thiện bộ test tích hợp Testcontainers LocalStack (`*IT.java`) cho toàn bộ luồng upload -> confirm -> SQS -> trạng thái/assets và kiểm tra cô lập quyền truy cập | `./mvnw -B clean verify` & `./scripts/verify.sh` | `specs/F-009-observability-integration-tests/` | `-` |
| F-010 | Thông báo Webhook khi hoàn tất xử lý Video (Epic A7 — P1) | `low` | `planned` | (P1 — thực hiện sau khi P0 ổn định) Hỗ trợ cấu hình `webhookUrl`/`webhookSecret` theo Project và gửi HTTP callback có chữ ký HMAC kèm cơ chế retry khi video đạt trạng thái `COMPLETED` hoặc `FAILED_*` | `./mvnw -B verify` | `specs/F-010-project-webhook/` | `-` |

## State definitions

- `planned`: nằm trong kế hoạch, chưa bắt đầu.
- `active`: đang được thực hiện.
- `blocked`: bị chặn; ghi lý do trong `PROGRESS.md` hoặc spec.
- `verified`: các verification bắt buộc đã thực sự pass, có evidence.
- `completed`: đã được con người chấp nhận theo quy trình review/merge.
- `cancelled`: đã loại khỏi phạm vi, ghi lý do trong ghi chú feature hoặc decision record.

## Quy tắc cập nhật

1. Không thêm feature chỉ vì agent nghĩ ra; cần yêu cầu hoặc quyết định của người có thẩm quyền.
2. Mỗi feature cần ID ổn định, không tái sử dụng ID đã hủy.
3. Khi feature bắt đầu, cập nhật state thành `active` và thêm đường dẫn spec nếu đã có.
4. Chỉ chuyển `active → verified` khi có bằng chứng kiểm tra.
5. Chỉ chuyển `verified → completed` khi đã có human acceptance/merge theo quy trình dự án.
6. Không đánh dấu `completed` khi còn acceptance criteria bắt buộc chưa đạt.
