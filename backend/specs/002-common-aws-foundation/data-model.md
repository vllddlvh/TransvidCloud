# Data Model & Configuration Schema: Nền tảng Common & AWS (F-002)

**Feature**: `002-common-aws-foundation` | **Ngày lập**: 2026-10-31 | **Trạng thái**: Hoàn thành (Design)

Tài liệu này đặc tả cấu trúc mô hình dữ liệu nội bộ, schema cấu hình hệ thống, và các thành phần cốt lõi của Feature F-002.

---

## 1. Mô hình Phản hồi Lỗi (`ErrorResponse`)

Phù hợp tuyệt đối với định nghĩa `ErrorResponse` trong hợp đồng `contracts/openapi.yaml`.

### 1.1. Cấu trúc Record `ErrorResponse`

```java
package com.transvidcloud.backend.common.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
    String code,
    String message,
    Instant timestamp,
    Map<String, Object> details
) {
    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now(), null);
    }

    public ErrorResponse(String code, String message, Map<String, Object> details) {
        this(code, message, Instant.now(), details);
    }
}
```

### 1.2. Thuộc tính & Ràng buộc

| Thuộc tính | Kiểu dữ liệu | Bắt buộc | Mô tả | Ví dụ |
|---|---|---|---|---|
| `code` | `String` | Có | Mã định danh lỗi máy đọc được | `"VALIDATION_ERROR"`, `"UPLOAD_LIMIT_EXCEEDED"` |
| `message` | `String` | Có | Thông điệp giải thích lỗi thân thiện với người dùng | `"Dung lượng file vượt quá giới hạn tối đa 200 MB"` |
| `timestamp` | `Instant` | Có | Thời điểm lỗi phát sinh (ISO-8601 UTC) | `"2026-10-31T10:15:30Z"` |
| `details` | `Map<String, Object>` | Không | Thông tin bổ sung, đặc biệt hữu ích khi validation thất bại | `{"sizeBytes": "must be less than or equal to 209715200"}` |

---

## 2. Bảng Mã Lỗi Hệ thống (`ErrorCode`)

Định nghĩa enum quản lý tập trung mã lỗi, thông điệp mặc định và HTTP status tương ứng:

```java
package com.transvidcloud.backend.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 400 Bad Request
    VALIDATION_ERROR("VALIDATION_ERROR", "Dữ liệu yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    MALFORMED_JSON("MALFORMED_JSON", "Định dạng JSON trong request body không hợp lệ", HttpStatus.BAD_REQUEST),
    TYPE_MISMATCH("TYPE_MISMATCH", "Kiểu dữ liệu của tham số không đúng", HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER("INVALID_PARAMETER", "Tham số không hợp lệ", HttpStatus.BAD_REQUEST),

    // 401 Unauthorized
    UNAUTHENTICATED("UNAUTHENTICATED", "Yêu cầu xác thực tài khoản hoặc token không hợp lệ", HttpStatus.UNAUTHORIZED),

    // 403 Forbidden
    UNAUTHORIZED("UNAUTHORIZED", "Bạn không có quyền thực hiện thao tác trên tài nguyên này", HttpStatus.FORBIDDEN),

    // 404 Not Found
    NOT_FOUND("NOT_FOUND", "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "Phương thức HTTP không được hỗ trợ trên endpoint này", HttpStatus.METHOD_NOT_ALLOWED),

    // 409 Conflict
    CONFLICT("CONFLICT", "Trạng thái tài nguyên xung đột với yêu cầu thao tác", HttpStatus.CONFLICT),

    // 413 Payload Too Large
    UPLOAD_LIMIT_EXCEEDED("UPLOAD_LIMIT_EXCEEDED", "Dung lượng tải lên vượt quá giới hạn tối đa cho phép", HttpStatus.PAYLOAD_TOO_LARGE),

    // 422 Unprocessable Entity
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "Định dạng phương tiện hoặc nội dung không được hỗ trợ", HttpStatus.UNPROCESSABLE_ENTITY),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Đã xảy ra sự cố nội bộ trong hệ thống. Vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public String getDefaultMessage() { return defaultMessage; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
```

---

## 3. Ngoại lệ Nghiệp vụ Nền tảng (`AppException`)

```java
package com.transvidcloud.backend.common.exception;

import java.util.Map;

public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public AppException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.details = null;
    }

    public AppException(ErrorCode errorCode, String customMessage, Map<String, Object> details) {
        super(customMessage);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public Map<String, Object> getDetails() { return details; }
}
```

---

## 4. Schema Cấu hình Tập trung (`TvcProperties`)

Ánh xạ từ tiền tố cấu hình `tvc` trong `application.yml`:

```java
package com.transvidcloud.backend.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "tvc")
public class TvcProperties {

    @Valid
    @NotNull
    private AwsProperties aws = new AwsProperties();

    @Valid
    @NotNull
    private UploadProperties upload = new UploadProperties();

    @Valid
    @NotNull
    private SecurityProperties security = new SecurityProperties();

    @Valid
    @NotNull
    private MediaProperties media = new MediaProperties();

    @Getter
    @Setter
    public static class AwsProperties {
        private String endpoint; // null hoặc URL (ví dụ http://localhost:4566)

        @Valid
        @NotNull
        private S3Properties s3 = new S3Properties();

        @Valid
        @NotNull
        private SqsProperties sqs = new SqsProperties();

        @Valid
        @NotNull
        private DynamoDbProperties dynamodb = new DynamoDbProperties();
    }

    @Getter
    @Setter
    public static class S3Properties {
        @NotBlank
        private String inputBucket = "tvc-local-input";

        @NotBlank
        private String outputBucket = "tvc-local-output";

        private String presignEndpoint;

        @Min(1)
        private int presignTtlMinutes = 15;
    }

    @Getter
    @Setter
    public static class SqsProperties {
        @NotBlank
        private String jobsQueueName = "tvc-local-jobs";
    }

    @Getter
    @Setter
    public static class DynamoDbProperties {
        @NotBlank
        private String tablePrefix = "tvc-local-";
    }

    @Getter
    @Setter
    public static class UploadProperties {
        @Min(1)
        private long maxBytes = 209715200L; // 200 MB

        @NotEmpty
        private List<String> allowedContentTypes = List.of(
            "video/mp4",
            "video/quicktime",
            "video/x-matroska",
            "video/webm"
        );
    }

    @Getter
    @Setter
    public static class SecurityProperties {
        @Valid
        @NotNull
        private JwtProperties jwt = new JwtProperties();

        @Valid
        @NotNull
        private CorsProperties cors = new CorsProperties();
    }

    @Getter
    @Setter
    public static class JwtProperties {
        @NotBlank
        @Size(min = 32, message = "JWT secret must be at least 32 bytes long for HMAC-SHA256")
        private String secret;

        @Min(60)
        private long expirationSeconds = 86400L;
    }

    @Getter
    @Setter
    public static class CorsProperties {
        @NotEmpty
        private List<String> allowedOrigins = List.of("http://localhost:5173", "http://localhost:8000");
    }

    @Getter
    @Setter
    public static class MediaProperties {
        @NotBlank
        private String baseUrl = "/media";
    }
}
```
