# 11. Backend Plan & Timeline

Hiện tại **tuần 7**, học kỳ kết thúc và nộp BTL trong **tuần 10**. Kế hoạch Backend theo [timeline chung v4](../introduction/1.%20Timelines.md); sprint là giai đoạn ngắn trong tuần 7–9, không phải bốn tuần mới. Phương rà phần đã làm và chỉ trừ effort khi có code/kết quả kiểm chứng.

| Giai đoạn | Trọng tâm Backend | Deliverable / exit criteria |
|---|---|---|
| **Tuần 7 / Sprint 1 — Nền tảng** | Chốt API/data/state; dựng Spring Boot và cấu hình; bắt đầu auth/ownership, project/video để mở khóa frontend. | Contract được review; chạy local; bàn giao cổng/env/health và quyền AWS. |
| **Cuối tuần 7–8 / Sprint 2 — Upload và tích hợp AWS** | Hoàn thiện auth/ownership, presigned upload, confirm, DynamoDB/SQS; list/detail/status/assets và tích hợp Worker/Frontend. | M2 cuối tuần 8: pipeline AWS hoàn chỉnh; kiểm tra tài nguyên thuộc người dùng và các lỗi confirm; retry/idempotency cơ bản hoạt động. |
| **Đầu tuần 9 / Sprint 3 — Tích hợp edge** | Kiểm tra auth/header/cache qua CloudFront; hỗ trợ frontend, log/metric và scaling. | M3: SaaS/API chạy cùng origin; không chờ đến giai đoạn này mới làm auth. |
| **Phần còn lại tuần 9 / Sprint 4 — Kiểm chứng** | E2E, duplicate/failure/ownership cases, hỗ trợ benchmark/chaos; sửa lỗi và viết kết quả báo cáo. | M4: số liệu, báo cáo đầy đủ, demo hai lần và đóng băng phiên bản. |
| **Tuần 10 — Nộp bài** | Rà hướng dẫn API/source, liên kết và phiên bản nộp; chỉ sửa lỗi chặn nộp/demo. | M5: hồ sơ sẵn sàng đầu tuần, nộp đúng hạn và lưu xác nhận. |

## Dependencies

- Chốt OpenAPI và các contract liên quan trước khi nhiều module implement song song.
- S3/SQS/DynamoDB local foundation từ phía Cloud phải có để integration test.
- Worker phải tuân theo cùng message/state/S3 convention.
- Frontend cần schema/response API đã thống nhất.

## Rủi ro chính

- OpenAPI và các contract hiện còn DRAFT.
- Update DynamoDB và SendMessage không nguyên tử; confirm/recovery cần quyết định rõ.
- Authentication, ownership và luồng upload nếu hoàn thiện muộn sẽ chặn tích hợp.
- Baseline P0 toàn nhóm khoảng 34 pd, trong khi capacity tối đa tuần 7–9 ở mức 2 ngày/người/tuần là 19,2 pd; phải trừ phần đã làm rồi ước lượng lại. Webhook/C6 ngoài cam kết nộp; giữ luồng cốt lõi và kiểm chứng trước.

Mỗi sprint nên tích hợp sớm với ít nhất một module khác; không đợi tới cuối mới nối Backend với Worker/Frontend.
