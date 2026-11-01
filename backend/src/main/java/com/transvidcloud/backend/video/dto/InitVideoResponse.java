package com.transvidcloud.backend.video.dto;

/**
 * Phản hồi khởi tạo video thành công kèm thông tin S3 Presigned URL.
 */
public record InitVideoResponse(
        VideoResponse video,
        PresignedUploadInfo upload
) {}
