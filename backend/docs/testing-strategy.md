# Testing and Verification Strategy

## Mục tiêu

Xác định bằng chứng cần có để kết luận một thay đổi đúng với acceptance criteria và không gây regression.

## Test layers

| Layer | Mục đích | Khi nào cần | Project command / evidence |
|---|---|---|---|
| Format / lint / type-check | Kiểm tra biên dịch Java 21 và annotation processors (Lombok, MapStruct) | Mọi thay đổi code | `./mvnw -B test-compile` |
| Unit tests | Kiểm tra business logic, validation, ownership isolation cô lập (Mockito) | Mọi thay đổi ở Service/Domain/Mapper | `./mvnw -B test` |
| Component / web slice tests | Kiểm tra Controller, HTTP status, validation và Security Filter (`@WebMvcTest`) | Khi thêm/sửa REST endpoint hoặc cấu hình bảo mật | `./mvnw -B test` |
| Integration / contract tests | Kiểm tra tương tác DynamoDB, S3, SQS với LocalStack / Testcontainers (`*IT.java`) | Khi thay đổi Repository, AWS Adapter hoặc contract | `./mvnw -B verify` |
| E2E / system tests | Kiểm tra luồng upload -> confirm -> SQS -> Worker -> HLS | Khi kiểm thử tích hợp toàn stack | `(cd .. && make e2e)` |
| Static/security analysis | Kiểm tra cấu trúc harness và không lộ secret | Trước khi kết thúc task/PR | `./scripts/check-setup.sh && ./scripts/verify.sh` |

Không yêu cầu mọi test layer cho mọi task. Chọn dựa trên code path, rủi ro và acceptance criteria.

## Verification mapping

Mỗi acceptance criterion phải có ít nhất một cách kiểm chứng hoặc được ghi rõ lý do chưa thể tự động hóa.

| Acceptance criterion | Test / check | Result | Evidence |
|---|---|---|---|
| Project build & context load thành công | `./mvnw -B clean verify` | `PASS` | `TransvidCloudBackendApplicationTests` |
| Ứng dụng khởi chạy và truy cập được trên `localhost:8080/api/actuator/health` | `curl -i http://localhost:8080/api/actuator/health` | `PASS` | HTTP 200 `{"status":"UP"}` |

## CI/CD quality gates

- Required checks trước merge: GitHub Actions workflow `ci.yml` (`cd backend && ./mvnw -B verify`).
- Coverage policy (nếu có): Đảm bảo các luồng nghiệp vụ P0 và kiểm tra quyền sở hữu tài nguyên đều có Unit/API test.
- Static/security gates: Không hard-code credentials; pass `./scripts/verify.sh`.
- Human approver / CODEOWNERS: Phương (Backend) và review chéo khi sửa `contracts/`.

## Quy tắc báo cáo

- Ghi chính xác command, exit status và kết quả.
- Phân biệt `PASS`, `FAIL`, `NOT RUN`, `BLOCKED`.
- Không coi AI review hoặc Spec Kit Converge là thay thế cho automated tests.
