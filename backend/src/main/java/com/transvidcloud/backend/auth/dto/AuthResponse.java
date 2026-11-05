package com.transvidcloud.backend.auth.dto;

/**
 * Response DTO trả về phiên xác thực (JWT Access Token) và thông tin người dùng.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserSummary user
) {
    public static AuthResponse bearer(String accessToken, long expiresInSeconds, UserSummary user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
