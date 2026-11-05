package com.transvidcloud.backend.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tọa độ con trỏ phân trang trên GSI project-index của bảng tvc-{env}-videos.
 * Đại diện cho LastEvaluatedKey của DynamoDB phục vụ mã hóa/giải mã Base64URL.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoCursor(
        String projectId,
        String videoId,
        String createdAt
) {}
