# Feature Verification Evidence — F-002

- Feature: `F-002 — Nền tảng Common, AWS Clients, CORS & Xử lý lỗi chuẩn (Epic A1/A2)`
- Spec: [`specs/002-common-aws-foundation/spec.md`](../../specs/002-common-aws-foundation/spec.md)
- Plan: [`specs/002-common-aws-foundation/plan.md`](../../specs/002-common-aws-foundation/plan.md)
- Tasks: [`specs/002-common-aws-foundation/tasks.md`](../../specs/002-common-aws-foundation/tasks.md)
- Branch / commit: `phuong-backend` / `fdb4c35`
- Date: `2026-10-31T17:31:00+07:00`

## Acceptance criteria mapping

| Criterion ID | Mô tả tóm tắt | Evidence / test | Result | Reference |
|---|---|---|---|---|
| `US1-AC1` | Lỗi validation 400 có `code`, `message`, `timestamp`, `details` | `GlobalExceptionHandlerTest.shouldHandleValidationErrors` | `PASS` | `GlobalExceptionHandlerTest.java` |
| `US1-AC2` | Ngoại lệ nghiệp vụ `AppException` trả về đúng status 4xx và ErrorCode | `GlobalExceptionHandlerTest.shouldHandle*` (401, 403, 404, 409, 413, 422) | `PASS` | `GlobalExceptionHandlerTest.java` |
| `US1-AC3` | Ngoại lệ hệ thống trả 500 `INTERNAL_SERVER_ERROR`, ẩn stack trace | `GlobalExceptionHandlerTest.shouldHandleUnhandledExceptionSafely` | `PASS` | `GlobalExceptionHandlerTest.java` |
| `US2-AC1` | Tương thích LocalStack với DynamoDB, S3, SQS | `AwsClientsIT.should*` (LocalStack 3.0 container) | `PASS` | `AwsClientsIT.java` |
| `US2-AC2` | Hỗ trợ Cloud mode không endpoint tùy biến | `AwsConfigUnitTest.shouldCreateAwsBeansInCloudMode` | `PASS` | `AwsConfigUnitTest.java` |
| `US2-AC3` | Ký Presigned PUT URL 15 phút và upload thành công | `AwsClientsIT.shouldGeneratePresignedPutUrlAndUpload` | `PASS` | `AwsClientsIT.java` |
| `US3-AC1` | Preflight OPTIONS từ allowed origin trả về CORS headers | `CorsConfigTest.shouldAllowConfiguredOriginOnPreflight` | `PASS` | `CorsConfigTest.java` |
| `US3-AC2` | Chặn cấp header CORS cho unauthorized origin | `CorsConfigTest.shouldRejectDisallowedOriginOnPreflight` | `PASS` | `CorsConfigTest.java` |
| `US4-AC1` | Nạp cấu hình hợp lệ vào `TvcProperties` | `TvcPropertiesValidationTest.shouldLoadValidPropertiesSuccessfully` | `PASS` | `TvcPropertiesValidationTest.java` |
| `US4-AC2` | Fail-fast khi cấu hình sai (JWT secret < 32 ký tự, TTL <= 0, max-bytes <= 0) | `TvcPropertiesValidationTest.shouldFailWhen*` | `PASS` | `TvcPropertiesValidationTest.java` |

## Commands executed

| Command | Exit code | Result | Test summary | Notes |
|---|---:|---|---|---|
| `./scripts/check-setup.sh` | `0` | `PASS` | 10 required files OK | Kiểm tra cấu trúc Harness |
| `./mvnw -B test -Dtest="GlobalExceptionHandlerTest"` | `0` | `PASS` | 11 runs, 0 failures | WebMvc slice test cho lỗi toàn cục |
| `./mvnw -B test -Dtest="TvcPropertiesValidationTest"` | `0` | `PASS` | 4 runs, 0 failures | Unit test thẩm định Fail-fast |
| `./mvnw -B test -Dtest="CorsConfigTest"` | `0` | `PASS` | 2 runs, 0 failures | Unit test CORS headers & preflight |
| `./mvnw -B test -Dtest="AwsConfigUnitTest"` | `0` | `PASS` | 2 runs, 0 failures | Unit test AWS builder logic |
| `./mvnw -B test -Dtest="AwsClientsIT"` | `0` | `PASS` | 4 runs, 0 failures | Integration test Testcontainers LocalStack 3.0 (DynamoDB, S3, SQS) |
| `./scripts/verify.sh` (`./mvnw -B test`) | `0` | `PASS` | 20 runs, 0 failures | Test suite toàn bộ các Unit Tests |

## Verification Layers

- **Static Analysis / Structure**: `scripts/check-setup.sh` (PASS).
- **Unit / Web Slice Tests**: `GlobalExceptionHandlerTest`, `TvcPropertiesValidationTest`, `CorsConfigTest`, `AwsConfigUnitTest` (PASS, 19 tests).
- **Integration Tests**: `AwsClientsIT` với Testcontainers LocalStack 3.0 (PASS, 4 tests).
- **E2E Tests**: Chưa thực hiện (E2E toàn diện luồng video sẽ được chạy trong `F-007`/`F-009`).

## Review

- Reviewer: User & AI Pair Programmer
- Decision: `Approved & Completed by User` (Chính thức nghiệm thu và hoàn thành Feature F-002)
- Findings: Tất cả 20 tasks đã hoàn thành và kiểm chứng thành công. Không có lỗi phát sinh. Bộ Postman collection lưu tại `backend/postman/F-002-common-aws-cors-error-foundation.postman_collection.json`. Hạ tầng local (`make infra`) đã được kiểm chứng hoạt động hoàn hảo.

## Remaining risks / gaps

- Docker Engine 28+ yêu cầu cấu hình `api.version=1.44` trong `docker-java.properties` để tương thích với Testcontainers; đã được giải quyết và lưu trữ trong `src/test/resources/docker-java.properties`.
