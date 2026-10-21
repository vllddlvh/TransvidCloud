# 6. Data Model & Access Patterns

## Quy tắc

TransVidCloud dùng DynamoDB cho metadata và trạng thái. Thiết kế truy vấn dựa trên **access patterns**, không mặc định suy nghĩ theo JPA/SQL hoặc dùng scan để thay thế key/index phù hợp.

Schema chính thức nằm tại [`../contracts/dynamodb-tables.md`](../contracts/dynamodb-tables.md); file đó hiện là DRAFT.

## Bảng hiện mô tả trong contract

| Bảng | Mục đích |
|---|---|
| `users` | Định danh user và tra cứu email |
| `projects` | Project, owner và cấu hình tích hợp |
| `apikeys` | API key hash và project liên quan |
| `videos` | Video metadata, trạng thái, attempt count và assets |

Các key/index hiện khai báo trong contract phải được đọc trực tiếp từ `dynamodb-tables.md`; không sao chép key schema vào đây để tránh lệch phiên bản.

## Access patterns Backend cần hỗ trợ

- Tìm user khi đăng nhập.
- Liệt kê project theo owner.
- Xác thực API key và xác định project tương ứng.
- Liệt kê video theo project có phân trang.
- Lấy chi tiết video theo ID sau khi xác minh quyền truy cập.
- Cập nhật trạng thái/attempt/assets theo điều kiện hợp lệ.

## Implementation rules

- Dùng cursor dựa trên `LastEvaluatedKey` theo contract; không giả lập offset pagination.
- Cập nhật trạng thái bằng conditional write khi cần bảo vệ invariant.
- `COMPLETED` là trạng thái cuối theo state-machine contract.
- Không log password, raw API key, presigned URL hoặc secrets.
- Phân biệt DTO, domain logic và DynamoDB item; không để cấu trúc lưu trữ vô tình trở thành API contract.

Nếu access pattern mới cần GSI/key schema mới, cập nhật contract và các cấu hình local/Terraform liên quan trong cùng thay đổi.
