package com.transvidcloud.backend.video.dto;

import java.util.Map;

/**
 * Thông tin Presigned URL và các HTTP headers bắt buộc gửi kèm khi tải tệp tin lên S3.
 */
public record PresignedUploadInfo(
        String presignedUrl,
        String method,
        int expiresInSeconds,
        Map<String, String> requiredHeaders
) {}
