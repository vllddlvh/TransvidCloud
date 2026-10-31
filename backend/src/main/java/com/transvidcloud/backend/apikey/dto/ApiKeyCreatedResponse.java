package com.transvidcloud.backend.apikey.dto;

public record ApiKeyCreatedResponse(
        String keyHash,
        String projectId,
        String name,
        String rawKey,
        String keyPrefix,
        String createdAt
) {}
