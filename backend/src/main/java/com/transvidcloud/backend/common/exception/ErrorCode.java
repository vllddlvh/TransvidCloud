package com.transvidcloud.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Bảng liệt kê các mã lỗi chuẩn của TransvidCloud Backend.
 * Mỗi mã lỗi ánh xạ với mã máy đọc được (String code), thông báo mặc định và HttpStatus.
 */
public enum ErrorCode {
    // 400 Bad Request
    VALIDATION_ERROR("VALIDATION_ERROR", "Dữ liệu yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    MALFORMED_JSON("MALFORMED_JSON", "Định dạng JSON trong request body không hợp lệ", HttpStatus.BAD_REQUEST),
    TYPE_MISMATCH("TYPE_MISMATCH", "Kiểu dữ liệu của tham số không đúng", HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER("INVALID_PARAMETER", "Tham số không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_KEY("INVALID_KEY", "Khóa API hoặc tham số yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),

    // 401 Unauthorized
    UNAUTHENTICATED("UNAUTHENTICATED", "Yêu cầu xác thực tài khoản hoặc token không hợp lệ", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Email hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),

    // 403 Forbidden
    UNAUTHORIZED("UNAUTHORIZED", "Bạn không có quyền thực hiện thao tác trên tài nguyên này", HttpStatus.FORBIDDEN),
    ACCESS_DENIED("ACCESS_DENIED", "Bạn không có quyền truy cập tài nguyên này", HttpStatus.FORBIDDEN),

    // 404 Not Found
    NOT_FOUND("NOT_FOUND", "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "Phương thức HTTP không được hỗ trợ trên endpoint này", HttpStatus.METHOD_NOT_ALLOWED),

    // 409 Conflict
    CONFLICT("CONFLICT", "Trạng thái tài nguyên xung đột với yêu cầu thao tác", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "Địa chỉ email đã được sử dụng trên hệ thống", HttpStatus.CONFLICT),
    RESOURCE_IN_USE("RESOURCE_IN_USE", "Tài nguyên đang được sử dụng hoặc có tiến trình xử lý chưa hoàn tất", HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION("INVALID_STATE_TRANSITION", "Trạng thái video hiện tại không cho phép thực hiện thao tác này", HttpStatus.CONFLICT),

    // 413 Payload Too Large
    UPLOAD_LIMIT_EXCEEDED("UPLOAD_LIMIT_EXCEEDED", "Dung lượng tải lên vượt quá giới hạn tối đa cho phép", HttpStatus.PAYLOAD_TOO_LARGE),

    // 422 Unprocessable Entity
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "Định dạng phương tiện hoặc nội dung không được hỗ trợ", HttpStatus.UNPROCESSABLE_ENTITY),
    S3_OBJECT_NOT_FOUND("S3_OBJECT_NOT_FOUND", "Tệp đối tượng chưa tồn tại trên hệ thống lưu trữ S3", HttpStatus.UNPROCESSABLE_ENTITY),
    UPLOAD_SIZE_MISMATCH("UPLOAD_SIZE_MISMATCH", "Dung lượng tệp tải lên không hợp lệ hoặc rỗng", HttpStatus.UNPROCESSABLE_ENTITY),

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

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
