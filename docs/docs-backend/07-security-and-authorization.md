# 7. Security & Authorization

## Authentication

- **SaaS Web (JWT)**: Sử dụng Access Token phi trạng thái (Stateless Bearer JWT) ký bằng thuật toán `HMAC-SHA256` với khóa bí mật cấu hình từ `tvc.security.jwt.secret` (tối thiểu 32 ký tự). Thời hạn mặc định là 86.400 giây (24 giờ), cấp phát qua `POST /api/auth/register` và `POST /api/auth/login`.
- **Bảo mật mật khẩu**: Mật khẩu người dùng được băm một chiều an toàn bằng `BCryptPasswordEncoder` (độ mạnh 10 rounds) trước khi lưu vào DynamoDB; tuyệt đối không lưu hoặc để lộ mật khẩu gốc.
- **Tính duy nhất của Email**: Áp dụng giao dịch nguyên tử DynamoDB `TransactWriteItems` ghi đồng thời User Item (`usr_...`) và Email Lock Item (`EMAIL#<normalized-email>`) với điều kiện `attribute_not_exists(userId)`, ngăn chặn triệt để race condition khi đăng ký trùng email.
- **Developer API**: Xác thực qua tiêu đề `X-API-Key` với giá trị hash SHA-256 lưu trong bảng `apikeys` (triển khai tại Feature F-005).
- **Phạm vi bảo vệ Endpoint**:
  - Công khai (Public): `/api/auth/register`, `/api/auth/login`, `/api/actuator/**`, `/api/swagger-ui/**`.
  - Bảo mật (Secured): Toàn bộ các endpoint còn lại (bao gồm `/api/auth/me`, `/api/projects/**`, `/api/videos/**`) bắt buộc phải có Bearer JWT hoặc API Key hợp lệ.

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
