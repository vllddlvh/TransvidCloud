package com.transvidcloud.backend.apikey.dto;

public record ApiKeyCreatedResponse(
        String keyHash,
        String projectId,
        String name,
        String rawKey,
        String keyPrefix,
        String createdAt
) {
    @Override
    public String toString() {
        return "ApiKeyCreatedResponse[keyHash=" + keyHash + ", projectId=" + projectId + ", name=" + name
                + ", rawKey=[PROTECTED], keyPrefix=" + keyPrefix + ", createdAt=" + createdAt + "]";
    }
}
