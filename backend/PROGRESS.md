# Project Progress — Snapshot hiện tại

> File này là snapshot trạng thái mới nhất, không phải nhật ký lịch sử. Cập nhật/thay thế khi có thay đổi đáng kể hoặc kết thúc session. Không ghi lại toàn bộ prompt và tool calls.

## Cập nhật

- Thời điểm: `2026-11-01T18:25:00+07:00`
- Branch: `phuong-backend`

## Trạng thái tổng quan

- Current focus: `F-009: Quan sát hệ thống, Bảo mật Log & Bộ kiểm thử Tích hợp / Contract (Epic A5/A8/C5)` (Hoàn tất & Đóng feature)
- Feature spec F-009: [`specs/009-observability-integration-tests/spec.md`](specs/009-observability-integration-tests/spec.md)
- Checklist F-009: [`specs/009-observability-integration-tests/checklists/requirements.md`](specs/009-observability-integration-tests/checklists/requirements.md)
- Feature vừa hoàn thành: `F-009: Quan sát hệ thống, Bảo mật Log & Bộ kiểm thử Tích hợp / Contract` (completed)
- Evidence F-009: [`quality/evidence/F-009-observability-integration-tests.md`](quality/evidence/F-009-observability-integration-tests.md)
- Status: `completed`

## Kết quả đạt được của F-009

1. **Structured Logging & Correlation Tracking (US1)**:
   - Triển khai `LogContext` và `CorrelationIdFilter` (`Ordered.HIGHEST_PRECEDENCE`) tự động tiếp nhận hoặc tạo mới `X-Correlation-ID` (hoặc fallback `X-Request-ID`), làm sạch ký tự bẩn, gán vào Logback MDC và trả về trong HTTP response header.
   - Làm giàu ngữ cảnh nghiệp vụ (`userId`, `projectId`, `videoId`) xuyên suốt `VideoService`, `ProjectService`, `ApiKeyService` và `GlobalExceptionHandler`.
   - `OutboxRelayScheduler` tự sinh correlation ID dạng `sched-outbox-...` cho các tác vụ nền không có HTTP request.
2. **Log Masking & Security (US2)**:
   - Cơ chế phòng thủ theo chiều sâu (Defense-in-Depth): Loại trừ trường nhạy cảm trong DTO `toString()` (`RegisterRequest`, `LoginRequest`, `ApiKeyCreatedResponse`, `@ToString(exclude = "passwordHash")` trên `UserItem`).
   - Xây dựng `MaskingMessageConverter` và `StructuredLoggingLayout` kết hợp `logback-spring.xml` tự động quét regex và che giấu `password`, `rawApiKey`, `Bearer token`, `aws_secret_access_key` thành `[PROTECTED]`, và loại bỏ toàn bộ query string chứa chữ ký S3 Presigned URL (`X-Amz-Signature`).
   - Hỗ trợ biến môi trường `LOG_FORMAT=text` (màu có cấu trúc) hoặc `LOG_FORMAT=json` (single-line JSON).
3. **Full-Lifecycle Integration & Contract Tests (US3)**:
   - Xây dựng `ObservabilityLoggingIT` kiểm chứng trích xuất header correlation, MDC enrichment, và log masking an toàn.
   - Xây dựng `VideoFullLifecycleIT` chạy trên môi trường đám mây giả lập Testcontainers LocalStack (DynamoDB, S3, SQS): kiểm chứng chuỗi trọn vẹn Auth -> Project -> API Key -> Upload Init -> S3 Upload -> Confirm -> SQS Dispatch -> Worker Emulation -> Query Detail/List.
   - Thẩm định 100% tính tương thích của message SQS nhận được với hợp đồng `docs/contracts/sqs-job-message.schema.json` v1 (`jobId`, `videoId`, `projectId`, `inputKey`, `requestedAt`, `schemaVersion = 1`).
   - Thẩm định Multi-tenancy Isolation (Tenant B cố tình dùng JWT hoặc API Key truy cập tài nguyên Tenant A đều bị chặn đứng với 403 `ACCESS_DENIED`).
   - Thẩm định xung đột trạng thái: Chặn xóa video khi `PROCESSING` với 409 `INVALID_STATE_TRANSITION`, cho phép Fast Delete khi `COMPLETED` với 204 No Content.

## Việc còn lại

- Chuẩn bị kế hoạch và kích hoạt Feature tiếp theo theo roadmap: `F-010: Thông báo Webhook khi hoàn tất xử lý Video (Epic A7 — P1)`.

## Blocker / Quyết định cần con người

- Không có blocker. Tất cả các tính năng P0 của dự án (từ F-001 đến F-009) đã hoàn thành, kiểm chứng và đóng thành công.

## Verification gần nhất

| Lệnh | Kết quả | Thời điểm | Evidence |
|---|---|---|---|
| `./mvnw -B test -Dtest=ObservabilityLoggingIT,VideoFullLifecycleIT` | `PASS` (6/6 tests pass, 0 failures, 0 errors) | `2026-11-01T18:18:56+07:00` | Kiểm thử tích hợp Testcontainers LocalStack đạt 100% |
| `./scripts/verify.sh` (`./mvnw -B clean verify`) | `PASS` (100% test suite pass, 24 IT tests, 100+ unit tests, 0 failures) | `2026-11-01T18:22:19+07:00` | Đạt Zero Regression tuyệt đối trên toàn bộ dự án |
| `./scripts/check-setup.sh` | `PASS` (10/10 checks passed) | `2026-11-01T18:20:00+07:00` | Harness structure check hoàn chỉnh |

## Hành động tiếp theo

- Bàn giao session sạch (Clean Handoff) cho Feature F-009.
- Chuyển sang Feature F-010 khi người dùng yêu cầu.
