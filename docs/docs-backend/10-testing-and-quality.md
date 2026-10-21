# 10. Testing & Quality

## Các lớp test

| Loại | Kiểm chứng |
|---|---|
| Unit | Business rules, validation và authorization logic |
| API | HTTP status/body, validation và error handling theo OpenAPI |
| Integration | DynamoDB/S3/SQS adapter với môi trường test/local phù hợp |
| Contract | Khớp `openapi.yaml`, SQS schema, state machine và upload limits |
| E2E | Upload → confirm → SQS → Worker → output → trạng thái/assets |

Repo hiện có `tests/e2e/`; ưu tiên dùng lại kịch bản và tooling đã tồn tại thay vì tạo một bộ test trùng lặp.

## Các case Backend cần kiểm tra

- Đăng nhập sai và request không có credential.
- User/project không sở hữu tài nguyên được yêu cầu.
- Upload vượt giới hạn hoặc confirm khi object chưa có.
- Confirm lặp và lỗi enqueue SQS.
- Cursor pagination qua nhiều trang.
- Video `COMPLETED` không bị chuyển ngược trạng thái.
- Lỗi AWS được ánh xạ thành response phù hợp, không lộ nội bộ.
- SQS message và response API không lệch contract.

## Definition of Done cho task Backend

- Business behavior và authorization được kiểm thử.
- Test liên quan chạy thành công; lỗi/bỏ qua test phải nêu rõ.
- Contract được cập nhật nếu giao diện bên ngoài thay đổi.
- Docs được cập nhật nếu quyết định hoặc hành vi có ảnh hưởng tới nhóm.
- Không commit secrets; log không chứa credential/presigned URL đầy đủ.
