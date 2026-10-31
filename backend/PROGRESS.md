# Project Progress — Snapshot hiện tại

> File này là snapshot trạng thái mới nhất, không phải nhật ký lịch sử. Cập nhật/thay thế khi có thay đổi đáng kể hoặc kết thúc session. Không ghi lại toàn bộ prompt và tool calls.

## Cập nhật

- Thời điểm: `2026-10-31T21:16:00+07:00`
- Branch: `phuong-backend`

## Trạng thái tổng quan

- Current focus: `F-004: Quản lý Project & Phân quyền sở hữu tài nguyên (Epic A2)` — **ĐÃ HOÀN THÀNH & NGHIỆM THU**
- Feature ID / Spec path: `F-004` (`specs/004-project-management/spec.md`)
- Phase: `Feature Completed & Session Closed`
- Status: `completed`

## Kết quả đã được kiểm chứng

- **Mã nguồn và kiến trúc**: Đã hoàn thành 100% (36/36 tasks, 0 gaps sau `/speckit-converge`).
  - Tầng lưu trữ DynamoDB Enhanced Client: Entity `ProjectItem` với `@DynamoDbBean`, `@DynamoDbPartitionKey` (`projectId`), GSI `owner-index` (`ownerId` HASH, `createdAt` RANGE), hỗ trợ Soft Delete (`status = DELETED`, `deletedAt`).
  - Repository `ProjectRepository`: Thực hiện Query `owner-index` với `ScanIndexForward = false` (sắp xếp mới nhất lên đầu, không dùng DynamoDB Scan), lọc bỏ các project đã xóa mềm.
  - An toàn vòng đời Video: `VideoSafetyChecker` query GSI `project-index` trên bảng `videos` với `limit(1)` kiểm tra trạng thái in-flight (`UPLOADING`, `PENDING`, `PROCESSING`), trả về HTTP `409 Conflict` (`RESOURCE_IN_USE`) nếu còn video đang xử lý khi xóa Project.
  - Phân quyền sở hữu đa người thuê (Multi-tenant Isolation): Kiểm tra `ownerId` trong `ProjectService`, từ chối truy cập hoặc thao tác chéo với HTTP `403 Forbidden` (`ACCESS_DENIED`).
  - Controllers & Endpoints: Đầy đủ 5 endpoints RESTful (`POST /api/projects`, `GET /api/projects`, `GET /api/projects/{projectId}`, `PATCH /api/projects/{projectId}`, `DELETE /api/projects/{projectId}`), trích xuất an toàn `userId` từ `Authentication`.
  - ErrorCode mở rộng: `ACCESS_DENIED`, `RESOURCE_NOT_FOUND`, `RESOURCE_IN_USE`.
- **Kiểm thử đa tầng (Real Verification)**:
  - Unit tests: `ProjectServiceTest` (11 tests pass).
  - WebMvc slice tests: `ProjectControllerTest` (10 tests pass).
  - Integration test: `ProjectManagementIT` (1 test pass, kiểm thử vòng đời CRUD, GSI `owner-index`, safety guard và cô lập dữ liệu trên LocalStack 3.0 DynamoDB).
  - Postman collection: `backend/postman/F-004-project-management.postman_collection.json` (19 kịch bản kiểm thử toàn diện, độc lập cho F-004).
  - Feature Evidence: Đã nghiệm thu chính thức tại `quality/evidence/F-004-project-management.md` (16/16 Acceptance Criteria đạt PASS).
- **Zero Regression**: Lệnh chính thức `./scripts/verify.sh` (`./mvnw -B clean verify`) đạt PASS tuyệt đối (exit code 0, 68/68 tests pass, gồm 47 tests tích lũy F-001/F-002/F-003 + 21 tests mới F-004).

## Việc còn lại

- Không còn việc tồn đọng cho Feature F-004.

## Blocker / Quyết định cần con người

- Không có blocker. Tất cả bài test kỹ thuật và integration test đều pass 100%. Người dùng đã phê duyệt nghiệm thu hoàn tất Feature F-004.

## Verification gần nhất

| Lệnh | Kết quả | Thời điểm | Evidence |
|---|---|---|---|
| `./scripts/verify.sh` (`./mvnw -B clean verify`) | `PASS` (68 tests run, 0 failures) | `2026-10-31T20:54:00+07:00` | Zero Regression test suite pass (tích lũy toàn bộ features) |
| `./mvnw -B test -Dtest="ProjectManagementIT"` | `PASS` (1 test run, 0 failures) | `2026-10-31T20:52:00+07:00` | Real Verification Testcontainers LocalStack DynamoDB pass |
| `./mvnw -B test -Dtest="ProjectControllerTest"` | `PASS` (10 tests run, 0 failures) | `2026-10-31T20:50:00+07:00` | WebMvc slice tests pass |
| `./mvnw -B test -Dtest="ProjectServiceTest"` | `PASS` (11 tests run, 0 failures) | `2026-10-31T20:48:00+07:00` | Unit tests pass |
| `./scripts/check-setup.sh` | `PASS` (10 required files OK) | `2026-10-31T21:11:40+07:00` | Harness structure check pass |

## Hành động tiếp theo

1. Khởi động feature tiếp theo trong backlog: **`F-005: Quản lý API Key & Xác thực Dual Security JWT + X-API-Key (Epic A2)`**.
2. Thực thi lệnh Spec Kit: `/speckit-specify F-005`.
