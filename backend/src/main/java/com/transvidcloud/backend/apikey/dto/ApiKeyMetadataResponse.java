package com.transvidcloud.backend.apikey.dto;

public record ApiKeyMetadataResponse(
        String keyHash,
        String projectId,
        String name,
        String keyPrefix,
        String createdAt
) {}
