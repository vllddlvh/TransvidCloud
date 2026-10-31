package com.transvidcloud.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO đăng nhập tài khoản SaaS.
 */
public record LoginRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        String password
) {}
