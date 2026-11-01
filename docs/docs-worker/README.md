# docs-worker — Tài liệu dịch vụ Worker

> **Phụ trách:** Vũ (nhóm App) · **Thư mục code:** `worker/`
> **Công nghệ:** Go + FFmpeg/ffprobe, chạy trong Docker trên EC2 (ASG, Spot)
> **Nguồn sự thật:** `docs/contracts/` — khi code và contract lệch nhau, contract đúng, code sai.

Tài liệu riêng của dịch vụ Worker — bên nhận job từ SQS, transcode video bằng FFmpeg và ghi trạng thái vào DynamoDB.

| File | Nội dung | Người đọc chính |
|---|---|---|
| [[1. Introduction]] | Worker là gì, phạm vi trách nhiệm, tech stack, nguyên tắc | Cả nhóm |
| [[2. Business]] | Luồng nghiệp vụ: vòng đời job, các nhánh lỗi, retry/DLQ, quy tắc nghiệp vụ | Cả nhóm, báo cáo |
| [[3. Architecture]] | Kiến trúc nội bộ Go, lệnh FFmpeg, cấu hình, IAM, SIGTERM/Spot, observability | Vũ, Nguyên, Lưu |
| [[4. Timelines]] | Kế hoạch từ tuần 7 đến hạn nộp tuần 10; A4/A5, phối hợp C5/A8 và báo cáo | Vũ, cả nhóm |
| [[5. Contact docs]] | Hợp đồng giao tiếp của worker: SQS, DynamoDB, S3, log, metric, biến môi trường | Phương, Lưu, Nguyên |

## Tài liệu liên quan

- `docs/contracts/` — hợp đồng tích hợp (state machine, SQS schema, S3 key, DynamoDB, upload limits)
- `docs/introduction/1. Timelines.md` — epic và phân việc chung của cả nhóm
- `docs/docs-common/5. Architecture decisions.md` — lý do chỉ dùng DynamoDB (ADR 0001)

## Trạng thái

`DRAFT` — viết theo `docs/contracts/` hiện tại, cập nhật linh hoạt theo tiến độ; các phần DRAFT được review chung trước M1. Chỗ nào contract chưa chốt sẽ ghi rõ **(chờ chốt)**.
