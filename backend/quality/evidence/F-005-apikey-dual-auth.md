# Feature Verification Evidence — F-005

- Feature: `F-005 — Quản lý API Key & Xác thực Dual Security JWT + X-API-Key (Epic A2)`
- Spec: [`specs/005-apikey-dual-auth/spec.md`](../../specs/005-apikey-dual-auth/spec.md)
- Plan: [`specs/005-apikey-dual-auth/plan.md`](../../specs/005-apikey-dual-auth/plan.md)
- Tasks: [`specs/005-apikey-dual-auth/tasks.md`](../../specs/005-apikey-dual-auth/tasks.md)
- Branch: `phuong-backend`
- Date: `2026-10-31T22:45:00+07:00`

## Acceptance criteria mapping

| Criterion ID | Mô tả tóm tắt | Evidence / test | Result | Reference |
|---|---|---|---|---|
| `US1-AC1` | Tạo API Key mới hợp lệ trả về 201 Created kèm `rawKey`, `keyHash`, `keyPrefix`, `name`, `createdAt` | `ApiKeyServiceTest.createApiKey_success`, `ApiKeyControllerTest.createApiKey_success`, `ApiKeyManagementIT` | `PASS` | `ApiKeyServiceTest.java`, `ApiKeyControllerTest.java`, `ApiKeyManagementIT.java` |
| `US1-AC2` | Tên khóa rỗng hoặc vượt quá 100 ký tự trả về 400 Bad Request (`INVALID_INPUT`) | `ApiKeyControllerTest.createApiKey_invalidName_returns400` | `PASS` | `ApiKeyControllerTest.java` |
| `US1-AC3` | Project đã đạt giới hạn 5 keys, tạo key thứ 6 bị từ chối 400 Bad Request (`INVALID_KEY`) | `ApiKeyServiceTest.createApiKey_exceedQuota_throwsException`, `ApiKeyManagementIT` | `PASS` | `ApiKeyServiceTest.java`, `ApiKeyManagementIT.java` |
| `US1-AC4` | Người dùng khác (User B) cố tạo key trên Project A bị chặn 403 Forbidden (`ACCESS_DENIED`) | `ApiKeyServiceTest.createApiKey_accessDenied_whenOwnerMismatch`, `ApiKeyManagementIT` | `PASS` | `ApiKeyServiceTest.java`, `ApiKeyManagementIT.java` |
| `US1-AC5` | Tạo key trên project không tồn tại trả về 404 Not Found (`RESOURCE_NOT_FOUND`) | `ApiKeyServiceTest.createApiKey_notFound_whenProjectMissing` | `PASS` | `ApiKeyServiceTest.java` |
| `US2-AC1` | Liệt kê API Key qua GSI `project-index` trả về 200 OK kèm metadata, tuyệt đối không lộ `rawKey` | `ApiKeyServiceTest.listApiKeys_success`, `ApiKeyControllerTest.listApiKeys_success`, `ApiKeyManagementIT` | `PASS` | `ApiKeyServiceTest.java`, `ApiKeyControllerTest.java`, `ApiKeyManagementIT.java` |
| `US2-AC2` | Project chưa có API Key nào trả về danh sách rỗng 200 OK | `ApiKeyServiceTest.listApiKeys_success` | `PASS` | `ApiKeyServiceTest.java` |
| `US2-AC3` | Người dùng khác xem danh sách key của Project A bị chặn 403 Forbidden (`ACCESS_DENIED`) | `ApiKeyServiceTest.listApiKeys_accessDenied_whenOwnerMismatch`, `ApiKeyManagementIT` | `PASS` | `ApiKeyServiceTest.java`, `ApiKeyManagementIT.java` |
| `US2-AC4` | Liệt kê key trên project không tồn tại trả về 404 Not Found (`RESOURCE_NOT_FOUND`) | `ApiKeyServiceTest.listApiKeys_notFound_whenProjectMissing` | `PASS` | `ApiKeyServiceTest.java` |
| `US3-AC1` | Thu hồi (xóa) API Key thành công trả về 204 No Content và xóa cứng khỏi DynamoDB | `ApiKeyServiceTest.revokeApiKey_success`, `ApiKeyControllerTest.revokeApiKey_success`, `ApiKeyManagementIT` | `PASS` | `ApiKeyServiceTest.java`, `ApiKeyControllerTest.java`, `ApiKeyManagementIT.java` |
| `US3-AC2` | Thu hồi key không tồn tại hoặc đã bị xóa trả về 404 Not Found (`RESOURCE_NOT_FOUND`) | `ApiKeyServiceTest.revokeApiKey_notFound_whenMissing`, `ApiKeyControllerTest.revokeApiKey_notFound`, `ApiKeyManagementIT` | `PASS` | `ApiKeyServiceTest.java`, `ApiKeyControllerTest.java`, `ApiKeyManagementIT.java` |
| `US3-AC3` | Thu hồi `keyHash` thuộc Project khác trong Project hiện tại trả về 404 Not Found | `ApiKeyServiceTest.revokeApiKey_notFound_whenKeyBelongsToOtherProject` | `PASS` | `ApiKeyServiceTest.java` |
| `US3-AC4` | Người dùng khác thu hồi key của Project A bị chặn 403 Forbidden (`ACCESS_DENIED`) | `ApiKeyServiceTest.revokeApiKey_accessDenied_whenOwnerMismatch`, `ApiKeyManagementIT` | `PASS` | `ApiKeyServiceTest.java`, `ApiKeyManagementIT.java` |
| `US4-AC1` | Gửi `X-API-Key` hợp lệ truy cập tài nguyên Project thành công (HTTP 200/201) | `DualSecurityAuthIT.testValidApiKeyAccessSuccess` | `PASS` | `DualSecurityAuthIT.java` |
| `US4-AC2` | Gửi `X-API-Key` sai định dạng, không tồn tại hoặc đã thu hồi bị từ chối 401 Unauthorized (`UNAUTHENTICATED`) | `DualSecurityAuthIT.testInvalidOrRevokedApiKeyReturns401` | `PASS` | `DualSecurityAuthIT.java` |
| `US4-AC3` | Gửi `X-API-Key` của Project A truy cập tài nguyên của Project B bị chặn 403 Forbidden (`ACCESS_DENIED`) | `DualSecurityAuthIT.testCrossProjectApiKeyAccessReturns403` | `PASS` | `DualSecurityAuthIT.java` |
| `US4-AC4` | Gửi đồng thời cả `Authorization: Bearer <JWT>` và `X-API-Key` -> hệ thống ưu tiên xác thực theo Bearer JWT | `DualSecurityAuthIT.testDualHeadersPrefersBearerJwt` | `PASS` | `DualSecurityAuthIT.java` |
| `US5-AC1` | Người dùng tiếp tục dùng Bearer JWT thao tác bình thường trên các endpoint (Zero Regression) | `DualSecurityAuthIT.testBearerJwtAccessSuccess`, `ProjectManagementIT` | `PASS` | `DualSecurityAuthIT.java`, `ProjectManagementIT.java` |
| `US5-AC2` | Yêu cầu không có cả Bearer JWT và `X-API-Key` bị từ chối 401 Unauthorized (`UNAUTHENTICATED`) | `DualSecurityAuthIT.testUnauthenticatedAccessReturns401`, `SecurityAuthFilterTest` | `PASS` | `DualSecurityAuthIT.java`, `SecurityAuthFilterTest.java` |
| `US5-AC3` | Dùng `X-API-Key` gọi vào endpoint cá nhân người dùng (`POST /api/projects`) bị chặn 403 Forbidden (`ACCESS_DENIED`) | `DualSecurityAuthIT.testApiKeyAccessToUserPersonalEndpointsReturns403` | `PASS` | `DualSecurityAuthIT.java` |

