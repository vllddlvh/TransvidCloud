# 11. Backend Plan & Timeline

Kế hoạch này là workstream Backend, phải khớp với master timeline/plan dùng chung trong `../docs/`. Đây là trình tự và tiêu chí bàn giao, không thay thế lịch tổng thể của nhóm.

| Giai đoạn | Trọng tâm Backend | Deliverable / exit criteria |
|---|---|---|
| **Sprint 1 — Contract & foundation** | Chốt API/data/state assumptions; dựng Spring Boot skeleton và cấu hình cơ bản; chia module và test skeleton. | Contract có người review; project chạy được local; ranh giới module và các quyết định mở được ghi lại. |
| **Sprint 2 — Upload & dispatch** | Project/video metadata, presigned URL, confirm, DynamoDB và SQS producer. | Video upload thẳng S3; confirm tạo trạng thái/job đúng contract; luồng lỗi quan trọng có test. |
| **Sprint 3 — SaaS/API integration** | Auth/authorization, list/detail/status/assets, pagination và tích hợp Frontend/Worker. | User truy vấn đúng tài nguyên; cả flow upload → processing → result hoạt động qua API. |
| **Sprint 4 — Reliability & verification** | Sửa tích hợp, retry/idempotency cases, contract/e2e tests, lỗi và logging. | Demo ổn định; có bằng chứng test cho ownership, duplicate/failure cases và trạng thái/assets. |

## Dependencies

- Chốt OpenAPI và các contract liên quan trước khi nhiều module implement song song.
- S3/SQS/DynamoDB local foundation từ phía Cloud phải có để integration test.
- Worker phải tuân theo cùng message/state/S3 convention.
- Frontend cần schema/response API đã thống nhất.

## Rủi ro chính

- OpenAPI và các contract hiện còn DRAFT.
- Update DynamoDB và SendMessage không nguyên tử; confirm/recovery cần quyết định rõ.
- Authentication, ownership và luồng upload nếu hoàn thiện muộn sẽ chặn tích hợp.
- Baseline tổng dự án 43 person-days vượt capacity khả dụng ước tính; giữ P0 trước, cắt P1/P2 theo quyết định nhóm nếu tiến độ thực tế yêu cầu.

Mỗi sprint nên tích hợp sớm với ít nhất một module khác; không đợi tới cuối mới nối Backend với Worker/Frontend.
