# Research & Technical Decisions: Nền tảng Common, AWS Clients, CORS & Xử lý lỗi chuẩn (F-002)

**Feature**: `002-common-aws-foundation` | **Ngày lập**: 2026-10-31 | **Trạng thái**: Hoàn thành (Resolved)

Tài liệu này ghi nhận kết quả nghiên cứu, đánh giá các giải pháp kỹ thuật và đưa ra quyết định kiến trúc cụ thể cho Feature F-002 nhằm xây dựng nền tảng vững chắc cho toàn bộ backend của TransvidCloud.

---

## 1. Quản lý và Thẩm định Cấu hình Tập trung (`TvcProperties`)

### Quyết định kỹ thuật
- Sử dụng `@ConfigurationProperties(prefix = "tvc")` kết hợp `@Validated` (Jakarta Bean Validation) trên lớp cấu hình chính `TvcProperties`.
- Tổ chức các nhóm cấu hình con thành các lớp static/record nội bộ:
  - `Aws`: `endpoint`, `s3` (`inputBucket`, `outputBucket`, `presignEndpoint`, `presignTtlMinutes`), `sqs` (`jobsQueueName`), `dynamodb` (`tablePrefix`).
  - `Upload`: `maxBytes`, `allowedContentTypes`.
  - `Security`: `jwt` (`secret`, `expirationSeconds`), `cors` (`allowedOrigins`).
  - `Media`: `baseUrl`.
- Đặt các ràng buộc validation rõ ràng: `@NotNull`, `@NotBlank`, `@Min(1)`, `@Size(min = 32)` cho JWT secret.

### Lý do lựa chọn
- **Fail-fast:** Nếu người vận hành cấu hình sai (ví dụ: `JWT_SECRET` dưới 32 bytes không đủ an toàn cho HMAC-SHA256, hoặc `presign-ttl-minutes <= 0`), ứng dụng sẽ dừng khởi động ngay lập tức kèm thông báo lỗi rõ ràng.
- **Type-safe:** Giúp các service khác trong ứng dụng inject trực tiếp `TvcProperties` có kiểu dữ liệu tường minh, tránh sử dụng rải rác `@Value("${...}")` khó bảo trì và dễ lỗi chính tả.

### Các giải pháp thay thế đã xem xét
- *Dùng `@Value` rải rác trong từng Service:* Bị từ chối vì khó kiểm soát tập trung, không có cơ chế fail-fast khi khởi động, khó viết unit test.
- *Tự validate trong `@PostConstruct`:* Bị từ chối vì tốn code kiểm tra thủ công (boilerplate), không tận dụng được chuẩn declarative của Spring Boot Validation.

---

## 2. Khởi tạo và Đăng ký Beans AWS SDK v2 (`AwsConfig`)

### Quyết định kỹ thuật
- Xây dựng lớp `@Configuration` mang tên `AwsConfig` để khởi tạo và cung cấp 4 bean AWS SDK v2 chính thức:
  1. `DynamoDbClient` và `DynamoDbEnhancedClient`: Dùng cho mọi thao tác NoSQL trên 4 bảng DynamoDB (`users`, `projects`, `apikeys`, `videos`).
  2. `S3Client`: Dùng để kiểm tra `HeadObject` khi client xác nhận upload, và xóa video files khi có yêu cầu.
  3. `S3Presigner`: Dùng để sinh Presigned PUT URL có thời hạn 15 phút cho client tải video trực tiếp lên S3.
  4. `SqsAsyncClient`: Dùng để đẩy thông điệp job video vào hàng đợi SQS `tvc-{env}-jobs` bất đồng bộ.
- Cơ chế linh hoạt đa môi trường:
  - Nếu `tvc.aws.endpoint` được cấu hình (ví dụ `http://localhost:4566` cho LocalStack): Gọi `.endpointOverride(URI.create(endpoint))` trên tất cả các client builders, đồng thời kích hoạt `.forcePathStyle(true)` cho `S3Client` và `S3Presigner`.
  - Nếu `tvc.aws.endpoint` để trống hoặc chạy trên AWS Cloud: Sử dụng endpoint mặc định của AWS theo `Region` và cơ chế phân giải credentials chuẩn (IAM Role/Instance Profile).

