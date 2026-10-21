# 2. Business Domain

## Khái niệm chính

| Khái niệm | Ý nghĩa nghiệp vụ |
|---|---|
| **User** | Người sử dụng SaaS; sở hữu project theo mô hình cơ bản. |
| **Project** | Không gian tổ chức video và phạm vi gắn API key. |
| **API Key** | Credential để ứng dụng ngoài gọi Developer API; chỉ lưu hash theo data contract. |
| **Video** | Metadata của video gốc, project sở hữu, trạng thái và assets. |
| **Processing job** | Yêu cầu xử lý video được gửi qua SQS; không mặc định là một bảng DynamoDB riêng. |
| **Asset** | Đầu ra như HLS 360p/720p và thumbnail; được liên kết với video. |

Quan hệ logic: `User 1—N Project`; `Project 1—N Video`; `Project 1—N API Key`; `Video 1—N Asset` (logical relationship, không đồng nghĩa mỗi loại phải là bảng riêng).

## Use case Backend

1. Người dùng đăng ký/đăng nhập.
2. Tạo và liệt kê project thuộc quyền truy cập.
3. Tạo API key cho project theo contract đã chốt.
4. Khởi tạo video và nhận presigned URL.
5. Client upload trực tiếp lên S3 rồi gọi confirm.
6. Backend đưa job vào SQS; Worker xử lý bất đồng bộ.
7. Client truy vấn chi tiết/trạng thái/assets và lấy thông tin phát/nhúng video.

## Quy tắc nghiệp vụ nền tảng

- Video thuộc một project; quyền truy cập video phải được xác minh qua project sở hữu.
- Biết `projectId` hoặc `videoId` không tự tạo ra quyền truy cập.
- API chỉ đánh dấu upload hợp lệ sau khi xác minh object theo quy định trong contract.
- Video `COMPLETED` không bị Worker ghi đè thành trạng thái khác.
- Một job có thể được giao lại; xử lý cần tính đến duplicate delivery.
- User/project sharing, team membership và RBAC nâng cao **không mặc định nằm trong MVP**.

## Điểm cần chốt

- **TBD:** phạm vi API key theo project và endpoints được bảo vệ bằng JWT hay API key; OpenAPI hiện vẫn là DRAFT.
- **TBD:** hành vi chính xác khi confirm lặp hoặc khi cập nhật DynamoDB thành công nhưng gửi SQS thất bại.
