package com.transvidcloud.backend.common.exception;

import java.util.Map;

/**
 * Ngoại lệ nghiệp vụ nền tảng cho TransvidCloud Backend.
 */
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

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
