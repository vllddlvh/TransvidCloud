package com.transvidcloud.backend.video.dto;

import com.transvidcloud.backend.video.entity.VideoStatus;
import java.time.Instant;
import java.util.Map;

/**
 * Phản hồi chi tiết thông tin Video khớp với hợp đồng contracts/openapi.yaml.
 */
public record VideoResponse(
        String videoId,
        String projectId,
        String title,
        VideoStatus status,
        Long sizeBytes,
        String contentType,
        Integer attemptCount,
        String errorCode,
        Map<String, String> assets,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt
) {}
