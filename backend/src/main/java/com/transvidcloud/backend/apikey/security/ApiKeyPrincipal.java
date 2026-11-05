package com.transvidcloud.backend.apikey.security;

import java.security.Principal;

/**
 * Principal đại diện cho định danh của Client khi xác thực qua X-API-Key.
 */
public record ApiKeyPrincipal(
        String projectId,
        String name,
        String keyPrefix
) implements Principal {

    @Override
    public String getName() {
        return "api-key:" + projectId + ":" + keyPrefix;
    }
}
