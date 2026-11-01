package com.transvidcloud.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO đăng ký tài khoản SaaS mới.
 */
public record RegisterRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 100, message = "Mật khẩu phải từ 8 đến 100 ký tự")
        String password,

        @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
        String fullName
) {
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=[PROTECTED], fullName=" + fullName + "]";
    }
}
