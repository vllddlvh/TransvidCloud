package com.transvidcloud.backend.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO khởi tạo Project mới.
 */
public record CreateProjectRequest(
        @NotBlank(message = "Tên project không được để trống")
        @Size(min = 1, max = 120, message = "Tên project phải từ 1 đến 120 ký tự")
        String name,

        @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
        String description,

        @Pattern(regexp = "^(https?://.+)?$", message = "Webhook URL phải là URL hợp lệ (bắt đầu bằng http:// hoặc https://)")
        String webhookUrl
) {}
