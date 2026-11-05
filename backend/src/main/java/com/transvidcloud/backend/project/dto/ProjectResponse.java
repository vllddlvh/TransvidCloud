package com.transvidcloud.backend.project.dto;

import java.util.List;

/**
 * Response DTO chi tiết Project.
 */
public record ProjectResponse(
        String projectId,
        String ownerId,
        String name,
        String description,
        String webhookUrl,
        String createdAt,
        String updatedAt
) {}
