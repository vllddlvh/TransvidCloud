# Feature Backlog

> Một dòng cho mỗi feature. Cột Behavior mô tả outcome ngắn; Verification nêu lệnh hoặc tiêu chí cần chạy; Evidence trỏ tới CI run, PR, commit hoặc báo cáo khi có. Chi tiết yêu cầu/task nằm trong spec tương ứng.

| ID | Feature | Priority | State | Behavior / Outcome | Verification | Spec | Evidence |
|---|---|---|---|---|---|---|---|
| F-001 | Khởi tạo môi trường Spring Boot Backend | `high` | `verified` | Dựng khung Maven Wrapper, Spring Boot 3.4.5, Java 21, AWS SDK v2 (DynamoDB/S3/SQS), Security JWT, Lombok, MapStruct, Swagger UI; build và chạy thành công trên `localhost:8080/api` | `./mvnw -B clean verify` & `curl -f http://localhost:8080/api/actuator/health` | `-` | `./scripts/verify.sh` PASS |
| F-002 | Auth, Project & API Key Management (Epic A2) | `high` | `planned` | Đăng ký/đăng nhập JWT, tạo/liệt kê Project, tạo/xác thực API Key (SHA-256) trên DynamoDB | `./mvnw -B verify` | `-` | `-` |
| F-003 | Video Upload, Confirm & SQS Dispatch (Epic A3) | `high` | `planned` | Cấp S3 Presigned PUT URL (giới hạn 200MB), xác nhận qua `HeadObject`, chuyển `UPLOADING -> PENDING` và gửi job vào SQS | `./mvnw -B verify` | `-` | `-` |

## State definitions

- `planned`: nằm trong kế hoạch, chưa bắt đầu.
- `active`: đang được thực hiện.
- `blocked`: bị chặn; ghi lý do trong `PROGRESS.md` hoặc spec.
- `verified`: các verification bắt buộc đã thực sự pass, có evidence.
- `completed`: đã được con người chấp nhận theo quy trình review/merge.
- `cancelled`: đã loại khỏi phạm vi, ghi lý do trong ghi chú feature hoặc decision record.

## Quy tắc cập nhật

1. Không thêm feature chỉ vì agent nghĩ ra; cần yêu cầu hoặc quyết định của người có thẩm quyền.
2. Mỗi feature cần ID ổn định, không tái sử dụng ID đã hủy.
3. Khi feature bắt đầu, cập nhật state thành `active` và thêm đường dẫn spec nếu đã có.
4. Chỉ chuyển `active → verified` khi có bằng chứng kiểm tra.
5. Chỉ chuyển `verified → completed` khi đã có human acceptance/merge theo quy trình dự án.
6. Không đánh dấu `completed` khi còn acceptance criteria bắt buộc chưa đạt.
