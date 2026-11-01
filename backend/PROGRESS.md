# Project Progress — Snapshot hiện tại

> File này là snapshot trạng thái mới nhất, không phải nhật ký lịch sử. Cập nhật/thay thế khi có thay đổi đáng kể hoặc kết thúc session. Không ghi lại toàn bộ prompt và tool calls.

## Cập nhật

- Thời điểm: `2026-11-01T16:40:00+07:00`
- Branch: `phuong-backend`

## Trạng thái tổng quan

- Feature vừa hoàn thành: `F-007: Xác nhận Upload, Chuyển trạng thái & Điều phối SQS Job (Epic A3/A5)`
- Trạng thái F-007: `completed` (Đã nghiệm thu, hoàn tất kiểm chứng và commit)
- Feature tiếp theo: `F-008: Truy vấn Danh sách (Cursor Pagination), Chi tiết, Assets & Xóa Video (Epic A2/A3)`
- Trạng thái F-008: `planned`
- Feature spec F-007: `specs/007-video-confirm-dispatch/spec.md`
- Feature Evidence F-007: `quality/evidence/F-007-video-confirm-dispatch.md`

## Kết quả đã được kiểm chứng (F-007)

- **Hoàn thành 100% 40/40 tasks trong `specs/007-video-confirm-dispatch/tasks.md`**:
  - Triển khai endpoint `POST /api/videos/{videoId}/confirm`.
  - Mẫu kiến trúc Transactional Outbox Event theo Single-Table trên bảng `tvc-{env}-videos` bằng `TransactWriteItems` nguyên tử.
  - Tích hợp kiểm tra S3 `HeadObject` (chống file rỗng 0 byte, giới hạn 200MB linh hoạt).
  - Điều phối SQS Job (`schemaVersion: 1`, `jobId`, `videoId`, `projectId`, `inputKey`, `requestedAt`) tới queue `tvc-{env}-jobs`.
  - Cơ chế Outbox Relay Scheduler (`@Scheduled`) quét GSI `project-index` (`OUTBOX#PENDING`) phát bù sự kiện mà không Table Scan.
  - Idempotency trọn vẹn: lần 1 trả 202 Accepted, lần 2 trả 200 OK không gửi lặp SQS message.
  - Phân quyền kép (Bearer JWT + `X-API-Key`) và các mã lỗi 401, 403, 404, 409, 413, 422.
- **Real Verification với Testcontainers LocalStack 3.0 (`VideoConfirmDispatchIT.java`)**: 5/5 kịch bản tích hợp thực tế PASS 100%.
- **Newman E2E Postman Collection (`F-007-video-confirm-dispatch.postman_collection.json`)**: 16/16 requests, 29/29 assertions PASS 100%.
- **Zero Regression (`./scripts/verify.sh`)**: 100% tests PASS (109 tests cũ + toàn bộ tests mới của F-007).
- **Speckit Converge**: Đạt chuẩn `converged` (100% requirements, success criteria, plan decisions và hiến pháp đều thỏa mãn).
- **Feature Evidence**: Đã lập hồ sơ đầy đủ tại `quality/evidence/F-007-video-confirm-dispatch.md`.

## Việc còn lại / Hành động tiếp theo

1. Bắt đầu session mới cho **`F-008: Truy vấn Danh sách (Cursor Pagination), Chi tiết, Assets & Xóa Video (Epic A2/A3)`**.
2. Thực hiện quy trình SDD cho F-008: `/speckit-specify F-008` ➔ `/speckit-plan` ➔ `/speckit-tasks` ➔ `/speckit-implement`.

## Blocker / Quyết định cần con người

- Không có blocker. F-007 đã được hoàn tất và đóng gọn gàng.

## Verification gần nhất

| Lệnh | Kết quả | Thời điểm | Evidence |
|---|---|---|---|
| `./scripts/verify.sh` (`./mvnw -B clean verify`) | `PASS` (100% tests pass, 0 failures, 0 errors) | `2026-11-01T16:26:22+07:00` | Zero Regression test suite pass toàn diện |
| `npx --yes newman run backend/postman/F-007-video-confirm-dispatch.postman_collection.json` | `PASS` (16 requests, 29 assertions, 0 failures) | `2026-11-01T16:24:11+07:00` | E2E Postman test suite pass 100% |
| `./mvnw -B test -Dtest="VideoConfirmDispatchIT"` | `PASS` (5 runs, 0 failures) | `2026-11-01T16:21:06+07:00` | Real Verification LocalStack S3 + DynamoDB + SQS |
| `./scripts/check-setup.sh` | `PASS` (10 required files OK) | `2026-11-01T16:24:25+07:00` | Harness structure check pass |
