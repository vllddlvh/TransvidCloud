# 5. API Design & Contracts

## Nguồn sự thật

[`../contracts/openapi.yaml`](../../contracts/openapi.yaml) là nguồn định nghĩa REST API chính thức (SSOT).

Danh sách các endpoint trong `openapi.yaml` (dưới base path `/api`):

| Method | Path | Xác thực | Mục đích |
|---|---|---|---|
| `POST` | `/auth/register` | Public | Đăng ký tài khoản SaaS mới (đảm bảo email duy nhất) |
| `POST` | `/auth/login` | Public | Đăng nhập, cấp JWT Access Token |
| `GET` | `/projects` | `Bearer JWT` | Liệt kê danh sách Project của user hiện tại |
| `POST` | `/projects` | `Bearer JWT` | Tạo Project mới |
| `GET` | `/projects/{projectId}` | `Bearer JWT` | Xem chi tiết một Project |
| `PATCH` | `/projects/{projectId}` | `Bearer JWT` | Cập nhật tên, mô tả hoặc `webhookUrl` của Project |
| `DELETE` | `/projects/{projectId}` | `Bearer JWT` | Xóa Project (từ chối nếu còn video đang xử lý) |
| `GET` | `/projects/{projectId}/api-keys` | `Bearer JWT` | Liệt kê các API Key của Project (ẩn `rawKey`) |
| `POST` | `/projects/{projectId}/api-keys` | `Bearer JWT` | Tạo API Key mới (trả `rawKey` một lần duy nhất) |
| `DELETE` | `/projects/{projectId}/api-keys/{keyHash}` | `Bearer JWT` | Thu hồi (xóa) API Key |
| `GET` | `/projects/{projectId}/videos` | `Bearer JWT` \| `X-API-Key` | Liệt kê video trong Project (phân trang `cursor` + `limit`) |
| `POST` | `/projects/{projectId}/videos` | `Bearer JWT` \| `X-API-Key` | Khởi tạo video (`UPLOADING`) và nhận S3 Presigned PUT URL 15 phút |
| `POST` | `/videos/{videoId}/confirm` | `Bearer JWT` \| `X-API-Key` | Xác nhận đã upload (`HeadObject` -> `PENDING` -> gửi SQS job; idempotent) |
| `GET` | `/videos/{videoId}` | `Bearer JWT` \| `X-API-Key` | Chi tiết video, `status`, `errorCode` và `assets` (HLS/thumbnail URLs) |
| `DELETE` | `/videos/{videoId}` | `Bearer JWT` \| `X-API-Key` | Xóa video khỏi Project (từ chối `409` nếu đang `PROCESSING`) |

Không tự thêm endpoint hoặc tự đổi body/status code khác với `openapi.yaml`. Nếu cần API mới, cập nhật contract và review theo quy trình trong [`../contracts/README.md`](../../contracts/README.md).

## Quy ước đã chuẩn hóa trong OpenAPI

- **Dual Security Scheme**:
  - `BearerAuth` (`Authorization: Bearer <jwt>`): Dùng cho SaaS Web trên toàn bộ các nhóm endpoint cần xác thực.
  - `ApiKeyAuth` (`X-API-Key: <raw_key>`): Dùng cho hệ thống bên ngoài (Developer Platform), chỉ được phép thao tác trên nhóm endpoint Video thuộc đúng `projectId` gắn với API Key đó.
- **Idempotency cho `POST /videos/{videoId}/confirm`**:
  - Lần đầu xác nhận thành công (`UPLOADING -> PENDING`): trả `202 Accepted`.
  - Gọi lặp lại khi video đã ở `PENDING`, `PROCESSING` hoặc `COMPLETED`: trả `200 OK` kèm trạng thái hiện tại, không bắn trùng message vào SQS.
- **Chuẩn hóa `assets` trong `VideoResponse`**:
  - DynamoDB lưu S3 key (`{projectId}/{videoId}/hls/master.m3u8`, ...), còn API trả về đường dẫn phát đã ghép sẵn `TVC_MEDIA_BASE_URL` (`masterPlaylistUrl`, `hls360Url`, `hls720Url`, `thumbnailUrl`).
- **Error Model chuẩn (`ErrorResponse`)**:
  - Mọi lỗi `4xx`/`5xx` trả về `{ "code", "message", "timestamp", "details" }`.

## Quy trình thay đổi

1. Sửa contract trước hoặc cùng PR với code thích ứng.
2. Khi đổi API/message có ảnh hưởng thành phần khác, báo các bên liên quan review.
3. Không để Frontend/Worker dựa vào response chưa được ghi trong contract.
4. Thêm/cập nhật test để xác nhận implementation khớp contract.
