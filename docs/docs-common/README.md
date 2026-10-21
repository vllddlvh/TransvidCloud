# docs-common — Tài liệu chung cho cả nhóm

Thư mục này là **điểm vào nhanh** trước khi đọc tài liệu chuyên sâu theo vai (`docs-backend/`, `docs-worker/`, `docs-cloud/`, `docs-frontend/`). Nội dung được rà soát theo repo và [runbook triển khai](../docs-cloud/4.%20Implement.md) (cập nhật **21/10/2026**, tuần 7/10).

| File | Đọc khi nào |
|---|---|
| [1. Overall system](1.%20Overall%20system.md) | Cần hiểu sản phẩm, kiến trúc, luồng upload → HLS, local vs AWS |
| [2. Implementation status](2.%20Implementation%20status.md) | Cần biết phần nào **đã có trong repo**, phần nào còn checklist |
| [3. Contracts and boundaries](3.%20Contracts%20and%20boundaries.md) | Sửa API, SQS, DynamoDB, S3; phân vai App / Cloud |
| [4. Timelines và điểm đồng bộ](4.%20Timelines.md) | Bàn giao giữa App và Cloud, mốc M0–M5 |
| [5. Architecture decisions](5.%20Architecture%20decisions.md) | Quyết định kiến trúc đã chốt (ADR); bản kiến trúc lịch sử chỉ để tham khảo |

**Nguồn điều hành tiến độ:** [Timeline chung v4](../introduction/1.%20Timelines.md) — không thay thế bằng checklist trong tài liệu nhóm nếu hai bên lệch nhau.

**Đặc tả sản phẩm đầy đủ:** [SRS highlevel](../introduction/3.%20SRS%20highlevel.md).

**Chạy local / lên cloud:** [LOCAL_AND_CICD](../introduction/2.%20LOCAL_AND_CICD.md).
