# Research: F-009 — Quan Sát Hệ Thống, Bảo Mật Log & Bộ Kiểm Thử Tích Hợp / Contract

**Feature**: F-009 (Epic A5/A8/C5)  
**Date**: 2026-11-01  
**Status**: Completed  

---

## 1. Nghiên cứu Kỹ thuật 1: Truy Vết Tương Quan (Correlation Tracking) trong Spring Boot 3.4 & Virtual Threads

### Vấn đề
Hệ thống cần gán mã tương quan (`correlationId`) cho từng request gửi đến Backend và các tác vụ chạy nền (như `OutboxRelayScheduler`) để toàn bộ log của cùng một chu trình xử lý có thể liên kết với nhau, phục vụ điều tra sự cố trên CloudWatch Logs Insights (Epic C5). Ứng dụng đã kích hoạt Java 21 Virtual Threads (`spring.threads.virtual.enabled: true`), do đó việc quản lý ngữ cảnh qua `MDC` (Mapped Diagnostic Context của SLF4J / Logback) cần đảm bảo an toàn, không bị rò rỉ dữ liệu giữa các thread.

### Quyết định kỹ thuật
- **Triển khai `CorrelationIdFilter`** kế thừa `OncePerRequestFilter`:
  - Đặt thứ tự ưu tiên cao nhất (`Ordered.HIGHEST_PRECEDENCE` hoặc ngay trước Security Filter Chain).
  - Trích xuất header `X-Correlation-ID` (hoặc fallback `X-Request-ID`).
  - Nếu thiếu, rỗng hoặc chứa ký tự độc hại/quá dài (>128 ký tự): tự động sinh mới một UUID an toàn (`UUID.randomUUID().toString()`).
  - Đẩy vào `MDC.put("correlationId", correlationId)`.
  - Thiết lập header `X-Correlation-ID` trong `HttpServletResponse` để client nhận diện.
  - Sử dụng khối `try ... finally` để luôn gọi `MDC.clear()` ngay khi request kết thúc, bảo đảm an toàn tuyệt đối cho Virtual Threads và Thread Pool.
- **Tác vụ nền (`OutboxRelayScheduler`)**:
  - Tự động gán `MDC.put("correlationId", "sched-outbox-" + UUID.randomUUID())` ở đầu mỗi chu kỳ quét hoặc mỗi lượt dispatch message SQS, và giải phóng trong `finally`.
- **Bổ sung ngữ cảnh nghiệp vụ**:
  - Tại tầng Controller / Service khi xác định được `userId`, `projectId`, `videoId`, chủ động đưa vào MDC qua tiện ích `LogContext` để mọi dòng log nghiệp vụ sau đó tự động hiển thị các định danh này.

### Các phương án đã cân nhắc
- *Phương án thay thế 1 (Spring Cloud Sleuth / Micrometer Tracing)*: Mang theo nhiều dependency nặng, cấu hình phức tạp với OTel/Zipkin exporter. Bị loại vì dự án chỉ yêu cầu correlation ID trong log và HTTP header, áp dụng nguyên lý KISS / YAGNI.
- *Phương án thay thế 2 (Custom Interceptor)*: Chỉ bắt được ở tầng Spring MVC sau khi request đã đi qua hàng loạt filter bảo mật. Bị loại vì cần correlation ID ngay từ tầng Filter đầu tiên để log cả các lỗi xác thực (401/403).

---

## 2. Nghiên cứu Kỹ thuật 2: Định Dạng Xuất Log Linh Hoạt (Structured Dual-Format Logging)

### Vấn đề
Môi trường phát triển cục bộ (`local`/`test`) cần log text màu có cấu trúc để lập trình viên đọc gỡ lỗi trên terminal một cách tự nhiên. Trong khi đó, môi trường container triển khai trên AWS ECS/CloudWatch (Epic C5) cần log dạng JSON một dòng (Single-line JSON) chứa các trường chuẩn hóa để CloudWatch Logs Insights bóc tách chỉ số và thống kê lỗi mà không cần regex phức tạp.

