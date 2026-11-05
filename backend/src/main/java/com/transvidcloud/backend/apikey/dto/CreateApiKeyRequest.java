package com.transvidcloud.backend.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(
        @NotBlank(message = "API key name must not be blank")
        @Size(min = 1, max = 100, message = "API key name must be between 1 and 100 characters")
        String name
) {}