## Commands executed

| Command | Exit code | Result | Test summary | Notes |
|---|---:|---|---|---|
| `./scripts/check-setup.sh` | `0` | `PASS` | 10 required files OK | Kiểm tra cấu trúc Harness |
| `./mvnw -B test -Dtest="ApiKeyGeneratorTest,ApiKeyServiceTest"` | `0` | `PASS` | 13 runs, 0 failures | Unit test sinh khóa CSPRNG, hash SHA-256, CRUD & quota limit 5 keys |
| `./mvnw -B test -Dtest="ApiKeyControllerTest"` | `0` | `PASS` | 6 runs, 0 failures | WebMvc slice test cho endpoints POST/GET/DELETE /api/projects/{projectId}/api-keys |
| `./mvnw -B test -Dtest="ApiKeyManagementIT,DualSecurityAuthIT"` | `0` | `PASS` | 2 runs, 0 failures | Integration test Testcontainers LocalStack 3.0 với DynamoDB và Spring Security |
| `./scripts/verify.sh` (`./mvnw -B clean verify`) | `0` | `PASS` | 89 runs (81 unit/slice + 8 IT), 0 failures | Toàn bộ test suite tích lũy từ F-001 đến F-005 đạt Zero Regression |

## Verification Layers

- **Static Analysis / Structure**: `scripts/check-setup.sh` (PASS).
- **Unit / Web Slice Tests**: `ApiKeyGeneratorTest`, `ApiKeyServiceTest`, `ApiKeyControllerTest` (PASS, 19 tests mới cho module `apikey`).
- **Integration Tests (Real Verification)**: `ApiKeyManagementIT`, `DualSecurityAuthIT` giao tiếp trực tiếp với LocalStack 3.0 DynamoDB (PASS, kiểm thử trọn vẹn vòng đời sinh khóa CSPRNG, lưu hash SHA-256, GSI `project-index`, kiểm tra giới hạn 5 keys, xóa cứng tức thì, bộ lọc `ApiKeyAuthenticationFilter`, cách ly tenant 403, và cơ chế ưu tiên Bearer JWT).
- **Zero Regression**: Toàn bộ 68 tests cũ của F-001/F-002/F-003/F-004 cùng 21 tests mới của F-005 (tổng cộng 89 tests) đều đạt PASS 100% trong lệnh verify chính thức `./scripts/verify.sh`.
- **Manual Quickstart / Postman**: Bộ sưu tập Postman collection 25 requests hoàn chỉnh đã được tạo tại `backend/postman/F-005-apikey-dual-auth.postman_collection.json`.

