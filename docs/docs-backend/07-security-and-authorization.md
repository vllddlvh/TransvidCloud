# 7. Security & Authorization

## Authentication

- SaaS: JWT theo định hướng hiện tại.
- Developer API: API key theo project được nêu trong OpenAPI draft.
- Security scheme, header chính xác, thời hạn JWT, refresh/logout và phạm vi endpoint phải được chốt trong OpenAPI; hiện còn **TBD**.

## Authorization và isolation

- Mỗi request đọc/sửa project, video hoặc asset phải kiểm tra quyền của principal hiện tại.
- Với video, kiểm tra quyền qua project sở hữu; không chỉ kiểm tra video ID có tồn tại.
- Không trả dữ liệu để lộ tài nguyên thuộc user/project khác.
- Không triển khai RBAC/team sharing phức tạp khi chưa được thêm vào scope.

## API keys và secrets

- Chỉ lưu hash API key theo data contract; không ghi raw key vào log.
- Secret chỉ hiển thị theo luồng tạo key đã được thống nhất; thu hồi key phải làm key mất hiệu lực.
- Không commit `.env`, AWS credentials hoặc secrets. Dùng cấu hình mẫu và cơ chế secret/config phù hợp với local/cloud.

## S3 và AWS permissions

- Bucket input/output không mở public tùy tiện.
- Presigned URL có thời hạn; không log URL đầy đủ vì URL chứa chữ ký.
- Backend dùng IAM Role/permissions tối thiểu khi chạy trên AWS; không hard-code access key.
- API chỉ cấp quyền upload và quản lý metadata; client upload trực tiếp S3, không proxy video qua Backend.

## Test bảo mật bắt buộc

- Thiếu/sai/hết hạn credential bị từ chối.
- User A không đọc/sửa project, video hoặc assets của user B.
- API key của project A không truy cập dữ liệu project B.
- Không lộ secrets trong response hoặc log.
