# docs/ — Tài liệu TransVidCloud

**Lịch hiện hành: học kỳ 10 tuần, hiện tại tuần 7, nộp BTL trong tuần 10.** [Timeline chung v4](introduction/1.%20Timelines.md) là nguồn điều hành tiến độ của tất cả nhóm. Không tính một sprint là một tuần khi đọc các checklist cũ.

| Thời gian | Mốc chung |
|---|---|
| Phần còn lại tuần 7 | M0–M1: rà hiện trạng, contract, local/Docker, FFmpeg và nền tảng dữ liệu |
| Cuối tuần 8 | M2: pipeline video end-to-end trên AWS |
| Đầu tuần 9 | M3: SaaS/API/HLS qua CloudFront, scaling 1 → 2 |
| Cuối tuần 9 | M4: benchmark/chaos, báo cáo đầy đủ, hai lần diễn tập, đóng băng |
| Tuần 10 | M5: hồ sơ sẵn sàng từ đầu tuần, nộp đúng hạn và lưu xác nhận |

| Mục | Nội dung |
|---|---|
| **[Tài liệu chung (docs-common)](docs-common/README.md)** | **Tổng quan hệ thống, hiện trạng repo, hợp đồng, mốc đồng bộ — đọc trước khi vào tài liệu nhóm** |
| [Timeline chung](introduction/1.%20Timelines.md) | Phân công, capacity, phạm vi và mốc nghiệm thu |
| [Local và CI/CD](introduction/2.%20LOCAL_AND_CICD.md) | Đường đi local → AWS; CI/CD đầy đủ là P1 |
| [SRS](introduction/3.%20SRS%20highlevel.md) | Bài toán, yêu cầu và lịch tuần 7–10 tại mục 29 |
| [Cloud](docs-cloud/README.md) | Hạ tầng, thiết kế, triển khai và timeline |
| [Backend](docs-backend/11-plan-and-timeline.md) | Kế hoạch API, auth, upload và tích hợp |
| [Worker](docs-worker/4.%20Timelines.md) | Pipeline, retry, scaling và hỗ trợ thực nghiệm |
| [Frontend](docs-frontend/2.%20Timelines.md) | Upload, trạng thái, player và tích hợp SaaS |
| [Điểm đồng bộ (rút gọn)](docs-common/4.%20Timelines.md) | Bàn giao App ↔ Cloud và M0–M5 |
| [Hợp đồng tích hợp](contracts/README.md) | API, SQS, state, DynamoDB, S3 key, upload limits (DRAFT) |
| [Quyết định kiến trúc (ADR)](docs-common/5.%20Architecture%20decisions.md) | Quy ước ADR, quyết định đã chốt, tài liệu kiến trúc lịch sử |
| [Báo cáo](report/README.md) | Báo cáo tiến độ, báo cáo cuối và bằng chứng |

Báo cáo viết song song từ tuần 7; tuần 10 không dùng để bắt đầu tính năng hay thực nghiệm mới. Các mốc trong bản kiến trúc Cloud cũ và PDF báo cáo tiến độ là lịch sử, không thay thế timeline v4. Quyết định kiến trúc quan trọng vẫn cần ADR; thay đổi lịch điều hành cập nhật ở timeline chung và các tài liệu nhóm.