### Quyết định kỹ thuật
- Cấu hình Logback thông qua tệp `logback-spring.xml` (hoặc profile-specific configuration):
  - Hỗ trợ biến môi trường `LOG_FORMAT` (giá trị: `text` hoặc `json`).
  - Mặc định (`local`, `test`, `dev`): Sử dụng Console Appender với Pattern Layout có màu và cấu trúc:
    ```text
    %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %highlight(%-5level) %cyan(%logger{36}) [corr=%X{correlationId:-none} user=%X{userId:-none} prj=%X{projectId:-none} vid=%X{videoId:-none}] - %msg%n
    ```
  - Khi `LOG_FORMAT=json` hoặc profile `docker`/`prod`: Sử dụng JSON Layout / Logstash Logback Encoder hoặc Jackson Layout xuất JSON một dòng chuẩn:
    ```json
    {
      "timestamp": "2026-11-01T17:50:00.123Z",
      "level": "INFO",
      "logger": "com.transvidcloud.backend.video.service.VideoService",
      "message": "Video upload initialized successfully",
      "correlationId": "550e8400-e29b-41d4-a716-446655440000",
      "userId": "usr_abc123",
      "projectId": "prj_xyz789",
      "videoId": "vid_mno456"
    }
    ```

### Các phương án đã cân nhắc
- *Chỉ dùng JSON trên mọi môi trường*: Gây khó khăn lớn cho lập trình viên khi quan sát log trực tiếp trên console trong quá trình dev và chạy unit test.
- *Chỉ dùng Plain text*: CloudWatch agent phải cấu hình parser regex phức tạp, dễ vỡ khi định dạng log thay đổi nhẹ.

---

## 3. Nghiên cứu Kỹ thuật 3: Cơ Chế Bảo Mật & Che Giấu Dữ Liệu Nhạy Cảm (Log Masking - Defense-in-Depth)

### Vấn đề
Các dữ liệu nhạy cảm như mật khẩu người dùng (`password`), token JWT (`Bearer eyJ...`), khóa API thô (`rawApiKey`), thông tin xác thực AWS (`aws_secret_access_key`), và đặc biệt là S3 Presigned URL chứa chữ ký tạm thời (`X-Amz-Signature=...`, `X-Amz-Credential=...`) tuyệt đối không được phép rò rỉ ra log stream.

### Quyết định kỹ thuật
Áp dụng chiến lược **Phòng thủ theo chiều sâu (Defense-in-Depth)** gồm 2 lớp độc lập:
1. **Lớp 1 — Tầng DTO / Mô hình Dữ liệu (Object Level)**:
   - Tất cả các DTO chứa secret (như `RegisterRequest`, `LoginRequest`, `ApiKeyResponse`, `TvcProperties`, `User`) chủ động override `toString()` hoặc cấu hình Lombok `@ToString(exclude = {"password", ...})` để không in ra dữ liệu nhạy cảm khi đối tượng được truyền vào log.
   - Khi log thao tác cấp S3 Presigned URL trong `VideoService`: Chủ động chỉ log `bucket`, `objectKey` và `expiresInMinutes`, không bao giờ gọi `log.info("URL: {}", presignedUrl)`.