### Lý do lựa chọn
- AWS SDK for Java v2 sử dụng non-blocking I/O và cấu trúc builder hiện đại, cung cấp `DynamoDbEnhancedClient` giúp ánh xạ đối tượng Java với DynamoDB items một cách an toàn và tinh gọn.
- Bắt buộc phải có `forcePathStyle(true)` đối với S3 khi làm việc với LocalStack (để URL có dạng `http://localhost:4566/{bucket}/{key}` thay vì subdomain ảo `{bucket}.localhost:4566`).

### Các giải pháp thay thế đã xem xét
- *Dùng hoàn toàn Spring Cloud AWS Auto-Configuration mặc định:* Bị từ chối vì Spring Cloud AWS đôi khi không tự động inject cấu hình endpoint tùy biến cho `S3Presigner` đúng cách khi chạy với LocalStack, dẫn đến Presigned URL bị sai host. Việc chủ động cấu hình bean với builder tường minh giúp kiểm soát 100% hành vi.

---

## 3. Xử lý Ngoại lệ và Chuẩn hóa Lỗi Toàn cục (`GlobalExceptionHandler`)

### Quyết định kỹ thuật
- Tạo lớp `@RestControllerAdvice` mang tên `GlobalExceptionHandler`.
- Định nghĩa DTO phản hồi lỗi `ErrorResponse` dưới dạng Java `record` bất biến:
  ```java
  public record ErrorResponse(
      String code,
      String message,
      Instant timestamp,
      Map<String, Object> details
  ) {}
  ```
- Định nghĩa enum `ErrorCode` chuẩn hóa toàn bộ các lỗi của hệ thống:
  - `VALIDATION_ERROR` (400 Bad Request)
  - `MALFORMED_JSON` (400 Bad Request)
  - `TYPE_MISMATCH` (400 Bad Request)
  - `UNAUTHENTICATED` (401 Unauthorized)
  - `UNAUTHORIZED` (403 Forbidden)
  - `NOT_FOUND` (404 Not Found)
  - `METHOD_NOT_ALLOWED` (405 Method Not Allowed)
  - `CONFLICT` (409 Conflict)
  - `UPLOAD_LIMIT_EXCEEDED` (413 Payload Too Large)
  - `UNSUPPORTED_MEDIA_TYPE` (422 Unprocessable Entity)
  - `INTERNAL_SERVER_ERROR` (500 Internal Server Error)
- Định nghĩa ngoại lệ nghiệp vụ nền tảng `AppException extends RuntimeException` nhận `ErrorCode` hoặc `message` và `details` tùy chọn.
- Xử lý các loại ngoại lệ:
  - `MethodArgumentNotValidException`: Trích xuất danh sách lỗi validation của từng trường (`field` -> `defaultMessage`) đưa vào `details`.
  - `HttpMessageNotReadableException`: Trả về `MALFORMED_JSON`.
  - `MethodArgumentTypeMismatchException`: Trả về `TYPE_MISMATCH`.
  - `HttpRequestMethodNotSupportedException`: Trả về `METHOD_NOT_ALLOWED`.
  - `AppException`: Trả về mã lỗi và thông điệp tương ứng của `ErrorCode`.
  - `Exception` chung: Ghi log cảnh báo kèm stack trace ở mức `ERROR` trên server, nhưng chỉ trả về mã `INTERNAL_SERVER_ERROR` và thông điệp chung cho client, tuyệt đối không lộ stack trace.

### Lý do lựa chọn
- Khớp 100% với hợp đồng Single Source of Truth (SSOT) tại `contracts/openapi.yaml`.
- Giúp ứng dụng Frontend hiển thị thông báo lỗi thân thiện, đồng thời các lập trình viên tích hợp có thể viết code bắt lỗi theo `code` chuỗi một cách ổn định.

### Các giải pháp thay thế đã xem xét
- *Dùng mã lỗi số nguyên (int code: 1001, 1002) như gợi ý trong một số tài liệu cũ:* Bị từ chối vì vi phạm hợp đồng API `openapi.yaml` (nơi quy định `code` là chuỗi như `UPLOAD_LIMIT_EXCEEDED`). Quy tắc SSOT của repository bắt buộc tuân theo `openapi.yaml`.

