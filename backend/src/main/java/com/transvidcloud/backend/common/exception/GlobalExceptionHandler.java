package com.transvidcloud.backend.common.exception;

import com.transvidcloud.backend.common.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Bộ xử lý ngoại lệ tập trung toàn cục cho toàn bộ REST API.
 * Đảm bảo 100% phản hồi lỗi (4xx và 5xx) tuân thủ schema contracts/openapi.yaml.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse response = new ErrorResponse(
                ErrorCode.VALIDATION_ERROR.getCode(),
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                Instant.now(),
                details.isEmpty() ? null : details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        log.warn("Malformed JSON received: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.MALFORMED_JSON.getCode(),
                ErrorCode.MALFORMED_JSON.getDefaultMessage(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                ErrorCode.METHOD_NOT_ALLOWED.getCode(),
                ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                ErrorCode.NOT_FOUND.getCode(),
                ErrorCode.NOT_FOUND.getDefaultMessage(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Map<String, Object> details = Map.of(
                "parameter", ex.getName(),
                "expectedType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );
        ErrorResponse response = new ErrorResponse(
                ErrorCode.TYPE_MISMATCH.getCode(),
                ErrorCode.TYPE_MISMATCH.getDefaultMessage(),
                Instant.now(),
                details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (var result : ex.getAllValidationResults()) {
            String paramName = result.getMethodParameter().getParameterName();
            for (var resolvableError : result.getResolvableErrors()) {
                details.put(paramName != null ? paramName : "parameter", resolvableError.getDefaultMessage());
            }
        }
        ErrorResponse response = new ErrorResponse(
                ErrorCode.VALIDATION_ERROR.getCode(),
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                Instant.now(),
                details.isEmpty() ? null : details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            details.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        ErrorResponse response = new ErrorResponse(
                ErrorCode.VALIDATION_ERROR.getCode(),
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                Instant.now(),
                details.isEmpty() ? null : details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        ErrorResponse response = new ErrorResponse(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getDefaultMessage(),
                Instant.now(),
                ex.getDetails()
        );
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.UNAUTHENTICATED.getCode(),
                ErrorCode.UNAUTHENTICATED.getDefaultMessage(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.UNAUTHORIZED.getCode(),
                ErrorCode.UNAUTHORIZED.getDefaultMessage(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnhandledException(Exception ex) {
        log.error("Unhandled server exception encountered: ", ex);
        ErrorResponse response = new ErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
