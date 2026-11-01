package com.transvidcloud.backend.video.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu khởi tạo tải video mới và cấp S3 Presigned URL.
 */
public record InitVideoRequest(
        @NotBlank(message = "Tiêu đề video không được để trống")
        @Size(min = 1, max = 200, message = "Tiêu đề video phải từ 1 đến 200 ký tự")
        String title,

        @NotBlank(message = "Tên tệp tin gốc không được để trống")
        @Size(min = 1, max = 255, message = "Tên tệp tin phải từ 1 đến 255 ký tự")
        String filename,

        @NotNull(message = "Dung lượng tệp không được để trống")
        @Min(value = 1, message = "Dung lượng tệp phải lớn hơn 0 byte")
        Long sizeBytes,

        @NotBlank(message = "Loại nội dung contentType không được để trống")
        String contentType
) {}