---

## 4. Cấu hình Chia sẻ Tài nguyên Đa Nguồn (CORS Policy)

### Quyết định kỹ thuật
- Xây dựng `CorsConfigurationSource` bean và tích hợp trực tiếp vào `HttpSecurity` trong `SecurityConfig`:
  - `AllowedOrigins`: Đọc từ cấu hình `tvc.security.cors.allowed-origins` (mặc định hỗ trợ `http://localhost:5173`, `http://localhost:8000`).
  - `AllowedMethods`: `GET`, `POST`, `PATCH`, `DELETE`, `OPTIONS`.
  - `AllowedHeaders`: `Authorization`, `Content-Type`, `X-API-Key`, `Accept`, `Origin`, `X-Requested-With`.
  - `AllowCredentials`: `true`.
  - `MaxAge`: 3600 giây (1 giờ) để tối ưu hóa bộ nhớ đệm preflight của trình duyệt.
- Áp dụng cấu hình trên phạm vi URL `/api/**`.

### Lý do lựa chọn
- Khi cấu hình CORS ở tầng Spring Security Filter Chain, các preflight request (`OPTIONS`) sẽ được xử lý và trả về header CORS ngay lập tức trước khi chạm vào các bộ lọc xác thực (Authentication Filter), tránh tình trạng trình duyệt bị chặn preflight vì thiếu token xác thực.

---

## 5. Chiến lược Kiểm thử Toàn diện (Testing Strategy: Unit + Integration)

### Quyết định kỹ thuật
Hệ thống kiểm thử cho Feature F-002 được tổ chức thành 2 lớp rõ ràng:

1. **Lớp Kiểm thử Đơn vị (Unit Tests - Không phụ thuộc môi trường ngoài, thực thi nhanh):**
   - `TvcPropertiesValidationTest`: Thử nghiệm nạp cấu hình hợp lệ và các bộ cấu hình thiếu/sai (TTL âm, secret quá ngắn) bằng `ApplicationContextRunner` để kiểm chứng hành vi Fail-fast.
   - `GlobalExceptionHandlerTest`: Sử dụng `@WebMvcTest` hoặc `MockMvcStandalone` với một test controller giả lập để kích hoạt các loại lỗi (validation vi phạm, JSON lỗi, AppException, NullPointerException) và xác minh:
     - Mã trạng thái HTTP chính xác.
     - Cấu trúc JSON trả về có đúng `{ code, message, timestamp, details }`.
     - Không có stack trace hay thông tin nhạy cảm nào bị rò rỉ.
   - `CorsConfigTest`: Gửi các request preflight `OPTIONS` và request thường có header `Origin` hợp lệ/không hợp lệ để xác nhận header phản hồi CORS.
   - `AwsConfigUnitTest`: Kiểm thử đơn vị các logic cấu hình `endpointOverride` và `forcePathStyle`.

2. **Lớp Kiểm thử Tích hợp (Integration Tests - Sử dụng Testcontainers LocalStack):**
   - `AwsClientsIntegrationTest`:
     - Kế thừa hoặc cấu hình với `LocalStackContainer` từ Testcontainers (`localstack/localstack:3.0` hoặc mới nhất).
     - Kích hoạt các dịch vụ `DYNAMODB`, `S3`, `SQS`.
     - Chạy với `@SpringBootTest` đầy đủ.
     - Xác nhận tương tác thực tế với container:
       - `DynamoDbEnhancedClient`: Tạo bảng thử nghiệm, lưu và đọc bản ghi thành công.
       - `S3Client`: Tạo bucket và upload/download object thành công với path-style access.
       - `S3Presigner`: Tạo một Presigned PUT URL và dùng `HttpURLConnection` hoặc RestTemplate tải một payload mẫu lên S3 thành công.
       - `SqsAsyncClient`: Tạo queue, gửi message và nhận lại message thành công.

### Lý do lựa chọn
- Đáp ứng trọn vẹn yêu cầu chất lượng cao của dự án: vừa có Unit Test nhanh phục vụ CI feedback tức thì, vừa có Integration Test với LocalStack thật để đảm bảo cấu hình AWS SDK v2 không bao giờ gặp lỗi ngầm khi deploy.
