# Feature Verification Evidence — F-004

- Feature: `F-004 — Quản lý Project & Phân quyền sở hữu tài nguyên (Epic A2)`
- Spec: [`specs/004-project-management/spec.md`](../../specs/004-project-management/spec.md)
- Plan: [`specs/004-project-management/plan.md`](../../specs/004-project-management/plan.md)
- Tasks: [`specs/004-project-management/tasks.md`](../../specs/004-project-management/tasks.md)
- Branch: `phuong-backend`
- Date: `2026-10-31T20:54:00+07:00`

## Acceptance criteria mapping

| Criterion ID | Mô tả tóm tắt | Evidence / test | Result | Reference |
|---|---|---|---|---|
| `US1-AC1` | Tạo Project mới hợp lệ trả về 201 Created và ProjectResponse | `ProjectServiceTest.createProject_success`, `ProjectControllerTest.createProject_success`, `ProjectManagementIT` | `PASS` | `ProjectServiceTest.java`, `ProjectControllerTest.java`, `ProjectManagementIT.java` |
| `US1-AC2` | Tạo Project chỉ có tên (mô tả/webhook null) thành công | `ProjectServiceTest.createProject_success`, `ProjectControllerTest.createProject_success` | `PASS` | `ProjectServiceTest.java`, `ProjectControllerTest.java` |
| `US1-AC3` | Tên rỗng hoặc > 120 ký tự, webhook URL sai định dạng trả về 400 Bad Request | `ProjectControllerTest.createProject_invalidName_returns400`, `createProject_invalidWebhookUrl_returns400` | `PASS` | `ProjectControllerTest.java` |
| `US2-AC1` | Liệt kê danh sách project qua GSI owner-index sắp xếp mới nhất lên đầu (createdAt giảm dần) | `ProjectServiceTest.listProjects_success`, `ProjectControllerTest.listProjects_success`, `ProjectManagementIT` | `PASS` | `ProjectServiceTest.java`, `ProjectManagementIT.java` |
| `US2-AC2` | User mới chưa có project trả về mảng rỗng 200 OK | `ProjectServiceTest.listProjects_success`, `ProjectManagementIT` | `PASS` | `ProjectServiceTest.java`, `ProjectManagementIT.java` |
| `US2-AC3` | Xem chi tiết project thành công trả về 200 OK | `ProjectServiceTest.getProject_success`, `ProjectControllerTest.getProject_success`, `ProjectManagementIT` | `PASS` | `ProjectServiceTest.java`, `ProjectManagementIT.java` |
| `US2-AC4` | Xem chi tiết project không tồn tại trả về 404 Not Found (`RESOURCE_NOT_FOUND`) | `ProjectServiceTest.getProject_notFound_whenMissing`, `ProjectControllerTest.getProject_notFound` | `PASS` | `ProjectServiceTest.java`, `ProjectControllerTest.java` |
| `US3-AC1` | Cập nhật thông tin project thành công, làm mới `updatedAt` | `ProjectServiceTest.updateProject_success`, `ProjectControllerTest.updateProject_success`, `ProjectManagementIT` | `PASS` | `ProjectServiceTest.java`, `ProjectManagementIT.java` |
| `US3-AC2` | Gỡ bỏ webhook bằng null thành công | `ProjectServiceTest.updateProject_success`, `ProjectControllerTest.updateProject_success` | `PASS` | `ProjectServiceTest.java` |
| `US3-AC3` | Cập nhật tên rỗng hoặc webhook URL sai định dạng trả về 400 Bad Request | `ProjectControllerTest.createProject_invalidName_returns400` | `PASS` | `ProjectControllerTest.java` |
| `US3-AC4` | Cập nhật project đã xóa mềm trả về 404 Not Found (`RESOURCE_NOT_FOUND`) | `ProjectServiceTest.getProject_notFound_whenDeleted` | `PASS` | `ProjectServiceTest.java` |
| `US4-AC1` | Xóa project khi không còn video in-flight thực hiện Soft Delete 204 No Content | `ProjectServiceTest.deleteProject_success_whenNoActiveVideos`, `ProjectControllerTest.deleteProject_success`, `ProjectManagementIT` | `PASS` | `ProjectServiceTest.java`, `ProjectManagementIT.java` |
| `US4-AC2` | Xóa project khi còn video in-flight (`UPLOADING`, `PENDING`, `PROCESSING`) bị từ chối 409 Conflict (`RESOURCE_IN_USE`) | `ProjectServiceTest.deleteProject_conflict_whenHasActiveVideos`, `ProjectControllerTest.deleteProject_conflict`, `ProjectManagementIT` | `PASS` | `ProjectServiceTest.java`, `ProjectManagementIT.java` |
| `US4-AC3` | Xóa project không tồn tại hoặc đã xóa trả về 404 Not Found | `ProjectServiceTest.getProject_notFound_whenMissing`, `ProjectControllerTest.getProject_notFound` | `PASS` | `ProjectServiceTest.java`, `ProjectControllerTest.java` |
| `US5-AC1` | User B xem chi tiết project của User A bị từ chối 403 Forbidden (`ACCESS_DENIED`) | `ProjectServiceTest.getProject_accessDenied_whenOwnerMismatch`, `ProjectControllerTest.getProject_accessDenied`, `ProjectManagementIT` | `PASS` | `ProjectServiceTest.java`, `ProjectManagementIT.java` |
| `US5-AC2` | User B cập nhật hoặc xóa project của User A bị từ chối 403 Forbidden (`ACCESS_DENIED`) | `ProjectServiceTest.updateProject_accessDenied`, `ProjectServiceTest.deleteProject_accessDenied`, `ProjectManagementIT` | `PASS` | `ProjectServiceTest.java`, `ProjectManagementIT.java` |
| `US5-AC3` | User B liệt kê project không thấy project của User A | `ProjectManagementIT.testProjectFullLifecycleAndSafetyGuard` | `PASS` | `ProjectManagementIT.java` |