2. **Lớp 2 — Tầng Logback Message Converter (Stream Level)**:
   - Triển khai `MaskingMessageConverter` (kế thừa `ch.qos.logback.classic.pattern.MessageConverter`) được đăng ký trong `logback-spring.xml`.
   - Sử dụng các biểu thức chính quy (Regex) tối ưu để quét và thay thế chuỗi nhạy cảm:
     - Regex JSON/Key-value: `"password"\s*:\s*"[^"]+"` -> `"password":"[PROTECTED]"`
     - Regex API Key: `"rawApiKey"\s*:\s*"[^"]+"` -> `"rawApiKey":"[PROTECTED]"`
     - Regex Bearer Token: `Bearer\s+[A-Za-z0-9\-_.~+/]+=*` -> `Bearer [PROTECTED]`
     - Regex S3 Presigned URL: Bất kỳ URL nào chứa `X-Amz-Signature=` sẽ được cắt gọt query string, chỉ giữ lại phần base URL an toàn (`https://.../bucket/path?X-Amz-Signature=[PROTECTED]`).
   - Lớp này đóng vai trò như "chốt chặn an toàn cuối cùng", bảo vệ hệ thống ngay cả khi lập trình viên vô tình log trực tiếp một đối tượng chưa qua xử lý hoặc khi xảy ra lỗi ngoại lệ in stack trace.

---

## 4. Nghiên cứu Kỹ thuật 4: Kiến Trúc Bộ Kiểm Thử Tích Hợp Vòng Đời & Khớp Hợp Đồng (Epic A5/A8)

### Vấn đề
Dự án cần một bộ kiểm thử tích hợp thực tế (Real Verification) chạy trên môi trường LocalStack để chứng minh toàn bộ chu trình Backend khớp 100% với hợp đồng kiến trúc (OpenAPI, SQS Schema, DynamoDB tables, Multi-tenant Isolation) mà không có bất kỳ giả định chủ quan nào.

### Quyết định kỹ thuật
Tổ chức thành 2 test class chuyên biệt chạy với `@SpringBootTest` và Testcontainers LocalStack:

1. **`ObservabilityLoggingIT`** (`src/test/java/.../common/logging/`):
   - Sử dụng `OutputCaptureExtension` của Spring Boot để bắt toàn bộ log stream sinh ra trong quá trình test.
   - Test Case 1: Gửi request không có header -> Xác nhận sinh `correlationId` mới, có trong response header và trong log.
   - Test Case 2: Gửi request có `X-Correlation-ID` -> Xác nhận tái sử dụng đúng chuỗi đó.
   - Test Case 3: Gửi request đăng nhập / đăng ký với password, tạo API Key, cấp Presigned URL -> Bắt log stream và khẳng định 100% không chứa password bản rõ, không chứa raw key, không chứa `X-Amz-Signature`.
2. **`VideoFullLifecycleIT`** (`src/test/java/.../video/repository/`):
   - Kịch bản xuyên suốt (End-to-End Contract Verification):
     1. Khởi tạo 2 người dùng độc lập: Tenant A và Tenant B.
     2. Tenant A tạo Project A và sinh API Key A.
     3. Dùng API Key A khởi tạo upload Video A -> Nhận S3 Presigned URL.
     4. Giả lập client tải file nhị phân lên S3 input bucket qua AWS S3 Client.
     5. Gọi xác nhận upload (`POST /api/videos/{videoId}/confirm`).
     6. Đọc SQS message từ queue `tvc-local-jobs` -> Thẩm định cấu trúc JSON đối chiếu với `../contracts/sqs-job-message.schema.json` (chứa đầy đủ `jobId`, `videoId`, `projectId`, `inputBucket`, `inputKey`, `outputBucket`, `outputPrefix`, `createdAt`, `schemaVersion: 1`).
     7. Giả lập Worker cập nhật video sang `COMPLETED` với mock HLS assets trên DynamoDB.
     8. Gọi API `GET /api/videos/{videoId}` -> Xác nhận assets đã ghép `TVC_MEDIA_BASE_URL`.
     9. Gọi API `GET /api/projects/{projectId}/videos` -> Xác nhận video hiển thị trong danh sách phân trang.
     10. Kiểm tra Multi-tenant Isolation: Dùng token/key của Tenant B để truy cập hoặc thao tác trên Video A -> Xác nhận trả về HTTP 403 Forbidden (`ACCESS_DENIED`).
