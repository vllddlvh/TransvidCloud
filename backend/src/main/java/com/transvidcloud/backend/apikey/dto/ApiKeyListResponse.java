package com.transvidcloud.backend.apikey.dto;

import java.util.List;

public record ApiKeyListResponse(
        List<ApiKeyMetadataResponse> items
) {}
