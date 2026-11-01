# work/ — Nhật ký công việc Worker

Ghi kế hoạch và tiến độ thực tế theo tuần của phần Worker (Vũ). Mỗi tuần một file `Week<N>.md`.

Quy ước:

- Đánh số theo tuần làm việc của kế hoạch (Week 1 = Sprint 1); đối chiếu tuần học kỳ trong [[../4. Timelines]].
- Cập nhật bảng tiến độ **sau mỗi buổi làm**; mỗi mục hoàn thành phải kèm bằng chứng (lệnh đã chạy, log, output, ảnh chụp).
- Mọi thay đổi contract phải review với bên liên quan — không "sửa lặng" so với `docs/contracts/`.
- Tuần hiện tại: [[Week3]].

Danh sách:

- [[Week1]] — Sprint 1 / tuần 7: contract, POC FFmpeg/ffprobe, khung Go + LocalStack, bàn giao env/IAM.
- [[Week2]] — Sprint 2 / cuối tuần 7–8: pipeline hoàn chỉnh, heartbeat/retry/SIGTERM, chaos/DLQ, M2 AWS.
- [[Week3]] — Sprint 3+4 / tuần 9: đóng nợ M2/A4/A5, scale-in protection và scale 1→2→1 (M3), benchmark/chaos AWS, báo cáo và đóng băng (M4).
- [[Week3-AWS-runbook]] — thao tác M2/M3/M4 khi có môi trường AWS (chưa chạy; chỉ dùng khi có credential/hạ tầng thật).
