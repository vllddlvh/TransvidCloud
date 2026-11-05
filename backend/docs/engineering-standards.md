# Engineering Standards

> Chỉ ghi các quy tắc đã được team xác nhận. Nêu nguồn hoặc lý do cho hard constraints quan trọng.

## Architecture

- Hard constraints: Chỉ dùng DynamoDB (AWS SDK v2 Enhanced Client), không dùng RDBMS/JPA (`../docs/adr/0001-dynamodb-only.md`). Không nhận/trung chuyển luồng byte video qua Backend.
- Patterns được ưu tiên: `Controller -> Service -> Repository / AWS Adapter`; tách biệt API DTO và DynamoDB Entity (`@DynamoDbBean`) bằng MapStruct.
- Anti-patterns cần tránh: Dùng DynamoDB `Scan` thay cho `Query` theo Partition Key/GSI; giả lập offset pagination thay vì dùng cursor `LastEvaluatedKey`; để lộ cấu trúc bảng DynamoDB trực tiếp ra REST response.

## Code conventions

- Naming/style: Chuẩn Java 21; dùng Lombok cho Entity/Model mutable và Java `record` cho DTO bất biến; cấu hình ứng dụng bằng YAML (`application.yml`).
- Error handling: Xử lý tập trung qua `@RestControllerAdvice`, không lộ stack trace hoặc chi tiết hạ tầng AWS nội bộ ra client. Validation/Error response với customized ErrorCode theo pattern sau:
```
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Uncategorized error", HttpStatus.BAD_REQUEST),
    USER_EXISTED(1002, "User existed", HttpStatus.BAD_REQUEST),
    USERNAME_INVALID(1003, "Username must be at least {min} characters", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1004, "Password must be at least {min} characters", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1005, "User not existed", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1006, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_DOB(1008, "Your age must be at least {min}", HttpStatus.BAD_REQUEST),
    ;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}

public class UserCreationRequest {
    @Size(min = 4, message = "USERNAME_INVALID")
    String username;

    @Size(min = 6, message = "INVALID_PASSWORD")
    String password;

    String firstName;
    String lastName;

    @DobConstraint(min = 10, message = "INVALID_DOB")
    LocalDate dob;
}

public class AppException extends RuntimeException {

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    private ErrorCode errorCode;

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }
}
```
```
@ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ApiResponse apiResponse = new ApiResponse();

        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }
```
- Logging/observability: Không log password, raw API key, JWT secret hoặc Presigned URL đầy đủ. Đính kèm `videoId`/`projectId`/`jobId` khi log nghiệp vụ.
- Dependency policy: Quản lý phiên bản qua Spring Boot BOM (`3.4.5`) và Spring Cloud AWS BOM (`3.3.0`).
- Standardized API Response: public class ApiResponse<T>
```
{
  @Builder.Default
  private int code = 1000;

  private String message;
  private T result;
  }
```

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
