# Contract: Structured Logging Output Format

**Version**: 1.0  
**Scope**: Backend Log Streams (Stdout / Container Driver / CloudWatch Logs)  
**Date**: 2026-11-01  

---

## 1. Single-Line JSON Schema (Khi `LOG_FORMAT=json` hoặc profile `docker`/`prod`)

Mỗi bản ghi log được xuất dưới dạng một dòng JSON duy nhất (Single-line JSON không chứa ký tự newline bên trong chuỗi):

```json
{
  "timestamp": "2026-11-01T17:50:00.123Z",
  "level": "INFO",
  "logger": "com.transvidcloud.backend.video.service.VideoService",
  "message": "Video upload confirmed and SQS job dispatched",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "usr_9f8e7d6c5b4a",
  "projectId": "prj_1a2b3c4d5e6f",
  "videoId": "vid_9876543210ab",
  "jobId": "job_fedcba098765"
}
```

### Các trường dữ liệu chuẩn:

| Tên Trường | Kiểu | Bắt buộc | Mô Tả |
|---|---|---|---|
| `timestamp` | `String` (ISO-8601 UTC) | Có | Thời điểm ghi log |
| `level` | `String` | Có | `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `logger` | `String` | Có | Tên class phát sinh log |
| `message` | `String` | Có | Thông điệp nghiệp vụ (đã qua bộ lọc masking) |
| `correlationId` | `String` | Có | Mã tương quan truy vết |
| `userId` | `String` | Tùy chọn | ID người dùng (nếu có trong ngữ cảnh) |
| `projectId` | `String` | Tùy chọn | ID dự án (nếu có trong ngữ cảnh) |
| `videoId` | `String` | Tùy chọn | ID video (nếu có trong ngữ cảnh) |
| `jobId` | `String` | Tùy chọn | ID công việc SQS (nếu có trong ngữ cảnh) |

---

## 2. Structured Console Pattern (Khi `LOG_FORMAT=text` hoặc profile `local`/`test`)

```text
2026-11-01 17:50:00.123 [main] INFO  c.t.b.video.service.VideoService [corr=550e8400-e29b-41d4-a716-446655440000 user=usr_9f8e7d6c5b4a prj=prj_1a2b3c4d5e6f vid=vid_9876543210ab] - Video upload confirmed and SQS job dispatched
```
