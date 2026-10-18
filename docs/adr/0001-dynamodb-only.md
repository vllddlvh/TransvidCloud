# ADR 0001: Chỉ dùng DynamoDB làm CSDL

**Trạng thái:** Chấp nhận

## Bối cảnh
Hệ thống có Backend (Spring Boot) và Worker (Go) cùng đọc/ghi metadata video, user, project, API key.

## Quyết định
Dùng DynamoDB (on-demand) cho toàn bộ dữ liệu. Không dùng PostgreSQL/RDS.

## Lý do
- Truy vấn đơn giản, cố định, không cần join hay báo cáo ad-hoc.
- Worker cập nhật trạng thái bằng `UpdateItem` có điều kiện, khớp thiết kế chống ghi đè `COMPLETED`.
- Worker chỉ cần IAM role, không cần chuỗi kết nối/Security Group; giữ được mạng đơn giản (không NAT, không private subnet cho DB).
- Chi phí gần 0 ở quy mô demo; LocalStack hỗ trợ sẵn khi chạy local.

## Hệ quả
- Phải thiết kế key/GSI theo access pattern (`contracts/dynamodb-tables.md`).
- Email duy nhất cần giao dịch ghi hai item.
- Nếu sau này cần báo cáo phức tạp: xuất dữ liệu sang Athena thay vì thêm CSDL quan hệ.

## Phương án đã loại
PostgreSQL/RDS cho tất cả; mô hình lai (Postgres + DynamoDB) vì tăng gấp đôi migration, test và nguy cơ sai lệch dữ liệu.