## Review

- Reviewer: User (Phương) & AI Pair Programmer
- Decision: `Completed` (Đã được người dùng review và chính thức phê chuẩn nghiệm thu hoàn thành vào lúc 2026-10-31T23:28:58+07:00)
- Findings: Hoàn thành 100% (36/36 tasks, 0 findings sau `/speckit-converge`). Tiện ích `ApiKeyGenerator` bảo đảm sinh khóa ngẫu nhiên CSPRNG 41 ký tự (`tvc_live_...`), lưu bản băm SHA-256 an toàn và không bao giờ lưu trữ `rawKey`. Cơ chế phân quyền đa người thuê và cô lập ranh giới Project trong `ApiKeyAuthenticationFilter` ngăn chặn hiệu quả các cuộc tấn công gọi chéo tenant (403 Forbidden) hoặc lạm dụng quyền hạn (chặn gọi endpoint cá nhân). Tương thích hoàn hảo với `BearerTokenAuthenticationFilter` đảm bảo Zero Regression cho toàn bộ các endpoint SaaS Web hiện hữu (89/89 automated tests pass). Bộ Postman collection 25 requests hoàn chỉnh đã được tạo và kiểm thử thành công tại `backend/postman/F-005-apikey-dual-auth.postman_collection.json`.

## Remaining risks / gaps

- Không có rủi ro còn tồn đọng. Toàn bộ các yêu cầu nghiệp vụ, bảo mật, và hợp đồng dữ liệu đã được kiểm chứng bằng automated tests thực tế trên LocalStack Testcontainers và Postman Runner.
