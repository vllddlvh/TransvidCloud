# Quickstart & Verification Guide: Nền tảng Common & AWS (F-002)

**Feature**: `002-common-aws-foundation` | **Ngày lập**: 2026-10-31 | **Trạng thái**: Hoàn thành (Design)

Tài liệu này cung cấp hướng dẫn chạy kiểm chứng thực tế cho Feature F-002, bao gồm đầy đủ cả hai lớp kiểm thử: **Unit Test** và **Integration Test**.

---

## 1. Yêu cầu Tiên quyết (Prerequisites)

- Java 21 LTS (`java -version` hiển thị OpenJDK 21 hoặc tương đương).
- Docker engine đang chạy (để phục vụ lớp Integration Test với Testcontainers LocalStack).
- Thư mục làm việc: `/home/phuong/workspace/TransvidCloud/backend`.

---

## 2. Kịch bản Kiểm chứng Đơn vị (Unit Tests)

### Mục tiêu
Kiểm tra tính đúng đắn của logic xử lý lỗi toàn cục (`GlobalExceptionHandler`), bộ thẩm định cấu hình (`TvcProperties`) và chính sách CORS mà không cần khởi động container hoặc mạng bên ngoài. Thời gian chạy dưới 10 giây.

### Các ca kiểm thử đơn vị cần có:
1. `TvcPropertiesValidationTest`:
   - Xác nhận cấu hình mặc định trong `application.yml` nạp thành công và vượt qua validation.
   - Thử nghiệm cấu hình sai (ví dụ `jwt.secret` < 32 ký tự, `presign-ttl-minutes <= 0`, `upload.max-bytes <= 0`) xác nhận ứng dụng dừng khởi động (Fail-fast).
2. `GlobalExceptionHandlerTest`:
   - Gửi payload JSON rỗng vào endpoint có `@Valid` -> Nhận HTTP `400` với mã `VALIDATION_ERROR` và danh sách trường lỗi trong `details`.
   - Gửi payload JSON sai cú pháp (`{bad-json`) -> Nhận HTTP `400` với mã `MALFORMED_JSON`.
   - Gọi endpoint ném `AppException(ErrorCode.UPLOAD_LIMIT_EXCEEDED)` -> Nhận HTTP `413` với mã `UPLOAD_LIMIT_EXCEEDED`.
   - Ném ngoại lệ chưa phân loại `RuntimeException("database error")` -> Nhận HTTP `500` với mã `INTERNAL_SERVER_ERROR`, tuyệt đối không lộ nội dung ngoại lệ hay stack trace.
3. `CorsConfigTest`:
   - Gửi HTTP request preflight `OPTIONS` có header `Origin: http://localhost:5173` -> Trả về `200 OK` kèm các header `Access-Control-Allow-Origin: http://localhost:5173` và các methods `GET, POST, PATCH, DELETE`.
   - Gửi HTTP request từ origin không hợp lệ (`http://malicious-site.com`) -> Không cấp phát header `Access-Control-Allow-Origin`.

### Lệnh thực thi Unit Test:
```bash
./mvnw -B test -Dtest="*Test"
```

**Kết quả mong đợi**:
```text
[INFO] Tests run: X, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 3. Kịch bản Kiểm thử Tích hợp (Integration Tests với Testcontainers LocalStack)

### Mục tiêu
Chứng minh các bean AWS SDK v2 (`DynamoDbEnhancedClient`, `S3Client`, `S3Presigner`, `SqsAsyncClient`) kết nối, xác thực và thực thi thành công các thao tác đọc/ghi thực tế với môi trường giả lập AWS (LocalStack).

### Các ca kiểm thử tích hợp (`AwsClientsIT`):
1. **Khởi động Container:** Kích hoạt `LocalStackContainer` (hỗ trợ `DYNAMODB`, `S3`, `SQS`).
2. **Kiểm tra DynamoDbEnhancedClient:**
   - Tạo một bảng thử nghiệm tạm thời trên LocalStack.
   - Lưu một item và truy vấn lại qua Partition Key, xác nhận client đọc/ghi trơn tru.
3. **Kiểm tra S3Client & S3Presigner:**
   - Tạo bucket `tvc-test-input` trên LocalStack.
   - `S3Presigner` ký một Presigned PUT URL có thời hạn 15 phút.
   - Dùng HTTP client đẩy một tập tin byte mẫu lên URL vừa ký.
   - `S3Client` gọi `headObject` và `getObject` kiểm tra tập tin vừa tải lên, xác nhận path-style access hoạt động chính xác.
4. **Kiểm tra SqsAsyncClient:**
   - Tạo một queue `tvc-test-jobs` trên LocalStack.
   - Gửi một thông điệp JSON mẫu vào hàng đợi.
   - Nhận lại thông điệp và xác nhận nội dung payload nguyên vẹn.

### Lệnh thực thi Integration Test:
```bash
./mvnw -B test -Dtest="*IT"
```
*(Hoặc chạy toàn bộ cả Unit Test và Integration Test qua pha verify)*:
```bash
./mvnw -B verify
```

**Kết quả mong đợi**:
```text
[INFO] Running com.transvidcloud.backend.common.aws.AwsClientsIT
...
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 4. Kịch bản Kiểm thử Thực nghiệm Trực tiếp (Live Endpoint Verification)

Sau khi ứng dụng khởi động (`./mvnw spring-boot:run`):

### 4.1. Kiểm tra Endpoint Lỗi 400 Validation
```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"invalid-email","password":"123"}'
```
**Phản hồi mong đợi**:
- HTTP Status: `400 Bad Request`
- Body JSON có `code`: `"VALIDATION_ERROR"` và `details` chứa lỗi của `email` và `password`.

### 4.2. Kiểm tra Preflight CORS
```bash
curl -i -X OPTIONS http://localhost:8080/api/projects \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Authorization, Content-Type"
```
**Phản hồi mong đợi**:
- HTTP Status: `200 OK`
- Header: `Access-Control-Allow-Origin: http://localhost:5173`
- Header: `Access-Control-Allow-Methods: GET,POST,PATCH,DELETE,OPTIONS`
