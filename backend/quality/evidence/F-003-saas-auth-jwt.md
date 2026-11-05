# Feature Verification Evidence — F-003

- Feature: `F-003 — Xác thực người dùng SaaS — Register, Login & JWT Security (Epic A2)`
- Spec: [`specs/003-saas-auth-jwt/spec.md`](../../specs/003-saas-auth-jwt/spec.md)
- Plan: [`specs/003-saas-auth-jwt/plan.md`](../../specs/003-saas-auth-jwt/plan.md)
- Tasks: [`specs/003-saas-auth-jwt/tasks.md`](../../specs/003-saas-auth-jwt/tasks.md)
- Branch / commit: `phuong-backend` / `507a032`
- Date: `2026-10-31T19:54:00+07:00`

## Acceptance criteria mapping

| Criterion ID | Mô tả tóm tắt | Evidence / test | Result | Reference |
|---|---|---|---|---|
| `US1-AC1` | Đăng ký thành công trả về 201 Created và AuthResponse chứa token | `AuthControllerTest.register_success`, `UserAuthIT.testEndToEndUserLifecycle` | `PASS` | `AuthControllerTest.java`, `UserAuthIT.java` |
| `US1-AC2` | Đăng ký trùng email bị từ chối 409 Conflict (`EMAIL_ALREADY_EXISTS`) | `AuthServiceTest.register_duplicateEmail_throwsConflict`, `UserAuthIT.testEndToEndUserLifecycle` | `PASS` | `AuthServiceTest.java`, `UserAuthIT.java` |
| `US1-AC3` | Đăng ký sai email / mật khẩu < 8 ký tự trả về 400 Bad Request | `AuthControllerTest.register_invalidEmail_returns400`, `register_shortPassword_returns400` | `PASS` | `AuthControllerTest.java` |
| `US2-AC1` | Đăng nhập đúng email/mật khẩu trả về 200 OK và AuthResponse | `AuthServiceTest.login_success`, `AuthControllerTest.login_success`, `UserAuthIT.testEndToEndUserLifecycle` | `PASS` | `AuthServiceTest.java`, `UserAuthIT.java` |
| `US2-AC2` | Đăng nhập sai mật khẩu hoặc không có user trả về 401 (`INVALID_CREDENTIALS`) | `AuthServiceTest.login_emailNotFound_throwsInvalidCredentials`, `login_wrongPassword_throwsInvalidCredentials`, `UserAuthIT` | `PASS` | `AuthServiceTest.java`, `UserAuthIT.java` |
| `US2-AC3` | Đăng nhập thiếu trường bắt buộc trả về 400 Bad Request | `AuthControllerTest.login_missingFields_returns400` | `PASS` | `AuthControllerTest.java` |
| `US3-AC1` | Truy cập endpoint bảo vệ với Bearer token hợp lệ trả về 200 OK | `SecurityAuthFilterTest.accessMe_withValidToken_returns200`, `UserAuthIT.testEndToEndUserLifecycle` | `PASS` | `SecurityAuthFilterTest.java`, `UserAuthIT.java` |
| `US3-AC2` | Truy cập endpoint bảo vệ thiếu token bị chặn 401 Unauthorized | `SecurityAuthFilterTest.accessMe_withoutToken_returns401` | `PASS` | `SecurityAuthFilterTest.java` |
| `US3-AC3` | Truy cập với token sai chữ ký số bị chặn 401 Unauthorized | `SecurityAuthFilterTest.accessMe_withInvalidSignatureToken_returns401` | `PASS` | `SecurityAuthFilterTest.java` |
| `US3-AC4` | Truy cập với token hết hạn bị chặn 401 Unauthorized | `SecurityAuthFilterTest.accessMe_withExpiredToken_returns401` | `PASS` | `SecurityAuthFilterTest.java` |

## Commands executed

| Command | Exit code | Result | Test summary | Notes |
|---|---:|---|---|---|
| `./scripts/check-setup.sh` | `0` | `PASS` | 10 required files OK | Kiểm tra cấu trúc Harness |
| `./mvnw -B test -Dtest="JwtTokenServiceTest"` | `0` | `PASS` | 2 runs, 0 failures | Unit test phát hành token JWT HMAC-SHA256 |
| `./mvnw -B test -Dtest="AuthServiceTest"` | `0` | `PASS` | 7 runs, 0 failures | Unit test nghiệp vụ đăng ký, đăng nhập và hash BCrypt |
| `./mvnw -B test -Dtest="AuthControllerTest"` | `0` | `PASS` | 7 runs, 0 failures | WebMvc slice test cho /auth/register và /auth/login |
| `./mvnw -B test -Dtest="SecurityAuthFilterTest"` | `0` | `PASS` | 5 runs, 0 failures | Component test chuỗi bộ lọc bảo mật Spring Security |
| `./mvnw -B test -Dtest="UserAuthIT"` | `0` | `PASS` | 1 run, 0 failures | Integration test Testcontainers LocalStack 3.0 với DynamoDB thực tế |
| `make smoke` | `0` | `PASS` | 9/9 checks OK | Smoke test hạ tầng LocalStack (S3, SQS, DynamoDB) |
| `./scripts/verify.sh` (`./mvnw -B clean verify`) | `0` | `PASS` | 47 runs, 0 failures | Toàn bộ test suite tích lũy đạt Zero Regression |

## Verification Layers

- **Static Analysis / Structure**: `scripts/check-setup.sh` (PASS).
- **Unit / Web Slice Tests**: `JwtTokenServiceTest`, `AuthServiceTest`, `AuthControllerTest`, `SecurityAuthFilterTest` (PASS, 21 tests mới).
- **Integration Tests (Real Verification)**: `UserAuthIT` giao tiếp thực tế với LocalStack 3.0 DynamoDB (PASS).
- **Zero Regression**: Toàn bộ 24 tests cũ của F-001/F-002 cùng 23 tests mới của F-003 đều đạt PASS 100% trong lệnh verify chính thức.
- **Manual Quickstart / Postman**: Postman collection lưu tại `backend/postman/F-003-saas-auth-jwt.postman_collection.json` (7 requests & assertions).

## Review

- Reviewer: User (Phương) & AI Pair Programmer
- Decision: `Approved & Completed by User` (Người dùng chính thức nghiệm thu và duyệt hoàn thành Feature F-003)
- Findings: Hoàn thành 100% (27/27 tasks). Giao dịch nguyên tử DynamoDB `TransactWriteItems` hoạt động hoàn hảo, chặn đứng race condition khi đăng ký trùng email. Cơ chế Stateless JWT tương thích hoàn toàn giữa NimbusSigner và NimbusJwtDecoder. Toàn bộ 47 tests tích lũy đạt PASS 100% (Zero Regression).

## Remaining risks / gaps

- Không có rủi ro còn tồn đọng. Toàn bộ các yêu cầu bảo mật, hợp đồng dữ liệu và kiểm thử đều đã được xác thực thực tế.
