# Video state machine (DRAFT)

```
UPLOADING --(API: confirm)--> PENDING --(Worker nhận job)--> PROCESSING --> COMPLETED
                                                                  |
                                                                  +--> FAILED_VALIDATION | FAILED_TRANSCODE | FAILED_STORAGE
```

| Chuyển trạng thái | Ai ghi | Ghi chú |
|---|---|---|
| (tạo mới) -> UPLOADING | API | Khi cấp presigned URL |
| UPLOADING -> PENDING | API | Khi Client gọi confirm; đồng thời gửi SQS |
| PENDING / PROCESSING / FAILED_* -> PROCESSING | Worker | Nhận job (kể cả giao lại/retry). `ConditionExpression`: `status <> COMPLETED` |
| PROCESSING -> COMPLETED | Worker | Ghi `assets`; **COMPLETED là trạng thái cuối, không bị ghi đè** |
| PROCESSING -> FAILED_* | Worker | Ghi `errorCode`; vẫn có thể được retry qua SQS |

Quy tắc:
- Message SQS giao lại (at-least-once): Worker thấy video đã `COMPLETED` thì bỏ qua và xóa message.
- Mỗi lần Worker nhận job, tăng `attemptCount`.
- IAM: API chỉ ghi nhóm trạng thái tiếp nhận; Worker chỉ ghi nhóm trạng thái xử lý.
