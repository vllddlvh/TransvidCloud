package com.transvidcloud.backend.apikey.security;

import java.util.Collection;
import java.util.Collections;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication Token của Spring Security cho ngữ cảnh xác thực bằng API Key.
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final ApiKeyPrincipal principal;
    private final String rawKey;

    public ApiKeyAuthenticationToken(ApiKeyPrincipal principal, String rawKey, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.rawKey = rawKey;
        setAuthenticated(true);
    }

    public static ApiKeyAuthenticationToken unauthenticated(String rawKey) {
        return new ApiKeyAuthenticationToken(null, rawKey, Collections.emptyList());
    }

    @Override
    public Object getCredentials() {
        return rawKey;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public String getProjectId() {
        return principal != null ? principal.projectId() : null;
    }
}
