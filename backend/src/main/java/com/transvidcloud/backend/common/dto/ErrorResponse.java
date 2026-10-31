package com.transvidcloud.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Phản hồi lỗi chuẩn hóa theo hợp đồng contracts/openapi.yaml (ErrorResponse).
 *
 * @param code Mã định danh lỗi máy đọc được (machine-readable code).
 * @param message Thông điệp mô tả lỗi dành cho người dùng.
 * @param timestamp Thời điểm phát sinh lỗi theo chuẩn ISO-8601 UTC.
 * @param details Thông tin chi tiết bổ sung (ví dụ danh sách trường vi phạm validation).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
