package com.transvidcloud.backend.project.dto;

import java.util.List;

/**
 * Response DTO danh sách Project.
 */
public record ProjectListResponse(
        List<ProjectResponse> items
) {}
