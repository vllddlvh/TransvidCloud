package com.transvidcloud.backend.auth.dto;

/**
 * DTO tóm tắt thông tin người dùng SaaS.
 */
public record UserSummary(
        String userId,
        String email,
        String fullName,
        String createdAt
) {}
