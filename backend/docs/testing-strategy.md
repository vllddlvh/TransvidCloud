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

## Zero Regression & Real Verification Policy

1. **Zero Regression ("Everything in the past was okay, so now it must be okay"):**
   - Mọi feature mới khi hoàn thành tuyệt đối không được làm hỏng hay suy giảm bất kỳ tính năng nào đã xây dựng trước đó.
   - Toàn bộ test suite tích lũy (bao gồm cả Unit Tests và Integration Tests của tất cả các feature quá khứ) bắt buộc phải chạy lại và **PASS 100%** trong lệnh verify chính thức `./scripts/verify.sh` (`./mvnw -B clean verify`). Nếu có bất kỳ test cũ nào fail, feature mới bị coi là **FAIL/REGRESSION** và không được phép nghiệm thu.

2. **Real Verification (Tiêu chuẩn kiểm chứng thực tế tối thiểu):**
   - Unit Test với Mockito/MockBean chỉ là công cụ hỗ trợ cục bộ trong quá trình phát triển (WIP), không phản ánh đầy đủ tích hợp hạ tầng và hành vi dữ liệu thực tế.
   - Để được công nhận là **Real Verification** cho một Feature:
     - **Tối thiểu phải có Integration Test (`*IT.java`)** giao tiếp trực tiếp với các dịch vụ thực tế (DynamoDB, S3, SQS trên LocalStack / Testcontainers), kiểm tra đúng schema, luồng dữ liệu và transaction thực.
     - **E2E / System Test**: Đối với các luồng liên module (Upload -> S3 -> Confirm -> SQS -> Worker -> Video HLS), bắt buộc phải có kiểm chứng E2E toàn stack (`make e2e` hoặc Postman Newman runner) để chứng minh tính đúng đắn trọn vòng đời.
   - Tuyệt đối không chấp nhận một Feature nghiệp vụ chỉ dựa vào Unit test cô lập làm bằng chứng hoàn thành.

## CI/CD quality gates

- Required checks trước merge: GitHub Actions workflow `ci.yml` (`cd backend && ./mvnw -B verify`).
- Coverage policy (nếu có): Đảm bảo các luồng nghiệp vụ P0 và kiểm tra quyền sở hữu tài nguyên đều có Unit/API test.
- Static/security gates: Không hard-code credentials; pass `./scripts/verify.sh`.
- Human approver / CODEOWNERS: Phương (Backend) và review chéo khi sửa `contracts/`.

## Quy tắc báo cáo

- Ghi chính xác command, exit status và kết quả.
- Phân biệt `PASS`, `FAIL`, `NOT RUN`, `BLOCKED`.
- Không coi AI review hoặc Spec Kit Converge là thay thế cho automated tests.
