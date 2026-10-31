# Engineering Standards

> Chỉ ghi các quy tắc đã được team xác nhận. Nêu nguồn hoặc lý do cho hard constraints quan trọng.

## Architecture

- Hard constraints: Chỉ dùng DynamoDB (AWS SDK v2 Enhanced Client), không dùng RDBMS/JPA (`../docs/adr/0001-dynamodb-only.md`). Không nhận/trung chuyển luồng byte video qua Backend.
- Patterns được ưu tiên: `Controller -> Service -> Repository / AWS Adapter`; tách biệt API DTO (`record`) và DynamoDB Entity (`@DynamoDbBean`) bằng MapStruct.
- Anti-patterns cần tránh: Dùng DynamoDB `Scan` thay cho `Query` theo Partition Key/GSI; giả lập offset pagination thay vì dùng cursor `LastEvaluatedKey`; để lộ cấu trúc bảng DynamoDB trực tiếp ra REST response.

## Code conventions

- Naming/style: Chuẩn Java 21; dùng Lombok cho Entity/Model mutable và Java `record` cho DTO bất biến; cấu hình ứng dụng bằng YAML (`application.yml`).
- Error handling: Xử lý tập trung qua `@RestControllerAdvice`, không lộ stack trace hoặc chi tiết hạ tầng AWS nội bộ ra client.
- Logging/observability: Không log password, raw API key, JWT secret hoặc Presigned URL đầy đủ. Đính kèm `videoId`/`projectId`/`jobId` khi log nghiệp vụ.
- Dependency policy: Quản lý phiên bản qua Spring Boot BOM (`3.4.5`) và Spring Cloud AWS BOM (`3.3.0`).

## API / data contracts

- API conventions: Khớp `../contracts/openapi.yaml` (dưới context path `/api`).
- Compatibility/versioning: Mọi thay đổi API schema, SQS message (`schemaVersion`), DynamoDB key hoặc S3 key phải cập nhật `../contracts/` trước.
- Validation: Sử dụng Jakarta Bean Validation (`@Valid`) tại Controller kết hợp kiểm tra giới hạn upload (`MAX_UPLOAD_BYTES`) tại Service.

## Security and privacy

- Authentication/authorization: Hỗ trợ JWT (SaaS Web) và SHA-256 API Key (Developer API); bắt buộc kiểm tra quyền sở hữu `projectId` / `ownerId`.
- Sensitive data handling: Mật khẩu băm bằng `BCrypt`; API Key chỉ lưu mã băm `SHA-256` trong DynamoDB.
- Secrets policy: Không commit `.env`, credentials hoặc secrets vào git hoặc đưa vào log.

## Rule metadata (khuyến nghị cho rule quan trọng)

| Rule | Source / Rationale | Applies to | Review condition |
|---|---|---|---|
| DynamoDB Only | `../docs/adr/0001-dynamodb-only.md` | Toàn bộ tầng Persistence | Khi có yêu cầu thay đổi kiến trúc lưu trữ |
| Contract-First | `../contracts/README.md` | REST API, SQS, DynamoDB, S3 keys | Mỗi khi thêm/sửa endpoint hoặc message |
