# Project Progress — Snapshot hiện tại

> File này là snapshot trạng thái mới nhất, không phải nhật ký lịch sử. Cập nhật/thay thế khi có thay đổi đáng kể hoặc kết thúc session. Không ghi lại toàn bộ prompt và tool calls.

## Cập nhật

- Thời điểm: `2026-10-31T23:30:00+07:00`
- Branch: `phuong-backend`

## Trạng thái tổng quan

- Current focus: `F-005: Quản lý API Key & Xác thực Dual Security JWT + X-API-Key (Epic A2)`
- Feature ID / Spec path: `F-005` (`specs/005-apikey-dual-auth/spec.md`)
- Phase: `Feature Completed & Clean Handoff`
- Status: `completed`

## Kết quả đã được kiểm chứng

- **Mã nguồn tính năng F-005 hoàn thành 100% (36/36 tasks)**:
  - Mã lỗi chuẩn: `ErrorCode.INVALID_KEY` (400 Bad Request khi vượt hạn ngạch 5 keys/project).
  - Bảo mật sinh khóa CSPRNG: `ApiKeyGenerator` sinh `rawKey` 41 ký tự (`tvc_live_...`), tính băm một chiều SHA-256 `keyHash` 64 ký tự hex, trích xuất `keyPrefix` hiển thị `tvc_live_xxxxxxxx...`.
  - Tầng dữ liệu DynamoDB: `ApiKeyItem` bean trên bảng `tvc-{env}-apikeys` (PK `keyHash`, GSI `project-index` PK `projectId`).
  - DTOs & Mappers: `CreateApiKeyRequest`, `ApiKeyCreatedResponse`, `ApiKeyMetadataResponse`, `ApiKeyListResponse` (record) và `ApiKeyMapper`.
  - Tầng Repository: `ApiKeyRepository` sử dụng `DynamoDbEnhancedClient` (`save`, `findByKeyHash`, `findByProjectId`, `countByProjectId`, `deleteByKeyHash`).
  - Tầng Service & Controller: `ApiKeyService` và `ApiKeyController` xử lý đầy đủ các nghiệp vụ POST/GET/DELETE, kiểm soát hạn mức 5 keys, xóa cứng khỏi DynamoDB, kiểm tra quyền sở hữu project `ownerId`.
  - Bộ lọc bảo mật kép: `ApiKeyPrincipal`, `ApiKeyAuthenticationToken`, `ApiKeyAuthenticationFilter` kiểm tra và xác thực `X-API-Key`, cô lập ranh giới Project (chặn 403 gọi chéo tenant, chặn 403 gọi endpoint cá nhân), ưu tiên Bearer JWT khi gửi đồng thời cả hai tiêu đề.
- **Real Verification & Zero Regression (89/89 automated tests PASS 100%)**:
  - `ApiKeyGeneratorTest`: 6 tests PASS.
  - `ApiKeyServiceTest`: 7 tests PASS.
  - `ApiKeyControllerTest`: 6 tests PASS.
  - `ApiKeyManagementIT`: 1 IT suite PASS (LocalStack 3.0 DynamoDB Testcontainers, kiểm thử toàn bộ vòng đời tạo key, GSI query, quota 5 keys, cô lập tenant, xóa cứng).
  - `DualSecurityAuthIT`: 1 IT suite PASS (LocalStack 3.0 + Spring Security, kiểm thử xác thực X-API-Key, Bearer JWT, ưu tiên JWT, chặn 401 key sai/thu hồi, chặn 403 gọi chéo hoặc gọi endpoint user).
  - Zero Regression: 81 Surefire unit/slice tests + 8 Failsafe IT tests = 89 tests đạt PASS 100% không một lỗi nào.
- **Postman Collection E2E**:
  - `backend/postman/F-005-apikey-dual-auth.postman_collection.json` với 25 kịch bản kiểm thử toàn diện, assert status code và dữ liệu phản hồi chi tiết.
- **Hồ sơ Feature Evidence & Phê chuẩn nghiệm thu**:
  - `quality/evidence/F-005-apikey-dual-auth.md` ghi nhận đầy đủ bằng chứng kiểm thử thực tế và ánh xạ 19 Acceptance Criteria.
  - Đã được người dùng chính thức review và phê chuẩn nghiệm thu hoàn thành (`completed`) vào lúc `2026-10-31T23:28:58+07:00`.
- **Convergence**:
  - Đạt trạng thái `converged` qua `/speckit-converge` (0 gaps, 0 findings, 100% artifacts và codebase đồng bộ tuyệt đối).

## Việc còn lại

1. Khởi động Feature tiếp theo trong backlog: **`F-006: Khởi tạo Upload Video & Cấp S3 Presigned PUT URL (Epic A3)`**.

## Blocker / Quyết định cần con người

- Không có blocker. F-005 đã hoàn thành, đóng lại và bàn giao sạch sẽ (Clean Handoff).

## Verification gần nhất

| Lệnh | Kết quả | Thời điểm | Evidence |
|---|---|---|---|
| `./scripts/verify.sh` (`./mvnw -B clean verify`) | `PASS` (89 tests run: 81 unit/slice + 8 IT, 0 failures) | `2026-10-31T22:43:40+07:00` | Zero Regression test suite pass toàn diện |
| `./mvnw -B test -Dtest="ApiKeyManagementIT,DualSecurityAuthIT"` | `PASS` (2 IT suites, 0 failures) | `2026-10-31T22:42:02+07:00` | Real Verification LocalStack Testcontainers |
| `./scripts/check-setup.sh` | `PASS` (10 required files OK) | `2026-10-31T22:41:02+07:00` | Harness structure check pass |

## Hành động tiếp theo

- Thực hiện Git commit đóng gói toàn bộ tính năng F-005.
- Sẵn sàng khởi động session phát triển cho `F-006`.