## Commands executed

| Command | Exit code | Result | Test summary | Notes |
|---|---:|---|---|---|
| `./scripts/check-setup.sh` | `0` | `PASS` | 10 required files OK | Kiểm tra cấu trúc Harness |
| `./mvnw -B test -Dtest="ProjectServiceTest"` | `0` | `PASS` | 11 runs, 0 failures | Unit test nghiệp vụ Project: CRUD, isolation, safety guard |
| `./mvnw -B test -Dtest="ProjectControllerTest"` | `0` | `PASS` | 10 runs, 0 failures | WebMvc slice test cho các endpoints /projects |
| `./mvnw -B test -Dtest="ProjectManagementIT"` | `0` | `PASS` | 1 run, 0 failures | Integration test Testcontainers LocalStack 3.0 với DynamoDB thực tế |
| `./scripts/verify.sh` (`./mvnw -B clean verify`) | `0` | `PASS` | 68 runs, 0 failures | Toàn bộ test suite tích lũy đạt Zero Regression |

## Verification Layers

- **Static Analysis / Structure**: `scripts/check-setup.sh` (PASS).
- **Unit / Web Slice Tests**: `ProjectServiceTest`, `ProjectControllerTest` (PASS, 21 tests mới).
- **Integration Tests (Real Verification)**: `ProjectManagementIT` giao tiếp trực tiếp với LocalStack 3.0 DynamoDB (PASS, kiểm thử trọn vẹn CRUD, GSI `owner-index`, chặn xóa khi có video `PROCESSING`, Soft Delete và Multi-tenant Isolation).
- **Zero Regression**: Toàn bộ 47 tests cũ của F-001/F-002/F-003 cùng 21 tests mới của F-004 (tổng cộng 68 tests) đều đạt PASS 100% trong lệnh verify chính thức.
- **Manual Quickstart / Postman**: Postman collection lưu tại `backend/postman/F-004-project-management.postman_collection.json` (8 requests & assertions).

## Review

- Reviewer: User (Phương) & AI Pair Programmer
- Decision: `Completed` (Đã được người dùng review và nghiệm thu chính thức vào lúc 2026-10-31T21:15:43+07:00)
- Findings: Hoàn thành 100% (36/36 tasks, 0 findings sau `/speckit-converge`). Tầng lưu trữ DynamoDB Enhanced Client với GSI `owner-index` hoạt động chính xác theo quy chuẩn không dùng Scan. Cơ chế Soft Delete (`status = DELETED`, `deletedAt`) và kiểm tra an toàn `VideoSafetyChecker` ngăn ngừa xóa khi có video in-flight đạt hiệu quả tuyệt đối. Phân quyền cô lập tài nguyên (`ownerId` isolation) bảo vệ an toàn dữ liệu đa người thuê. Toàn bộ 68 automated tests đều PASS (Zero Regression). Bộ Postman collection 19 requests hoàn chỉnh đã được tạo tại `backend/postman/F-004-project-management.postman_collection.json`.

## Remaining risks / gaps

- Không có rủi ro còn tồn đọng. Toàn bộ các yêu cầu chức năng, bảo mật và hợp đồng dữ liệu đã được kiểm chứng và nghiệm thu.
